/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.forge.cli.command

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf

/**
 * {@code project migrate} reads the build file and the settings Gradle builds the project with. A
 * {@code build.gradle} or {@code settings.gradle} that is a link to a file is that file, as it is for
 * Gradle; one that is a link to nothing is no file for Gradle, which then builds with the
 * {@code .kts} file beside it, and so that one is read.
 */
@IgnoreIf({ os.windows })
class ProjectMigrateLinkedBuildFilesSpec extends CommandSpec {

    static final String KOTLIN_BUILD = 'plugins {\n    id("org.apache.grails.gradle.grails-web")\n}\n\ngroup = "com.example.kotlin"\n'
    static final String GROOVY_BUILD = 'plugins {\n    id "org.apache.grails.gradle.grails-web"\n    id "org.apache.grails.gradle.grails-gsp"\n}\n\ngroup = "com.example.linked"\n'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        // where a change of a project that was interrupted is looked for
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        new File(dir, 'grails-app/conf').mkdirs()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    void "a build file and settings that are links to nothing are not the ones Gradle builds with, and the Kotlin ones beside them are read"() {
        given:
        Files.createSymbolicLink(new File(dir, 'build.gradle').toPath(), Path.of('conf/nothing.gradle'))
        Files.createSymbolicLink(new File(dir, 'settings.gradle').toPath(), Path.of('conf/nothing.gradle'))
        new File(dir, 'build.gradle.kts').text = KOTLIN_BUILD
        new File(dir, 'settings.gradle.kts').text = 'rootProject.name = "kshop"\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'type') == ['rest-api', '(the Gradle plugin grails-web of build.gradle.kts, without grails-gsp)']
        row(said, 'name') == ['kshop', '(rootProject.name of settings.gradle.kts)']
        row(said, 'package') == ['com.example.kotlin', '(group of build.gradle.kts)']
    }

    void "a build file and settings that are links to files are read as the files they lead to, also where Kotlin ones are beside them"() {
        given:
        new File(dir, 'conf').mkdirs()
        new File(dir, 'conf/build.gradle').text = GROOVY_BUILD
        new File(dir, 'conf/settings.gradle').text = "rootProject.name = 'linked'\n"
        Files.createSymbolicLink(new File(dir, 'build.gradle').toPath(), Path.of('conf/build.gradle'))
        Files.createSymbolicLink(new File(dir, 'settings.gradle').toPath(), Path.of('conf/settings.gradle'))
        new File(dir, 'build.gradle.kts').text = KOTLIN_BUILD
        new File(dir, 'settings.gradle.kts').text = 'rootProject.name = "kshop"\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'type') == ['web', '(the Gradle plugins grails-web and grails-gsp of build.gradle)']
        row(said, 'name') == ['linked', '(rootProject.name of settings.gradle)']
        row(said, 'package') == ['com.example.linked', '(group of build.gradle)']
        !said.any { it.contains('layout ') }
    }

    private static List<String> row(List<String> said, String what) {
        String line = said.find { String each -> each.startsWith("    ${what} ") }
        assert line != null: "no row ${what} in ${said}"
        String rest = line.substring(4 + what.length()).trim()
        int open = rest.indexOf(' (')
        return [rest.substring(0, open).trim(), rest.substring(open + 1).trim()]
    }
}

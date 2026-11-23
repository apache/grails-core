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
package org.apache.grails.forge.cli.console

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Predicate
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * A Gradle wrapper above the current directory that is a link runs the jar and the properties beside the
 * file the link leads to: the script finds them from where it really is, not from where the link is. So
 * those are held to the rule a wrapper above is held to, and so are the ones beside the link, as before.
 * Here the project is the current directory, and the root of its build above it has a {@code gradlew}
 * that is a link to {@code tools/gradlew}; {@code gradle/wrapper} beside the link and
 * {@code tools/gradle/wrapper} beside its file have files of their own.
 */
@IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
class LinkedWrapperSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File root
    File project

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        root = dir.canonicalFile
        project = new File(root, 'app')
        new File(project, 'grails-app').mkdirs()
        new File(root, 'settings.gradle').text = "include 'app'\n"
        File tools = new File(root, 'tools')
        tools.mkdirs()
        File script = new File(tools, 'gradlew')
        script.text = '#!/bin/sh\necho "the wrapper ran with $*"\n'
        script.setExecutable(true, false)
        Files.createSymbolicLink(new File(root, 'gradlew').toPath(), Path.of('tools/gradlew'))
        [root, tools].each { File holder ->
            new File(holder, 'gradle/wrapper').mkdirs()
            new File(holder, 'gradle/wrapper/gradle-wrapper.jar').text = "the jar of ${holder.name}"
            new File(holder, 'gradle/wrapper/gradle-wrapper.properties').text = "distributionUrl=https\\://example.invalid/${holder.name}.zip\n"
        }
        System.setProperty('user.dir', project.path)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
    }

    @Unroll
    void "the wrapper is not run when #what beside the file the link leads to is not the user's own"() {
        given:
        ConsoleProject.setOwnedByUser({ Path path -> path != root.toPath().resolve(notOwned) && ProcessUser.current().owns(path) } as Predicate<Path>)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        !out.toString().contains('the wrapper ran')
        errors().size() == 1
        errors()[0].contains("The Gradle wrapper ${root.path}/gradlew is not run for the project in ${project.path}: ${root.path}/${notOwned} is owned by ")
        errors()[0].contains("name ${root.path} in the environment variable GRAILS_SAFE_DIRECTORY to run it all the same")

        when: 'the root is named as a directory to use whoever owns it'
        out.reset()
        ConsoleProject.setSafeDirectories(root.path)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with build')

        where:
        what                    | notOwned
        'the jar'               | 'tools/gradle/wrapper/gradle-wrapper.jar'
        'the properties'        | 'tools/gradle/wrapper/gradle-wrapper.properties'
        'gradle/wrapper'        | 'tools/gradle/wrapper'
        'gradle'                | 'tools/gradle'
    }

    void "the files beside the link are still held to the rule"() {
        given:
        ConsoleProject.setOwnedByUser({ Path path -> path != root.toPath().resolve('gradle/wrapper/gradle-wrapper.jar') && ProcessUser.current().owns(path) } as Predicate<Path>)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        !out.toString().contains('the wrapper ran')
        errors()[0].contains("${root.path}/gradle/wrapper/gradle-wrapper.jar is owned by ")
    }

    void "a jar beside the file the link leads to that has another name elsewhere is not run"() {
        given: 'the jar the script runs is a second name of a file outside the build'
        File elsewhere = Files.createTempFile(root.parentFile.toPath(), 'grailsforgejar', '.jar').toFile()
        File jar = new File(root, 'tools/gradle/wrapper/gradle-wrapper.jar')
        jar.delete()
        Files.createLink(jar.toPath(), elsewhere.toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        !out.toString().contains('the wrapper ran')
        errors()[0].contains("${jar.path} has another name elsewhere (a hard link)")

        cleanup:
        elsewhere?.delete()
    }

    void "a directory beside the file the link leads to that everybody can write to is not run from"() {
        given:
        Files.setPosixFilePermissions(new File(root, 'tools/gradle/wrapper').toPath(), PosixFilePermissions.fromString('rwxrwxrwx'))

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        !out.toString().contains('the wrapper ran')
        errors()[0].contains("${root.path}/tools/gradle/wrapper can be written to by everybody")
    }

    void "a link whose file has its jar and properties the user's own is run"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        errors() == []
        out.toString().contains('the wrapper ran with build')
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

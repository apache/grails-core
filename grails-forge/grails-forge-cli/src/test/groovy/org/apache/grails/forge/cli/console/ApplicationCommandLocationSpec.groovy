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

import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli

/**
 * Where the command line finds the commands of the application and its plugins: in the file the
 * Gradle task {@code grailsCommandCatalog} writes into the directory of the project, whatever the build
 * of the project does with its build directory, which the command line does not run the build to
 * learn. A file an earlier build left in a build directory is not read.
 */
class ApplicationCommandLocationSpec extends CommandSpec implements CommandFixture {

    private static final Map STALE = [name: 'stale-command', aliases: [], description: 'Listed by an earlier build', origin: 'application', target: 'application',
                                      registration: 'descriptor']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "the command line reads the catalog where the task of the build writes it"() {
        expect:
        ApplicationCommandFile.LOCATION == CommandCatalogBuilder.LOCATION
        ApplicationCommandFile.LOCATION == '.grails/cache/command-catalog.json'
    }

    void "in a project whose #dsl build moves its build directory the commands are those of the catalog in the directory of the project"() {
        given: 'a build that moves its build directory, and catalogs that earlier builds left in build directories'
        File script = file(buildFile, moving)
        file('build/grails/command-catalog.json', ApplicationCommandsSpec.doc([STALE]))
        file('out/grails/command-catalog.json', ApplicationCommandsSpec.doc([STALE]))
        File catalog = file(ApplicationCommandFile.LOCATION, ApplicationCommandsSpec.doc([ApplicationCommandsSpec.HELLO]))
        catalog.setLastModified(script.lastModified() + 2000)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == true
        described.diagnostics == []
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == ['hello-world']

        when: 'the command is run by its name'
        out.reset()
        err.reset()
        ForgeCli.execute(beanContext, 'stale-command')

        then: 'what an earlier build listed is no command'
        err.toString().contains('stale-command')
        !all().contains('Running the command stale-command')

        where:
        dsl      | buildFile          | moving
        'Groovy' | 'build.gradle'     | "layout.buildDirectory = layout.projectDirectory.dir('out')\n"
        'Kotlin' | 'build.gradle.kts' | 'layout.buildDirectory.set(layout.projectDirectory.dir("out"))\n'
    }

    void "a catalog that an earlier build left in the build directory alone is not read, and the task that lists the commands is named"() {
        given:
        file('build.gradle', "layout.buildDirectory = layout.projectDirectory.dir('out')\n")
        File stale = file('build/grails/command-catalog.json', ApplicationCommandsSpec.doc([STALE]))
        stale.setLastModified(System.currentTimeMillis() + 2000)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == [ApplicationCommandsSpec.MISSING]
        ((List<Map>) described.commands).every { it.origin == 'built-in' }
    }

    void "the commands of a generated application whose build moves its build directory are there once the build has listed them"() {
        given: 'an application with a command, whose build directory is moved, and a catalog an earlier build left in the build directory it had'
        generateProject(ApplicationType.WEB)
        new File(dir, 'build.gradle') << "\nlayout.buildDirectory = layout.projectDirectory.dir('out')\n"
        ForgeCli.execute(beanContext, 'create-command', 'greet')
        file('build/grails/command-catalog.json', ApplicationCommandsSpec.doc([STALE]))

        when: 'the build lists the commands, as the command line says to'
        out.reset()
        int listed = ForgeCli.execute(beanContext, 'gradle', 'grailsCommandCatalog')

        then:
        listed == 0
        new File(dir, ApplicationCommandFile.LOCATION).isFile()
        new File(dir, 'out').isDirectory()
        !new File(dir, 'out/grails').exists()

        when:
        out.reset()
        err.reset()
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then: 'the command of the application is one of the command line, and what the earlier build listed is not'
        exit == 0
        ((List<Map>) described.commands).findAll { it.origin == 'application' }*.name == ['greet']
        !((List<Map>) described.commands).any { it.name == STALE.name }
        !((List<String>) described.diagnostics).any { it.contains(ApplicationCommandFile.LOCATION) }
    }

    private File file(String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.setText(text, StandardCharsets.UTF_8.name())
        return file
    }

    private Map document() {
        return (Map) new JsonSlurper().parseText(out.toString().readLines().last())
    }

    private String all() {
        return out.toString() + err.toString()
    }
}

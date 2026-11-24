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

import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout

/**
 * A command the application was given with {@code create-command} is a command of the command line by its
 * name, in a project that was never built: the build lists the commands of the application, which
 * compiles it, and the command is run in the application. As {@code create-command} writes it, the command says
 * what it was given and succeeds. Once the build has compiled the project the list is current, also for a
 * command added since. A command that extends another is listed, and run, by the name it has when it runs.
 */
@IgnoreIf({ os.windows })
@Timeout(900)
class GeneratedApplicationCommandSpec extends CommandSpec implements CommandFixture {

    // the Gradle daemon of the builds of the application is one of this specification, which ends it
    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void cleanupSpec() {
        gradle.close()
    }

    void "a command created in an application that was never built is run by its name, as it was created"() {
        given: 'a generated application with a command as create-command writes it, and no list of its commands'
        generateProject(ApplicationType.WEB)
        new File(dir, 'settings.gradle') << '\n' + gradle.recording
        new File(dir, 'gradle.properties') << "\norg.gradle.daemon.registry.base=${gradle.registry.canonicalPath}\norg.gradle.daemon.idletimeout=${IsolatedGradle.IDLE_MILLIS}\n"
        out.reset()
        assert ForgeCli.execute(beanContext, 'create-command', 'data-import') == 0
        assert out.toString().readLines() == [
                '| Rendered command to grails-app/commands/example/grails/DataImportCommand.groovy',
                '| Run it with: grails-forge-cli data-import'
        ]
        assert !new File(dir, ApplicationCommandFile.LOCATION).exists()
        out.reset()
        err.reset()

        when: 'it is run by its name, as the guide shows it'
        int exit = ForgeCli.execute(beanContext, 'data-import', 'orders.csv')

        then: 'the build listed the commands once, and the command ran in the application and succeeded'
        exit == 0
        err.toString().readLines()[0] == '| Listing the commands of the application and its plugins, of which data-import may be one, with the Gradle task grailsCommandCatalog'
        err.toString().contains('| Running the command data-import of the application, with the Gradle task runCommand')
        out.toString().readLines().contains('Ran data-import with the arguments [orders.csv]')
        catalog().commands.find { it.name == 'data-import' }.origin == 'application'

        when: 'a second command, and a build that compiles the project'
        assert ForgeCli.execute(beanContext, 'create-command', 'wave') == 0
        out.reset()
        err.reset()
        int compiled = ForgeCli.execute(beanContext, 'compile')

        then: 'the build listed it, and the list is current for the command line, which lists it as a command of the application'
        compiled == 0
        ((List<Map>) catalog().commands)*.name.containsAll(['data-import', 'wave'])

        when:
        out.reset()
        err.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        described.complete == true
        !((List<String>) described.diagnostics).any { String line -> line.contains(ApplicationCommandFile.LOCATION) }
        ((List<Map>) described.commands).findAll { it.origin == 'application' }*.name.containsAll(['data-import', 'wave'])
        !err.toString().contains('| Listing')
    }

    void "a command that extends another is listed by the name it sets over the one it inherits, and is run by it"() {
        given: 'a generated application with a command that sets its name in its constructor, over the name the command it extends has'
        generateProject(ApplicationType.WEB)
        new File(dir, 'settings.gradle') << '\n' + gradle.recording
        new File(dir, 'gradle.properties') << "\norg.gradle.daemon.registry.base=${gradle.registry.canonicalPath}\norg.gradle.daemon.idletimeout=${IsolatedGradle.IDLE_MILLIS}\n"
        File commands = new File(dir, 'grails-app/commands/example/grails')
        commands.mkdirs()
        new File(commands, 'OldNameCommand.groovy').text = '''\
            package example.grails

            import org.apache.grails.core.cli.GrailsApplicationCommand

            abstract class OldNameCommand implements GrailsApplicationCommand {

                String name = 'old-name'
            }
            '''.stripIndent()
        new File(commands, 'LegacyImportCommand.groovy').text = '''\
            package example.grails

            class LegacyImportCommand extends OldNameCommand {

                LegacyImportCommand() {
                    name = 'import-data'
                }

                boolean handle() {
                    println "Ran ${name} with the arguments ${args}"
                    return true
                }
            }
            '''.stripIndent()
        out.reset()
        err.reset()

        when: 'it is run by the name it sets'
        int exit = ForgeCli.execute(beanContext, 'import-data', 'orders.csv')

        then: 'the build listed it by that name only, and the command that ran is the one listed'
        exit == 0
        ((List<Map>) catalog().commands).findAll { it.origin == 'application' }*.name == ['import-data']
        err.toString().contains('| Running the command import-data of the application, with the Gradle task runCommand')
        out.toString().readLines().contains('Ran import-data with the arguments [orders.csv]')
    }

    private Map catalog() {
        return (Map) new JsonSlurper().parse(new File(dir, ApplicationCommandFile.LOCATION))
    }
}

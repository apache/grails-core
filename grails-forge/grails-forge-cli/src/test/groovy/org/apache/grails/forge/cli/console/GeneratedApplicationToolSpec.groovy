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
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * A tool the application was given with {@code create-tool} is a command of the command line by its
 * name, in a project that was never built: the build lists the tools of the application, which
 * compiles it, and the tool is run in the application. As {@code create-tool} writes it, the tool says
 * what it was given and succeeds. Once the build has compiled the project the catalog is current, also for a
 * tool added since. A tool in the deprecated folder grails-app/commands that extends another is listed by a name it runs by:
 * the name it has when it runs, when the compiler can read it, and the name its class gives otherwise, and is run after a
 * warning that the folder is deprecated.
 *
 * <p>The build compiles src/cli and runs a tool with the task runTool where the Gradle plugin of the checkout has the folders
 * of tools; the specification runs there.</p>
 */
@IgnoreIf({ os.windows })
@Requires({ ToolsOfTheBuild.hasToolFolders() })
@Timeout(900)
class GeneratedApplicationToolSpec extends CommandSpec implements CommandFixture {

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

    void "a tool created in an application that was never built is run by its name, as it was created"() {
        given: 'a generated application with a tool as create-tool writes it, and no catalog of its tools'
        generateProject(ApplicationType.WEB)
        new File(dir, 'settings.gradle') << '\n' + gradle.recording
        new File(dir, 'gradle.properties') << "\norg.gradle.daemon.registry.base=${gradle.registry.canonicalPath}\norg.gradle.daemon.idletimeout=${IsolatedGradle.IDLE_MILLIS}\n"
        out.reset()
        assert ForgeCli.execute(beanContext, 'create-tool', 'data-import') == 0
        assert out.toString().readLines() == [
                '| Rendered tool to src/cli/groovy/example/grails/DataImportTool.groovy',
                '| Run it with: grails-forge-cli data-import'
        ]
        assert !new File(dir, ToolCatalogFile.LOCATION).exists()
        out.reset()
        err.reset()

        when: 'it is run by its name, as the guide shows it'
        int exit = ForgeCli.execute(beanContext, 'data-import', 'orders.csv')

        then: 'the build listed the tools once, and the tool ran in the application and succeeded'
        exit == 0
        err.toString().readLines()[0] == '| Listing the tools of the application and its plugins, of which data-import may be one, with the Gradle task grailsToolCatalog'
        err.toString().contains('| Running the tool data-import of the application, with the Gradle task runTool')
        out.toString().readLines().contains('Ran data-import with the arguments [orders.csv]')
        catalog().tools.find { it.name == 'data-import' }.origin == 'application'

        when: 'a second tool, one that runs as a process of its own, and a build that compiles the project'
        assert ForgeCli.execute(beanContext, 'create-tool', '--standalone', 'wave') == 0
        out.reset()
        err.reset()
        int compiled = ForgeCli.execute(beanContext, 'compile')

        then: 'the build listed it, and the catalog is current for the command line, which lists it as a tool of the application'
        compiled == 0
        ((List<Map>) catalog().tools)*.name.containsAll(['data-import', 'wave'])

        when:
        out.reset()
        err.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        described.complete == true
        !((List<String>) described.diagnostics).any { String line -> line.contains(ToolCatalogFile.LOCATION) }
        ((List<Map>) described.commands).findAll { it.origin == 'application' }*.name.containsAll(['data-import', 'wave'])
        !err.toString().contains('| Listing')
    }

    @Unroll
    void "a tool in grails-app/commands that extends another is listed by a name it runs under, and is run by it, after a warning: #how"() {
        given: 'a generated application with a tool in the deprecated folder that has a name of its own, over the name the tool it extends has'
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
        new File(commands, 'LegacyImportCommand.groovy').text = 'package example.grails\n\nclass LegacyImportCommand extends OldNameCommand {\n\n' +
                members + '\n\n    boolean handle() {\n        println "Ran ${name} with the arguments ${args}"\n        return true\n    }\n}\n'
        out.reset()
        err.reset()

        when: 'it is run by the name the build lists it by'
        int exit = ForgeCli.execute(beanContext, listed, 'orders.csv')

        then: 'the build listed it by that name only, and the tool that ran is the one listed, with the name it has'
        exit == 0
        ((List<Map>) catalog().tools).findAll { it.origin == 'application' }.collect { [it.name, it.registration] } == [[listed, registration]]
        err.toString().contains("| Running the tool ${listed} of the application, with the Gradle task runTool")
        err.toString().contains("| Warning ${listed} is in grails-app/commands, which is deprecated: move it to src/cli/groovy")
        out.toString().readLines().contains('Ran import-data with the arguments [orders.csv]')

        where:
        how                                                      | members                                                                                                         || listed          | registration
        'its constructor sets the name'                          | "    LegacyImportCommand() {\n        name = 'import-data'\n    }"                                                 || 'import-data'   | 'descriptor'
        'its constructor calls a helper that sets the name'      | "    LegacyImportCommand() {\n        rename()\n    }\n\n    private void rename() {\n        name = 'import-data'\n    }" || 'import-data' | 'descriptor'
        'its name is worked out, and it is listed by its class'  | "    LegacyImportCommand() {\n        name = System.getProperty('import.name', 'import-data')\n    }"             || 'legacy-import' | 'factories'
    }

    private Map catalog() {
        return (Map) new JsonSlurper().parse(new File(dir, ToolCatalogFile.LOCATION))
    }
}

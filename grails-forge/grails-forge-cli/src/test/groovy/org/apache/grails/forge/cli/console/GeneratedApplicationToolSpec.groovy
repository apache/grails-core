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
import org.grails.forge.cli.ForgeContext
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
 * the name it has when it runs, when the compiler can read it, and the name its class gives otherwise. The build warns that
 * the folder is deprecated when it compiles it, and the command line warns again before it runs a tool whose descriptor
 * says that its source is in that folder; a tool without a descriptor has the warning of the build only.
 *
 * <p>The build compiles src/cli and runs a tool with the task runTool where the Gradle plugin of the checkout has the folders
 * of tools; the specification runs there.</p>
 */
@IgnoreIf({ os.windows })
@Requires({ ToolsOfTheBuild.hasToolFolders() })
@Timeout(900)
class GeneratedApplicationToolSpec extends CommandSpec implements CommandFixture {

    /** What the build says, as a warning, each time it compiles grails-app/commands. */
    static final String LEGACY_FOLDER_COMPILED = 'grails-app/commands is deprecated: the classes in it are compiled and run as before, ' +
            'and are no longer packaged with the application. Move them to src/cli/groovy, as they are; a new tool is written there by: grails create-tool'

    /**
     * What has the builds of a generated application compile what they compile: a compilation that is taken from the
     * build cache, as one of an earlier run of the specification is, says none of the warnings it said then.
     */
    static final String WITHOUT_BUILD_CACHE = 'org.gradle.caching=false\n'

    /** A tool written by hand that lacks everything the compiler warns of: no description, and arguments it reads undeclared. */
    static final String BARE_TOOL = '''\
        package example.grails

        import org.apache.grails.core.cli.ApplicationTool
        import org.apache.grails.core.cli.ToolSupport

        class BareTool implements ApplicationTool, ToolSupport {

            boolean handle() {
                println "Bare ${args}"
                return true
            }
        }
        '''.stripIndent()

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
        new File(dir, 'gradle.properties') << gradle.ofAGeneratedApplication
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
        out.toString().readLines().contains('Ran data-import for orders.csv')
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
        // the warning is said by the compilation, which is to run and not be taken from the build cache
        new File(dir, 'gradle.properties') << gradle.ofAGeneratedApplication + WITHOUT_BUILD_CACHE
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
        out.toString().readLines().contains('Ran import-data with the arguments [orders.csv]')

        and: 'the build said that the folder is deprecated when it compiled it'
        out.toString().readLines().contains(LEGACY_FOLDER_COMPILED)

        and: 'the command line says it again before the tool runs where the descriptor of the tool says where its source is; a tool without one has the line of the build only'
        warnedWhenRun ? err.toString().readLines().contains("| Warning ${listed} is in grails-app/commands, which is deprecated: move it to src/cli/groovy".toString()) :
                !err.toString().contains(' is in grails-app/commands, which is deprecated')

        where:
        how                                                      | members                                                                                                         || listed          | registration | warnedWhenRun
        'its constructor sets the name'                          | "    LegacyImportCommand() {\n        name = 'import-data'\n    }"                                                 || 'import-data'   | 'descriptor' | true
        'its constructor calls a helper that sets the name'      | "    LegacyImportCommand() {\n        rename()\n    }\n\n    private void rename() {\n        name = 'import-data'\n    }" || 'import-data' | 'descriptor' | true
        'its name is worked out, and it is listed by its class'  | "    LegacyImportCommand() {\n        name = System.getProperty('import.name', 'import-data')\n    }"             || 'legacy-import' | 'factories'  | false
    }

    void "a tool create-tool writes compiles without a warning and lacks nothing; a declared option reaches it, a mistyped one is refused before Gradle starts, and its result is read"() {
        given: 'a generated application whose builds compile what they compile, the two tools create-tool writes, and one written by hand that lacks everything'
        generateProject(ApplicationType.WEB)
        new File(dir, 'settings.gradle') << '\n' + gradle.recording
        new File(dir, 'gradle.properties') << gradle.ofAGeneratedApplication + WITHOUT_BUILD_CACHE
        assert ForgeCli.execute(beanContext, 'create-tool', 'data-import') == 0
        assert ForgeCli.execute(beanContext, 'create-tool', '--standalone', 'wave') == 0
        new File(dir, 'src/cli/groovy/example/grails/BareTool.groovy').text = BARE_TOOL
        out.reset()
        err.reset()

        when: 'the project is compiled'
        int compiled = ForgeCli.execute(beanContext, 'compile')
        String said = out.toString() + err.toString()
        Map<String, Map> tools = ((List<Map>) catalog().tools).collectEntries { [(it.name): it] }

        then: 'the compiler warned of the tool written by hand, and of nothing create-tool wrote'
        compiled == 0
        said.contains('BareTool has no description: help and agents show only its name')
        said.contains('BareTool reads its arguments without declaring them')
        !said.contains('DataImportTool')
        !said.contains('WaveTool')

        and: 'the tools create-tool wrote lack nothing, and declare their description, their parameter and their hints'
        !tools['data-import'].containsKey('incomplete')
        !tools.wave.containsKey('incomplete')
        tools.bare.incomplete == ['description', 'arguments', 'hints']
        [tools['data-import'], tools.wave].every { Map tool ->
            tool.parameters == [[label: 'NAME', arity: '0..1', type: 'string', description: 'What the tool is run for']] &&
                    tool.hints == [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true]
        }
        tools['data-import'].description == 'DataImport'
        tools.wave.target == 'standalone'

        when: 'project health is asked what the tools lack'
        out.reset()
        ForgeCli.execute(beanContext, 'project', 'health', '--json')
        Map<String, Map> findings = ((List<Map>) new JsonSlurper().parseText(out.toString()).findings).collectEntries { [(it.id): it] }

        then: 'the tool written by hand is named, and those create-tool wrote are not'
        ['tools.description-missing', 'tools.arguments-undeclared', 'tools.hints-missing'].every { String id -> findings[id].message.startsWith('The tool bare of the application ') }

        when: 'the tool declares an option and sets a result, and is run with the option'
        File source = new File(dir, 'src/cli/groovy/example/grails/DataImportTool.groovy')
        source.text = source.text
                .replace('import org.apache.grails.core.cli.ToolHints\n', 'import org.apache.grails.core.cli.ToolHints\nimport org.apache.grails.core.cli.ToolOption\n')
                .replace('    String target\n', "    String target\n\n    @ToolOption(names = ['--times'], description = 'How often')\n    int times = 1\n")
                .replace('        println "Ran ${name} for ${target ?: \'nothing\'}"\n',
                        '        times.times { println "Ran ${name} for ${target ?: \'nothing\'}" }\n        executionContext.result = [target: target, times: times]\n')
        CommandStreams streams = CommandStreams.capture()
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', 'data-import', '--times', '3', 'orders.csv')
        ToolResult result = ToolResult.take(streams)

        then: 'the option reached the tool, its result was read, and the file of the result is gone'
        exit == 0
        streams.standardOutput().readLines().count { it == 'Ran data-import for orders.csv' } == 3
        result.value == [target: 'orders.csv', times: 3L]
        result.json == '{"target":"orders.csv","times":3}'
        !new File(dir, RunDirectory.LOCATION).list().any { String name -> name.startsWith('tool-result-') || name.startsWith('command-args-') }

        when: 'the option is mistyped, now that the build has listed it'
        assert ToolCatalogFile.read(dir.toPath()).state == ToolCatalogFile.State.CURRENT
        assert ((List<Map>) catalog().tools).find { it.name == 'data-import' }.options*.names == [['--times']]
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'data-import', '--tiems', '3')

        then: 'it is refused with one line, and nothing was started: Gradle wrote nothing'
        exit == 2
        err.toString().readLines() == ['| Error data-import: --tiems is not one of its options. grails-forge-cli help data-import shows its options']
        out.toString().isEmpty()
    }

    private Map catalog() {
        return (Map) new JsonSlurper().parse(new File(dir, ToolCatalogFile.LOCATION))
    }
}

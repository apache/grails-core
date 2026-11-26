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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.health.HealthContext
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * {@code project health} and the tools of a project: the catalog of the tools ({@code tool-catalog}), and
 * what of the tools is written as Grails 9 deprecates it or no longer has it ({@code tools}): sources
 * that import the types of the commands of Grails 7, which no longer compile, sources in the deprecated
 * folder {@code grails-app/commands}, and tools of the application of a deprecated type.
 */
class ProjectHealthToolsSpec extends CommandSpec {

    static final String NOTES = "https://grails.apache.org/docs/${VersionInfo.documentationVersion}/guide/upgrading.html#"

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome
    File project

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        home = Files.createTempDirectory('grailshealthhome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        HealthContext.setEnvironment([:])
        assert run(dir, 'create-app', 'com.example.shop') == 0
        project = new File(dir, 'shop')
        out.reset()
        err.reset()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        HealthContext.setEnvironment(null)
        home.deleteDir()
    }

    void "a project create-app has just generated has nothing of the tools to be told, and the catalog is not there yet"() {
        when:
        int exit = run(project, 'project', 'health', '--json')
        Map<String, Map> findings = findings()

        then:
        exit == 0
        findings['tools.ok'].message == 'No source imports grails.dev.commands, grails-app/commands has no source, and no tool of the application implements a deprecated type'
        findings['tool-catalog.missing'].level == 'advice'
        findings['tool-catalog.missing'].message == 'The tools of the application and its plugins are not listed: there is no .grails/cache/tool-catalog.json, ' +
                'so the grails command does not have them by their names'
        findings['tool-catalog.missing'].command == 'grails-forge-cli gradle grailsToolCatalog'
        findings['tool-catalog.missing'].upgradeNote == NOTES + 'upgradingCommandCatalog'
        !findings.keySet().any { String id -> id.startsWith('command-catalog.') }

        and: 'a generated application has none of the folders of tools until it has a tool'
        !new File(project, 'grails-app/commands').exists()
        !new File(project, 'src/cli').exists()
        !new File(project, 'src/standalone').exists()
    }

    @Unroll
    void "a source in #folder that imports the commands of Grails 7 is an error that names the import to write"() {
        given:
        write(path, "package example\n\nimport grails.dev.commands.GrailsApplicationCommand\nimport grails.dev.commands.template.TemplateRenderer\n\n" +
                'class ImportCommand implements GrailsApplicationCommand {\n    boolean handle() { true }\n}\n')

        when:
        int exit = run(project, 'project', 'health', '--json')
        Map finding = findings()['tools.grails7-imports']

        then: 'the command fails, as for every error'
        exit == 1
        finding.level == 'error'
        finding.message == "${path} imports grails.dev.commands.GrailsApplicationCommand, grails.dev.commands.template.TemplateRenderer, which Grails 9 does not have: " +
                'import org.apache.grails.core.cli.GrailsApplicationCommand, org.apache.grails.core.cli.template.TemplateRenderer'
        finding.fix == "Change the imports of ${path} to org.apache.grails.core.cli: a tool implements org.apache.grails.core.cli.ApplicationTool with ToolSupport, " +
                'or, with no other change, the deprecated org.apache.grails.core.cli.GrailsApplicationCommand, whose members are the same'
        finding.files == [path]
        finding.upgradeNote == NOTES + 'upgradingLegacyBridgeRemoved'

        where:
        folder                | path
        'src/cli'             | 'src/cli/groovy/example/ImportCommand.groovy'
        'src/standalone'      | 'src/standalone/groovy/example/ImportCommand.groovy'
        'grails-app/commands' | 'grails-app/commands/example/ImportCommand.groovy'
        'src/main/groovy'     | 'src/main/groovy/example/ImportCommand.groovy'
        'a Java source'       | 'src/cli/java/example/ImportCommand.java'
    }

    void "each source that imports the commands of Grails 7 is one finding, and an import of a type of the same name elsewhere is none"() {
        given:
        write('src/cli/groovy/example/OneCommand.groovy', 'import grails.dev.commands.*\nclass OneCommand { }\n')
        write('src/cli/groovy/example/TwoCommand.groovy', '  import static grails.dev.commands.ExecutionContext.foo\nclass TwoCommand { }\n')
        write('src/cli/groovy/example/Fine.groovy', 'import org.apache.grails.core.cli.ExecutionContext\n// import grails.dev.commands.ApplicationCommand in a comment\nclass Fine { }\n')
        write('src/test/groovy/example/OldSpec.groovy', 'import grails.dev.commands.ExecutionContext\nclass OldSpec { }\n')

        when:
        run(project, 'project', 'health', '--json')
        List<Map> found = ((List<Map>) parsed().findings).findAll { it.id == 'tools.grails7-imports' }

        then: 'the sources of the folders of tools and of the application are read, and the tests are not'
        found*.files == [['src/cli/groovy/example/OneCommand.groovy'], ['src/cli/groovy/example/TwoCommand.groovy']]
        found*.message == [
                'src/cli/groovy/example/OneCommand.groovy imports grails.dev.commands.*, which Grails 9 does not have: import org.apache.grails.core.cli.*',
                'src/cli/groovy/example/TwoCommand.groovy imports grails.dev.commands.ExecutionContext.foo, which Grails 9 does not have: import org.apache.grails.core.cli.ExecutionContext.foo']
    }

    void "sources in grails-app/commands are advice that names them and the folder to move them to"() {
        given:
        write('grails-app/commands/example/GreetCommand.groovy', 'package example\nclass GreetCommand { }\n')
        write('grails-app/commands/example/ReportCommand.java', 'package example;\nclass ReportCommand { }\n')
        write('grails-app/commands/README.txt', 'not a source\n')

        when:
        int exit = run(project, 'project', 'health', '--json', '--strict')
        Map finding = findings()['tools.legacy-folder']

        then: 'advice fails nothing, also with --strict'
        exit == 0
        finding.level == 'advice'
        finding.message == 'grails-app/commands is deprecated, and has 2 sources: the classes in it are compiled and run as before, and are no longer packaged with the application'
        finding.fix == 'Move them to src/cli/groovy, as they are; a new tool is written there by: grails-forge-cli create-tool NAME'
        finding.files == ['grails-app/commands/example/GreetCommand.groovy', 'grails-app/commands/example/ReportCommand.java']
        finding.upgradeNote == NOTES + 'upgradingToolsRenamed'
    }

    void "a folder of more sources than a finding names is named by the first of them and how many more there are"() {
        given:
        25.times { int each -> write("grails-app/commands/example/Tool${String.format('%02d', each)}Command.groovy", "class Tool${each}Command { }\n") }

        when:
        run(project, 'project', 'health', '--json')
        Map finding = findings()['tools.legacy-folder']

        then:
        finding.message.startsWith('grails-app/commands is deprecated, and has 25 sources: ')
        finding.files.size() == 21
        finding.files[0] == 'grails-app/commands/example/Tool00Command.groovy'
        finding.files[19] == 'grails-app/commands/example/Tool19Command.groovy'
        finding.files[20] == '(and 5 more)'
    }

    void "tools of the application of a deprecated type are advice, as the catalog says, and those of plugins are not"() {
        given:
        catalog([
                [name: 'old-report', origin: 'application', target: 'application', registration: 'descriptor', deprecated: ['type']],
                [name: 'old-greet', origin: 'application', target: 'application', registration: 'descriptor', deprecated: ['type', 'folder']],
                [name: 'moved', origin: 'application', target: 'application', registration: 'descriptor', deprecated: ['folder']],
                [name: 'of-a-plugin', origin: 'org.example:old-cli:1.0', target: 'application', registration: 'factories', deprecated: ['type']],
                [name: 'fine', origin: 'application', target: 'application', registration: 'descriptor']])

        when:
        int exit = run(project, 'project', 'health', '--json')
        Map<String, Map> findings = findings()

        then:
        exit == 0
        findings['tools.legacy-type'].level == 'advice'
        findings['tools.legacy-type'].message == 'The tools old-greet, old-report of the application implement ApplicationCommand or GrailsApplicationCommand, which are deprecated'
        findings['tools.legacy-type'].fix == 'Implement ApplicationTool instead (for GrailsApplicationCommand: ApplicationTool, ToolSupport); nothing else changes'
        findings['tools.legacy-type'].files == ['.grails/cache/tool-catalog.json']
        findings['tools.legacy-type'].upgradeNote == NOTES + 'upgradingToolsRenamed'
        findings['tool-catalog.ok'].message == 'The tools of the application and its plugins are listed (.grails/cache/tool-catalog.json): 5 tools'
    }

    @Unroll
    void "tool-catalog.#finding when #what"() {
        given:
        change.call(project)

        when:
        run(project, 'project', 'health', '--json')
        Map<String, Map> findings = findings()

        then:
        findings.containsKey("tool-catalog.${finding}".toString())
        findings["tool-catalog.${finding}".toString()].message.contains(said)

        where:
        finding      | what                                      | said                                                             | change
        'refused'    | 'the catalog is no file'                  | 'so it is not used, and the grails command does not have the tools' | { File p -> new File(p, '.grails/cache/tool-catalog.json').mkdirs() }
        'stale'      | 'a source of src/cli is newer'            | 'may not list the tools the build has now: src/cli was changed'     | { File p -> catalog(p, []); later(p, 'src/cli/groovy/example/GreetTool.groovy') }
        'stale'      | 'a source of src/standalone is newer'     | 'may not list the tools the build has now: src/standalone was changed' | { File p -> catalog(p, []); later(p, 'src/standalone/java/example/RenderTool.java') }
        'incomplete' | 'a tool of the catalog is left out'       | 'lists 0 tools of the application and its plugins, and not every one the build has: Tool 1 of' | { File p -> catalog(p, [[name: 'gone', origin: 'application', target: 'gradle', registration: 'descriptor']]) }
    }

    // a source of the project, newer than the catalog
    private static void later(File project, String path) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = 'class Later { }\n'
        for (File each = file; each != project; each = each.parentFile) {
            each.setLastModified(System.currentTimeMillis() + 60_000)
        }
    }

    private void catalog(List<Map> tools) {
        catalog(project, tools)
    }

    private static void catalog(File project, List<Map> tools) {
        File file = new File(project, '.grails/cache/tool-catalog.json')
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: tools])
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private Map<String, Map> findings() {
        return ((List<Map>) parsed().findings).collectEntries { [(it.id): it] }
    }

    private Map parsed() {
        return (Map) new JsonSlurper().parseText(out.toString())
    }

    private int run(File directory, String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
        }
    }
}

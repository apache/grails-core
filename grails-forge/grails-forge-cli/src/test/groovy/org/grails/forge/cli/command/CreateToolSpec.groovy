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

import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.console.ProjectTools
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * {@code create-tool} writes a tool the build runs into {@code src/cli/groovy}, and with
 * {@code --standalone} a tool that runs as a process of its own into {@code src/standalone/groovy},
 * each from its template; never into {@code grails-app/commands}, where tools were before, and never a
 * class of a deprecated type. No application is built: what the build does with the two folders is
 * specified with the Gradle plugin.
 */
class CreateToolSpec extends CommandSpec implements CommandFixture {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        generateProject(ApplicationType.WEB)
        // the project is not built: a name that is no command does not have the build list the tools
        ProjectTools.setListing('false')
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ProjectTools.setListing(null)
    }

    @Unroll
    void "create-tool #options writes a tool #kind into #folder"() {
        when:
        int exit = ForgeCli.execute(beanContext, (['create-tool'] + options + ['data-import']) as String[])
        String written = new File(dir, "${folder}/example/grails/DataImportTool.groovy").text

        then: 'the class, of the type of its folder, with the helpers of a tool'
        exit == 0
        written.startsWith('package example.grails\n')
        written.contains("import org.apache.grails.core.cli.${type}\n")
        written.contains('import org.apache.grails.core.cli.ToolSupport\n')
        written.contains("class DataImportTool implements ${type}, ToolSupport {")
        written.contains('boolean handle() {')
        !written.contains('ApplicationCommand')

        and: 'what was written, and how the tool is run by its name'
        out.toString().readLines() == ["| Rendered tool to ${folder}/example/grails/DataImportTool.groovy".toString(), '| Run it with: grails-forge-cli data-import']
        err.toString().isEmpty()

        and: 'nothing in the other folders, the deprecated one among them'
        (['src/cli', 'src/standalone', 'grails-app/commands'] - [folder.substring(0, folder.lastIndexOf('/'))]).every { String other -> !new File(dir, other).exists() }

        where:
        options          | kind                                     | folder                  | type
        []               | 'the build runs'                         | 'src/cli/groovy'        | 'ApplicationTool'
        ['--standalone'] | 'that runs as a process of its own'      | 'src/standalone/groovy' | 'StandaloneTool'
    }

    @Unroll
    void "create-tool #given is the tool #name in #file"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'create-tool', given)

        then:
        exit == 0
        new File(dir, file).text.contains("class ${new File(file).name - '.groovy'} implements ApplicationTool, ToolSupport {")
        out.toString().readLines().last() == "| Run it with: grails-forge-cli ${name}".toString()

        where:
        given                | name             | file
        'ImportTool'         | 'import'         | 'src/cli/groovy/example/grails/ImportTool.groovy'
        'create-report-tool' | 'create-report'  | 'src/cli/groovy/example/grails/CreateReportTool.groovy'
        'org.shop.Audit'     | 'audit'          | 'src/cli/groovy/org/shop/AuditTool.groovy'
    }

    void "the templates of the project are used for each kind, and install-templates copies the two of a tool"() {
        given: 'the templates of the command line in the project, changed'
        assert ForgeCli.execute(beanContext, 'install-templates') == 0
        File tool = new File(dir, 'src/main/templates/artifacts/Tool.groovy')
        File standalone = new File(dir, 'src/main/templates/artifacts/StandaloneTool.groovy')
        tool.text = tool.text.replace('boolean handle() {', '// of the project\n    boolean handle() {')
        standalone.text = standalone.text.replace('boolean handle() {', '// standalone of the project\n    boolean handle() {')
        out.reset()

        expect: 'the template of create-command of earlier versions is not copied'
        !new File(dir, 'src/main/templates/artifacts/Command.groovy').exists()

        when:
        int built = ForgeCli.execute(beanContext, 'create-tool', 'report')
        int alone = ForgeCli.execute(beanContext, 'create-tool', '--standalone', 'render')

        then:
        built == 0
        alone == 0
        new File(dir, 'src/cli/groovy/example/grails/ReportTool.groovy').text.contains('// of the project\n')
        new File(dir, 'src/standalone/groovy/example/grails/RenderTool.groovy').text.contains('// standalone of the project\n')
        out.toString().readLines().containsAll(['| Using the template src/main/templates/artifacts/Tool.groovy of the project',
                                               '| Using the template src/main/templates/artifacts/StandaloneTool.groovy of the project'])
    }

    void "a project that has the template of create-command has a tool the build runs written from it, after a warning, and not a standalone one"() {
        given: 'the template create-command of earlier versions read, in the project'
        File command = new File(dir, 'src/main/templates/artifacts/Command.groovy')
        command.parentFile.mkdirs()
        command.text = '@artifact.package@\nimport org.apache.grails.core.cli.GrailsApplicationCommand\n\nclass @artifact.name@Command implements GrailsApplicationCommand {\n' +
                '    // of the project\n    boolean handle() { true }\n}\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'create-tool', 'report')

        then: 'the class is written as the template has it, in a file of the name of its class, in src/cli'
        exit == 0
        new File(dir, 'src/cli/groovy/example/grails/ReportCommand.groovy').text.contains('class ReportCommand implements GrailsApplicationCommand {\n    // of the project\n')
        out.toString().readLines() == [
                '| Warning Using the template src/main/templates/artifacts/Command.groovy of the project, the template of create-command, which is deprecated: ' +
                        'a tool is written from src/main/templates/artifacts/Tool.groovy, which install-templates copies into the project',
                '| Rendered tool to src/cli/groovy/example/grails/ReportCommand.groovy',
                '| Run it with: grails-forge-cli report']
        !new File(dir, 'grails-app/commands').exists()

        when: 'a standalone tool, which create-command never wrote'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-tool', '--standalone', 'render')

        then: 'it is written from the template of the command line'
        exit == 0
        new File(dir, 'src/standalone/groovy/example/grails/RenderTool.groovy').text.contains('class RenderTool implements StandaloneTool, ToolSupport {')
        !out.toString().contains('Command.groovy')

        when: 'the project has the template of a tool as well'
        out.reset()
        File tool = new File(dir, 'src/main/templates/artifacts/Tool.groovy')
        tool.text = '@artifact.package@\nclass @artifact.name@Tool { }\n'
        exit = ForgeCli.execute(beanContext, 'create-tool', 'audit')

        then: 'that one is used, and nothing is said of the other'
        exit == 0
        new File(dir, 'src/cli/groovy/example/grails/AuditTool.groovy').text == 'package example.grails\n\nclass AuditTool { }\n'
        !out.toString().contains('Command.groovy')
    }

    @Unroll
    void "create-tool #options greet refuses a tool of the same name in #existing, and writes nothing"() {
        given:
        File other = new File(dir, existing)
        other.parentFile.mkdirs()
        other.text = 'package example.grails\n\nclass Existing { }\n'

        when:
        int exit = ForgeCli.execute(beanContext, (['create-tool'] + options + ['greet']) as String[])

        then:
        exit == 1
        err.toString().readLines() == ["| Error ${existing} is a tool of the name greet already, and a project has one tool of a name. ${advice}. Nothing was written".toString()]
        !new File(dir, written).exists()

        where:
        options          | existing                                                  | written                                                 | advice
        []               | 'grails-app/commands/example/grails/GreetCommand.groovy' | 'src/cli/groovy/example/grails/GreetTool.groovy'        | 'grails-app/commands is deprecated: move that tool to src/cli/groovy as it is'
        ['--standalone'] | 'grails-app/commands/example/grails/GreetCommand.groovy' | 'src/standalone/groovy/example/grails/GreetTool.groovy' | 'grails-app/commands is deprecated: move that tool to src/cli/groovy as it is'
        []               | 'src/standalone/groovy/example/grails/GreetTool.groovy'  | 'src/cli/groovy/example/grails/GreetTool.groovy'        | 'Rename one of them'
        ['--standalone'] | 'src/cli/groovy/example/grails/GreetTool.groovy'         | 'src/standalone/groovy/example/grails/GreetTool.groovy' | 'Rename one of them'
        []               | 'src/cli/groovy/example/grails/GreetCommand.groovy'      | 'src/cli/groovy/example/grails/GreetTool.groovy'        | 'Rename one of them'
        []               | 'src/cli/java/example/grails/GreetTool.java'             | 'src/cli/groovy/example/grails/GreetTool.groovy'        | 'Rename one of them'
    }

    void "a tool that is there is kept, and replaced with -f"() {
        given:
        assert ForgeCli.execute(beanContext, 'create-tool', 'greet') == 0
        File written = new File(dir, 'src/cli/groovy/example/grails/GreetTool.groovy')
        written.text = '// changed\n'
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'create-tool', 'greet')

        then:
        exit == 1
        written.text == '// changed\n'
        err.toString().readLines() == ['| Error src/cli/groovy/example/grails/GreetTool.groovy already exists. Run again with -f to overwrite.']

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-tool', '-f', 'greet')

        then:
        exit == 0
        written.text.contains('class GreetTool implements ApplicationTool, ToolSupport {')
    }

    @Unroll
    void "create-tool is typed by its whole name only: #typed is #what"() {
        when:
        int exit = ForgeCli.execute(beanContext, typed, 'Foo')

        then: 'no short form reaches create-tool, and those that fit create-taglib as well are create-taglib, as before'
        exit == code
        new File(dir, 'grails-app/taglib/example/grails/FooTagLib.groovy').exists() == (code == 0)
        !new File(dir, 'src/cli').exists()
        code == 0 || err.toString().readLines()[0].startsWith("There is no command ${typed}")

        where:
        typed  | code | what
        'cT'   | 0    | 'create-taglib, as in Grails 8'
        'ct'   | 0    | 'create-taglib, as in Grails 8'
        'crt'  | 0    | 'create-taglib, as in Grails 8'
        'cTa'  | 0    | 'create-taglib, as in Grails 8'
        'cTo'  | 2    | 'no command'
        'cto'  | 2    | 'no command'
        'crTo' | 2    | 'no command'
    }

    void "the help of create-tool has no short form, and help --json lists it as every other command"() {
        when:
        ForgeCli.execute(beanContext, 'help', 'create-tool')
        List<String> tool = out.toString().readLines()
        out.reset()
        ForgeCli.execute(beanContext, 'help', 'create-taglib')
        List<String> taglib = out.toString().readLines()
        out.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map createTool = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString().readLines().last())).commands).find { it.name == 'create-tool' }

        then:
        !tool.any { String line -> line.startsWith('Short form:') }
        taglib.contains('Short form: ct')
        createTool.origin == 'built-in'
        createTool.aliases == []
    }

    void "the help of create-tool says where each kind of tool is written"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'create-tool')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then:
        exit == 0
        help.startsWith('Usage: grails-forge-cli create-tool [-fhvVx] [--non-interactive] [--plain-output] [--standalone] TOOL-NAME ')
        help.contains('Creates a tool of the application: a class the grails command runs by its name.')
        help.contains('A tool is written to src/cli/groovy and is run by the build, with the application started around it unless it says otherwise.')
        help.contains('With --standalone it is written to src/standalone/groovy and runs as a process of its own, without Gradle and without the application, on the files of the project.')
        help.contains('--standalone Creates a tool that runs as a process of its own, without Gradle and without the application, in src/standalone/groovy')
        help.contains('TOOL-NAME The name of the tool to create')
    }
}

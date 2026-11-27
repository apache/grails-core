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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

/**
 * A tool that declares its options and parameters is held to them by the command line before Gradle is
 * started, by the rules and with the words the class that runs a tool holds it to: the cases of
 * {@code tool-arguments-cases.json}, which this build has a copy of. Arguments that are refused end the
 * command with one line and the exit code 2, and nothing is started; arguments that are taken are given
 * to the build as they are. The wrapper of the project here writes down that it ran and what it was
 * given, and is no Gradle.
 */
@IgnoreIf({ os.windows })
class ToolArgumentsPreCheckSpec extends CommandSpec {

    static final String CASES = 'tool-arguments-cases.json'

    @Shared
    Map<String, Object> cases = (Map<String, Object>) new JsonSlurper().parse(ToolArgumentsPreCheckSpec.getResource("/${CASES}"), StandardCharsets.UTF_8.name())

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File ran
    File given

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
        ran = new File(dir, 'wrapper-ran')
        given = new File(dir, 'wrapper-given')
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '''\
            #!/bin/sh
            here="$(dirname "$0")"
            for argument in "$@"; do
                case "$argument" in
                    --args-file=*) cat "$here/${argument#--args-file=}" > "$here/wrapper-given" ;;
                esac
            done
            printf '%s\\n' "$@" >> "$here/wrapper-ran"
            '''.stripIndent()
        wrapper.setExecutable(true, false)
        List<Map<String, Object>> tools = ((Map<String, Map>) cases.tools).collect { String name, Map declared ->
            [name: name, aliases: [], description: "The ${name} tool".toString(), origin: 'application', target: 'application', registration: 'descriptor'] + declared
        }
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: tools]), StandardCharsets.US_ASCII.name())
        // listed after everything the build lists the tools from
        catalog.setLastModified(System.currentTimeMillis() + 60_000)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "the cases are those the classes that run a tool are held to, in grails-core"() {
        given: 'the file of the cases of grails-core in this checkout: the specs run in grails-forge/grails-forge-cli'
        File original = new File(previousUsrDir, "../../grails-core/src/test-cli/resources/${CASES}").canonicalFile

        expect:
        original.isFile()
        ToolArgumentsPreCheckSpec.getResource("/${CASES}").getText(StandardCharsets.UTF_8.name()) == original.getText(StandardCharsets.UTF_8.name())
        cases.format == 1
        ((List) cases.cases).size() >= 50
    }

    @Unroll
    void "#title: #tool #arguments"() {
        when:
        int exit = ForgeCli.execute(beanContext, ([tool] + arguments) as String[])

        then:
        refused == null || exit == 2
        refused == null || err.toString().readLines() == ["| Error ${tool}: ${refused}. grails-forge-cli help ${tool} shows its options".toString()]
        refused == null || (!ran.exists() && !new File(dir, RunDirectory.LOCATION).exists())

        and: 'arguments that are taken are given to the tool as they are, by the build'
        refused != null || exit == 0
        refused != null || ran.readLines().any { it == 'runTool' || it == ':runTool' }
        refused != null || new JsonSlurper().parse(given, StandardCharsets.UTF_8.name()) == [tool] + arguments

        where:
        testCase << ((List<Map>) new JsonSlurper().parse(ToolArgumentsPreCheckSpec.getResource("/${CASES}"), StandardCharsets.UTF_8.name()).cases)
        title = testCase.title
        tool = testCase.tool
        arguments = testCase.arguments as List<String>
        refused = testCase.refused
    }

    @Unroll
    void "#arguments after the name of a tool that declares its arguments is answered with its help, and nothing is started"() {
        when:
        int exit = ForgeCli.execute(beanContext, (['audit-report'] + arguments) as String[])
        String help = out.toString()
        out.reset()
        ForgeCli.execute(beanContext, 'help', 'audit-report')

        then: 'what help audit-report shows'
        exit == 0
        help == out.toString()
        help.startsWith('Usage: grails-forge-cli audit-report ')
        help.contains('-h, --help')
        err.toString().isEmpty()
        !ran.exists()

        where:
        arguments << [['--help'], ['-h'], ['out.txt', '--help'], ['--nope', '-h'], ['--since', '2026-01-01', '--help']]
    }

    @Unroll
    void "#arguments is no question for the help where the class that runs the tool reads it otherwise: #why"() {
        given:
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        Map tools = (Map) new JsonSlurper().parse(catalog)
        ((List) tools.tools).add([name: 'host-check', aliases: [], description: 'Checks a host', origin: 'application', target: 'application', registration: 'descriptor',
                                  options: [[names: ['--host', '-h'], arity: '1', type: 'string', label: 'HOST']], parameters: [[label: 'PORT', arity: '0..1', type: 'string']]])
        catalog.setText(JsonOutput.toJson(tools), StandardCharsets.US_ASCII.name())
        catalog.setLastModified(System.currentTimeMillis() + 60_000)

        when:
        int exit = ForgeCli.execute(beanContext, ([tool] + arguments) as String[])

        then:
        exit == expected
        ran.exists() == (expected == 0)
        expected != 0 || new JsonSlurper().parse(given, StandardCharsets.UTF_8.name()) == [tool] + arguments
        expected != 2 || err.toString().readLines() == ["| Error ${tool}: ${refused}. grails-forge-cli help ${tool} shows its options".toString()]

        where:
        tool           | arguments                      | why                                                     || expected | refused
        'audit-report' | ['--since', '--help', 'o.txt'] | 'the value of an option that takes one'                 || 0        | null
        'audit-report' | ['--', '--help']               | 'a parameter after --'                                  || 0        | null
        'audit-report' | ['--help=yes', 'o.txt']        | 'an option with a value, which no tool declares'        || 2        | '--help is not one of its options'
        'host-check'   | ['-h', 'example.org']          | 'an option of the tool, which declares -h'              || 0        | null
        'free-form'    | ['--help']                     | 'a tool that declares nothing, which is given it'       || 0        | null
        'free-form'    | ['-h', 'x']                    | 'a tool that declares nothing, which is given it'       || 0        | null
    }

    void "a tool that declares -h is answered with its help for --help only, which names --help alone"() {
        given:
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: [
                [name: 'host-check', aliases: [], description: 'Checks a host', origin: 'application', target: 'application', registration: 'descriptor',
                 options: [[names: ['--host', '-h'], arity: '1', type: 'string', label: 'HOST', description: 'The host']]]]]), StandardCharsets.US_ASCII.name())
        catalog.setLastModified(System.currentTimeMillis() + 60_000)

        when:
        int exit = ForgeCli.execute(beanContext, 'host-check', '--help')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then:
        exit == 0
        !ran.exists()
        help.contains('--help Show this help message and exit.')
        !help.contains('-h, --help')
        help.contains('-h, --host=HOST The host.')
    }

    void "a tool whose catalog may be out of date is given its arguments, and the class that runs it holds it to what it declares then"() {
        given: 'a source of a tool changed after the catalog was written'
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.setLastModified(System.currentTimeMillis() - 60_000)
        File source = new File(dir, 'src/cli/groovy/example/AuditReportTool.groovy')
        source.parentFile.mkdirs()
        source.text = 'class AuditReportTool { }\n'

        when: 'an option the catalog does not list, as one the tool has since declared'
        int exit = ForgeCli.execute(beanContext, 'audit-report', '--added', 'out.txt')

        then:
        exit == 0
        new JsonSlurper().parse(given, StandardCharsets.UTF_8.name()) == ['audit-report', '--added', 'out.txt']
    }

    void "the line that refuses an argument repeats no value that was given, and names an option that cannot be shown in words"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'audit-report', '--format=\u001b[31mXML', '--since', 'secret', 'out.txt')

        then:
        exit == 2
        err.toString().readLines() == ['| Error audit-report: --format takes one of TEXT, JSON. grails-forge-cli help audit-report shows its options']
        !err.toString().contains('secret')

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'audit-report', '--\u001b[31mred', 'out.txt')

        then:
        exit == 2
        err.toString().readLines() == ['| Error audit-report: an option it was given is not one of its options. grails-forge-cli help audit-report shows its options']
    }

    void "a tool that reads what is typed is refused with --non-interactive, before anything is started"() {
        given:
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: [
                [name: 'import-data', aliases: [], description: 'Imports what is typed', origin: 'application', target: 'application', registration: 'descriptor',
                 readsInput: true]]]), StandardCharsets.US_ASCII.name())
        catalog.setLastModified(System.currentTimeMillis() + 60_000)

        when:
        int exit = ForgeCli.execute(beanContext, '--non-interactive', 'import-data', 'orders.csv')

        then:
        exit == 2
        err.toString().readLines()[0] == 'import-data reads what is typed, and --non-interactive gives it nothing to read'
        !ran.exists()

        when: 'without the option it is run, and the build is given the input of the command line'
        err.reset()
        exit = ForgeCli.execute(beanContext, 'import-data', 'orders.csv')

        then:
        exit == 0
        ran.readLines().any { it == 'runTool' || it == ':runTool' }
        err.toString().readLines()[0] == '| Running the tool import-data of the application, with the Gradle task runTool'
    }
}

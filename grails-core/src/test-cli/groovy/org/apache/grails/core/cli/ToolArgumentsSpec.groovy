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
package org.apache.grails.core.cli

import java.nio.file.Files

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.WarningMessage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.cli.compiler.ToolCompilation

/**
 * A tool that declares its options and parameters is given its arguments on its fields before it runs,
 * and arguments that are not what it declares are refused with one line and the exit code 2; a tool that
 * declares none is given them as it always was. The cases are those of {@code tool-arguments-cases.json},
 * which the command line checks the same rules with against the catalog: each is run here through the
 * runner of standalone tools, in a JVM of its own, with the tools compiled as a project compiles them.
 */
class ToolArgumentsSpec extends Specification {

    private static final String TOOLS = '''
import groovy.transform.AnnotationCollector
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolArgumentsReport
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter

enum ReportFormat { TEXT, JSON }

@ToolHints(readOnlyHint = true)
class AuditReportTool implements StandaloneTool {
    String description = 'Writes the audit report'
    @ToolOption(names = ['--since', '-s'], label = 'DATE', description = 'The first day of the report') String since
    @ToolOption(names = ['--format'], description = 'The format of the report') ReportFormat format = ReportFormat.TEXT
    @ToolOption(names = ['--include-deleted'], description = 'Lists deleted records as well') boolean includeDeleted
    @ToolOption(names = ['--limit', '-l'], description = 'How many records at most') Integer limit
    @ToolOption(names = ['--tag'], description = 'A tag the records have') List<String> tags
    @ToolParameter(index = 0, label = 'OUTPUT', description = 'The file to write') String output
    boolean handle(ExecutionContext executionContext) { ToolArgumentsReport.report(this, executionContext) }
}

@ToolHints(destructiveHint = false)
class CopyFilesTool implements StandaloneTool {
    String description = 'Copies files'
    @ToolOption(names = ['--mode'], required = true, description = 'How to copy') String mode
    @ToolOption(names = ['--force', '-f'], description = 'Replaces the files that are there') boolean force
    @ToolParameter(index = 0, label = 'SOURCE', description = 'What to copy') String source
    @ToolParameter(index = 1, label = 'TARGETS', description = 'Where to copy it') List<String> targets
    boolean handle(ExecutionContext executionContext) { ToolArgumentsReport.report(this, executionContext) }
}

@ToolHints(readOnlyHint = true)
class GreetPeopleTool implements StandaloneTool {
    String description = 'Greets people'
    @ToolOption(names = ['--loud'], description = 'Greets loudly') Boolean loud
    @ToolOption(names = ['--times'], defaultValue = '1', description = 'How often') long times
    @ToolParameter(index = 0, label = 'GREETING', required = false, description = 'The greeting') String greeting
    @ToolParameter(index = 1, label = 'NAMES', required = false, description = 'Who is greeted') List<String> names
    boolean handle(ExecutionContext executionContext) { ToolArgumentsReport.report(this, executionContext) }
}

@ToolOption(names = ['--contexts'], arity = '1', description = 'The contexts of the change sets to run')
@ToolOption(names = ['--dataSource'], arity = '1', label = 'DATASOURCE', description = 'The data source to update')
@AnnotationCollector
@interface MigrationOptions {
}

@MigrationOptions
@ToolOption(names = ['--force'], arity = '0', description = 'Runs without asking')
@ToolParameter(index = 0, label = 'CHANGELOG', arity = '1', required = false, description = 'The change log')
@ToolHints
class UpdateDatabaseTool implements StandaloneTool {
    String description = 'Updates the database'
    boolean handle(ExecutionContext executionContext) { ToolArgumentsReport.report(this, executionContext) }
}

@ToolHints
class CleanCacheTool implements StandaloneTool {
    String description = 'Cleans the cache'
    @ToolOption(names = ['--level'], description = 'How much to clean') int level
    boolean handle(ExecutionContext executionContext) { ToolArgumentsReport.report(this, executionContext) }
}

@ToolHints
class FreeFormTool implements StandaloneTool {
    String description = 'Takes anything'
    boolean handle(ExecutionContext executionContext) { ToolArgumentsReport.report(this, executionContext) }
}
'''

    @Shared
    Map<String, Object> cases = (Map<String, Object>) new JsonSlurper().parse(ToolArgumentsSpec.getResource('/tool-arguments-cases.json'), 'UTF-8')

    @Shared
    File dir = Files.createTempDirectory('tool-arguments').toFile()

    @Shared
    ToolCompilation compiled

    def setupSpec() {
        compiled = ToolCompilation.compile(dir, 'src/standalone/groovy/example/ArgumentsTools.groovy', TOOLS, [],
                WarningMessage.LIKELY_ERRORS, Phases.ALL)
    }

    def cleanupSpec() {
        dir.deleteDir()
    }

    def "the tools of the cases compile without an error or a warning"() {
        expect:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
    }

    @Unroll
    def "the declarations of the tool #name are those of the cases, as its descriptor and the catalog have them"() {
        when:
        Map descriptor = compiled.descriptor("example.${className}")
        Map declared = (Map) cases.tools[name]

        then:
        descriptor.name == name
        (descriptor.options ?: []) == (declared.options ?: [])
        (descriptor.parameters ?: []) == (declared.parameters ?: [])

        where:
        name              | className
        'audit-report'    | 'AuditReportTool'
        'copy-files'      | 'CopyFilesTool'
        'greet-people'    | 'GreetPeopleTool'
        'update-database' | 'UpdateDatabaseTool'
        'clean-cache'     | 'CleanCacheTool'
        'free-form'       | 'FreeFormTool'
    }

    def "every tool of the cases has a case, and every case a tool"() {
        expect:
        cases.cases*.tool.toSet() == ((Map) cases.tools).keySet()
        cases.cases.every { Map each -> each.containsKey('refused') != each.containsKey('bound') }
    }

    @Unroll
    def "#title (#tool #arguments)"() {
        given: 'the arguments in a file, as the command line gives them'
        File run = new File(dir, '.grails/run')
        run.mkdirs()
        File file = new File(run, 'command-args-0.json')
        file.setText(JsonOutput.toJson([tool] + arguments), 'UTF-8')

        when:
        Map<String, Object> result = ToolArgumentsReport.run(dir, compiled.classes, ["--grails-args-file=${file.path}".toString()])

        then:
        result.ended
        if (refused != null) {
            assert result.exit == ToolArguments.REFUSED
            assert result.out == ''
            assert result.err.readLines() == ["| Error ${tool}: ${refused}. grails help ${tool} shows its options".toString()]
        } else {
            assert result.exit == 0
            assert result.err == ''
            Map reported = (Map) new JsonSlurper().parseText(((String) result.out).readLines().find { it.startsWith('BOUND=') }.substring(6))
            assert reported.bound == bound
            assert reported.remaining == remaining
            assert options == null || reported.options == options
        }

        where:
        each << cases.cases
        title = each.title
        tool = each.tool
        arguments = each.arguments
        refused = each.refused
        bound = each.bound
        remaining = each.remaining
        options = each.options
    }

    def "a value beyond the range of an int field is refused by the runner, which knows the type of the field"() {
        when:
        Map<String, Object> result = ToolArgumentsReport.run(dir, compiled.classes, ['audit-report', '-l', '3000000000', 'out.txt'])

        then:
        result.exit == ToolArguments.REFUSED
        result.err.readLines() == ['| Error audit-report: -l takes a whole number from -2147483648 to 2147483647. grails help audit-report shows its options']
    }

    def "a tool given its arguments themselves, and not in a file, is held to them as well"() {
        when:
        Map<String, Object> accepted = ToolArgumentsReport.run(dir, compiled.classes, ['copy-files', '--force', 'a', 'b', '--mode=x'])
        Map<String, Object> refused = ToolArgumentsReport.run(dir, compiled.classes, ['copy-files', 'a', 'b'])

        then:
        accepted.exit == 0
        accepted.out.contains('"remaining":["a","b"]')
        refused.exit == ToolArguments.REFUSED
        refused.err.readLines() == ['| Error copy-files: --mode is required. grails help copy-files shows its options']
    }
}

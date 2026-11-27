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
package org.apache.grails.core.cli.compiler

import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.WarningMessage
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.apache.grails.gradle.model.cli.ToolCatalogBuilder
import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * What a tool declares with {@code @ToolOption}, {@code @ToolParameter} and {@code @ToolHints} is written
 * into its descriptor by the compiler, in the form {@code help --json} of the command line has, and from
 * there into the catalog of the project. A declaration that breaks a rule does not compile, with an error
 * that names the field, or the declaration on the class, and the rule.
 */
class ToolDeclarationsWriterSpec extends Specification {

    private static final String IMPORTS = 'import org.apache.grails.core.cli.ToolOption\nimport org.apache.grails.core.cli.ToolOptions\n' +
            'import org.apache.grails.core.cli.ToolParameter\nimport org.apache.grails.core.cli.ToolHints\n'
    private static final String APPLICATION = 'org.apache.grails.core.cli.ApplicationTool'
    private static final String STANDALONE = 'org.apache.grails.core.cli.StandaloneTool'
    private static final String SUPPORT = 'org.apache.grails.core.cli.ToolSupport'
    private static final String HANDLE = 'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'
    private static final String CLI = 'src/cli/groovy/example/ReportTool.groovy'

    private static final String AUDIT_REPORT_TOOL = IMPORTS + '''
enum ReportFormat { TEXT, JSON }

@ToolHints(readOnlyHint = true)
class AuditReportTool implements org.apache.grails.core.cli.ApplicationTool, org.apache.grails.core.cli.ToolSupport {

    String description = 'Writes the audit report'

    @ToolOption(names = ['--since', '-s'], label = 'DATE', description = 'The first day of the report')
    String since

    @ToolOption(names = ['--format'], description = 'The format of the report')
    ReportFormat format = ReportFormat.TEXT

    @ToolOption(names = ['--include-deleted'], description = 'Lists deleted records as well')
    boolean includeDeleted

    @ToolParameter(index = 0, label = 'OUTPUT', description = 'The file to write')
    String output

    boolean handle() { true }
}
'''

    @TempDir
    File dir

    def "a tool that declares its options, parameters and hints has them in its descriptor, in the form of help --json, and in the catalog"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, 'src/cli/groovy/example/AuditReportTool.groovy', AUDIT_REPORT_TOOL)

        then: 'it compiles without an error or a warning, and lacks nothing'
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()

        and: 'the descriptor has the members only where they say something, in a fixed order'
        compiled.descriptorFile('example.AuditReportTool').getText('US-ASCII') ==
                '{"format":1,"class":"example.AuditReportTool","name":"audit-report","description":"Writes the audit report",' +
                '"target":"application","startsApplication":true,' +
                '"options":[{"names":["--since","-s"],"arity":"1","type":"string","label":"DATE","description":"The first day of the report"},' +
                '{"names":["--format"],"arity":"1","type":"string","label":"FORMAT","description":"The format of the report","choices":["TEXT","JSON"],"default":"TEXT"},' +
                '{"names":["--include-deleted"],"arity":"0","type":"boolean","description":"Lists deleted records as well"}],' +
                '"parameters":[{"label":"OUTPUT","arity":"1","type":"string","description":"The file to write"}],' +
                '"hints":{"readOnlyHint":true,"destructiveHint":true,"idempotentHint":false,"openWorldHint":true}}\n'

        when: 'the catalog of the project is put together from the classes'
        ToolCatalogBuilder catalog = new ToolCatalogBuilder()
        catalog.add(ToolCatalogBuilder.ORIGIN_APPLICATION, compiled.classes)

        then: 'the tool is listed with what it declares, as the descriptor has it'
        catalog.toJson() == '{"format":1,"complete":true,"diagnostics":[],"tools":[{"name":"audit-report","aliases":[],' +
                '"description":"Writes the audit report","origin":"application","target":"application","startsApplication":true,"registration":"descriptor",' +
                '"options":[{"names":["--since","-s"],"arity":"1","type":"string","label":"DATE","description":"The first day of the report"},' +
                '{"names":["--format"],"arity":"1","type":"string","label":"FORMAT","description":"The format of the report","choices":["TEXT","JSON"],"default":"TEXT"},' +
                '{"names":["--include-deleted"],"arity":"0","type":"boolean","description":"Lists deleted records as well"}],' +
                '"parameters":[{"label":"OUTPUT","arity":"1","type":"string","description":"The file to write"}],' +
                '"hints":{"readOnlyHint":true,"destructiveHint":true,"idempotentHint":false,"openWorldHint":true}}]}\n'
    }

    @Unroll
    def "the type of the field of an option says what the option takes: #type"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, tool('', "${extra}\n@ToolOption(names = ['--value'], description = 'A value') ${type} value"))

        then:
        compiled.errors.isEmpty()
        compiled.descriptor('example.ReportTool').options == [declared]

        where:
        type           | extra                       || declared
        'String'       | ''                          || [names: ['--value'], arity: '1', type: 'string', label: 'VALUE', description: 'A value']
        'boolean'      | ''                          || [names: ['--value'], arity: '0', type: 'boolean', description: 'A value']
        'Boolean'      | ''                          || [names: ['--value'], arity: '0', type: 'boolean', description: 'A value']
        'int'          | ''                          || [names: ['--value'], arity: '1', type: 'integer', label: 'VALUE', description: 'A value']
        'Integer'      | ''                          || [names: ['--value'], arity: '1', type: 'integer', label: 'VALUE', description: 'A value']
        'long'         | ''                          || [names: ['--value'], arity: '1', type: 'integer', label: 'VALUE', description: 'A value']
        'Long'         | ''                          || [names: ['--value'], arity: '1', type: 'integer', label: 'VALUE', description: 'A value']
        'Kind'         | 'enum Kind { SMALL, LARGE }' || [names: ['--value'], arity: '1', type: 'string', label: 'VALUE', description: 'A value', choices: ['SMALL', 'LARGE']]
        'List<String>' | ''                          || [names: ['--value'], arity: '1', type: 'string', label: 'VALUE', description: 'A value', repeatable: true]
    }

    @Unroll
    def "the type of the field of a parameter, and whether it is required, say what the parameter takes: #type, required #required"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, tool('',
                "${extra}\n@ToolParameter(index = 0, label = 'VALUE', required = ${required}) ${type} value"))

        then:
        compiled.errors.isEmpty()
        compiled.descriptor('example.ReportTool').parameters == [declared]

        where:
        type           | required | extra                       || declared
        'String'       | true     | ''                          || [label: 'VALUE', arity: '1', type: 'string']
        'String'       | false    | ''                          || [label: 'VALUE', arity: '0..1', type: 'string']
        'Integer'      | true     | ''                          || [label: 'VALUE', arity: '1', type: 'integer']
        'long'         | false    | ''                          || [label: 'VALUE', arity: '0..1', type: 'integer']
        'Kind'         | true     | 'enum Kind { SMALL, LARGE }' || [label: 'VALUE', arity: '1', type: 'string', choices: ['SMALL', 'LARGE']]
        'List<String>' | true     | ''                          || [label: 'VALUE', arity: '1..*', type: 'string', repeatable: true]
        'List<String>' | false    | ''                          || [label: 'VALUE', arity: '0..*', type: 'string', repeatable: true]
    }

    @Unroll
    def "the default of an option is its defaultValue, or the constant its field starts with: #title"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, tool('', "enum Kind { SMALL, LARGE }\n${member}"))

        then:
        compiled.errors.isEmpty()
        compiled.descriptor('example.ReportTool').options[0].default == expected

        where:
        title                                  | member                                                                       || expected
        'a defaultValue'                       | "@ToolOption(names = ['--value'], defaultValue = 'all') String value"       || 'all'
        'a defaultValue, over the field'       | "@ToolOption(names = ['--value'], defaultValue = 'all') String value = 'x'" || 'all'
        'a text the field starts with'         | "@ToolOption(names = ['--value']) String value = 'some'"                    || 'some'
        'a number the field starts with'       | "@ToolOption(names = ['--value']) int value = 3"                            || '3'
        'a negative number'                    | "@ToolOption(names = ['--value']) long value = -3L"                         || '-3'
        'a constant of the enum'               | "@ToolOption(names = ['--value']) Kind value = Kind.LARGE"                  || 'LARGE'
        'a value that is worked out'           | "@ToolOption(names = ['--value']) String value = System.getProperty('x')"   || null
        'nothing'                              | "@ToolOption(names = ['--value']) String value"                             || null
    }

    def "options and parameters on the class are those the tool reads itself, and a Groovy collector of them is a set several tools share"() {
        given:
        String source = IMPORTS + '''
import groovy.transform.AnnotationCollector

@ToolOption(names = ['--contexts'], arity = '1', description = 'The contexts of the change sets to run')
@ToolOption(names = ['--dataSource'], arity = '1', label = 'DATASOURCE', description = 'The data source to update')
@AnnotationCollector
@interface MigrationOptions {
}

@MigrationOptions
@ToolOption(names = ['--force'], arity = '0', description = 'Runs without asking')
@ToolParameter(index = 0, label = 'CHANGELOG', arity = '1', required = false, description = 'The change log')
@ToolParameter(index = 1, label = 'MORE', arity = '1..*', required = false)
class ReportTool implements org.apache.grails.core.cli.ApplicationTool {
    String description = 'Updates the database'
    @ToolOption(names = ['--verbose'], description = 'Says more')
    boolean verbose
    boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }
}
'''

        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, source)

        then: 'the options on the class come first, those of the collector after those written on the class, then those of the fields'
        compiled.errors.isEmpty()
        Map described = compiled.descriptor('example.ReportTool')
        described.options == [
                [names: ['--force'], arity: '0', type: 'boolean', description: 'Runs without asking'],
                [names: ['--contexts'], arity: '1', type: 'string', label: 'CONTEXTS', description: 'The contexts of the change sets to run'],
                [names: ['--dataSource'], arity: '1', type: 'string', label: 'DATASOURCE', description: 'The data source to update'],
                [names: ['--verbose'], arity: '0', type: 'boolean', description: 'Says more'],
        ]
        described.parameters == [[label: 'CHANGELOG', arity: '0..1', type: 'string', description: 'The change log'],
                                 [label: 'MORE', arity: '0..*', type: 'string', repeatable: true]]

        and: 'a tool that declares on its class only declares its arguments too'
        !(described.incomplete ?: []).contains('arguments')
    }

    def "the options of a container written by hand are those of the class"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, IMPORTS + """
@ToolOptions([@ToolOption(names = ['--a'], arity = '0'), @ToolOption(names = ['--b'], arity = '1')])
class ReportTool implements ${APPLICATION} { String description = 'Reports'; ${HANDLE} }
""")

        then:
        compiled.errors.isEmpty()
        compiled.descriptor('example.ReportTool').options*.names == [['--a'], ['--b']]
    }

    def "the fields of the classes a tool extends are declarations of the tool, from the class at the root down"() {
        when: 'a class it extends is compiled with it'
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, IMPORTS + """
abstract class BaseTool implements ${APPLICATION} {
    @ToolOption(names = ['--base']) String base
}
class ReportTool extends BaseTool {
    String description = 'Reports'
    @ToolOption(names = ['--own']) String own
    ${HANDLE}
}
""")

        then:
        compiled.errors.isEmpty()
        compiled.descriptor('example.ReportTool').options*.names == [['--base'], ['--own']]

        when: 'a class it extends is compiled before it, as in a jar'
        ToolCompilation base = ToolCompilation.compile(dir, 'src/cli/groovy/example/BaseTool.groovy', IMPORTS + """
abstract class BaseTool implements ${APPLICATION} {
    @ToolOption(names = ['--base'], description = 'From the jar') String base
    @ToolParameter(index = 0, label = 'INPUT') String input
}
""", [], WarningMessage.LIKELY_ERRORS, Phases.ALL)
        ToolCompilation extending = ToolCompilation.compile(new File(dir, 'other'), CLI, IMPORTS + """
class ReportTool extends BaseTool {
    String description = 'Reports'
    boolean startsApplication = true
    @ToolOption(names = ['--own']) String own
    ${HANDLE}
}
""", [base.classes])

        then:
        base.errors.isEmpty()
        extending.errors.isEmpty()
        extending.descriptor('example.ReportTool').options == [[names: ['--base'], arity: '1', type: 'string', label: 'BASE', description: 'From the jar'],
                                                              [names: ['--own'], arity: '1', type: 'string', label: 'OWN']]
        extending.descriptor('example.ReportTool').parameters == [[label: 'INPUT', arity: '1', type: 'string']]
    }

    def "hints that are not given are the defaults of the Model Context Protocol"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, tool('@ToolHints(destructiveHint = false)', ''))

        then:
        compiled.descriptor('example.ReportTool').hints == [readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: true]
    }

    @Unroll
    def "a declaration that breaks a rule does not compile, with an error that names it and the rule: #rule"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, tool(onClass, members))

        then:
        compiled.errors == [message]

        and: 'the tool gets no descriptor'
        !compiled.descriptorFile('example.ReportTool').exists()

        where:
        rule                                   | onClass                                                     | members                                                                                                  || message
        'a name that is no name'               | ''                                                          | "@ToolOption(names = ['since']) String since"                                                            || '@ToolOption on ReportTool.since has a name that is neither a long name (two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters, as --since) nor a short name (a hyphen and one letter, as -s).'
        'a long name of 33 characters'         | ''                                                          | "@ToolOption(names = ['--${'a' * 31}']) String since"                                                     || '@ToolOption on ReportTool.since has a name that is neither a long name (two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters, as --since) nor a short name (a hyphen and one letter, as -s).'
        'a long name in upper case'            | ''                                                          | "@ToolOption(names = ['--Since']) String since"                                                          || '@ToolOption on ReportTool.since has a name that is neither a long name (two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters, as --since) nor a short name (a hyphen and one letter, as -s).'
        'a short name of two letters'          | ''                                                          | "@ToolOption(names = ['--since', '-sx']) String since"                                                   || '@ToolOption on ReportTool.since has a name that is neither a long name (two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters, as --since) nor a short name (a hyphen and one letter, as -s).'
        'no long name'                         | ''                                                          | "@ToolOption(names = ['-s']) String since"                                                               || '@ToolOption on ReportTool.since has no long name, as --since.'
        'two short names'                      | ''                                                          | "@ToolOption(names = ['--since', '-s', '-S']) String since"                                              || '@ToolOption on ReportTool.since has more than one short name.'
        'the name --help'                      | ''                                                          | "@ToolOption(names = ['--help']) boolean help"                                                           || '@ToolOption on ReportTool.help takes the name --help, which the command line keeps for itself.'
        'the name --version'                   | ''                                                          | "@ToolOption(names = ['--version']) boolean version"                                                     || '@ToolOption on ReportTool.version takes the name --version, which the command line keeps for itself.'
        'the name -D'                          | ''                                                          | "@ToolOption(names = ['--define', '-D']) String define"                                                  || '@ToolOption on ReportTool.define takes the name -D, which the command line keeps for itself.'
        'a name twice'                         | ''                                                          | "@ToolOption(names = ['--since', '--since']) String since"                                               || '@ToolOption on ReportTool.since has a name twice.'
        'five names'                           | ''                                                          | "@ToolOption(names = ['--a', '--b', '--c', '--d', '--e']) String since"                                  || '@ToolOption on ReportTool.since has more than 4 names.'
        'a label in lower case'                | ''                                                          | "@ToolOption(names = ['--since'], label = 'date') String since"                                          || '@ToolOption on ReportTool.since has a label that is not an upper-case letter followed by at most 31 upper-case letters, digits and hyphens, as DATE.'
        'a description of two lines'           | ''                                                          | "@ToolOption(names = ['--since'], description = 'one\\ntwo') String since"                                || '@ToolOption on ReportTool.since has a description that is not one line of at most 512 characters that can be shown.'
        'a description of 513 characters'      | ''                                                          | "@ToolOption(names = ['--since'], description = '${'d' * 513}') String since"                            || '@ToolOption on ReportTool.since has a description that is not one line of at most 512 characters that can be shown.'
        'a switch that is required'            | ''                                                          | "@ToolOption(names = ['--force'], required = true) boolean force"                                       || '@ToolOption on ReportTool.force is a switch and is required: a switch is given or not.'
        'a switch with a label'                | ''                                                          | "@ToolOption(names = ['--force'], label = 'X') boolean force"                                           || '@ToolOption on ReportTool.force is a switch and has a label: a switch takes no value.'
        'a switch with a default'              | ''                                                          | "@ToolOption(names = ['--force'], defaultValue = 'true') boolean force"                                 || '@ToolOption on ReportTool.force is a switch and has a default: a switch is false unless it is given.'
        'a default that is no number'          | ''                                                          | "@ToolOption(names = ['--count'], defaultValue = 'many') int count"                                     || '@ToolOption on ReportTool.count has a default that is not a whole number.'
        'a default that is no choice'          | ''                                                          | "@ToolOption(names = ['--kind'], defaultValue = 'HUGE') Kind kind\nenum Kind { SMALL, LARGE }"           || '@ToolOption on ReportTool.kind has a default that is not one of its choices.'
        'a default of 129 characters'          | ''                                                          | "@ToolOption(names = ['--since'], defaultValue = '${'d' * 129}') String since"                           || '@ToolOption on ReportTool.since has a default that is not one line of at most 128 characters that can be shown.'
        'a required option with a default'     | ''                                                          | "@ToolOption(names = ['--since'], required = true, defaultValue = 'today') String since"               || '@ToolOption on ReportTool.since is required and has a default, which it never takes.'
        'a repeatable option with a default'   | ''                                                          | "@ToolOption(names = ['--tag'], defaultValue = 'a') List<String> tags"                                  || '@ToolOption on ReportTool.tags is repeatable and has a default: an option given more than once has the values it is given.'
        'a field of a type no option takes'    | ''                                                          | "@ToolOption(names = ['--when']) Date when"                                                              || '@ToolOption on ReportTool.when is on a field of the type java.util.Date, which no option takes: String, boolean, Boolean, int, Integer, long, Long, an enum or List<String>.'
        'a list of numbers'                    | ''                                                          | "@ToolOption(names = ['--count']) List<Integer> counts"                                                  || '@ToolOption on ReportTool.counts is on a field of the type java.util.List<java.lang.Integer>, which no option takes: String, boolean, Boolean, int, Integer, long, Long, an enum or List<String>.'
        'a parameter of a switch'              | ''                                                          | "@ToolParameter(index = 0, label = 'FLAG') boolean flag"                                                 || '@ToolParameter on ReportTool.flag is on a field of the type boolean, which no parameter takes: String, int, Integer, long, Long, an enum or List<String>.'
        'a static field'                       | ''                                                          | "@ToolOption(names = ['--since']) static String since"                                                  || 'ReportTool.since is static, and a value is given to the tool: declare the option or the parameter on a field of the tool.'
        'a final field'                        | ''                                                          | "@ToolOption(names = ['--since']) final String since = 'x'"                                             || 'ReportTool.since is final, so it cannot be given its value.'
        'an option and a parameter'            | ''                                                          | "@ToolOption(names = ['--a']) @ToolParameter(index = 0, label = 'A') String a"                          || 'ReportTool.a is declared more than once: a field is one option or one parameter.'
        'an arity on a field'                  | ''                                                          | "@ToolOption(names = ['--since'], arity = '1') String since"                                            || '@ToolOption on ReportTool.since gives an arity, which on a field the type of the field says: leave it out.'
        'an option on the class without arity' | "@ToolOption(names = ['--contexts'])"                       | ''                                                                                                       || "@ToolOption --contexts on the class ReportTool gives no arity, which an option on the class has to: '0' for a switch, '1' for an option that takes a value."
        'an option on the class of arity 2'    | "@ToolOption(names = ['--contexts'], arity = '2')"          | ''                                                                                                       || '@ToolOption --contexts on the class ReportTool has an arity that is neither 0, for a switch, nor 1, for an option that takes a value.'
        'a parameter on the class without arity' | "@ToolParameter(index = 0, label = 'TAG')"                | ''                                                                                                       || "The 1st @ToolParameter on the class ReportTool gives no arity, which a parameter on the class has to: '1' for one value, '1..*' for the rest."
        'a parameter of arity 0..1, required'  | "@ToolParameter(index = 0, label = 'TAG', arity = '0..1')"  | ''                                                                                                       || 'The 1st @ToolParameter on the class ReportTool has the arity 0..1, which can be left out, and is required: set required = false.'
        'a parameter on the class of arity 2'  | "@ToolParameter(index = 0, label = 'TAG', arity = '2')"     | ''                                                                                                       || 'The 1st @ToolParameter on the class ReportTool has an arity that is none of 1, 0..1, 1..*, 0..*.'
        'a label in lower case'                | ''                                                          | "@ToolParameter(index = 0, label = 'out') String output"                                                 || '@ToolParameter on ReportTool.output has no label that is an upper-case letter followed by at most 31 upper-case letters, digits and hyphens, as OUTPUT.'
        'a gap in the indexes'                 | ''                                                          | "@ToolParameter(index = 0, label = 'A') String a\n@ToolParameter(index = 2, label = 'B') String b"       || 'The parameters of ReportTool have the indexes 0, 2: they are counted from 0, without gaps.'
        'an index twice'                       | ''                                                          | "@ToolParameter(index = 0, label = 'A') String a\n@ToolParameter(index = 0, label = 'B') String b"       || '@ToolParameter on ReportTool.b has the index 0, which @ToolParameter on ReportTool.a has too: each parameter has an index of its own.'
        'a negative index'                     | ''                                                          | "@ToolParameter(index = -1, label = 'A') String a"                                                       || '@ToolParameter on ReportTool.a has an index that is not a whole number from 0.'
        'the rest before the last'             | ''                                                          | "@ToolParameter(index = 0, label = 'REST') List<String> rest\n@ToolParameter(index = 1, label = 'B') String b" || 'ReportTool has a parameter that takes any number of values, REST, before its last.'
        'a required after an optional'         | ''                                                          | "@ToolParameter(index = 0, label = 'A', required = false) String a\n@ToolParameter(index = 1, label = 'B') String b" || 'ReportTool has a parameter that has to be given, B, after one that can be left out.'
        'a name two options have'              | ''                                                          | "@ToolOption(names = ['--since']) String a\n@ToolOption(names = ['--until', '--since']) String b"        || 'ReportTool gives two options the name --since.'
        'a label two parameters have'          | ''                                                          | "@ToolParameter(index = 0, label = 'A') String a\n@ToolParameter(index = 1, label = 'A') String b"       || 'ReportTool gives two parameters the label A.'
        'more than 32 options'                 | ''                                                          | (0..32).collect { "@ToolOption(names = ['--o${it}']) String o${it}" }.join('\n')                        || 'ReportTool declares more than 32 options.'
        'more than 16 parameters'              | ''                                                          | (0..16).collect { "@ToolParameter(index = ${it}, label = 'P${it}') String p${it}" }.join('\n')          || 'ReportTool declares more than 16 parameters.'
        'an enum of 65 constants'              | ''                                                          | "@ToolOption(names = ['--kind']) Many kind\nenum Many { ${(0..64).collect { "C${it}" }.join(', ')} }"    || '@ToolOption on ReportTool.kind has choices that are not 1 to 64 different names of at most 64 characters, each an ASCII letter, _ or $ followed by ASCII letters, digits, _ and $.'
        'an enum with a constant outside ASCII' | ''                                                         | "@ToolOption(names = ['--kind']) Odd kind\nenum Odd { GROSS, GRÖSSER }"                                 || '@ToolOption on ReportTool.kind has choices that are not 1 to 64 different names of at most 64 characters, each an ASCII letter, _ or $ followed by ASCII letters, digits, _ and $.'
    }

    def "declarations that do not fit in a descriptor do not compile"() {
        given: 'the most options and parameters, each with the longest description, outside ASCII, which takes six bytes a character'
        String description = 'é' * 512
        String members = (0..<32).collect { "@ToolOption(names = ['--o${it}'], description = '${description}') String o${it}" }.join('\n')

        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, tool('', members))

        then:
        compiled.errors == ['ReportTool declares more than its descriptor can hold (16384 bytes, where each character outside ASCII takes 6): ' +
                                    'shorten the descriptions of its options and parameters.']
        !compiled.descriptorFile('example.ReportTool').exists()

        when: 'the same in ASCII'
        compiled = ToolCompilation.compile(new File(dir, 'ascii'), CLI, tool('', members.replace(description, 'd' * 200)))

        then:
        compiled.errors.isEmpty()
        compiled.descriptorFile('example.ReportTool').length() <= ToolDescriptor.MAX_BYTES
    }

    def "in a folder of tests a declaration that breaks a rule compiles, and the tool gets no descriptor"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, 'src/test/groovy/example/ReportTool.groovy', tool('', "@ToolOption(names = ['since']) String since"))

        then:
        compiled.errors.isEmpty()
        !compiled.descriptorFile('example.ReportTool').exists()
    }

    def "a standalone tool declares as an application tool does"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, 'src/standalone/groovy/example/ReportTool.groovy', IMPORTS + """
@ToolHints(readOnlyHint = true)
class ReportTool implements ${STANDALONE}, ${SUPPORT} {
    String description = 'Reports'
    @ToolOption(names = ['--force', '-f']) boolean force
    @ToolParameter(index = 0, label = 'DOMAIN-CLASS') String domainClass
    boolean handle() { true }
}
""")

        then:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
        Map described = compiled.descriptor('example.ReportTool')
        described.target == 'standalone'
        described.options == [[names: ['--force', '-f'], arity: '0', type: 'boolean']]
        described.parameters == [[label: 'DOMAIN-CLASS', arity: '1', type: 'string']]
    }

    private static String tool(String onClass, String members) {
        IMPORTS + "${onClass}\nclass ReportTool implements ${APPLICATION} {\nString description = 'Reports'\n${members}\n${HANDLE}\n}\n"
    }
}

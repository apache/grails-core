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
package org.apache.grails.gradle.model.cli

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Whose word holds for a command class that more than one element of the class path says something of.
 */
class ToolCatalogBuilderSpec extends Specification {

    private static final String CLASS = 'demo.ReportCommand'

    @TempDir
    File dir

    @Unroll
    def "the command of the application keeps the name the application gives it when a jar that comes later has a descriptor for its class: #title"() {
        given:
        File application = element('application', own)
        File later = element('later', ToolDescriptor.describe(CLASS, 'help', 'Not what the application says'))

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, application)
        builder.add('org.hostile:later:1.0', later)
        Map<String, Object> catalog = builder.build()

        then:
        catalog.tools == [[name: name, aliases: [], description: description, origin: 'application', target: 'application', startsApplication: true, registration: registration]]
        catalog.diagnostics == ['The command class demo.ReportCommand is listed as the application has it: another descriptor for it, in org.hostile:later:1.0, is not used.']
        catalog.complete == false

        where:
        title                             | own                                                               | name     | description        | registration
        'the application has none'        | null                                                              | 'report' | ''                 | 'factories'
        'the application has its own'     | ToolDescriptor.describe(CLASS, 'audit-report', 'Writes it')    | 'audit-report' | 'Writes it'  | 'descriptor'
    }

    def "a jar that has the same descriptor as the application says nothing else, and nothing is said of it"() {
        given:
        String same = ToolDescriptor.describe(CLASS, 'audit-report', 'Writes it')

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, element('application', same))
        builder.add('org.example:copy:1.0', element('copy', same))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.tools*.name == ['audit-report']
        catalog.tools*.origin == ['application']
        catalog.diagnostics == []
        catalog.complete == true
    }

    def "two jars that describe one class differently still leave it out: neither is the one whose class it is"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:one:1.0', element('one', ToolDescriptor.describe(CLASS, 'one', null)))
        builder.add('org.example:two:1.0', element('two', ToolDescriptor.describe(CLASS, 'two', null)))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.tools == []
        catalog.diagnostics == ['The command class demo.ReportCommand is not listed: it has descriptors that differ, in org.example:one:1.0, org.example:two:1.0.']
    }

    @Unroll
    def "a catalog lists no more commands than are read from it, the first by name, and says how many it left out: #title"() {
        given: 'more commands than a catalog lists, registered in an order that is not the order of their names'
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        (0..<3).each { int part ->
            File part0 = new File(dir, "part${part}")
            new File(part0, 'META-INF').mkdirs()
            List<String> classes = (0..<200).collect { int number -> "demo.p${part}.C${199 - number}Command".toString() }
            new File(part0, 'META-INF/grails-cli.factories').text = 'org.apache.grails.core.cli.ApplicationCommand=' + classes.join(',') + '\n'
            classes.each { String className ->
                File file = new File(part0, ToolDescriptor.location(className))
                file.parentFile.mkdirs()
                file.text = ToolDescriptor.describe(className, "c${2 - part}-${className.replaceAll(/\D/, '')}".toString(), letter * ToolDescriptor.MAX_DESCRIPTION_LENGTH)
            }
            builder.add("org.example:part${part}:1.0", part0)
        }

        when:
        String json = builder.toJson()
        Map<String, Object> catalog = (Map<String, Object>) StrictJson.parse(json.trim(), 8)
        List<String> names = ((List<Map>) catalog.tools)*.name

        then: 'within the bytes and the number a reader takes'
        json.length() <= ToolCatalogBuilder.MAX_BYTES
        names.size() <= ToolCatalogBuilder.MAX_COMMANDS
        names.size() >= atLeast

        and: 'the first by name, whatever element they came from'
        names == names.sort(false)
        names == (0..<3).collectMany { int part -> (0..<200).collect { int number -> "c${2 - part}-${part}${number}".toString() } }.sort().take(names.size())

        and: 'and what is left out is said, in one line'
        catalog.complete == false
        catalog.diagnostics == ["There are 600 commands, more than a catalog lists${how}: the last ${600 - names.size()} by name are not listed.".toString()]

        where:
        title                                     | letter   | atLeast | how
        'more than the number'                    | 'd'      | 512     | ' (512)'
        'more than the bytes, written as escapes' | '\u00e9' | 120     | ' in 524288 bytes'
    }

    @Unroll
    def "a class path that is not resolved completely leaves the catalog not complete, with one line of printable ASCII that names it: #title"() {
        given: 'what resolves of the class path, with a command'
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:report:1.0', element('report', ToolDescriptor.describe(CLASS, 'audit-report', 'Writes it')))

        when:
        builder.unresolved('runtimeClasspath', failure)
        Map<String, Object> catalog = builder.build()
        String line = 'runtimeClasspath cannot be resolved completely (gradle dependencies --configuration runtimeClasspath says why), ' +
                'so the commands of what is missing from it are not listed: ' + said

        then: 'the command of what resolves is listed'
        catalog.tools*.name == ['audit-report']

        and: 'the catalog says what does not resolve, in a line of a bounded length, and is not complete'
        catalog.complete == false
        catalog.diagnostics == [line.length() <= ToolCatalogBuilder.MAX_DIAGNOSTIC_LENGTH ? line :
                                        line.take(ToolCatalogBuilder.MAX_DIAGNOSTIC_LENGTH - 3) + '...']
        catalog.diagnostics.every { String each -> each.length() <= ToolCatalogBuilder.MAX_DIAGNOSTIC_LENGTH && each.every { it >= ' ' && it <= '~' } }

        where:
        title                         | failure                                             | said
        'as Gradle says it'           | 'Could not find org.example:missing:1.0.'           | 'Could not find org.example:missing:1.0.'
        'on lines of its own'         | '  Could not resolve a:b:1.\n\tRequired by:\r\n x ' | 'Could not resolve a:b:1. Required by: x'
        'with what a terminal cannot' | 'Could not find gr\u00fc\u00df:\u001b[31mred:1.'    | 'Could not find gr??:?[31mred:1.'
        'that is too long'            | 'Could not find ' + 'x' * 1000                      | 'Could not find ' + 'x' * 1000
    }

    @Unroll
    def "a tool of the output of #folders is listed when it is of the target of the source set, and left out with the reason when it is not: #target"() {
        given:
        File output = element('output', ToolDescriptor.describe(CLASS, 'report', 'Writes it', target, false, []))

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, [output], folders, expected)
        Map<String, Object> catalog = builder.build()

        then:
        ((List<Map>) catalog.tools)*.name == (said == null ? ['report'] : [])
        catalog.diagnostics == (said == null ? [] : [said])
        catalog.complete == (said == null)

        where:
        folders           | expected                            | target                              || said
        'src/standalone'  | ToolDescriptor.TARGET_STANDALONE    | ToolDescriptor.TARGET_STANDALONE    || null
        'src/cli'         | ToolDescriptor.TARGET_APPLICATION   | ToolDescriptor.TARGET_APPLICATION   || null
        'src/cli'         | ToolDescriptor.TARGET_APPLICATION   | ToolDescriptor.TARGET_STANDALONE    || STANDALONE_ELSEWHERE
        'src/standalone'  | ToolDescriptor.TARGET_STANDALONE    | ToolDescriptor.TARGET_APPLICATION   || APPLICATION_IN_STANDALONE
    }

    private static final String STANDALONE_ELSEWHERE = 'demo.ReportCommand is a StandaloneTool, and it is compiled from src/cli, not from src/standalone: ' +
            'a standalone tool is compiled without the classes of the project. Move it to src/standalone/groovy, or implement ApplicationTool to keep it here. ' +
            'It is not listed.'

    private static final String APPLICATION_IN_STANDALONE = 'demo.ReportCommand is an ApplicationTool, and it is compiled from src/standalone, ' +
            'which has neither the application nor its classes. Move it to src/cli/groovy, or implement StandaloneTool. It is not listed.'

    def "a jar with a tool of either target is held to no source set"() {
        given:
        File jar = element('jar', ToolDescriptor.describe(CLASS, 'report', null, ToolDescriptor.TARGET_STANDALONE, false, []))

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:tools-cli:1.0', jar)

        then:
        ((List<Map>) builder.build().tools).collect { [it.name, it.target] } == [['report', 'standalone']]
    }

    def "the commands of a plugin of Grails 7 are not listed, and the plugin is named once, also beside the bridge that ran them"() {
        given:
        File plugin = new File(dir, 'old-plugin')
        new File(plugin, 'META-INF').mkdirs()
        new File(plugin, 'META-INF/grails.factories').text = 'grails.dev.commands.ApplicationCommand=old.FirstCommand,old.SecondCommand\n'
        File bridge = new File(dir, 'bridge')
        new File(bridge, 'META-INF').mkdirs()
        new File(bridge, 'META-INF/grails-cli.factories').text =
                'org.apache.grails.core.cli.ApplicationCommandProvider=org.apache.grails.core.cli.compat.LegacyApplicationCommandProvider\n'
        new File(bridge, 'org/apache/grails/core/cli/compat').mkdirs()
        new File(bridge, 'org/apache/grails/core/cli/compat/LegacyApplicationCommandProvider.class').text = 'not a class'

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.apache.grails:grails-core-cli-legacy:8.0.0', bridge)
        builder.add('org.example:old-plugin:7.0.0', plugin)
        Map<String, Object> catalog = builder.build()

        then:
        catalog.tools == []
        catalog.complete == false
        catalog.diagnostics == ['org.example:old-plugin:7.0.0 has commands of Grails 7, which Grails 9 does not run: use a version of the plugin for Grails 9.']
    }

    def "the catalog copies what a tool declares from its descriptor, a descriptor written by hand as well, each member only where it says something"() {
        given: 'a descriptor written by hand, as a Java tool has one, with its members in another order'
        File plugin = element('plugin', '{"format":1,"class":"demo.ReportCommand","name":"report","description":"Reports","target":"standalone",' +
                '"incomplete":["hints"],"readsInput":true,"hints":{"openWorldHint":false,"readOnlyHint":true,"destructiveHint":false,"idempotentHint":true},' +
                '"parameters":[{"label":"OUTPUT","arity":"1"}],"options":[{"names":["--since"],"arity":"1","label":"DATE","required":false}]}')
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:report-tools-cli:1.0.0', plugin)

        expect:
        builder.toJson() == '{"format":1,"complete":true,"diagnostics":[],"tools":[{"name":"report","aliases":[],"description":"Reports",' +
                '"origin":"org.example:report-tools-cli:1.0.0","target":"standalone","startsApplication":false,"registration":"descriptor",' +
                '"options":[{"names":["--since"],"arity":"1","type":"string","label":"DATE"}],"parameters":[{"label":"OUTPUT","arity":"1","type":"string"}],' +
                '"hints":{"readOnlyHint":true,"destructiveHint":false,"idempotentHint":true,"openWorldHint":false},"readsInput":true,"incomplete":["hints"]}]}\n'
    }

    def "a tool whose descriptor has declarations that break a rule is not listed, and the rule is said"() {
        given:
        File plugin = element('plugin', '{"format":1,"class":"demo.ReportCommand","name":"report","options":[{"names":["--a"],"arity":"0"},{"names":["--a"],"arity":"1"}]}')
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:report-tools-cli:1.0.0', plugin)

        when:
        Map<String, Object> catalog = builder.build()

        then:
        catalog.tools == []
        catalog.diagnostics == ['The command class demo.ReportCommand of org.example:report-tools-cli:1.0.0 is not listed: its descriptor has declarations that say ' +
                                        'that the tool gives two options the name --a.']
    }

    def "a catalog whose tools declare more values than a reader takes lists as many tools as fit, the first by name, and says so"() {
        given: 'tools that each declare the most options, each with the most choices of a letter or two'
        List<String> choices = (('A'..'Z') + ('a'..'z') + ['_', '$'] + (0..9).collect { "B${it}".toString() }) as List<String>
        File element = new File(dir, 'tools')
        new File(element, 'META-INF').mkdirs()
        List<String> classes = (0..<80).collect { int number -> "demo.C${number}Tool".toString() }
        new File(element, 'META-INF/grails-cli.factories').text = "org.apache.grails.core.cli.GrailsTool=${classes.join(',')}\n"
        classes.each { String className ->
            List<Map> options = (0..<ToolDescriptor.MAX_OPTIONS).collect { int index -> [names: ["--o${index}".toString()], arity: '1', choices: choices] as Map }
            File file = new File(element, ToolDescriptor.location(className))
            file.parentFile.mkdirs()
            file.text = ToolDescriptor.describe(className, "t-${className.replaceAll(/\D/, '')}".toString(), 'A tool', 'application', true, [],
                    new ToolDescriptor.Declarations(options, null, null, false, null))
        }
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:tools:1.0', element)

        when:
        String json = builder.toJson()
        Map<String, Object> catalog = (Map<String, Object>) StrictJson.parse(json.trim(), 8)
        int listed = ((List) catalog.tools).size()

        then: 'within the values and the bytes a reader takes'
        values(catalog) <= ToolCatalogBuilder.MAX_VALUES
        json.length() <= ToolCatalogBuilder.MAX_BYTES
        listed > 0 && listed < 80

        and: 'the first by name, and what is left out is said'
        ((List<Map>) catalog.tools)*.name == classes.collect { "t-${it.replaceAll(/\D/, '')}".toString() }.sort().take(listed)
        catalog.diagnostics == ["There are 80 commands, more than a catalog lists in ${ToolCatalogBuilder.MAX_VALUES} values: the last ${80 - listed} by name are not listed.".toString()]
    }

    // the values of a document as the reader of a catalog counts them: every value, and not the names of the members of objects
    private static int values(Object value) {
        int count = 1
        if (value instanceof Map) {
            ((Map) value).values().each { count += values(it) }
        } else if (value instanceof List) {
            ((List) value).each { count += values(it) }
        }
        count
    }

    // a directory that registers the class, with the descriptor when there is one
    private File element(String name, String descriptor) {
        File element = new File(dir, name)
        new File(element, 'META-INF').mkdirs()
        new File(element, 'META-INF/grails-cli.factories').text = "org.apache.grails.core.cli.ApplicationCommand=${CLASS}\n"
        if (descriptor != null) {
            File file = new File(element, ToolDescriptor.location(CLASS))
            file.parentFile.mkdirs()
            file.text = descriptor
        }
        return element
    }
}

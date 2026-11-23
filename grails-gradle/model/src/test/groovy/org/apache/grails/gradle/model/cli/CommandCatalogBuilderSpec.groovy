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
class CommandCatalogBuilderSpec extends Specification {

    private static final String CLASS = 'demo.ReportCommand'

    @TempDir
    File dir

    @Unroll
    def "the command of the application keeps the name the application gives it when a jar that comes later has a descriptor for its class: #title"() {
        given:
        File application = element('application', own)
        File later = element('later', CommandDescriptor.describe(CLASS, 'help', 'Not what the application says'))

        when:
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add(CommandCatalogBuilder.ORIGIN_APPLICATION, application)
        builder.add('org.hostile:later:1.0', later)
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commands == [[name: name, aliases: [], description: description, origin: 'application', target: 'application', registration: registration]]
        catalog.diagnostics == ['The command class demo.ReportCommand is listed as the application has it: another descriptor for it, in org.hostile:later:1.0, is not used.']
        catalog.complete == false

        where:
        title                             | own                                                               | name     | description        | registration
        'the application has none'        | null                                                              | 'report' | ''                 | 'factories'
        'the application has its own'     | CommandDescriptor.describe(CLASS, 'audit-report', 'Writes it')    | 'audit-report' | 'Writes it'  | 'descriptor'
    }

    def "a jar that has the same descriptor as the application says nothing else, and nothing is said of it"() {
        given:
        String same = CommandDescriptor.describe(CLASS, 'audit-report', 'Writes it')

        when:
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add(CommandCatalogBuilder.ORIGIN_APPLICATION, element('application', same))
        builder.add('org.example:copy:1.0', element('copy', same))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commands*.name == ['audit-report']
        catalog.commands*.origin == ['application']
        catalog.diagnostics == []
        catalog.complete == true
    }

    def "two jars that describe one class differently still leave it out: neither is the one whose class it is"() {
        when:
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add('org.example:one:1.0', element('one', CommandDescriptor.describe(CLASS, 'one', null)))
        builder.add('org.example:two:1.0', element('two', CommandDescriptor.describe(CLASS, 'two', null)))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commands == []
        catalog.diagnostics == ['The command class demo.ReportCommand is not listed: it has descriptors that differ, in org.example:one:1.0, org.example:two:1.0.']
    }

    @Unroll
    def "a catalog lists no more commands than are read from it, the first by name, and says how many it left out: #title"() {
        given: 'more commands than a catalog lists, registered in an order that is not the order of their names'
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        (0..<3).each { int part ->
            File part0 = new File(dir, "part${part}")
            new File(part0, 'META-INF').mkdirs()
            List<String> classes = (0..<200).collect { int number -> "demo.p${part}.C${199 - number}Command".toString() }
            new File(part0, 'META-INF/grails-cli.factories').text = 'org.apache.grails.core.cli.ApplicationCommand=' + classes.join(',') + '\n'
            classes.each { String className ->
                File file = new File(part0, CommandDescriptor.location(className))
                file.parentFile.mkdirs()
                file.text = CommandDescriptor.describe(className, "c${2 - part}-${className.replaceAll(/\D/, '')}".toString(), letter * CommandDescriptor.MAX_DESCRIPTION_LENGTH)
            }
            builder.add("org.example:part${part}:1.0", part0)
        }

        when:
        String json = builder.toJson()
        Map<String, Object> catalog = (Map<String, Object>) StrictJson.parse(json.trim(), 8)
        List<String> names = ((List<Map>) catalog.commands)*.name

        then: 'within the bytes and the number a reader takes'
        json.length() <= CommandCatalogBuilder.MAX_BYTES
        names.size() <= CommandCatalogBuilder.MAX_COMMANDS
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
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add('org.example:report:1.0', element('report', CommandDescriptor.describe(CLASS, 'audit-report', 'Writes it')))

        when:
        builder.unresolved('runtimeClasspath', failure)
        Map<String, Object> catalog = builder.build()
        String line = 'runtimeClasspath cannot be resolved completely (gradle dependencies --configuration runtimeClasspath says why), ' +
                'so the commands of what is missing from it are not listed: ' + said

        then: 'the command of what resolves is listed'
        catalog.commands*.name == ['audit-report']

        and: 'the catalog says what does not resolve, in a line of a bounded length, and is not complete'
        catalog.complete == false
        catalog.diagnostics == [line.length() <= CommandCatalogBuilder.MAX_DIAGNOSTIC_LENGTH ? line :
                                        line.take(CommandCatalogBuilder.MAX_DIAGNOSTIC_LENGTH - 3) + '...']
        catalog.diagnostics.every { String each -> each.length() <= CommandCatalogBuilder.MAX_DIAGNOSTIC_LENGTH && each.every { it >= ' ' && it <= '~' } }

        where:
        title                         | failure                                             | said
        'as Gradle says it'           | 'Could not find org.example:missing:1.0.'           | 'Could not find org.example:missing:1.0.'
        'on lines of its own'         | '  Could not resolve a:b:1.\n\tRequired by:\r\n x ' | 'Could not resolve a:b:1. Required by: x'
        'with what a terminal cannot' | 'Could not find gr\u00fc\u00df:\u001b[31mred:1.'    | 'Could not find gr??:?[31mred:1.'
        'that is too long'            | 'Could not find ' + 'x' * 1000                      | 'Could not find ' + 'x' * 1000
    }

    // a directory that registers the class, with the descriptor when there is one
    private File element(String name, String descriptor) {
        File element = new File(dir, name)
        new File(element, 'META-INF').mkdirs()
        new File(element, 'META-INF/grails-cli.factories').text = "org.apache.grails.core.cli.ApplicationCommand=${CLASS}\n"
        if (descriptor != null) {
            File file = new File(element, CommandDescriptor.location(CLASS))
            file.parentFile.mkdirs()
            file.text = descriptor
        }
        return element
    }
}

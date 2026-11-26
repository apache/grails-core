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
 * The options the elements of a class path declare for a command, as the catalog of the commands lists them.
 */
class CommandOptionsSpec extends Specification {

    private static final Map TAIL = [command: 'run-app', name: 'query-tail', description: 'Capture every statement.', property: 'grails.gorm.queryTail.enabled']
    private static final String WHERE = 'META-INF/grails-command-options.json'

    @TempDir
    File dir

    def "the catalog lists the options that are declared, by command and name, with where each comes from"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, element('application', [
                [command: 'run-app', name: 'trace', description: 'Log every request.', type: 'boolean', property: 'logging.level.web', value: 'TRACE']]))
        builder.add('org.example:data-cli:1.0', element('data', [TAIL, [command: 'run-app', name: 'tenant', description: 'The tenant to run as.',
                                                                         type: 'string', property: 'demo.tenant', later: [1, 2]]]))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commandOptions == [
                [command: 'run-app', name: 'query-tail', description: 'Capture every statement.', type: 'boolean', property: 'grails.gorm.queryTail.enabled',
                 value: 'true', origin: 'org.example:data-cli:1.0'],
                [command: 'run-app', name: 'tenant', description: 'The tenant to run as.', type: 'string', property: 'demo.tenant', origin: 'org.example:data-cli:1.0'],
                [command: 'run-app', name: 'trace', description: 'Log every request.', type: 'boolean', property: 'logging.level.web', value: 'TRACE', origin: 'application'],
        ]
        catalog.diagnostics == []
        catalog.complete == true

        and: 'the document is written with them, after everything it had before'
        ((Map) StrictJson.parse(builder.toJson(), 8)).keySet().toList() == ['format', 'complete', 'diagnostics', 'tools', 'commandOptions']
    }

    def "a catalog of a class path that declares no option has no such member"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:none:1.0', element('none', null))

        then:
        !builder.build().containsKey('commandOptions')
        !builder.toJson().contains('commandOptions')
    }

    @Unroll
    def "an option that #title is left out and named by its place, with nothing of it repeated, and the others are listed"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.hostile:options:1.0', element('hostile', [TAIL + changed, [command: 'run-app', name: 'kept', description: 'Kept.', property: 'demo.kept']]))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commandOptions*.name == ['kept']
        catalog.diagnostics == ["Option 1 that org.hostile:options:1.0 declares for a command (${WHERE}) is not used: it ${why}.".toString()]
        changed.values().findAll { it instanceof String && it.length() > 12 }.every { String given -> !catalog.diagnostics[0].contains(given) }

        and: 'what is said of an option says nothing of the commands'
        catalog.complete == true

        where:
        title                                    | changed                                       | why
        'is for another command'                 | [command: 'test-app']                         | 'is for the command test-app, which takes no declared options in this version of Grails (run-app does)'
        'names no command'                       | [command: 'Run App; rm -rf']                  | 'names no command that takes declared options (run-app)'
        'has its hyphens in its name'            | [name: '--query-tail']                        | NAME
        'has a name of one letter'               | [name: 'q']                                   | NAME
        'has an upper-case name'                 | [name: 'queryTail']                           | NAME
        'has a name with an assignment'          | [name: 'query-tail=-Dorg.gradle.jvmargs']     | NAME
        'has a name that is too long'            | [name: 'a' * 33]                              | NAME
        'has no description'                     | [description: null]                           | DESCRIPTION
        'has a blank description'                | [description: '  ']                           | DESCRIPTION
        'has a description of two lines'         | [description: 'One line.\nAnother line.']     | DESCRIPTION
        'has an escape in its description'       | [description: 'Red \u001b[31m text here']     | DESCRIPTION
        'has a variable in its description'      | [description: 'Home is ${sys:user.home} now'] | DESCRIPTION
        'has markup in its description'          | [description: 'This is @|bold nothing|@ here'] | DESCRIPTION
        'has a format in its description'        | [description: 'This is 100%n of the line']    | DESCRIPTION
        'has a description that is too long'     | [description: 'a' * 201]                      | DESCRIPTION
        'has a type there is none of'            | [type: 'integer-number']                      | 'has a type that is none of boolean, string'
        'has a property of one part'             | [property: 'debug']                           | PROPERTY
        'has a property with a space'            | [property: 'demo.enabled -Xmx99g']            | PROPERTY
        'has a property with an assignment'      | [property: 'demo.enabled=true']               | PROPERTY
        'has a property that is too long'        | [property: 'demo.' + 'a' * 124]               | PROPERTY
        'sets a property of the JVM'             | [property: 'java.security.manager']           | PROPERTY
        'sets the class path of the JVM'         | [property: 'java.class.path']                 | PROPERTY
        'sets the home of the user'              | [property: 'user.home']                       | PROPERTY
        'sets a proxy'                           | [property: 'https.proxyHost']                 | PROPERTY
        'sets the environment'                   | [property: 'grails.env']                      | PROPERTY
        'sets the environment in another case'   | [property: 'Grails.ENV']                      | PROPERTY
        'names other configuration'              | [property: 'spring.config.additional-location'] | PROPERTY
        'names other configuration as bound'     | [property: 'spring.config.additional_Location'] | PROPERTY
        'names other configuration of Grails'    | [property: 'grails.config.locations']         | PROPERTY
        'selects profiles'                       | [property: 'spring.profiles.active']          | PROPERTY
        'adds sources to the application'        | [property: 'spring.main.sources']             | PROPERTY
        'sets properties as JSON'                | [property: 'spring.application.json']         | PROPERTY
        'names a logging configuration'          | [property: 'logging.config']                  | PROPERTY
        'sets the port, which --port does'       | [property: 'server.port']                     | PROPERTY
        'sets the key store, which --https does' | [property: 'server.ssl.key-store']            | PROPERTY
        'sets a value with a space'              | [value: 'true -Dother=1']                     | VALUE
        'sets a value that is no string'         | [value: true]                                 | VALUE
        'sets an empty value'                    | [value: '']                                   | VALUE
        'sets a value that is too long'          | [value: 'a' * 65]                             | VALUE
        'takes a value and declares one'         | [type: 'string', value: 'given-by-the-jar']   | 'takes a value on the command line, and declares one as well, which only a switch does'
    }

    @Unroll
    def "declarations that #title are not used at all, and that is said"() {
        given:
        File element = new File(dir, 'hostile')
        File file = new File(element, WHERE)
        file.parentFile.mkdirs()
        file.bytes = content

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.hostile:options:1.0', element)
        Map<String, Object> catalog = builder.build()

        then:
        !catalog.containsKey('commandOptions')
        catalog.diagnostics == ["The options org.hostile:options:1.0 declares for commands are not used: its declarations (${WHERE}) ${why}.".toString()]
        catalog.complete == true

        where:
        title                     | content                                                                     | why
        'are not JSON'            | 'options: []'.bytes                                                         | 'are not JSON in UTF-8 as the format has it (strict JSON, nested at most 5 deep)'
        'are not UTF-8'           | [0x7b, 0x22, 0xff, 0x22, 0x7d] as byte[]                                    | 'are not JSON in UTF-8 as the format has it (strict JSON, nested at most 5 deep)'
        'are nested too deep'     | '{"format":1,"options":[{"command":[[[[1]]]]}]}'.bytes                          | 'are not JSON in UTF-8 as the format has it (strict JSON, nested at most 5 deep)'
        'are an array'            | '[]'.bytes                                                                  | 'are not a JSON object'
        'are of another format'   | '{"format":2,"options":[]}'.bytes                                           | 'are not of format 1, the one this version of Grails reads'
        'have no format'          | '{"options":[]}'.bytes                                                      | 'are not of format 1, the one this version of Grails reads'
        'have no options'         | '{"format":1,"options":{}}'.bytes                                           | 'have no array of options'
        'are too large'           | ('{"format":1,"options":[]}' + ' ' * CommandOptions.MAX_BYTES).bytes        | 'have more than 32768 bytes or cannot be read'
    }

    def "an element declares no more options than an element can, and a catalog lists no more than a catalog can"() {
        given:
        List<Map> many = (1..20).collect { int number -> [command: 'run-app', name: "option-${number}".toString(), description: 'One of many.', property: "demo.option${number}".toString()] }

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:many:1.0', element('many', many))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commandOptions.size() == 16
        catalog.diagnostics == ['org.example:many:1.0 declares more than 16 options for commands; the last 4 are not used.']

        when:
        builder = new ToolCatalogBuilder()
        (1..3).each { int jar ->
            builder.add("org.example:jar${jar}:1.0", element("jar${jar}", (1..16).collect { int number ->
                [command: 'run-app', name: "option-${jar}-${number}".toString(), description: 'One of many.', property: "demo.option${jar}x${number}".toString()]
            }))
        }
        catalog = builder.build()

        then:
        catalog.commandOptions.size() == CommandOptions.MAX_OPTIONS
        catalog.diagnostics == ['More than 32 options are declared for commands: the last 16 by command and name are not used.']
        builder.toJson().length() < ToolCatalogBuilder.MAX_BYTES
    }

    def "a name two plugins declare is the option of neither, and both are named"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:two:1.0', element('two', [TAIL + [property: 'demo.two'], [command: 'run-app', name: 'kept', description: 'Kept.', property: 'demo.kept']]))
        builder.add('org.example:one:1.0', element('one', [TAIL + [property: 'demo.one']]))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commandOptions*.name == ['kept']
        catalog.diagnostics == ['The option run-app --query-tail is not used because more than one declares it, and not the same: org.example:one:1.0, org.example:two:1.0.']
    }

    def "an element that declares a name twice, and not the same, declares neither"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:twice:1.0', element('twice', [TAIL, TAIL + [value: 'false']]))
        Map<String, Object> catalog = builder.build()

        then:
        !catalog.containsKey('commandOptions')
        catalog.diagnostics == ['The option run-app --query-tail is not used: org.example:twice:1.0 declares it more than once, and not the same each time.']
    }

    def "the same declaration in two elements, as in a plugin and in its companion, is one: that of the first"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:data:1.0', element('data', [TAIL]))
        builder.add('org.example:data-cli:1.0', element('cli', [TAIL, TAIL]))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commandOptions*.origin == ['org.example:data:1.0']
        catalog.diagnostics == []
    }

    def "what the application declares holds, and the plugin that declares the name otherwise is named"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        // the output of a project is in more than one directory
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, [element('classes', null), element('resources', [TAIL + [property: 'demo.own']])])
        builder.add('org.example:data-cli:1.0', element('cli', [TAIL]))
        Map<String, Object> catalog = builder.build()

        then:
        catalog.commandOptions == [TAIL + [type: 'boolean', property: 'demo.own', value: 'true', origin: 'application']]
        catalog.diagnostics == ['The option run-app --query-tail is as the application declares it: what org.example:data-cli:1.0 declares for that name is not used.']
        catalog.complete == true
    }

    def "what is said of the options comes after what is said of the commands, within the diagnostics a catalog has"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:data-cli:1.0', element('cli', [TAIL + [name: 'Q']]))
        builder.unresolved('runtimeClasspath', 'Could not find a:b:1.')
        Map<String, Object> catalog = builder.build()

        then:
        catalog.complete == false
        catalog.diagnostics.size() == 2
        catalog.diagnostics[0].startsWith('runtimeClasspath cannot be resolved completely')
        catalog.diagnostics[1].startsWith('Option 1 that org.example:data-cli:1.0 declares')
        catalog.diagnostics.every { String line -> line.length() <= ToolCatalogBuilder.MAX_DIAGNOSTIC_LENGTH && line.every { String each -> each >= ' ' && each <= '~' } }
    }

    private static final String NAME = 'has a name that cannot be the name of an option (lower case letters and digits, in words joined by single hyphens, ' +
            'without the hyphens in front, 2 to 32 characters)'
    private static final String DESCRIPTION = 'has no description that is one line of at most 200 characters that can be shown, without $, % and @'
    private static final String PROPERTY = 'sets no property an option can set: a name of two or more parts joined by dots, of letters, digits, hyphens and underscores, ' +
            'at most 128 characters, that is not a property of the JVM, of the build, or one that says which environment, configuration, profiles or classes ' +
            'the application is started with'
    private static final String VALUE = 'sets a value that is not at most 64 letters, digits, dots, hyphens and underscores, as a string'

    private File element(String name, List<Map> options) {
        File element = new File(dir, name)
        new File(element, 'META-INF').mkdirs()
        if (options != null) {
            new File(element, WHERE).text = StrictJson.write([format: 1, options: options])
        }
        return element
    }
}

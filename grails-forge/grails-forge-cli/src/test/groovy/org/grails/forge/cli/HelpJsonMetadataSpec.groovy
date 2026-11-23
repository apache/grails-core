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
package org.grails.forge.cli

import groovy.json.JsonSlurper
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

/**
 * What {@code help --json} says of the options of the commands of the command line itself besides
 * their names: the values an option takes where those are all it takes, whether it can be given more
 * than once, the value it has when it is not given, and the rules that tie options together. A tool
 * reads these from the document and not from the descriptions, and the rules are those the commands
 * hold a command line to.
 */
class HelpJsonMetadataSpec extends CommandSpec {

    // A command line that breaks each rule of each command, which the command refuses as a mistake. The
    // project of a feature has a wrapper that says when it ran: a command line that breaks a rule runs nothing.
    @Shared
    Map<String, List<List<String>>> breaking = [
            'run-app exclusive --detach --foreground'                    : [['run-app', '--detach', '--foreground']],
            'project regenerate exclusive --output --diff'               : [['project', 'regenerate', '--output', 'elsewhere', '--diff'],
                                                                            ['project', 'regenerate']],
            'project regenerate requires --force --output'               : [['project', 'regenerate', '--diff', '--force']],
            'help exclusive --json COMMAND'                              : [['help', '--json', 'create-app']],
            'create-app requires --feature-pack-sha256 --features'       : [['create-app', 'shop', '--feature-pack-sha256', "org.example:pack:1.0.0=${'a' * 64}".toString()]],
            'create-webapp requires --feature-pack-sha256 --features'    : [['create-webapp', 'shop', '--feature-pack-sha256', "org.example:pack:1.0.0=${'a' * 64}".toString()]],
            'create-restapi requires --feature-pack-sha256 --features'   : [['create-restapi', 'shop', '--feature-pack-sha256', "org.example:pack:1.0.0=${'a' * 64}".toString()]],
            'create-plugin requires --feature-pack-sha256 --features'    : [['create-plugin', 'shop', '--feature-pack-sha256', "org.example:pack:1.0.0=${'a' * 64}".toString()]],
            'create-web-plugin requires --feature-pack-sha256 --features': [['create-web-plugin', 'shop', '--feature-pack-sha256', "org.example:pack:1.0.0=${'a' * 64}".toString()]],
            'create-rest-api-plugin requires --feature-pack-sha256 --features': [['create-rest-api-plugin', 'shop', '--feature-pack-sha256',
                                                                                  "org.example:pack:1.0.0=${'a' * 64}".toString()]]
    ]

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

    void "the options say what values they take, whether they repeat and what they are without being given, beside what they said before"() {
        when:
        Map document = describe()
        Map createApp = command(document, 'create-app')
        Map runApp = command(document, 'run-app')
        Map servlet = option(createApp, '--servlet')

        then: 'the document has the format it had: what is new is members a reader of the format ignores when it does not know them'
        document.format == 1
        document.commands.every { Map each -> each.options.every { Map option -> option.keySet().containsAll(['names', 'arity', 'label', 'required', 'description']) } }

        and: 'an option that takes one of some values gives them, as the help says them'
        servlet.choices.containsAll(['tomcat', 'jetty'])
        servlet.description.contains("Possible values: ${servlet.choices.join(', ')}")
        option(createApp, '--data').choices.contains('hibernate5')
        option(createApp, '--reloading').choices.contains('devtools')
        option(command(document, 'project', 'migrate'), '--type').choices == ['web', 'rest-api', 'web-plugin', 'plugin', 'rest-api-plugin']

        and: 'values that are not all an option takes are no choices: the features are listed by --list-features, which needs the generator'
        option(createApp, '--features').choices == null
        option(createApp, '--jdk').choices == null

        and: 'what can be given more than once, and what is given as a list in one value'
        option(createApp, '--features').repeatable == true
        option(createApp, '--features').split == ','
        option(createApp, '--feature-pack-dir').repeatable == true
        option(createApp, '--feature-pack-dir').split == null
        option(runApp, '-D').repeatable == true
        option(runApp, '--port').repeatable == false
        option(createApp, '--inplace').repeatable == false
        command(document, 'feature', 'add').parameters[0].repeatable == true
        command(document, 'feature', 'add').parameters[0].split == ','

        and: 'the value an option has when it is not given, where the command declares one; a switch has none'
        option(runApp, '--ready-timeout').default == '300'
        option(runApp, '--port').default == null
        option(runApp, '--detach').default == null
        document.options.every { Map each -> each.containsKey('default') && each.default == null }
    }

    void "the rules that tie options together are given with each command, and every command has the member"() {
        when:
        Map document = describe()
        List<Map> all = everyCommand(document.commands as List<Map>)

        then:
        all.every { Map each -> each.relations instanceof List }
        command(document, 'run-app').relations == [[kind: 'exclusive', members: ['--detach', '--foreground'], required: false]]
        command(document, 'project', 'regenerate').relations == [[kind: 'exclusive', members: ['--output', '--diff'], required: true],
                                                                 [kind: 'requires', members: ['--force'], requires: ['--output']]]
        command(document, 'help').relations == [[kind: 'exclusive', members: ['--json', 'COMMAND'], required: false]]
        command(document, 'create-app').relations == [[kind: 'requires', members: ['--feature-pack-sha256'], requires: ['--features']]]
        command(document, 'compile').relations == []

        and: 'a member of a rule is an option of the command by its longest name, or a parameter by its label'
        all.findAll { it.relations }.every { Map each ->
            List<String> names = (each.options as List<Map>).collect { Map option -> (option.names as List<String>).max { it.length() } } + (each.parameters as List<Map>)*.label
            (each.relations as List<Map>).every { Map relation -> names.containsAll(relation.members) && names.containsAll(relation.requires ?: []) }
        }

        and: 'every rule the document gives has a command line that breaks it below'
        everyRule(all) as Set == breaking.keySet()
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "the command holds a command line to its rule #rule: #typed is a mistake in the command line, and nothing is run"() {
        given: 'a project whose wrapper says when it ran'
        new File(dir, 'grails-app').mkdirs()
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\necho ran > "$(dirname "$0")/wrapper-ran"\n'
        wrapper.setExecutable(true, false)

        when:
        int exit = ForgeCli.execute(beanContext, typed as String[])

        then:
        exit == 2
        !new File(dir, 'wrapper-ran').exists()
        !new File(dir, 'shop').exists()
        !new File(dir, 'elsewhere').exists()

        where:
        [rule, typed] << breaking.collectMany { String rule, List<List<String>> lines -> lines.collect { [rule, it] } }
    }

    private Map describe() {
        out.reset()
        assert ForgeCli.execute(beanContext, 'help', '--json') == 0
        return (Map) new JsonSlurper().parseText(out.toString())
    }

    private static Map command(Map document, String... names) {
        List<Map> commands = document.commands as List<Map>
        Map found = null
        for (String name : names) {
            found = commands.find { it.name == name }
            commands = found.commands as List<Map>
        }
        return found
    }

    private static Map option(Map command, String name) {
        return (command.options as List<Map>).find { name in it.names }
    }

    private static List<Map> everyCommand(List<Map> commands) {
        return commands.collectMany { Map each -> [each] + everyCommand(each.commands as List<Map>) }
    }

    // each rule of each command, as the table of command lines above names it
    private static List<String> everyRule(List<Map> commands) {
        List<String> rules = []
        for (Map each : commands) {
            String name = each.name == 'regenerate' ? 'project regenerate' : each.name
            for (Map relation : (each.relations as List<Map>)) {
                rules << ([name, relation.kind] + (relation.members as List<String>) + ((relation.requires ?: []) as List<String>)).join(' ')
            }
        }
        return rules
    }
}

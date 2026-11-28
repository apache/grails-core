/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.cli

import groovy.json.JsonSlurper
import spock.lang.Unroll

/**
 * The names of the commands of {@code feature} and of {@code plugin} that find and show: {@code find}
 * and {@code show}, with {@code info} as the other name of {@code show}. No two of the commands of
 * a group start with the same letter, so that one letter is short for each of them, and the text
 * {@code find} lists by is an argument the command line requires.
 */
class FindAndShowNamesSpec extends CommandSpec {

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

    @Unroll
    void "#typed is short for #command alone"() {
        when:
        int exit = ForgeCli.execute(beanContext, (typed.split(' ').toList() + '--help') as String[])

        then:
        exit == 0
        out.toString().startsWith("Usage: grails-forge-cli ${command} ")
        err.toString().isEmpty()

        where:
        typed  | command
        'f f'  | 'feature find'
        'f s'  | 'feature show'
        'f i'  | 'feature show'
        'f d'  | 'feature diff'
        'f a'  | 'feature add'
        'f l'  | 'feature list'
        'pl f' | 'plugin find'
        'pl s' | 'plugin show'
        'pl i' | 'plugin show'
        'pl l' | 'plugin list'
    }

    @Unroll
    void "every command of #group, by each of its names, is found by the first letter of the name"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == group }
        List<List<String>> names = ((List<Map>) described.commands).collect { Map command -> [command.name as String] + ((command.aliases ?: []) as List<String>) }

        then:
        exit == 0
        names == expected

        and: 'no two names start alike, whichever command they are of'
        names.flatten().collect { String name -> name[0] }.unique().size() == names.flatten().size()

        and: 'and the first letter is that command'
        names.every { List<String> ofOne ->
            ofOne.every { String name ->
                out.reset()
                ForgeCli.execute(beanContext, group, name[0], '--help') == 0 &&
                        out.toString().startsWith("Usage: grails-forge-cli ${group} ${ofOne[0]} ")
            }
        }
        err.toString().isEmpty()

        where:
        group     | expected
        'feature' | [['add'], ['list'], ['find'], ['show', 'info'], ['diff']]
        'plugin'  | [['list'], ['find'], ['show', 'info']]
    }

    @Unroll
    void "#args is no command: there is no search command"() {
        when:
        int exit = ForgeCli.execute(beanContext, args as String[])

        then:
        exit == 2
        out.toString().isEmpty()
        !err.toString().isEmpty()

        where:
        args << [['feature', 'search', 'cache'], ['plugin', 'search', '--plugin-index', 'none', 'cache'], ['feature', 'se', 'cache'], ['plugin', 'se', 'cache']]
    }

    @Unroll
    void "the usage of #command has the text as an argument it requires"() {
        when:
        int exit = ForgeCli.execute(beanContext, (command.split(' ').toList() + '--help') as String[])
        String usage = synopsis(out.toString())

        then:
        exit == 0
        usage.startsWith("Usage: grails-forge-cli ${command} ")
        usage.endsWith(' TEXT')
        !usage.contains('[TEXT]')
        !usage.contains('FILTER')

        where:
        command << ['feature find', 'plugin find']
    }

    @Unroll
    void "the usage of #command has the text as an argument it can do without"() {
        when:
        int exit = ForgeCli.execute(beanContext, (command.split(' ').toList() + '--help') as String[])

        then:
        exit == 0
        synopsis(out.toString()).endsWith(' [TEXT]')

        where:
        command << ['plugin list', 'list-plugins']
    }

    @Unroll
    void "#args without a text is a mistake in the command line that the command line itself finds"() {
        when:
        int exit = ForgeCli.execute(beanContext, args as String[])

        then:
        exit == 2
        out.toString().isEmpty()
        err.toString().readLines()[0] == "Missing required parameter: 'TEXT'"
        err.toString().contains("Usage: grails-forge-cli ${args[0]} find ")

        where:
        args << [['feature', 'find'], ['feature', 'find', '--json'], ['plugin', 'find'], ['plugin', 'find', '--plugin-index', 'none', '--json'],
                 ['plugin', 'find', '--offline', '--deprecated']]
    }

    void "help --json says that find requires its text, and that the lists of plugins do not"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        List<Map> commands = (List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands
        List<Map> ofFeature = (List<Map>) commands.find { it.name == 'feature' }.commands
        List<Map> ofPlugin = (List<Map>) commands.find { it.name == 'plugin' }.commands

        then:
        exit == 0
        parameters(ofFeature.find { it.name == 'find' }) == [[label: 'TEXT', arity: '1']]
        parameters(ofPlugin.find { it.name == 'find' }) == [[label: 'TEXT', arity: '1']]
        parameters(ofPlugin.find { it.name == 'list' }) == [[label: 'TEXT', arity: '0..1']]
        parameters(commands.find { it.name == 'list-plugins' }) == [[label: 'TEXT', arity: '0..1']]

        and: 'the command that shows one, by its first name, with the other beside it'
        ofFeature.find { it.name == 'show' }.aliases == ['info']
        ofPlugin.find { it.name == 'show' }.aliases == ['info']
        parameters(ofFeature.find { it.name == 'show' }) == [[label: 'FEATURE', arity: '1']]

        and:
        !(ofFeature + ofPlugin).any { it.name == 'search' || 'search' in ((it.aliases ?: []) as List) }
    }

    void "plugin find lists by its text as plugin list does, and names plugin show"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'plugin', 'list', '--plugin-index', 'none', 'quartz')
        String listed = out.toString()
        out.reset()
        int found = ForgeCli.execute(beanContext, 'plugin', 'find', '--plugin-index', 'none', 'quartz')

        then:
        exit == 0
        found == 0
        err.toString().isEmpty()
        out.toString() == listed
        listed.readLines().any { it.contains(' org.apache.grails:grails-quartz ') }
        listed.readLines()[-1] == 'More of one plugin: grails-forge-cli plugin show NAME'
    }

    // the line of the help that says how the command is typed, with the indented lines it runs on to
    private static String synopsis(String help) {
        List<String> lines = help.readLines()
        int more = lines.tail().findIndexOf { String line -> !line.startsWith(' ') }
        return lines.subList(0, more < 0 ? lines.size() : more + 1)*.trim().join(' ')
    }

    private static List<Map> parameters(Map command) {
        return ((List<Map>) command.parameters).collect { Map parameter -> [label: parameter.label, arity: parameter.arity] }
    }
}

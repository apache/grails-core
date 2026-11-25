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
package org.apache.grails.forge.cli.plugin

import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * {@code plugin list}, {@code plugin show} with its other name {@code plugin info}, and
 * {@code plugin search}: the commands {@code list-plugins} and {@code plugin-info} by their names
 * among the commands of {@code plugin}. No plugin index is named, so nothing is asked of the network.
 */
class PluginGroupCommandsSpec extends CommandSpec {

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
    void "#recent writes what #former writes, but for the commands it names"() {
        when:
        int formerExit = ForgeCli.execute(beanContext, former as String[])
        String formerSaid = out.toString()
        String formerErrors = err.toString()
        out.reset()
        err.reset()
        int recentExit = ForgeCli.execute(beanContext, recent as String[])

        then:
        formerExit == expected
        recentExit == expected
        !(formerSaid + formerErrors).isEmpty()

        and: 'the same lines, each command naming the commands of its own kind'
        out.toString() == formerSaid.replace('grails-forge-cli plugin-info NAME', 'grails-forge-cli plugin show NAME')
        err.toString() == formerErrors.replace('grails-forge-cli list-plugins lists', 'grails-forge-cli plugin list lists')

        where:
        former                                                        | recent                                                                    | expected
        ['list-plugins', '--plugin-index', 'none']                    | ['plugin', 'list', '--plugin-index', 'none']                              | 0
        ['list-plugins', '--plugin-index', 'none', 'cache']           | ['plugin', 'list', '--plugin-index', 'none', 'cache']                     | 0
        ['list-plugins', '--plugin-index', 'none', 'cache']           | ['plugin', 'search', '--plugin-index', 'none', 'cache']                   | 0
        ['list-plugins', '--plugin-index', 'none', '--json']          | ['plugin', 'list', '--plugin-index', 'none', '--json']                    | 0
        ['list-plugins', '--offline', '--deprecated', 'no-such-text'] | ['plugin', 'search', '--offline', '--deprecated', 'no-such-text']         | 0
        ['plugin-info', 'quartz', '--offline']                        | ['plugin', 'show', 'quartz', '--offline']                                 | 0
        ['plugin-info', 'quartz', '--offline']                        | ['plugin', 'info', 'quartz', '--offline']                                 | 0
        ['plugin-info', 'quartz', '--offline', '--json']              | ['plugin', 'show', 'quartz', '--offline', '--json']                       | 0
        ['plugin-info', 'org.apache.grails:grails-cache', '--plugin-index', 'none', '--snapshots'] | ['plugin', 'info', 'org.apache.grails:grails-cache', '--plugin-index', 'none', '--snapshots'] | 0
        ['plugin-info', 'no-such-plugin', '--offline']                | ['plugin', 'show', 'no-such-plugin', '--offline']                         | 1
    }

    void "plugin list names plugin show for more of one plugin, and the two lists are the same plugins"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'plugin', 'list', '--plugin-index', 'none')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines[-1] == 'More of one plugin: grails-forge-cli plugin show NAME'
        lines.any { it.contains(' org.apache.grails:grails-quartz ') }
    }

    void "plugin search lists the plugins that have the text, and is not run without one"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'plugin', 'search', '--plugin-index', 'none', 'QUARTZ')
        List<String> lines = out.toString().readLines()

        then: 'in whichever case, over the name, the coordinates and the description'
        exit == 0
        lines.any { it.contains(' org.apache.grails:grails-quartz ') }
        !lines.any { it.contains(' org.apache.grails:grails-cache ') }

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin', 'search', '--plugin-index', 'none', '--json', 'cache')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        ((List<Map>) document.plugins)*.coordinates.contains('org.apache.grails:grails-cache')
        ((List<Map>) document.plugins).every { Map plugin -> [plugin.name, plugin.coordinates, plugin.description].any { it?.toString()?.toLowerCase()?.contains('cache') } }

        when: 'no text'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin', 'search', '--plugin-index', 'none')

        then: 'a mistake in the command line'
        exit == 2
        out.toString().isEmpty()
        err.toString().contains('Give the text to search the plugins by: plugin list lists every plugin')
    }

    @Unroll
    void "the commands of plugin are commands of the command line #where: in the help, by a short form, in help --json, and with the options every command has"() {
        given:
        if (inProject) {
            assert ForgeCli.execute(beanContext, 'create-app', 'example.shop') == 0
            System.setProperty('user.dir', new File(dir, 'shop').absolutePath)
            out.reset()
            err.reset()
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        List<Map> commands = (List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands
        Map plugin = commands.find { it.name == 'plugin' }

        then: 'beside the two names they had before'
        exit == 0
        commands*.name.containsAll(['list-plugins', 'plugin-info', 'plugin'])
        ((List<Map>) plugin.commands)*.name == ['list', 'search', 'show']
        ((List<Map>) plugin.commands).find { it.name == 'show' }.aliases == ['info']

        and: 'each with the options of the command it is'
        optionNames(((List<Map>) plugin.commands).find { it.name == 'list' }) == optionNames(commands.find { it.name == 'list-plugins' })
        optionNames(((List<Map>) plugin.commands).find { it.name == 'search' }) == optionNames(commands.find { it.name == 'list-plugins' })
        optionNames(((List<Map>) plugin.commands).find { it.name == 'show' }) == optionNames(commands.find { it.name == 'plugin-info' })

        when: 'how the commands are used'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'plugin')

        then:
        exit == 0
        out.toString().readLines()[0].startsWith('Usage: grails-forge-cli plugin ')
        ['list', 'search', 'show, info'].every { String name -> out.toString().readLines().any { it.trim().startsWith(name + ' ') } }

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin', 'info', '--help')

        then:
        exit == 0
        out.toString().readLines()[0].startsWith('Usage: grails-forge-cli plugin show ')

        when: 'plain, and never waiting for what is typed'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin', 'show', 'cache', '--offline', '--plain-output', '--non-interactive')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == 'Plugin: Cache (org.apache.grails:grails-cache)'
        !out.toString().contains('\u001b[')

        when: 'by what is short for the names'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin', 'sh', 'cache', '--offline')

        then:
        exit == 0
        out.toString().readLines()[0] == 'Plugin: Cache (org.apache.grails:grails-cache)'

        when: 'without a command of it'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin')

        then:
        exit == 2
        err.toString().contains('No command specified: plugin list lists the Grails plugins')

        where:
        inProject << [false, true]
        where = inProject ? 'in a project' : 'outside a project'
    }

    private static List<String> optionNames(Map command) {
        return ((List<Map>) command.options).collect { ((List<String>) it.names).join('|') }.sort()
    }
}

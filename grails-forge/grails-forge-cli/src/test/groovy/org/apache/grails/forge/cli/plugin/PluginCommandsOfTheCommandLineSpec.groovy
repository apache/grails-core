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
import org.apache.grails.forge.cli.console.FormerCommands
import org.apache.grails.forge.core.bom.BomVersions
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * {@code list-plugins} and {@code plugin-info} as commands of the command line, outside a project and
 * in one. No plugin index is named ({@code --plugin-index none}, and the environment of the tests
 * names none either), so nothing is asked of the network.
 */
class PluginCommandsOfTheCommandLineSpec extends CommandSpec {

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
    void "the two commands are commands of the command line #where"() {
        given:
        if (inProject) {
            assert ForgeCli.execute(beanContext, 'create-app', 'example.shop') == 0
            System.setProperty('user.dir', new File(dir, 'shop').absolutePath)
            out.reset()
            err.reset()
        }

        when: 'the commands there are here'
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        List<Map> commands = (List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands

        then:
        exit == 0
        commands*.name.containsAll(['list-plugins', 'plugin-info'])
        commands.find { it.name == 'list-plugins' }.description.startsWith('Lists the Grails plugins')

        and: 'they are no commands of earlier versions that are gone'
        FormerCommands.find('list-plugins') == null
        FormerCommands.find('plugin-info') == null

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'list-plugins', '--plugin-index', 'none', 'cache')

        then: 'the plugins of this release of Grails, without the network'
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == "Grails plugins for Grails ${VersionInfo.grailsVersion}"
        out.toString().readLines()[2] ==~ "  Cache +org\\.apache\\.grails:grails-cache +${java.util.regex.Pattern.quote(BomVersions.of('grails-bom').versionOf('org.apache.grails:grails-cache'))} +Caches .*"
        out.toString().readLines()[-1] == 'More of one plugin: grails-forge-cli plugin-info NAME'

        when: 'by what is short for its name, and with the environment naming no index'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'lP', 'quartz')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().contains(' org.apache.grails:grails-quartz ')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin-info', 'quartz', '--offline')

        then:
        exit == 0
        out.toString().readLines()[0] == 'Plugin: Quartz (org.apache.grails:grails-quartz)'
        out.toString().contains('* Feature: grails-quartz. In the directory of an application: grails-forge-cli feature add grails-quartz')
        out.toString().contains("    implementation 'org.apache.grails:grails-quartz'")

        when: 'a plugin nobody knows'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'plugin-info', 'no-such-plugin', '--offline')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().size() == 1
        err.toString().startsWith('| Error No plugin no-such-plugin is known among the plugins of this release of Grails. grails-forge-cli list-plugins lists the plugins')

        when: 'how a command is used'
        err.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'plugin-info')

        then:
        exit == 0
        out.toString().readLines()[0].startsWith('Usage: grails-forge-cli plugin-info ')

        where:
        inProject << [false, true]
        where = inProject ? 'in a project' : 'outside a project'
    }
}

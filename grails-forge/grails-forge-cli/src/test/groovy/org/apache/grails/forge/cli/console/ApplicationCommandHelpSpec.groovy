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
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * {@code help NAME} for a command of the application, of a plugin, or a script of the application:
 * answered from what the build listed, and nothing is run for it, neither the build nor the command.
 * {@code NAME --help} gives {@code --help} to the command, which runs it. The wrapper of the project
 * here writes down that it ran, and is no Gradle.
 */
@IgnoreIf({ os.windows })
class ApplicationCommandHelpSpec extends CommandSpec {

    static final List<Map> COMMANDS = [
            [name: 'audit-report', aliases: [], description: 'Writes the audit report', origin: 'application', target: 'application', registration: 'factories'],
            [name: 'hello-world', aliases: [], description: 'Says hello', origin: 'org.example:hello-cli:1.2.3', target: 'standalone', registration: 'descriptor']
    ]

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File ran

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'src/main/scripts').mkdirs()
        new File(dir, 'src/main/scripts/data-import.groovy').text = 'description "Imports data", "grails data-import"\n'
        ran = new File(dir, 'wrapper-ran')
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\nprintf \'%s\\n\' "$@" >> "$(dirname "$0")/wrapper-ran"\n'
        wrapper.setExecutable(true, false)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Unroll
    void "help #name describes the command from what the build listed, and runs neither the build nor the command"() {
        given:
        listed()

        when:
        int exit = ForgeCli.execute(beanContext, 'help', name)

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == "Usage: grails-forge-cli ${name} [ARGS...]"
        out.toString().replaceAll(/\s+/, ' ').contains(said)
        out.toString().replaceAll(/\s+/, ' ').contains('--help after the name is given to the command as well')

        and:
        !ran.exists()
        !new File(dir, '.grails').exists()

        where:
        name           | said
        'audit-report' | 'Writes the audit report A command of the application, which runs in the application'
        'hello-world'  | 'Says hello A command of org.example:hello-cli:1.2.3, which runs without the application'
        'data-import'  | 'A script of the application, in src/main/scripts'
    }

    @Unroll
    void "#name --help is given to the command, which the build runs"() {
        given:
        listed()

        when:
        int exit = ForgeCli.execute(beanContext, name, '--help')

        then: 'the wrapper was run, for the task that runs the command'
        exit == 0
        ran.readLines().any { it == task || it == ":${task}" }
        err.toString().startsWith('| Running ')

        where:
        name           | task
        'audit-report' | 'runCommand'
        'hello-world'  | 'runCommand'
        'data-import'  | 'runScriptCommand'
    }

    void "help of a command of the application before the build has listed them says how they are listed, and runs nothing"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'audit-report')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command audit-report')
        err.toString().contains('The commands of the application and its plugins are not listed yet. To list them: grails-forge-cli gradle grailsCommandCatalog')
        !ran.exists()
    }

    private void listed() {
        File file = new File(dir, ApplicationCommandFile.LOCATION)
        file.parentFile.mkdirs()
        file.setText(ApplicationCommandsSpec.doc(COMMANDS), StandardCharsets.UTF_8.name())
    }
}

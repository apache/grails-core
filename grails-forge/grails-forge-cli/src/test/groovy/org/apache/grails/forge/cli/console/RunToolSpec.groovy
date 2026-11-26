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
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * {@code run-tool} runs a tool of the application or of a plugin by its name with the Gradle task
 * {@code runTool}; {@code run-command}, its name before tools were called tools, runs it as well after
 * one warning line. A tool the build lists as deprecated, by its folder or by its type, is run after a
 * warning line for each, by its name and by {@code run-tool} alike. The Gradle wrapper of the project
 * here is a script that writes down what it was called with.
 */
@IgnoreIf({ os.windows })
class RunToolSpec extends CommandSpec implements CommandFixture {

    static final Map HELLO = [name: 'hello-world', aliases: [], description: 'Says hello', origin: 'org.example:hello-cli:1.2.3', target: 'application', registration: 'descriptor']
    static final Map LEGACY = [name: 'old-report', aliases: [], description: 'Written for Grails 8', origin: 'application', target: 'application', startsApplication: true,
                               registration: 'descriptor', deprecated: ['type', 'folder']]
    static final Map MOVED = [name: 'moved', aliases: [], description: 'Still in its folder', origin: 'application', target: 'application', startsApplication: true,
                              registration: 'descriptor', deprecated: ['folder']]
    static final String DEPRECATED = '| Warning run-command is deprecated: use run-tool'
    static final String FOLDER = '| Warning old-report is in grails-app/commands, which is deprecated: move it to src/cli/groovy'
    static final String TYPE = '| Warning old-report implements ApplicationCommand, which is deprecated: implement ApplicationTool'

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
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.absolutePath)
        new File(dir, 'grails-app').mkdirs()
        ran = new File(dir, 'wrapper-ran')
        // the wrapper writes down what it was called with and what the file of the arguments says, and ends as a file next to it says
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\nhere="$(dirname "$0")"\nprintf \'%s\\n\' "$@" >> "$here/wrapper-ran"\n' +
                'for a in "$@"; do case "$a" in --args-file=*) cat "${a#--args-file=}" > "$here/arguments-given";; esac; done\n' +
                'if [ -f "$here/exit-code" ]; then exit "$(cat "$here/exit-code")"; fi\n'
        wrapper.setExecutable(true, false)
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.bytes = ProjectToolsSpec.doc([HELLO, LEGACY, MOVED]).getBytes(StandardCharsets.UTF_8)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Unroll
    void "#typed runs a tool with the Gradle task runTool, the environment development and the arguments in a file#said"() {
        when:
        int exit = ForgeCli.execute(beanContext, typed, 'hello-world', 'two words', '--flag')

        then:
        exit == 0
        out.toString().isEmpty()
        err.toString().readLines() == lines
        gradleTasks() == ['runTool']
        ran.readLines().contains('-Dgrails.env=development')
        new JsonSlurper().parseText(argumentsFile()) == ['hello-world', 'two words', '--flag']

        where:
        typed         | said                                     | lines
        'run-tool'    | ''                                       | ['| Running the tool hello-world of org.example:hello-cli:1.2.3, with the Gradle task runTool']
        'run-command' | ', after a line that says it is deprecated' | [DEPRECATED, '| Running the tool hello-world of org.example:hello-cli:1.2.3, with the Gradle task runTool']
    }

    void "a tool is run by its name with the same arguments of Gradle as by run-tool"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'hello-world', 'two words', '--flag')

        then:
        exit == 0
        err.toString().readLines() == ['| Running the tool hello-world of org.example:hello-cli:1.2.3, with the Gradle task runTool']
        gradleTasks() == ['runTool']
        ran.readLines().contains('-Dgrails.env=development')
        new JsonSlurper().parseText(argumentsFile()) == ['hello-world', 'two words', '--flag']
    }

    @Unroll
    void "#typed in front of the environment and the name gives the tool the environment, and run-command says it is deprecated"() {
        when:
        int exit = ForgeCli.execute(beanContext, typed, '-Dgrails.env=production', 'hello-world')

        then:
        exit == 0
        ran.readLines().contains('-Dgrails.env=production')
        err.toString().readLines().contains(DEPRECATED) == (typed == 'run-command')

        where:
        typed << ['run-tool', 'run-command']
    }

    @Unroll
    void "a deprecated tool is run #how, after a line for each thing of it that is deprecated"() {
        when:
        int exit = ForgeCli.execute(beanContext, typed as String[])

        then:
        exit == 0
        err.toString().readLines() == lines
        gradleTasks() == ['runTool']

        where:
        how                | typed                                  | lines
        'by its name'      | ['old-report']                         | ['| Running the tool old-report of the application, with the Gradle task runTool', FOLDER, TYPE]
        'by run-tool'      | ['run-tool', 'old-report']             | ['| Running the tool old-report of the application, with the Gradle task runTool', FOLDER, TYPE]
        'by run-command'   | ['run-command', 'old-report']          | [DEPRECATED, '| Running the tool old-report of the application, with the Gradle task runTool', FOLDER, TYPE]
        'by its name, moved' | ['moved']                            | ['| Running the tool moved of the application, with the Gradle task runTool',
                                                                       '| Warning moved is in grails-app/commands, which is deprecated: move it to src/cli/groovy']
    }

    void "run-tool runs a name the build has not listed, and says so"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'run-tool', 'not-listed')

        then:
        exit == 0
        err.toString().readLines() == ['| Running the tool not-listed, which the build of the project has not listed, with the Gradle task runTool']
        gradleTasks() == ['runTool']
    }

    @Unroll
    void "#typed ends with exit code #code when #what"() {
        given:
        new File(dir, 'exit-code').text = gradleExit

        when:
        int exit = ForgeCli.execute(beanContext, typed as String[])

        then:
        exit == code

        where:
        what                                  | typed                                 | gradleExit || code
        'the tool succeeded'                  | ['run-tool', 'hello-world']           | '0'        || 0
        'the tool did not succeed'            | ['run-tool', 'hello-world']           | '1'        || 1
        'the build failed otherwise'          | ['run-command', 'hello-world']        | '3'        || 1
        'run-tool is not given a tool'        | ['run-tool']                          | '0'        || 2
        'run-command is not given a tool'     | ['run-command']                       | '0'        || 2
        'a tool fails by its name'            | ['hello-world']                       | '1'        || 1
    }

    void "run-command is another name of run-tool in the help and in the document for a tool, and a short form of it is run-tool without a warning"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'run-command')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then: 'the help of run-tool'
        exit == 0
        help.contains('Usage: grails-forge-cli run-tool [-hvVx] [--non-interactive] [--plain-output] [-DNAME=VALUE]... NAME [ARG...]')
        help.contains('Runs a tool of the Grails application: the Gradle task runTool.')
        help.contains('run-command is another name of this command, which is deprecated.')

        when:
        out.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map runTool = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString().readLines().last())).commands).find { it.name == 'run-tool' }

        then:
        runTool.aliases == ['run-command']
        runTool.origin == 'built-in'

        when: 'the first letters of the name of run-tool'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'rt', 'hello-world')

        then:
        exit == 0
        !err.toString().contains(DEPRECATED)
        gradleTasks() == ['runTool']
    }

    // the tasks Gradle was given, without the path of the project
    private List<String> gradleTasks() {
        return ran.readLines().findAll { String argument -> !argument.startsWith('-') }.collect { String task -> task.startsWith(':') ? task.substring(1) : task }
    }

    // what the file Gradle was given the arguments in said, which is deleted once the build has ended
    private String argumentsFile() {
        assert ran.readLines().count { String argument -> argument.startsWith('--args-file=') } == 1
        return new File(dir, 'arguments-given').text
    }
}

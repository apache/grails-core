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

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 * What the command line tells to type, where it tells it: on the command line of a shell a command is
 * typed after the name of the program, and at the prompt of the interactive console, which takes the
 * commands themselves and refuses the name of the program, without it. The same command lines are
 * given once to the program {@code grails} and once as lines of its console, which is a program of its
 * own here (see {@link InteractiveConsoleSpec}).
 */
@IgnoreIf({ os.windows || !ForkedCli.available() })
class ConsoleSuggestionsSpec extends Specification {

    static final int SECONDS = 300

    // what is typed, and what is then told to type, as the command line of a shell says it
    @Shared
    Map<String, List<String>> told = [
            'no-such-command'  : ["Run 'grails help' for the commands",
                                  'The commands of the application and its plugins are not listed yet: a build that compiles the project lists them, ' +
                                          'and so does typing the name of one. To list them now: grails gradle grailsCommandCatalog'],
            'compile --nothing': ["Run 'grails help compile' for the options"],
            'feature add'      : ["Run 'grails help feature add' for the options"],
            'test'             : ['test was another name of test-app in the Grails shell. Type: grails test-app'],
            'quit'             : ['quit ended the interactive mode of the Grails shell. The interactive mode of grails is ended with exit, and there is none to end outside it'],
            'install'          : ['Run: grails gradle publishToMavenLocal'],
            'create-controller': ['grails project migrate writes the record of an application that has none'],
            'cc'               : ['cc is short for more than one command: create-controller (ccon), create-command (ccom). ' +
                                          'Type more of the name of the one that is meant, or the short form in brackets after it']
    ]

    File dir = Files.createTempDirectory('grailsforgesuggestions').toFile().canonicalFile
    File written = new File(dir, 'out.txt')
    Process program

    void setup() {
        // a project without a record, whose wrapper says that it ran
        new File(dir, 'grails-app').mkdirs()
        File script = new File(dir, 'gradlew')
        script.text = '#!/bin/sh\necho "the wrapper ran with $*"\n'
        script.setExecutable(true, false)
    }

    void cleanup() {
        if (program != null) {
            program.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
            program.destroyForcibly()
            program.waitFor(60, TimeUnit.SECONDS)
        }
        dir.deleteDir()
    }

    void "the command line of a shell tells to type commands after the name of the program"() {
        expect:
        told.every { String typed, List<String> lines ->
            String said = run(typed.split(' ').toList(), null)
            lines.every { said.contains(it) }
        }
    }

    void "the console tells to type the commands themselves, which is what its prompt takes"() {
        given:
        String input = told.keySet().join('\n') + '\ngrails help\nexit\n'

        when:
        String said = run([], input)

        then: 'every line that tells what to type tells it without the name of the program'
        told.values().flatten().every { String line ->
            String inTheConsole = line.replace("'grails help", "'help").replace(': grails ', ': ').replace('Type: grails ', 'Type: ').replace('grails project migrate', 'project migrate')
            said.contains(inTheConsole)
        }

        and: 'the name of the program is refused at the prompt, and the answer says what to type there'
        said.contains('There is no command grails')
        !said.contains("'grails ")
        !said.contains('them now: grails ')
        !said.contains('Run: grails ')
        !said.contains('Type: grails ')
        !said.contains('grails project migrate')

        and: 'where the text names the program and tells nothing to type, the name is kept'
        said.contains('The interactive mode of grails is ended with exit')
    }

    // the program grails, with a command line or with the lines of its console
    private String run(List<String> args, String input) {
        ProcessBuilder builder = ForkedCli.builder(dir, [], args as String[]).redirectOutput(written).redirectErrorStream(true)
        int main = builder.command().indexOf(Application.name)
        builder.command().set(main, InteractiveConsoleSpec.AsGrails.name)
        builder.environment().remove('GRAILS_SAFE_DIRECTORY')
        builder.environment().put('NO_COLOR', '1')
        // what is told to type is said without a build: a name that is no command does not have the wrapper list the commands of the application
        builder.environment().put('GRAILS_LIST_COMMANDS', 'false')
        if (input == null) {
            builder.redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null')))
        } else {
            File lines = new File(dir, 'typed.txt')
            lines.text = input
            builder.redirectInput(lines)
        }
        program = builder.start()
        assert program.waitFor(SECONDS, TimeUnit.SECONDS)
        return written.text
    }
}

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

import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.springframework.context.ConfigurableApplicationContext
import picocli.CommandLine
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CommandLineCompleterSpec extends Specification {

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext context = Application.builder().run()

    @Shared
    CommandLine commandLine = ForgeCli.createCommandLine(context, true)

    @Shared
    @AutoCleanup
    Terminal terminal = TerminalBuilder.builder()
            .type(Terminal.TYPE_DUMB)
            .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
            .build()

    void 'an empty prompt offers every command and the options of the root command'() {
        expect:
        complete('').containsAll(['create-app', 'create-webapp', 'create-restapi', 'create-plugin', 'create-web-plugin', 'create-rest-api-plugin',
                                  '--help', '--version'])
    }

    void 'a partly typed command is offered as whole words, which the prompt narrows to the typed prefix'() {
        when:
        List<String> offered = complete('create-a')

        then: 'whole command names, not the rest of the word'
        offered.contains('create-app')
        !offered.contains('pp')
    }

    void 'an option typed after a command is completed from that command, not the root command'() {
        when:
        List<String> offered = complete('create-app --fe')

        then:
        offered.containsAll(['--features', '--servlet', '--jdk', '--gorm'])
        !offered.contains('create-plugin')
    }

    void 'the other names of a command are offered with the names, and an option after one is completed from its command'() {
        expect:
        complete('ru').containsAll(['run', 'run-app', 'run-script', 'run-command'])
        complete('st').containsAll(['stats', 'stop', 'stop-app'])
        complete('run --de').contains('--detach')
        complete('stop --he').contains('--help')
    }

    void 'the completer needs a command to complete from'() {
        when:
        new CommandLineCompleter(null)

        then:
        NullPointerException e = thrown()
        e.message == 'spec'
    }

    private List<String> complete(String typed) {
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
        List<Candidate> candidates = []
        new CommandLineCompleter(commandLine.commandSpec)
                .complete(reader, new DefaultParser().parse(typed, typed.length()), candidates)
        candidates*.value()
    }
}

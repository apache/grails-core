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
import java.util.function.Consumer
import groovy.json.JsonOutput
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.InteractiveShell
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import picocli.CommandLine

/**
 * Completion in the interactive console of a tool of the application or of a plugin: the options a tool
 * declares are offered after its name, as the options of a command of the command line are; a tool that
 * declares none is offered none, since everything after its name is its own.
 */
class ConsoleCompletionSpec extends CommandSpec {

    Terminal terminal

    void setup() {
        terminal = TerminalBuilder.builder()
                .type(Terminal.TYPE_DUMB)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build()
        new File(dir, 'grails-app').mkdirs()
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: [
                [name: 'audit-report', aliases: [], description: 'Writes the audit report', origin: 'application', target: 'application', registration: 'descriptor',
                 options: [[names: ['--since', '-s'], arity: '1', type: 'string', label: 'DATE'],
                           [names: ['--format'], arity: '1', type: 'string', label: 'FORMAT', choices: ['TEXT', 'JSON']],
                           [names: ['--include-deleted'], arity: '0', type: 'boolean']],
                 parameters: [[label: 'OUTPUT', arity: '1', type: 'string']]],
                [name: 'free-form', aliases: [], description: 'Takes anything', origin: 'application', target: 'application', registration: 'descriptor']]]),
                StandardCharsets.US_ASCII.name())
        catalog.setLastModified(System.currentTimeMillis() + 60_000)
    }

    void cleanup() {
        terminal?.close()
    }

    void "the options a tool declares are offered after its name, as whole words the prompt narrows to what is typed"() {
        expect:
        complete('audit-report --').containsAll(['--since', '--format', '--include-deleted'])
        complete('audit-report --f').contains('--format')
        complete('audit-report out.txt --in').contains('--include-deleted')
        complete('audit-report -').containsAll(['-s', '--since', '--format', '--include-deleted'])
        complete('audit-report --').contains('--help')
    }

    void "a tool that declares nothing is offered no option, and the name of a tool is offered as a command is"() {
        expect:
        complete('free-form --').isEmpty()
        complete('audit').contains('audit-report')
        complete('fre').contains('free-form')
    }

    private List<String> complete(String typed) {
        CommandLine commandLine = ForgeCli.createCommandLine(beanContext, true)
        InteractiveShell shell = new InteractiveShell(commandLine, { String[] args -> } as Consumer<String[]>, null)
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
        List<Candidate> candidates = []
        shell.completer().complete(reader, new DefaultParser().parse(typed, typed.length()), candidates)
        return candidates*.value()
    }
}

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

import groovy.transform.CompileStatic
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.MaskingCallback
import org.jline.reader.ParsedLine
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier

import static picocli.CommandLine.Help.Ansi.AUTO

@CompileStatic
class InteractiveShell {

    static final String DEFAULT_PROMPT = '@|blue grails>|@ '

    /** The line that leaves the prompt */
    static final String EXIT = 'exit'

    private final CommandLine commandLine
    private final Supplier<CommandLine> offered
    private final Consumer<String[]> executor
    private final BiFunction<Throwable, CommandLine, Integer> onError
    private final String prompt
    private final Terminal terminal

    InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError) {
        this(commandLine, executor, onError, DEFAULT_PROMPT)
    }

    InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            String prompt) {
        this(commandLine, { -> commandLine } as Supplier<CommandLine>, executor, onError, prompt)
    }

    /**
     * @param commandLine the command line whose help and colors the prompt starts with
     * @param offered the command line of the commands offered now, which the prompt completes
     * @param executor runs each line entered
     * @param onError reports what ends the prompt
     * @param prompt the prompt, with picocli's markup
     */
    InteractiveShell(CommandLine commandLine,
                            Supplier<CommandLine> offered,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            String prompt) {
        this(commandLine, offered, executor, onError, prompt, null)
    }

    /**
     * @param commandLine the command line whose help and colors the prompt starts with
     * @param offered the command line of the commands offered now, which the prompt completes
     * @param executor runs each line entered
     * @param onError reports what ends the prompt
     * @param prompt the prompt, with picocli's markup
     * @param terminal the terminal the prompt reads and writes, or {@code null} for the terminal of the JVM
     */
    InteractiveShell(CommandLine commandLine,
                            Supplier<CommandLine> offered,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            String prompt,
                            Terminal terminal) {
        this.terminal = terminal
        this.commandLine = commandLine
        this.offered = offered
        this.executor = executor
        this.onError = onError
        this.prompt = prompt
    }

    void start() {
        try {
            Terminal terminal = this.terminal ?: TerminalBuilder.terminal()
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new CommandLineCompleter({ -> offered.get().commandSpec } as Supplier<CommandSpec>))
                    .parser(new DefaultParser())
                    .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                    .build()

            // Applied to the command line as well as the prompt, so command help and usage text agree
            // with what the prompt does.
            CommandLine.Help.Ansi ansi = resolveAnsi(terminal)
            commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(ansi))

            String ansiPrompt = ansi.string(prompt)
            String rightPrompt = null

            // start the shell and process input until the user quits with Ctl-D
            String line
            while (true) {
                try {
                    line = reader.readLine(ansiPrompt, rightPrompt, (MaskingCallback) null, null)
                    if (line.matches('^\\s*#.*')) {
                        continue
                    }
                    if (line.equals(EXIT)) {
                        return
                    }
                    ParsedLine pl = reader.getParser().parse(line, 0)
                    String[] arguments = pl.words().toArray(new String[0])
                    executor.accept(arguments)
                } catch (UserInterruptException | EndOfFileException e) {
                    return
                }
            }
        } catch (Throwable t) {
            onError.apply(t, commandLine)
        }
    }

    /**
     * Decides whether the shell renders ansi.
     *
     * <p>{@code Help.Ansi.AUTO} cannot be trusted alone here any more. Its Windows branch reports
     * support only when a jansi {@code AnsiConsole} is installed on {@code System.out}, and this shell
     * no longer installs one. JLine's terminal is what actually renders, and it turns on
     * virtual-terminal processing itself, so it is the better answer whenever AUTO says no.</p>
     *
     * <p>{@code NO_COLOR} still wins over both, since a user asking for no colour means it.</p>
     */
    private static CommandLine.Help.Ansi resolveAnsi(Terminal terminal) {
        if (System.getenv('NO_COLOR') != null) {
            return CommandLine.Help.Ansi.OFF
        }
        if (AUTO.enabled()) {
            return CommandLine.Help.Ansi.ON
        }
        boolean terminalRenders = terminal != null && !Terminal.TYPE_DUMB.equals(terminal.getType())
        return terminalRenders ? CommandLine.Help.Ansi.ON : CommandLine.Help.Ansi.OFF
    }
}

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
package org.grails.forge.cli;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import static picocli.CommandLine.Help.Ansi.AUTO;

public class InteractiveShell {

    private static final String DEFAULT_PROMPT = "@|blue grails>|@ ";

    private final CommandLine commandLine;
    private final Consumer<String[]> executor;
    private final BiFunction<Throwable, CommandLine, Integer> onError;
    private final String prompt;

    public InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError) {
        this(commandLine, executor, onError, DEFAULT_PROMPT);
    }

    public InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            String prompt) {
        this.commandLine = commandLine;
        this.executor = executor;
        this.onError = onError;
        this.prompt = prompt;
    }

    public void start() {
        try {
            PicocliJLineCompleter picocliCommands = new PicocliJLineCompleter(commandLine.getCommandSpec());
            Terminal terminal = TerminalBuilder.terminal();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(picocliCommands)
                    .parser(new DefaultParser())
                    .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                    .build();

            // Applied to the command line as well as the prompt, so command help and usage text agree
            // with what the prompt does.
            CommandLine.Help.Ansi ansi = resolveAnsi(terminal);
            commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(ansi));

            String ansiPrompt = ansi.string(prompt);
            String rightPrompt = null;

            // start the shell and process input until the user quits with Ctl-D
            String line;
            while (true) {
                try {
                    line = reader.readLine(ansiPrompt, rightPrompt, (MaskingCallback) null, null);
                    if (line.matches("^\\s*#.*")) {
                        continue;
                    }
                    if (line.equals("exit")) {
                        return;
                    }
                    ParsedLine pl = reader.getParser().parse(line, 0);
                    String[] arguments = pl.words().toArray(new String[0]);
                    executor.accept(arguments);
                } catch (UserInterruptException | EndOfFileException e) {
                    return;
                }
            }
        } catch (Throwable t) {
            onError.apply(t, commandLine);
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
        if (System.getenv("NO_COLOR") != null) {
            return CommandLine.Help.Ansi.OFF;
        }
        if (AUTO.enabled()) {
            return CommandLine.Help.Ansi.ON;
        }
        boolean terminalRenders = terminal != null && !Terminal.TYPE_DUMB.equals(terminal.getType());
        return terminalRenders ? CommandLine.Help.Ansi.ON : CommandLine.Help.Ansi.OFF;
    }
}

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
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.PlainOutput
import org.jline.reader.Candidate
import org.jline.reader.Completer
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
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier

import static picocli.CommandLine.Help.Ansi.AUTO

@CompileStatic
class InteractiveShell {

    private static final String DEFAULT_PROMPT = '@|blue grails>|@ '

    private final CommandLine commandLine
    private final Consumer<String[]> executor
    private final BiFunction<Throwable, CommandLine, Integer> onError
    private final String prompt
    private final Supplier<CommandLine> commands
    private volatile CommandLine completed

    InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError) {
        this(commandLine, executor, onError, DEFAULT_PROMPT)
    }

    /**
     * @param commands gives the command line with the commands there are now, which is asked for
     * after every command: a command can change which commands there are, as one that makes the
     * directory a project or adds a feature to it does
     */
    InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            Supplier<CommandLine> commands) {
        this(commandLine, executor, onError, DEFAULT_PROMPT, commands)
    }

    InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            String prompt) {
        this(commandLine, executor, onError, prompt, null)
    }

    InteractiveShell(CommandLine commandLine,
                            Consumer<String[]> executor,
                            BiFunction<Throwable, CommandLine, Integer> onError,
                            String prompt,
                            Supplier<CommandLine> commands) {
        this.commandLine = commandLine
        this.executor = executor
        this.onError = onError
        this.prompt = prompt
        this.commands = commands
        this.completed = commandLine
    }

    /**
     * @return what completes a line of the shell: with the commands, options and values of the
     * command line as it was when the shell started or when {@link #refresh} was last called
     */
    Completer completer() {
        return { LineReader reader, ParsedLine line, List<Candidate> candidates ->
            new CommandLineCompleter(completed.getCommandSpec()).complete(reader, line, candidates)
        } as Completer
    }

    /**
     * Makes the commands there are now the ones that are completed. The shell does it after every command.
     */
    void refresh() {
        if (commands != null) {
            completed = commands.get()
        }
    }

    /**
     * Runs one line of the shell. While a command waits for Gradle, the interrupt of the terminal,
     * Ctrl-C, cancels the command and not the shell: the thread of the command is interrupted, which
     * stops Gradle. A command that does not run Gradle has no point at which it can be stopped and
     * leave what it writes whole, so during one the interrupt ends the shell with the command, as it
     * does at the prompt and as it did before commands could be cancelled.
     *
     * @param terminal the terminal of the shell
     * @param arguments the line, as the arguments it is
     */
    void execute(Terminal terminal, String... arguments) {
        Thread running = Thread.currentThread()
        Terminal.SignalHandler before = terminal.handle(Terminal.Signal.INT, { Terminal.Signal signal ->
            if (GradleRunner.isRunning()) {
                running.interrupt()
            } else {
                interruptedOutsideGradle()
            }
        } as Terminal.SignalHandler)
        try {
            executor.accept(arguments)
        } finally {
            terminal.handle(Terminal.Signal.INT, before)
            // an interrupt that came when nothing waited for one is not kept for the next line
            Thread.interrupted()
        }
    }

    /**
     * Called for the interrupt of the terminal while a command runs that does not wait for Gradle:
     * ends the JVM as the interrupt does when nothing handles it, with the exit code a shell gives
     * for it.
     */
    protected void interruptedOutsideGradle() {
        System.exit(130)
    }

    void start() {
        try {
            Terminal terminal = TerminalBuilder.terminal()
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer())
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
                    if (line.equals('exit')) {
                        return
                    }
                    ParsedLine pl = reader.getParser().parse(line, 0)
                    String[] arguments = pl.words().toArray(new String[0])
                    execute(terminal, arguments)
                    refresh()
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
        // --plain-output says the same for one run
        if (System.getenv('NO_COLOR') != null || PlainOutput.isOn()) {
            return CommandLine.Help.Ansi.OFF
        }
        if (AUTO.enabled()) {
            return CommandLine.Help.Ansi.ON
        }
        boolean terminalRenders = terminal != null && !Terminal.TYPE_DUMB.equals(terminal.getType())
        return terminalRenders ? CommandLine.Help.Ansi.ON : CommandLine.Help.Ansi.OFF
    }
}

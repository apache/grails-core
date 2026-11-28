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
import org.apache.grails.forge.cli.console.ConsoleProgram
import org.apache.grails.forge.cli.console.FormerCommands
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.PlainOutput
import org.jline.builtins.Completers
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.History
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
    private static final int INTERRUPTED = 130
    private static final String NOTHING_TO_REPEAT = 'There is no command to run again: ! alone runs the last command again, ' +
            'and ! followed by a command line runs that command line in the shell of the operating system, as !ls does'

    private final CommandLine commandLine
    private final Consumer<String[]> executor
    private final BiFunction<Throwable, CommandLine, Integer> onError
    private final String prompt
    private final Supplier<CommandLine> commands
    private volatile CommandLine completed
    // set when the interrupt of the terminal came during a command that cannot be cancelled, in a JVM that is not the shell's to end
    private volatile int ended
    /** Whether the JVM is the shell's own, which the interrupt of the terminal ends when nothing else can be done for it. */
    boolean endsJvm = true

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
        // the prompt is the name the command line was started under
        this.prompt = prompt.is(DEFAULT_PROMPT) ? "@|blue ${commandLine.getCommandName()}>|@ ".toString() : prompt
        this.commands = commands
        this.completed = commandLine
    }

    /**
     * @return what the shell asks with, before it is styled for the terminal: the name the command line was started under
     */
    String getPrompt() {
        return prompt
    }

    /**
     * @return what completes a line of the shell: with the commands, options and values of the
     * command line as it was when the shell started or when {@link #refresh} was last called, and on
     * a line that runs a program ({@link ConsoleProgram}) with the paths of files after the program
     */
    Completer completer() {
        return { LineReader reader, ParsedLine line, List<Candidate> candidates ->
            if (ConsoleProgram.of(line.line()) != null) {
                completeProgram(reader, line, candidates)
            } else {
                new CommandLineCompleter(completed.getCommandSpec()).complete(reader, line, candidates)
            }
        } as Completer
    }

    // On a line that runs a program the words after the program are completed as the paths of files, which is
    // what such a command line mostly takes; the program itself and the exclamation mark are not completed.
    private static void completeProgram(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        int program = line.words().isEmpty() || line.words()[0] != ConsoleProgram.MARK ? 0 : 1
        if (reader != null && line.wordIndex() > program) {
            new Completers.FileNameCompleter().complete(reader, line, candidates)
        }
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
            if (GradleBuild.isRunning()) {
                running.interrupt()
            } else if (endsJvm) {
                interruptedOutsideGradle()
            } else {
                // the JVM is someone else's: the shell ends when the command has, with the exit code of an interrupt
                ended = INTERRUPTED
                running.interrupt()
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
     * Runs the command line of a line that starts with an exclamation mark in the shell of the user,
     * in the current directory, and waits for it (see {@link ConsoleProgram}). The interrupt of the
     * terminal, Ctrl-C, is the program's while it runs: the terminal sends it to the program, which
     * does what it does with it, and the shell goes on once the program has ended.
     *
     * @param terminal the terminal of the shell
     * @param command what follows the exclamation mark
     * @return the exit code of the program, or -1 when the shell could not be started
     */
    int runProgram(Terminal terminal, String command) {
        Terminal.SignalHandler before = terminal.handle(Terminal.Signal.INT, { Terminal.Signal signal ->
            // the program was sent the interrupt as well, by the terminal: the shell waits for it as before
        } as Terminal.SignalHandler)
        boolean paused = false
        try {
            terminal.writer().flush()
            // a terminal that reads its input ahead stops doing so, so that what is typed reaches the program
            if (terminal.canPauseResume() && !terminal.paused()) {
                terminal.pause(true)
                paused = true
            }
            return ConsoleProgram.run(command, new File(System.getProperty('user.dir')), readsTerminal(terminal))
        } finally {
            if (paused) {
                terminal.resume()
            }
            terminal.handle(Terminal.Signal.INT, before)
            // an interrupt that came when nothing waited for one is not kept for the next line
            Thread.interrupted()
        }
    }

    // whether what the shell reads is a terminal a user types into, and not lines that were piped into it
    private static boolean readsTerminal(Terminal terminal) {
        String type = terminal.getType()
        return type != null && type != Terminal.TYPE_DUMB && type != Terminal.TYPE_DUMB_COLOR
    }

    // the last line of the history that was a command: not one that ran a program, not the mark alone, and no comment
    private static String lastCommand(History history) {
        Iterator<History.Entry> entries = history.reverseIterator()
        while (entries.hasNext()) {
            String line = entries.next().line()
            if (!line.isBlank() && ConsoleProgram.of(line) == null && !line.matches('^\\s*#.*')) {
                return line
            }
        }
        return null
    }

    /**
     * Called for the interrupt of the terminal while a command runs that does not wait for Gradle:
     * ends the JVM as the interrupt does when nothing handles it, with the exit code a shell gives
     * for it.
     */
    protected void interruptedOutsideGradle() {
        System.exit(INTERRUPTED)
    }

    /**
     * Runs the shell until it is left.
     *
     * @return 0, or the exit code of an interrupt when the interrupt of the terminal ended it in a
     * JVM that is not the shell's own
     */
    int start() {
        // the terminal is closed when the shell is left: what reads it ends and it is set back as it was, also in a
        // JVM that goes on after the shell, as one that runs the command line of its own does
        try (Terminal terminal = openTerminal()) {
            return start(terminal)
        } catch (Throwable t) {
            onError.apply(t, commandLine)
        }
        return 0
    }

    /**
     * @return the terminal {@link #start()} runs the shell on, which it closes when the shell is left: that
     * of the JVM
     * @throws IOException when the terminal of the JVM cannot be opened
     */
    protected Terminal openTerminal() throws IOException {
        return TerminalBuilder.terminal()
    }

    /**
     * Runs the shell on a terminal until it is left.
     *
     * @param terminal the terminal the lines are read from and the prompt is written to
     * @return as {@link #start()}
     */
    int start(Terminal terminal) {
        try {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer())
                    .parser(new DefaultParser())
                    .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                    // A line is what was typed: an exclamation mark refers to no line of the history, and a
                    // backslash is read once, by the parser, or by the shell on a line that runs a program.
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build()

            // Applied to the command line as well as the prompt, so command help and usage text agree
            // with what the prompt does.
            CommandLine.Help.Ansi ansi = resolveAnsi(terminal)
            commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(ansi))

            String ansiPrompt = ansi.string(prompt)
            String rightPrompt = null

            // start the shell and process input until the user leaves with exit or quit, or ends the input with Ctrl-D
            String line
            while (true) {
                try {
                    line = reader.readLine(ansiPrompt, rightPrompt, (MaskingCallback) null, null)
                    if (line.matches('^\\s*#.*')) {
                        continue
                    }
                    if (FormerCommands.endsTheConsole(line)) {
                        return 0
                    }
                    String program = ConsoleProgram.of(line)
                    if (program != null && !program.isBlank()) {
                        runProgram(terminal, program)
                        refresh()
                        continue
                    }
                    if (program != null) {
                        // the mark alone runs the last command again, as it did in the Grails shell
                        line = lastCommand(reader.getHistory())
                        if (line == null) {
                            System.err.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Error|@ ') + NOTHING_TO_REPEAT)
                            System.err.flush()
                            continue
                        }
                        terminal.writer().println(line)
                        terminal.writer().flush()
                    }
                    ParsedLine pl = reader.getParser().parse(line, 0)
                    String[] arguments = pl.words().toArray(new String[0])
                    execute(terminal, arguments)
                    if (ended != 0) {
                        return ended
                    }
                    refresh()
                } catch (UserInterruptException e) {
                    return 0
                } catch (EndOfFileException e) {
                    // the prompt was written and nothing was typed after it: what follows starts on a line of its own
                    terminal.writer().println()
                    terminal.writer().flush()
                    return 0
                }
            }
        } catch (Throwable t) {
            onError.apply(t, commandLine)
        }
        return 0
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

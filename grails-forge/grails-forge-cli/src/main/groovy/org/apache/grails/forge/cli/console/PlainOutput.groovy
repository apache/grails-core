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

import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * The one switch for output a program reads: with {@code --plain-output} nothing the command line
 * writes has colour or other markup of a terminal, and Gradle is asked for its plain console, which
 * has no progress display and writes every line once. Without the option the output is plain too
 * where it is not a terminal and where {@code NO_COLOR} is set, as it has been; the option says so
 * whatever the output is.
 *
 * <p>Everything the command line writes goes through picocli's automatic choice between a terminal
 * and plain text, which the system property {@code picocli.ansi} decides when it is set. The switch
 * sets that property while a command line that was given the option is run, so whatever writes that
 * way, now or later, follows it without knowing of it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PlainOutput {

    /** The option that asks for plain output, on the command line itself or after the name of a command. */
    public static final String OPTION = '--plain-output'

    private static final String PICOCLI_ANSI = 'picocli.ansi'
    private static final String GRADLE_CONSOLE = '--console'
    private static final String PASSED_ON = '--'

    private static int entered
    private static String before

    private PlainOutput() {
    }

    /**
     * @param commandLine the command line of the CLI, with the commands it has
     * @param args what was typed
     * @return whether it asks for plain output: the option stands in front of the command, or after
     * the name of a command that takes it. A command that gives all that follows its name to
     * something else, as {@code gradle} does, does not take it, and nothing after a {@code --} is
     * an option of the command line
     */
    static boolean given(CommandLine commandLine, String... args) {
        boolean inCommand = false
        boolean namesAnother = false
        boolean value = false
        for (String arg : args) {
            if (arg == PASSED_ON) {
                return false
            }
            // after the name of the tool that run-tool runs, everything is that tool's
            if (namesAnother && arg != null && !arg.startsWith('-') && !value) {
                return false
            }
            value = namesAnother && arg == '-D'
            if (arg == OPTION) {
                return true
            }
            if (!inCommand && arg != null && !arg.startsWith('-')) {
                inCommand = true
                CommandLine command = null
                try {
                    command = new CommandCatalog(commandLine).find(commandLine, arg)
                } catch (CommandLine.ParameterException ignored) {
                    // short for more than one command: none is run, and the refusal is written as was asked
                }
                if (command != null && command.getCommandSpec().findOption(OPTION) == null) {
                    return false
                }
                namesAnother = command != null && command.getCommandSpec().userObject() instanceof RunToolCommand
            }
        }
        return false
    }

    /**
     * Takes the options of the command line itself from the commands that give everything after
     * their name to something else, which are the commands that have none of the options every
     * command has, and the tools of the application, whose options are those they declare: there the
     * options are no options of the command, and its help does not show them.
     *
     * @param commandLine the command line of the CLI, with every command it has
     */
    static void notForWhatIsPassedOn(CommandLine commandLine) {
        for (CommandLine command : new LinkedHashSet<CommandLine>(commandLine.getSubcommands().values())) {
            CommandLine.Model.CommandSpec spec = command.getCommandSpec()
            if (spec.userObject() instanceof ToolCommand || spec.options().every { CommandLine.Model.OptionSpec option -> option.inherited() }) {
                for (CommandLine.Model.OptionSpec option : new ArrayList<CommandLine.Model.OptionSpec>(spec.options())) {
                    if (option.inherited()) {
                        spec.remove(option)
                    }
                }
            }
        }
    }

    /**
     * Makes the output plain until {@link #leave} is called as often as this was.
     */
    static synchronized void enter() {
        if (entered++ == 0) {
            before = System.getProperty(PICOCLI_ANSI)
            System.setProperty(PICOCLI_ANSI, 'false')
        }
    }

    static synchronized void leave() {
        // only when it still is what was set here: a value that was set during the run is its setter's
        if (entered > 0 && --entered == 0 && System.getProperty(PICOCLI_ANSI) == 'false') {
            if (before == null) {
                System.clearProperty(PICOCLI_ANSI)
            } else {
                System.setProperty(PICOCLI_ANSI, before)
            }
        }
    }

    /**
     * @return whether the output is plain because the command line asked for it
     */
    static synchronized boolean isOn() {
        return entered > 0
    }

    /**
     * @param arguments the arguments Gradle is about to be run with
     * @return what is put in front of them: the plain console of Gradle when the output is plain and
     * the arguments do not choose a console themselves
     */
    static List<String> forGradle(List<String> arguments) {
        boolean chosen = arguments.any { String argument -> argument == GRADLE_CONSOLE || argument.startsWith(GRADLE_CONSOLE + '=') }
        return isOn() && !chosen ? [GRADLE_CONSOLE + '=plain'] : Collections.<String>emptyList()
    }
}

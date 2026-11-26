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
package org.apache.grails.forge.cli

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.console.WrapperFiles
import org.grails.forge.cli.ForgeCli

/**
 * The {@code grails} command line as a program and as a library: the entry point an IDE, a script or
 * a build starts the Grails command line through. The class, its two methods and what is said of
 * them here are kept from one release of Grails to the next.
 *
 * <p><strong>The command line.</strong> {@code [--plain-output] [--non-interactive] [COMMAND [ARGUMENT...]]}.
 * Without a command the interactive console is started in a Grails project and the help is shown
 * elsewhere. {@code --plain-output} asks for output without colour, terminal markup or the progress
 * display of Gradle; {@code --non-interactive} for a run that never waits for what a user types: the
 * help is then shown where the console would be started, {@code shell} and {@code console} are
 * refused, and every build the command line starts is given an input that has ended and the
 * environment variable {@code GRAILS_NON_INTERACTIVE=true}, which a command or a script of an
 * application can read. Both are accepted in front of the command and after the name of every
 * command that does not give all that follows its name to something else, as {@code gradle}, a
 * tool of the application and the tool {@code run-tool} runs do. {@code help --json} writes the commands
 * there are in the current directory, with their options, as one JSON document on the standard
 * output.</p>
 *
 * <p><strong>What is gone.</strong> Until Grails 9 the {@code grails} command chose between the
 * Grails shell and Grails Forge, by the option {@code -t} or {@code --type} as its first argument or
 * the environment variable {@code GRAILS_PREFERRED_SHELL}. There is one command line: the option is
 * refused with exit code {@code 2} and a line that says so, and the variable is not read.</p>
 *
 * <p><strong>The exit code.</strong> {@code 0} when the command did what it was asked to; {@code 2}
 * when the command line was wrong, which is said on the standard error with how the command is used:
 * a command there is not, an option or a value a command does not take, features that cannot be had
 * together; {@code 1} for every other failure, with one line on the standard error that says why: a
 * build that failed, a directory that is no project, a file that cannot be written.</p>
 *
 * <p><strong>What is started.</strong> The help, the list of commands, the version, a refusal and
 * the commands that run the build of the project start nothing but this program. Only a command
 * that generates, as {@code create-app} and {@code feature add} do, starts the project generator,
 * when it runs.</p>
 *
 * <p><strong>The Grails wrapper.</strong> {@code Main-Class} of the jars of {@code org.apache.grails:grails-cli}
 * names this class, and every Grails wrapper starts it through {@code org.apache.grails.cli.DelegatingShellApplication}
 * of the jar it downloads. For the wrapper of Grails 7 and 8, which does not say that it started that class,
 * {@link #main} first replaces the wrapper of the project by the one of this version, with {@code update-wrapper},
 * where the current directory is in a Grails project of a version this command line works with that has a
 * wrapper; without arguments the JVM then ends with the exit code of that.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class GrailsCli {

    /** The name of the command line, as its help shows it. */
    public static final String NAME = 'grails'

    private static final List<String> FORMER_TYPE_OPTIONS = ['-t', '--type']

    // what org.apache.grails.cli.DelegatingShellApplication sets to true when the wrapper of Grails 7 or 8 started it
    private static final String EARLIER_WRAPPER = 'grails.cli.earlierWrapper'

    private GrailsCli() {
    }

    /**
     * Runs a command line and ends the JVM with its exit code.
     *
     * @param args the command line
     */
    static void main(String[] args) {
        if (Boolean.parseBoolean(System.clearProperty(EARLIER_WRAPPER))) {
            mainOfEarlierWrapper(args)
            return
        }
        // first: the classes the command is about to load are loaded beside it from here on, see StartupPreload
        StartupPreload.start(GrailsCli.classLoader, args)
        // Gradle is given the output of the process for as long as it is the output of the command line
        GradleBuild.outputOfProcess(System.out, System.err)
        System.exit(execute(args))
    }

    /**
     * The step from the wrapper of Grails 7 and 8 to the one of this version: replaces the wrapper of the
     * project of the current directory, runs the command line and ends the JVM with its exit code. The
     * earlier wrapper takes {@code update-wrapper} from the front of the command line, so a command line
     * without arguments is what is left of {@code ./grailsw update-wrapper}, and ends once the wrapper is
     * replaced.
     */
    private static void mainOfEarlierWrapper(String[] args) {
        // Gradle is given the output of the process for as long as it is the output of the command line
        GradleBuild.outputOfProcess(System.out, System.err)
        Integer updated = updateWrapper(args)
        if (updated != null && args.length == 0) {
            System.exit(updated)
        }
        System.exit(execute(args))
    }

    /**
     * Runs {@code update-wrapper} where it applies.
     *
     * @param args the command line that is run after it
     * @return the exit code of {@code update-wrapper}, or {@code null} where the current directory is
     * in no project whose wrapper is replaced
     */
    private static Integer updateWrapper(String[] args) {
        if (!WrapperFiles.appliesHere()) {
            return null
        }
        // --plain-output among the options in front of the command holds for update-wrapper too
        boolean plain = false
        for (String arg : args) {
            if (!arg.startsWith('-')) {
                break
            }
            plain = plain || arg == PlainOutput.OPTION
        }
        return execute(plain ? [PlainOutput.OPTION, WrapperFiles.COMMAND] as String[] : [WrapperFiles.COMMAND] as String[])
    }

    /**
     * Runs a command line in this JVM and returns when it has ended. The command line reads the
     * current directory of the JVM and writes to {@link System#out} and {@link System#err} as they
     * are when it is called. One command line is run at a time: what {@code --plain-output} asks for
     * holds for the JVM while the command runs.
     *
     * <p>A caller that is not a user at a terminal passes {@code --non-interactive}: without it a
     * command line that names no command starts the interactive console in this JVM, in a Grails
     * project. The console then does not end the JVM on the interrupt of the terminal: it ends when
     * the command that runs has ended, and this returns 130.</p>
     *
     * <p>When this returns the JVM is as it was. The system properties that starting the project
     * generator sets, the environment of Grails among them, are put back unless the caller changed
     * them in the meantime, and the environment of Grails also where the start failed; the
     * applications {@code run-app} started in the interactive console without {@code --detach} have
     * been stopped; the generator is closed and leaves no shutdown hook; the context class
     * loader of the thread, which is the class loader of this class while the command runs, is the one it was; and
     * {@code picocli.ansi}, which {@code --plain-output} sets for the run, has the value it had, or
     * the one the caller gave it during the run.</p>
     *
     * @param args the command line
     * @return the exit code, as described for the class
     */
    static int execute(String... args) {
        if (args && args[0] in FORMER_TYPE_OPTIONS) {
            // until Grails 9 the first argument could choose between the Grails shell and Grails Forge
            System.err.println("${NAME} has no option ${args[0]} since Grails 9: it chose between the Grails shell and Grails Forge, and there is one command line now. Give the command without it.")
            System.err.println("Run '${NAME} help' for the commands")
            return 2
        }
        // a tool may have loaded this class in a class loader of its own: what the command line finds
        // through the context class loader of the thread, the generator among it, is where this class is
        Thread thread = Thread.currentThread()
        ClassLoader previous = thread.contextClassLoader
        thread.contextClassLoader = GrailsCli.classLoader
        try {
            return ForgeCli.launch(NAME, true, args)
        } finally {
            thread.contextClassLoader = previous
        }
    }
}

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
package org.apache.grails.cli

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner
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
 * help is then shown where the console would be started, and a command that reads what is typed is
 * refused. Both are accepted in front of the command and after the name of every command but
 * {@code gradle}, which gives all that follows it to Gradle. {@code help --json} writes the commands
 * there are in the current directory, with their options, as one JSON document on the standard
 * output.</p>
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
 * @since 9.0
 */
@CompileStatic
final class GrailsCli {

    /** The name of the command line, as its help shows it. */
    public static final String NAME = 'grails'

    private GrailsCli() {
    }

    /**
     * Runs a command line and ends the JVM with its exit code.
     *
     * @param args the command line
     */
    static void main(String[] args) {
        // Gradle is given the output of the process for as long as it is the output of the command line
        GradleRunner.outputOfProcess(System.out, System.err)
        System.exit(execute(args))
    }

    /**
     * Runs a command line in this JVM and returns when it has ended. The command line reads the
     * current directory of the JVM and writes to {@link System#out} and {@link System#err} as they
     * are when it is called. One command line is run at a time: what {@code --plain-output} asks for
     * holds for the JVM while the command runs.
     *
     * @param args the command line
     * @return the exit code, as described for the class
     */
    static int execute(String... args) {
        return ForgeCli.launch(NAME, true, args)
    }
}

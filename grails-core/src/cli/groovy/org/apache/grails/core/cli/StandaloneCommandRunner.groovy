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
package org.apache.grails.core.cli

import groovy.transform.CompileStatic

import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.build.parsing.CommandLineParser

/**
 * The main class of the JVM the {@code grails} command line starts for a {@link StandaloneCommand}:
 * a process of its own, which is no build and starts no application. It finds the command by its
 * name among the commands of its class path, as the {@code runCommand} task of a build does, runs
 * it with the arguments it was given, and ends with the result of the command.
 *
 * <p>The JVM is started with the name of the command and its arguments, or with one argument that
 * names a file which holds them ({@link CommandArgumentsFile#ARGUMENT}), so that none of them is in
 * a process listing. The directory of the project is the system property {@code base.dir}, or the
 * directory the JVM was started in.</p>
 *
 * <p>Only a command whose class is a {@link StandaloneCommand} is run: any other command needs the
 * build or the application, and is refused with a line that says how it is run.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class StandaloneCommandRunner {

    private StandaloneCommandRunner() {
    }

    /**
     * Runs a command and says how it ended.
     *
     * @param args the name of the command and its arguments, or the one argument that names the file they are in
     * @return 0 when the command returned {@code true}, and 1 when it returned {@code false}, threw,
     * is no command of the class path, or is not a {@link StandaloneCommand}
     */
    static int run(String... args) {
        List<String> given
        try {
            given = arguments(args)
        } catch (CommandArgumentsFile.Refused refused) {
            System.err.println("The file of arguments ${refused.message}")
            return 1
        }
        if (given.isEmpty()) {
            System.err.println('Missing the name of the command')
            return 1
        }
        String name = given[0]
        ApplicationContextCommandRegistry registry = ApplicationContextCommandRegistry.instance
        ApplicationCommand command = registry.findCommand(name)
        if (command == null) {
            String hint = registry.missingCommandHint
            System.err.println(hint ? "Command not found for name: ${name}\n${hint}" : "Command not found for name: ${name}")
            return 1
        }
        Object target = command instanceof ApplicationCommandTargetAware ? ((ApplicationCommandTargetAware) command).target : command
        if (!(target instanceof StandaloneCommand)) {
            System.err.println("The command ${name} is not a StandaloneCommand, so it is not run as a process of its own. " +
                    "The build of the project runs it: grails run-command ${name}")
            return 1
        }
        try {
            boolean succeeded = command.handle(new ExecutionContext(new CommandLineParser().parse(given as String[])))
            System.out.flush()
            return succeeded ? 0 : 1
        } catch (Throwable e) {
            System.out.flush()
            System.err.println("Command execution error: ${e.message}")
            if (Boolean.getBoolean('grails.full.stacktrace')) {
                e.printStackTrace()
            }
            return 1
        }
    }

    private static List<String> arguments(String[] args) throws CommandArgumentsFile.Refused {
        if (args.length == 1 && args[0].startsWith(CommandArgumentsFile.ARGUMENT)) {
            return CommandArgumentsFile.read(new File(args[0].substring(CommandArgumentsFile.ARGUMENT.length())))
        }
        args.toList()
    }

    /**
     * @param args the name of the command and its arguments, or the one argument that names the file they are in
     */
    static void main(String[] args) {
        int exit = run(args)
        System.err.flush()
        System.exit(exit)
    }
}

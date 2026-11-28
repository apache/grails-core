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

import grails.util.Environment
import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.CommandLineParser

/**
 * The main class of the JVM the {@code grails} command line starts for a {@link StandaloneTool}:
 * a process of its own, which is no build and starts no application. It finds the tool by its
 * name among the tools of its class path, as the {@code runTool} task of a build does, runs
 * it with the arguments it was given, and ends with the result of the tool.
 *
 * <p>The JVM is started with the name of the command and its arguments, or with one argument that
 * names a file which holds them ({@link CommandArgumentsFile#ARGUMENT}), so that none of them is in
 * a process listing. The directory of the project is the system property {@code base.dir}, or the
 * directory the JVM was started in.</p>
 *
 * <p>Only a tool whose class is a {@link StandaloneTool} is run: an {@link ApplicationTool} needs the
 * build, and the application unless it says otherwise, and is refused with a line that says how it is
 * run.</p>
 *
 * <p>A tool that declares its options or parameters ({@link ToolOption}, {@link ToolParameter}) is given
 * them on its fields before it runs, and arguments that are not what it declares are refused with one
 * line, {@code | Error NAME: <what>. grails help NAME shows its options}, and the exit code 2. A tool
 * that declares none is given its arguments as it always was. When the arguments were given in a file
 * {@code command-args-<n>.json}, what the tool sets as {@link ExecutionContext#getResult() its result}
 * is written beside it, to {@code tool-result-<n>.json}, once the tool has returned.</p>
 *
 * <p>What the runner says, each one line, goes to the standard error the process was started with, also
 * when making a tool replaced {@code System.err}, as the console of Grails does.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class StandaloneToolRunner {

    private StandaloneToolRunner() {
    }

    /**
     * Runs a command and says how it ended.
     *
     * @param args the name of the command and its arguments, or the one argument that names the file they are in
     * @return 0 when the command returned {@code true}; 1 when it returned {@code false}, threw,
     * is no command of the class path, or is not a {@link StandaloneTool}; and 2 when it declares its
     * arguments and was given others
     */
    static int run(String... args) {
        // The standard error of the process, as it is before any tool is made: making the tools can replace
        // System.err, as a tool with a GrailsConsole in a field does with a stream that writes to the standard
        // output, and what the runner says has to reach the standard error, one line each.
        PrintStream err = System.err
        try {
            return run(err, args)
        } finally {
            err.flush()
        }
    }

    private static int run(PrintStream err, String[] args) {
        List<String> given
        try {
            given = arguments(args)
        } catch (CommandArgumentsFile.Refused refused) {
            err.println("The file of arguments ${refused.message}")
            return 1
        }
        if (given.isEmpty()) {
            err.println('Missing the name of the command')
            return 1
        }
        String name = given[0]
        ApplicationContextCommandRegistry registry = ApplicationContextCommandRegistry.instance
        GrailsTool command = registry.findCommand(name)
        if (command == null) {
            String hint = registry.missingCommandHint
            err.println(hint ? "Command not found for name: ${name}\n${hint}" : "Command not found for name: ${name}")
            return 1
        }
        Object target = command instanceof ApplicationCommandTargetAware ? ((ApplicationCommandTargetAware) command).target : command
        if (!(target instanceof StandaloneTool) || target instanceof ApplicationTool) {
            err.println("The tool ${name} is not a StandaloneTool, so it is not run as a process of its own. " +
                    "The build of the project runs it: grails run-tool ${name}")
            return 1
        }
        // a tool that declares its arguments is held to them before it runs; one that declares none is given them as it always was
        ToolArguments declared = ToolArguments.of(target.getClass())
        if (declared.broken != null) {
            err.println("| Error ${name}: ${declared.broken}.")
            return 1
        }
        ToolArguments.Bound bound = null
        if (declared.declaring) {
            try {
                bound = declared.bind(given as String[])
            } catch (ToolArguments.Refused refused) {
                err.println(ToolArguments.refusal(name, refused.message))
                return ToolArguments.REFUSED
            }
        }
        File resultFile = ToolResults.prepare(argumentsFile(args))
        try {
            ExecutionContext context = new ExecutionContext(bound != null ? bound.commandLine : parse(given as String[]))
            bound?.applyTo(target)
            boolean succeeded = command.handle(context)
            System.out.flush()
            ToolResults.give(name, resultFile, context.result, err)
            return succeeded ? 0 : 1
        } catch (Throwable e) {
            System.out.flush()
            err.println("Command execution error: ${e.message}")
            if (Boolean.getBoolean('grails.full.stacktrace')) {
                e.printStackTrace(err)
            }
            return 1
        }
    }

    // The arguments are data: a -Dgrails.env=NAME among them is one of them, in commandLine.systemProperties,
    // and does not change the environment of the tool. The parser sets the environment of the JVM when it
    // reads one, as the Grails shell did for what was typed to it; that is undone here.
    private static CommandLine parse(String[] args) {
        String environment = System.getProperty(Environment.KEY)
        try {
            return new CommandLineParser().parse(args)
        } finally {
            if (environment == null) {
                System.clearProperty(Environment.KEY)
            } else {
                System.setProperty(Environment.KEY, environment)
            }
        }
    }

    private static List<String> arguments(String[] args) throws CommandArgumentsFile.Refused {
        File file = argumentsFile(args)
        file != null ? CommandArgumentsFile.read(file) : args.toList()
    }

    // the file the arguments are in, or null when they are given themselves
    private static File argumentsFile(String[] args) {
        args.length == 1 && args[0].startsWith(CommandArgumentsFile.ARGUMENT) ? new File(args[0].substring(CommandArgumentsFile.ARGUMENT.length())) : null
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

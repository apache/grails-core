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

import java.util.function.BiFunction
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.jline.terminal.Terminal
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.PositionalParamSpec
import org.grails.forge.cli.command.AddPropertyCommand
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.CodeGenCommand
import org.grails.forge.cli.command.CreateControllerCommand
import org.grails.forge.cli.command.CreateDomainClassCommand
import org.grails.forge.cli.command.CreateInterceptorCommand
import org.grails.forge.cli.command.CreateJobCommand
import org.grails.forge.cli.command.CreateServiceCommand
import org.grails.forge.cli.command.CreateTagLibCommand
import org.grails.forge.cli.command.ProjectCommand
import org.grails.forge.cli.command.ProjectCommands
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler

/**
 * Runs the Forge CLI against the Grails application {@link Application} starts: builds the picocli
 * command line from the command beans of the context and executes it, once for a one-shot
 * invocation or repeatedly for the interactive shell, on the same context.
 */
@CompileStatic
final class ForgeCli {

    /**
     * Registration point for code-gen commands: they take CodeGenConfig in their constructor and
     * are not Spring beans, so a new command must be listed here as well as written.
     */
    private static final List<Class<? extends CodeGenCommand>> CODE_GEN_COMMANDS = [
            CreateControllerCommand,
            CreateServiceCommand,
            CreateDomainClassCommand,
            CreateTagLibCommand,
            CreateInterceptorCommand,
            CreateJobCommand,
            AddPropertyCommand
    ] as List<Class<? extends CodeGenCommand>>

    /**
     * The names of the code-gen commands, which are the Forge CLI's own even where they do not apply, so
     * naming one never runs the project's build
     */
    private static final Set<String> CODE_GEN_COMMAND_NAMES = CODE_GEN_COMMANDS
            .collect { Class<? extends CodeGenCommand> type -> type.getAnnotation(CommandLine.Command).name() }
            .toSet().asImmutable()

    /**
     * The environment variable that, set to {@code false}, keeps the Forge CLI from running the build of the
     * project on its own to update the project's commands: it offers those of a manifest the build already
     * wrote, and runs the build only for a project command the user names.
     */
    static final String RUN_BUILD_VARIABLE = 'GRAILS_FORGE_RUN_BUILD'

    /** The system property that does what {@link #RUN_BUILD_VARIABLE} does, and wins over it */
    static final String RUN_BUILD_PROPERTY = 'grails.forge.runBuild'

    private static final BiFunction<Throwable, CommandLine, Integer> EXCEPTION_HANDLER = { Throwable e, CommandLine commandLine ->
        BaseCommand command = commandLine.getCommand()
        command.err(e.message)
        if (command.showStacktrace()) {
            e.printStackTrace(commandLine.err)
        }
        return 1
    } as BiFunction<Throwable, CommandLine, Integer>

    private ForgeCli() {
    }

    /**
     * Starts the Grails application, runs the given command line on it, or the interactive shell
     * when there is none, and closes the application again.
     *
     * @param args the command line
     * @return the exit code
     */
    static int run(String... args) {
        ConfigurableApplicationContext context = Application.builder().run()
        try {
            if (args.length == 0) {
                interactive(context)
                return 0
            }
            return execute(context, args)
        }
        finally {
            context.close()
        }
    }

    /**
     * Executes one command line on a running context, which stays open for the next one.
     */
    static int execute(ApplicationContext context, String... args) {
        // the words of a response file, @file, choose the command and reach it as the words typed would
        args = expandAtFiles(args)
        boolean noOpConsole = args.length > 0 && args[0].startsWith('update-cli-config')
        CommandLine commandLine = createForgeCommandLine(context, noOpConsole)
        commandLine.expandAtFiles = false
        // the project's build runs only when the command line names a command that is not the Forge CLI's:
        // its own commands never wait for the build, and its options alone, such as --help, list the
        // project's commands from a manifest the build already wrote
        String command = commandName(commandLine, args)
        if (command == null) {
            if (args.length > 0) {
                addProjectCommands(commandLine, false)
            }
        }
        else if (!commandLine.subcommands.containsKey(command)) {
            // a code generation command that does not apply here never runs the build, but a project
            // command of its name, which the prompt and --help offer, runs from the manifest there is
            addProjectCommands(commandLine, !CODE_GEN_COMMAND_NAMES.contains(command))
        }
        return commandLine.execute(args)
    }

    /**
     * The command line with each response file, {@code @file}, replaced by its words, as picocli reads
     * them: once, so a word of the file that starts with {@code @}, as {@code @@name} gives, stays a word.
     */
    private static String[] expandAtFiles(String... args) {
        if (!args.any { String arg -> arg?.startsWith('@') }) {
            return args
        }
        CommandSpec words = CommandSpec.create()
        words.addPositional(PositionalParamSpec.builder().index('0..*').arity('0..*').type(String[]).build())
        words.parser().unmatchedArgumentsAllowed(true).unmatchedOptionsArePositionalParams(true)
        new CommandLine(words).parseArgs(args).expandedArgs() as String[]
    }

    /**
     * The command a command line names: its first word that is neither one of the Forge CLI's options
     * nor the value of one. None follows {@code --}, after which picocli takes every word as a parameter.
     */
    private static String commandName(CommandLine commandLine, String... args) {
        for (int i = 0; i < args.length; i++) {
            String word = args[i]
            if (word == '--') {
                return null
            }
            if (!word.startsWith('-')) {
                return word
            }
            OptionSpec option = commandLine.commandSpec.findOption(word)
            if (option != null && option.arity().min() > 0) {
                i++
            }
        }
        null
    }

    /**
     * Runs the interactive shell on a running context until the user leaves it.
     */
    static void interactive(ApplicationContext context) {
        interactive(context, null)
    }

    /**
     * Runs the interactive shell on a running context until the user leaves it or its input ends.
     *
     * @param terminal the terminal the shell reads and writes, or {@code null} for the terminal of the JVM
     */
    static void interactive(ApplicationContext context, Terminal terminal) {
        CommandLine commandLine = createCommandLine(context, true)
        // a line may run the build and change the project's commands, so the prompt then completes those
        // of the manifest as it is, read quietly, since a problem with it was reported already
        CommandLine[] offered = [commandLine] as CommandLine[]
        Supplier<CommandLine> current = { -> offered[0] } as Supplier<CommandLine>
        new InteractiveShell(commandLine, current, { String[] commandArgs -> runLine(context, offered, commandArgs) },
                EXCEPTION_HANDLER, InteractiveShell.DEFAULT_PROMPT, terminal).start()
    }

    /**
     * Runs a line entered at the prompt, then makes the commands it offers those of the project's
     * manifest as it is now. A problem reading them keeps those offered before, so the prompt goes on.
     *
     * @param offered holds the command line the prompt completes from
     */
    private static void runLine(ApplicationContext context, CommandLine[] offered, String[] commandArgs) {
        execute(context, commandArgs)
        try {
            CommandLine refreshed = createForgeCommandLine(context, true)
            addProjectCommands(refreshed, false, ConsoleOutput.NOOP)
            offered[0] = refreshed
        }
        catch (RuntimeException ignored) {
            // the next line that runs reports it
        }
    }

    /**
     * Builds the command line with every command it offers in the current directory: the Forge CLI's
     * own, and the commands of the Grails project there, if any.
     */
    static CommandLine createCommandLine(ApplicationContext context, boolean noOpConsole) {
        CommandLine commandLine = createForgeCommandLine(context, noOpConsole)
        addProjectCommands(commandLine, true)
        return commandLine
    }

    private static CommandLine createForgeCommandLine(ApplicationContext context, boolean noOpConsole) {
        ForgeCommand root = context.getBean(ForgeCommand)
        CommandLine commandLine = new CommandLine(root, new GrailsPicocliFactory(context))
        commandLine.executionExceptionHandler = { Exception ex, CommandLine failed, CommandLine.ParseResult parseResult ->
            EXCEPTION_HANDLER.apply(ex, failed)
        } as CommandLine.IExecutionExceptionHandler
        commandLine.usageHelpWidth = 100
        CodeGenConfig codeGenConfig = CodeGenConfig.load(context, noOpConsole ? ConsoleOutput.NOOP : root)
        if (codeGenConfig != null) {
            for (Class<? extends CodeGenCommand> type : CODE_GEN_COMMANDS) {
                CodeGenCommand command
                try {
                    command = type.getConstructor(CodeGenConfig).newInstance(codeGenConfig)
                }
                catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to create command ${type.name}", e)
                }
                if (command.applies()) {
                    commandLine.addSubcommand(command)
                }
            }
        }
        return commandLine
    }

    /**
     * Whether the build of the project may run to update the project's commands: unless
     * {@link #RUN_BUILD_PROPERTY}, or else {@link #RUN_BUILD_VARIABLE}, is {@code false}.
     */
    private static boolean buildMayRun() {
        String setting = System.getProperty(RUN_BUILD_PROPERTY) ?: System.getenv(RUN_BUILD_VARIABLE)
        !'false'.equalsIgnoreCase(setting?.trim())
    }

    /**
     * Adds the commands of the Grails project in the current directory, which run through the project's
     * build. A command named like one of the Forge CLI's own is left out: the Forge CLI's command runs.
     *
     * @param runBuild whether the project's build may run to update the commands
     */
    private static void addProjectCommands(CommandLine commandLine, boolean runBuild) {
        addProjectCommands(commandLine, runBuild, (ForgeCommand) commandLine.getCommand())
    }

    private static void addProjectCommands(CommandLine commandLine, boolean runBuild, ConsoleOutput console) {
        runBuild = runBuild && buildMayRun()
        File projectDir
        try {
            projectDir = FileSystemOutputHandler.getDefaultBaseDirectory()
        }
        catch (IOException ignored) {
            return
        }
        for (ProjectCommand command : new ProjectCommands(projectDir).load(console, runBuild)) {
            // exit leaves the prompt, so a project command of that name could be offered but never run
            if (!commandLine.subcommands.containsKey(command.name) && command.name != InteractiveShell.EXIT) {
                commandLine.addSubcommand(command.name, command.commandSpec)
            }
        }
    }

}

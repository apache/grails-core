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
import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import picocli.CommandLine
import org.grails.forge.cli.command.AddPropertyCommand
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.CodeGenCommand
import org.grails.forge.cli.command.CreateCommandCommand
import org.grails.forge.cli.command.CreateControllerCommand
import org.grails.forge.cli.command.CreateDomainClassCommand
import org.grails.forge.cli.command.CreateDomainResourceCommand
import org.grails.forge.cli.command.CreateFunctionalTestCommand
import org.grails.forge.cli.command.CreateIntegrationTestCommand
import org.grails.forge.cli.command.CreateInterceptorCommand
import org.grails.forge.cli.command.CreateJobCommand
import org.grails.forge.cli.command.CreateRestfulControllerCommand
import org.grails.forge.cli.command.CreateScriptCommand
import org.grails.forge.cli.command.CreateServiceCommand
import org.grails.forge.cli.command.CreateTagLibCommand
import org.grails.forge.cli.command.CreateUnitTestCommand
import org.grails.forge.cli.command.InstallTemplatesCommand
import org.grails.forge.io.ConsoleOutput

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
            AddPropertyCommand,
            CreateUnitTestCommand,
            CreateIntegrationTestCommand,
            CreateFunctionalTestCommand,
            CreateRestfulControllerCommand,
            CreateDomainResourceCommand,
            CreateCommandCommand,
            CreateScriptCommand,
            InstallTemplatesCommand
    ] as List<Class<? extends CodeGenCommand>>

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
        boolean noOpConsole = args.length > 0 && args[0].startsWith('update-cli-config')
        return createCommandLine(context, noOpConsole, readsProject(args)).execute(args)
    }

    /**
     * Whether a command line has to do with the project of the current directory, whose record is
     * then read for the code generation commands it has. A command that creates an application has
     * not, and neither has asking for the version: they do not read what the directory holds. Help
     * has, because it lists the commands there are in this directory.
     */
    static boolean readsProject(String... args) {
        String command = args.find { String arg -> !arg.startsWith('-') }
        if (command == null) {
            return !args.any { String arg -> arg == '-V' || arg == '--version' }
        }
        for (Class<?> type : ForgeCommand.getAnnotation(CommandLine.Command).subcommands()) {
            CommandLine.Command create = type.getAnnotation(CommandLine.Command)
            if (create != null && (create.name() == command || command in create.aliases())) {
                return false
            }
        }
        return true
    }

    /**
     * Runs the interactive shell on a running context until the user leaves it.
     */
    static void interactive(ApplicationContext context) {
        CommandLine commandLine = createCommandLine(context, true)
        new InteractiveShell(commandLine, { String[] commandArgs -> execute(context, commandArgs) }, EXCEPTION_HANDLER).start()
    }

    static CommandLine createCommandLine(ApplicationContext context, boolean noOpConsole) {
        return createCommandLine(context, noOpConsole, true)
    }

    /**
     * @param readsProject whether the record of the project of the current directory is read, for
     * the code generation commands the project has
     */
    static CommandLine createCommandLine(ApplicationContext context, boolean noOpConsole, boolean readsProject) {
        ForgeCommand root = context.getBean(ForgeCommand)
        CommandLine commandLine = new CommandLine(root, new GrailsPicocliFactory(context))
        commandLine.executionExceptionHandler = { Exception ex, CommandLine failed, CommandLine.ParseResult parseResult ->
            EXCEPTION_HANDLER.apply(ex, failed)
        } as CommandLine.IExecutionExceptionHandler
        commandLine.usageHelpWidth = 100
        CodeGenConfig codeGenConfig = readsProject ? CodeGenConfig.load(context, noOpConsole ? ConsoleOutput.NOOP : root) : null
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

}

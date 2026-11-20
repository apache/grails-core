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

import java.nio.file.Path
import java.util.function.BiFunction
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ApplicationCommands
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.PlainOutput
import org.springframework.context.ApplicationContext
import picocli.CommandLine
import org.apache.grails.forge.cli.console.CommandCatalog
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
import org.grails.forge.cli.command.HelpCommand
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler

/**
 * Runs the Forge CLI: builds the picocli command line and executes it, once for a one-shot
 * invocation or repeatedly for the interactive shell. The Grails application {@link Application}
 * starts, which generates, is not started for the command line: a command that needs it asks the
 * {@link ForgeContext} for it, and the same one serves every command of the interactive shell.
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

    private static final int USAGE_WIDTH = 100

    private ForgeCli() {
    }

    /**
     * Runs the given command line, or the interactive shell when there is none, as the program
     * {@code grails-forge-cli}. The Grails application that generates is started when a command
     * first needs it, see {@link ForgeContext}, and closed again.
     *
     * @param args the command line
     * @return the exit code
     */
    static int run(String... args) {
        return launch(new ForgeContext(), false, args)
    }

    /**
     * Runs a command line as the program of the given name, which the help then shows.
     *
     * @param commandName the name the command line is started under
     * @param consoleInProjectOnly whether, without a command, the interactive shell is started only
     * in a Grails project and the help is shown elsewhere
     * @param args the command line
     * @return the exit code
     */
    static int launch(String commandName, boolean consoleInProjectOnly, String... args) {
        ForgeContext context = new ForgeContext()
        context.commandName = commandName
        return launch(context, consoleInProjectOnly, args)
    }

    /**
     * Runs a command line, or without a command the interactive shell, and closes the application
     * when it was started. With {@code --non-interactive} the shell is not started: the help is
     * shown, as it is outside a project when the shell is for projects only.
     *
     * @param context the application, which is started when a command first needs it
     * @param consoleInProjectOnly whether, without a command, the interactive shell is started only
     * in a Grails project and the help is shown elsewhere
     * @param args the command line
     * @return the exit code
     */
    static int launch(ForgeContext context, boolean consoleInProjectOnly, String... args) {
        try {
            if (!args.every { String arg -> arg == PlainOutput.OPTION || arg == ForgeCommand.NON_INTERACTIVE }) {
                return execute(context, args)
            }
            if (ForgeCommand.NON_INTERACTIVE in args || (consoleInProjectOnly && !inProject())) {
                return execute(context, (args.toList() + ['--help']) as String[])
            }
            boolean plain = PlainOutput.OPTION in args
            if (plain) {
                PlainOutput.enter()
            }
            try {
                interactive(context)
            } finally {
                if (plain) {
                    PlainOutput.leave()
                }
            }
            return 0
        }
        finally {
            context.close()
        }
    }

    private static boolean inProject() {
        try {
            return ConsoleProject.find(FileSystemOutputHandler.getDefaultBaseDirectory().toPath()) != null
        } catch (IOException ignored) {
            return false
        }
    }

    /**
     * Executes one command line on a running context, which stays open for the next one.
     *
     * @see #execute(ForgeContext, String[])
     */
    static int execute(ApplicationContext context, String... args) {
        return execute(ForgeContext.of(context), args)
    }

    /**
     * Executes one command line on a context, which stays as it is for the next one: started when
     * the command needed it, and not started otherwise. The name of a command can be given in its
     * short form, as {@code cA} for {@code create-app}: it is replaced by the name before the
     * command line is read, see {@link CommandCatalog#expand}.
     */
    static int execute(ForgeContext context, String... args) {
        boolean noOpConsole = args.length > 0 && args[0].startsWith('update-cli-config')
        // What reading the record of the project has to say is held back until the command is known:
        // a short form is looked up among the commands of the project too, and may then be a command
        // that has nothing to do with the project.
        HeldBack held = new HeldBack()
        boolean reads = readsProject(args)
        CommandLine commandLine = createCommandLine(context, noOpConsole ? ConsoleOutput.NOOP : held, reads)
        if (reads && !noOpConsole) {
            advise(context, held)
        }
        return execute(commandLine, held, args)
    }

    // said once for a directory while the context lives, which in the interactive shell is once
    private static void advise(ForgeContext context, HeldBack held) {
        try {
            Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
            if (context.advised.add(current.toString())) {
                String guidance = EarlierGrailsProject.guidance(current)
                if (guidance != null) {
                    held.warning(guidance)
                }
            }
        } catch (IOException ignored) {
            // without a current directory there is no project to say anything of
        }
    }

    /**
     * Executes a command line on the commands that were put together for it, and on those that were
     * added to them since.
     */
    static int execute(CommandLine commandLine, String... args) {
        return execute(commandLine, new HeldBack(), args)
    }

    private static int execute(CommandLine commandLine, HeldBack held, String... args) {
        // before anything is written: what is held back, a refusal and the help are plain as well
        boolean plain = PlainOutput.given(commandLine, args)
        if (plain) {
            PlainOutput.enter()
        }
        try {
            return executePlainOrNot(commandLine, held, args)
        } finally {
            if (plain) {
                PlainOutput.leave()
            }
        }
    }

    private static int executePlainOrNot(CommandLine commandLine, HeldBack held, String... args) {
        ConsoleOutput root = (ConsoleOutput) commandLine.getCommand()
        // for the commands that were added since the command line was put together as well
        commandLine.setUsageHelpWidth(USAGE_WIDTH)
        String[] expanded
        try {
            expanded = new CommandCatalog(commandLine).expand(args)
        } catch (CommandLine.ParameterException e) {
            held.replay(root)
            return commandLine.getParameterExceptionHandler().handleParseException(e, args)
        }
        if (readsProject(expanded)) {
            CommandLine help = commandLine.getSubcommands().get(HelpCommand.NAME)
            if (help != null && commandOf(expanded) == HelpCommand.NAME && optionsOf(expanded).contains('--json')) {
                // the document is all that is written: it says what was held back
                HelpCommand command = (HelpCommand) help.getCommand()
                command.diagnostics = held.messages()
                command.complete = held.recordUsed
            } else {
                held.replay(root)
                ApplicationCommands.said(commandLine, commandOf(args), commandOf(expanded))
            }
        }
        return commandLine.execute(expanded)
    }

    /**
     * Whether a command line has to do with the project of the current directory, whose record is
     * then read for the code generation commands it has. A command that creates an application has
     * not, and neither has asking for the version: they do not read what the directory holds. Help
     * has, because it lists the commands there are in this directory.
     */
    static boolean readsProject(String... args) {
        String command = commandOf(args)
        if (command == null) {
            return !args.any { String arg -> arg == '-V' || arg == '--version' }
        }
        for (Class<?> type : ForgeCommand.getAnnotation(CommandLine.Command).subcommands()) {
            CommandLine.Command create = type.getAnnotation(CommandLine.Command)
            if (create != null && (create.name() == command || command in create.aliases())) {
                return type == HelpCommand
            }
        }
        return true
    }

    private static String commandOf(String... args) {
        return args.find { String arg -> !arg.startsWith('-') }
    }

    // what can be an option: after -- nothing is
    private static List<String> optionsOf(String... args) {
        int end = args.findIndexOf { String arg -> arg == '--' }
        return (args as List<String>).subList(0, end < 0 ? args.length : end)
    }

    /**
     * Runs the interactive shell on a running context until the user leaves it.
     */
    static void interactive(ApplicationContext context) {
        interactive(ForgeContext.of(context))
    }

    /**
     * Runs the interactive shell until the user leaves it, on a context that is started when a
     * command typed there first needs it.
     */
    static void interactive(ForgeContext context) {
        CommandLine commandLine = createCommandLine(context, true)
        // in the shell a command that starts an application returns to the prompt
        ConsoleCommands.setInteractive(true)
        try {
            new InteractiveShell(commandLine, { String[] commandArgs -> execute(context, commandArgs) }, EXCEPTION_HANDLER,
                    { createCommandLine(context, true) } as Supplier<CommandLine>).start()
        } finally {
            ConsoleCommands.setInteractive(false)
        }
    }

    static CommandLine createCommandLine(ApplicationContext context, boolean noOpConsole) {
        return createCommandLine(context, noOpConsole, true)
    }

    static CommandLine createCommandLine(ForgeContext context, boolean noOpConsole) {
        return createCommandLine(context, noOpConsole, true)
    }

    /**
     * @param readsProject whether the record of the project of the current directory is read, for
     * the code generation commands the project has
     */
    static CommandLine createCommandLine(ApplicationContext context, boolean noOpConsole, boolean readsProject) {
        return createCommandLine(ForgeContext.of(context), noOpConsole, readsProject)
    }

    /**
     * @param readsProject whether the record of the project of the current directory is read, for
     * the code generation commands the project has
     */
    static CommandLine createCommandLine(ForgeContext context, boolean noOpConsole, boolean readsProject) {
        return createCommandLine(context, noOpConsole ? ConsoleOutput.NOOP : null, readsProject)
    }

    /**
     * Puts the command line together: the commands that are registered with {@link ForgeCommand},
     * and in a project the code generation commands of {@link #CODE_GEN_COMMANDS} that apply to it.
     * A command that is registered in either way is found by its short form, shown by {@code help}
     * and described by {@code help --json} without anything else being done for it.
     *
     * Nothing here starts the application: a command that generates asks for it when it runs.
     *
     * @param console where reading the record of the project says what it has to say, or
     * {@code null} for the output of the command line
     */
    private static CommandLine createCommandLine(ForgeContext context, ConsoleOutput console, boolean readsProject) {
        GrailsPicocliFactory factory = new GrailsPicocliFactory(context)
        ForgeCommand root = factory.create(ForgeCommand)
        CommandLine commandLine = new CommandLine(root, factory)
        if (context.commandName != null) {
            commandLine.setCommandName(context.commandName)
        }
        // after a mistake the command is shown to be used without the features being listed, which only the generator can
        CommandLine.IParameterExceptionHandler mistakes = commandLine.getParameterExceptionHandler()
        commandLine.setParameterExceptionHandler({ CommandLine.ParameterException e, String[] given ->
            context.withoutListing({ mistakes.handleParseException(e, given) } as Supplier<Integer>)
        } as CommandLine.IParameterExceptionHandler)
        commandLine.executionExceptionHandler = { Exception ex, CommandLine failed, CommandLine.ParseResult parseResult ->
            EXCEPTION_HANDLER.apply(ex, failed)
        } as CommandLine.IExecutionExceptionHandler
        CodeGenConfig codeGenConfig = readsProject ? CodeGenConfig.load(context, console ?: root) : null
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
        ConsoleCommands.addTo(commandLine, readsProject)
        // last: a command of the application does not take the name of a command that is there
        ApplicationCommands.addTo(commandLine, readsProject)
        // everything after gradle is Gradle's, and everything after a command of the application is that command's
        PlainOutput.notForWhatIsPassedOn(commandLine)
        // once every command is there: the width is given to the commands there are when it is set
        commandLine.setUsageHelpWidth(USAGE_WIDTH)
        return commandLine
    }

    /** Keeps what is said until it is known whether it is to be said, and then says it in the order it came. */
    private static final class HeldBack implements ConsoleOutput, CodeGenConfig.RecordListener {

        private final List<Closure> said = []
        private final List<String> lines = []

        /** Whether no record of the project was found that could not be used. */
        boolean recordUsed = true

        @Override
        void recordNotUsed() {
            recordUsed = false
        }

        @Override
        void out(String message) {
            said.add { ConsoleOutput to -> to.out(message) }
        }

        @Override
        void err(String message) {
            lines.add(message)
            said.add { ConsoleOutput to -> to.err(message) }
        }

        @Override
        void warning(String message) {
            lines.add(message)
            said.add { ConsoleOutput to -> to.warning(message) }
        }

        @Override
        void green(String message) {
            said.add { ConsoleOutput to -> to.green(message) }
        }

        @Override
        void red(String message) {
            said.add { ConsoleOutput to -> to.red(message) }
        }

        @Override
        boolean showStacktrace() {
            return false
        }

        @Override
        boolean verbose() {
            return false
        }

        /** The warnings and errors, without what marks them as such on a terminal. */
        List<String> messages() {
            return new ArrayList<String>(lines)
        }

        void replay(ConsoleOutput to) {
            said.each { Closure each -> each.call(to) }
            said.clear()
        }
    }

}

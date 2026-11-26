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
import java.util.function.Predicate
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.Diagnostics
import org.apache.grails.forge.cli.artefact.FeatureArtefact
import org.apache.grails.forge.cli.artefact.PackArtefactCommand
import org.apache.grails.forge.cli.StartupPreload
import org.apache.grails.forge.core.pack.BundledPackFeatures
import org.apache.grails.forge.cli.console.ProjectTools
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.console.RunAppCommand
import org.apache.grails.forge.cli.plugin.ListPluginsCommand
import org.apache.grails.forge.cli.plugin.PluginGroupCommand
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.cli.plugin.PluginInfoCommand
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.UsageHelp
import org.springframework.context.ApplicationContext
import picocli.CommandLine
import org.apache.grails.forge.cli.console.CommandCatalog
import org.grails.forge.cli.command.AddPropertyCommand
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.CodeGenCommand
import org.grails.forge.cli.command.CreateCommand
import org.grails.forge.cli.command.CreateToolCommand
import org.grails.forge.cli.command.CreateControllerCommand
import org.grails.forge.cli.command.CreateDomainClassCommand
import org.grails.forge.cli.command.CreateDomainResourceCommand
import org.grails.forge.cli.command.CreateFunctionalTestCommand
import org.grails.forge.cli.command.CreateIntegrationTestCommand
import org.grails.forge.cli.command.CreateInterceptorCommand
import org.grails.forge.cli.command.CreateJobCommand
import org.grails.forge.cli.command.CreateRestfulControllerCommand
import org.grails.forge.cli.command.CreateServiceCommand
import org.grails.forge.cli.command.CreateTagLibCommand
import org.grails.forge.cli.command.CreateUnitTestCommand
import org.grails.forge.cli.command.FeatureCommand
import org.grails.forge.cli.command.InstallTemplatesCommand
import org.grails.forge.cli.command.HelpCommand
import org.grails.forge.cli.command.ProjectGroupCommand
import org.grails.forge.cli.command.ProjectHealthCommand
import org.grails.forge.cli.command.ProjectMigrateCommand
import org.grails.forge.cli.command.ProjectRegenerateCommand
import org.grails.forge.cli.command.ProjectShowCommand
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

    static {
        // Where the file system has no modes, as on Windows, the directories the records of changes are
        // kept in are the user's own as the commands of a project tell whose a file is: by the owner,
        // with what the group of the administrators owns for an elevated process (see ProcessUser).
        FeatureAddition.setOwnedWithoutModes({ Path directory -> ConsoleProject.isOwnedByUser(directory) } as Predicate<Path>)
    }

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
            CreateToolCommand,
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
        // started by a program, in a JVM that may be its own: the shell returns where it would end the JVM
        context.endsJvm = false
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
        if (Diagnostics.isOn()) {
            Diagnostics.say('preload', StartupPreload.status())
        }
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
                return interactive(context)
            } finally {
                if (plain) {
                    PlainOutput.leave()
                }
            }
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
     *
     * <p>A name that is no command, in a project whose list of the tools of the application and its
     * plugins is missing or may be out of date, has the build list them once, and the command line is
     * then put together again and read again: see {@link ProjectTools#listsFor}.</p>
     */
    static int execute(ForgeContext context, String... args) {
        // What reading the record of the project has to say is held back until the command is known:
        // a short form is looked up among the commands of the project too, and may then be a command
        // that has nothing to do with the project.
        HeldBack held = new HeldBack()
        held.mayList = true
        boolean reads = readsProject(args)
        CommandLine commandLine = createCommandLine(context, held, reads, listsApplication(args))
        if (reads) {
            held.advice = { advice(context) } as Supplier<String>
        } else {
            held.difference = { CodeGenConfig.earlierRecordDifference(context) } as Supplier<String>
        }
        try {
            int exit = execute(commandLine, held, args)
            if (!held.listed) {
                return exit
            }
            // the build has just listed the tools of the application and its plugins: they are read
            // again, and so is the record of the project, whose warnings were not said the first time
            HeldBack again = new HeldBack()
            CommandLine listed = createCommandLine(context, again, reads, listsApplication(args))
            if (reads) {
                again.advice = { advice(context) } as Supplier<String>
            } else {
                again.difference = { CodeGenConfig.earlierRecordDifference(context) } as Supplier<String>
            }
            ProjectTools commands = ProjectTools.of(listed)
            if (commands != null) {
                commands.listedJustNow = true
            }
            return execute(listed, again, args)
        } finally {
            if (Diagnostics.isOn()) {
                generator(context)
            }
        }
    }

    // whether the project generator runs, and how many templates of the packs of Forge it compiled and took as the build compiled them
    private static void generator(ForgeContext context) {
        if (!context.isStarted()) {
            Diagnostics.say('project generator', 'not started')
            return
        }
        try {
            BundledPackFeatures packs = context.get().getBean(BundledPackFeatures)
            Diagnostics.say('project generator', "running; templates of the packs of Forge since it started: ${packs.precompiledTemplates} precompiled by the build of Forge, ".toString() +
                    "${packs.compiledTemplates} compiled at run time".toString())
        } catch (RuntimeException e) {
            Diagnostics.say('project generator', "running; the templates it compiled are not known: ${e.getClass().getSimpleName()}".toString())
        }
    }

    // said once for a directory while the context lives, which in the interactive shell is once
    private static String advice(ForgeContext context) {
        try {
            Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
            return context.advised.add(current.toString()) ? EarlierGrailsProject.guidance(current) : null
        } catch (IOException ignored) {
            // without a current directory there is no project to say anything of
            return null
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
            String refused = refusedIn(args)
            if (refused != null) {
                // whatever was typed, it is no command that is run in this project
                root.err(refused)
                return 1
            }
            String typed = commandOf(args)
            // The help or the version asked for in front of the name is the answer, and is no mistake that comes here;
            // after the name, as in a name followed by --help, it is an argument of the command the name may be.
            if (held.mayList && isNoCommand(commandLine, e, typed) && ProjectTools.listsFor(commandLine, typed)) {
                // it may be a tool of the application or of a plugin that the build has not listed yet
                int listing = ProjectTools.list(commandLine, typed)
                held.listed = listing == 0
                return listing
            }
            held.replay(root)
            // what was typed may be the name of a tool of the application that is not there for it
            ProjectTools.said(commandLine, typed)
            return commandLine.getParameterExceptionHandler().handleParseException(e, args)
        }
        CommandLine help = commandLine.getSubcommands().get(HelpCommand.NAME)
        boolean document = help != null && commandOf(expanded) == HelpCommand.NAME && optionsOf(expanded).contains('--json')
        String refused = refusedIn(expanded)
        if (refused != null) {
            if (commandOf(expanded) != null && commandOf(expanded) != HelpCommand.NAME) {
                // nothing of the project is run, read for a command or written
                root.err(refused)
                return 1
            }
            // the help is shown all the same, and says first why its commands of a project are not run
            held.warning(refused)
        }
        if (goesByRecord(commandLine, expanded)) {
            held.sayRecordsDiffer()
        }
        if (!readsProject(expanded)) {
            // of a command that is not put together from the record, the one thing there can be to say of it
            held.replay(root)
        } else {
            // A project of an earlier version of Grails is said to be one by the commands that write
            // into it what this version has, and by the help; a command that runs its build does in
            // it what it does in any project, and says nothing.
            CommandLine named = commandOf(expanded) == null ? null : commandLine.getSubcommands().get(commandOf(expanded))
            if (named == null || named.is(help) || named.getCommand() instanceof CodeGenCommand) {
                held.advise()
            }
            if (document) {
                // the document is all that is written: it says what was held back
                HelpCommand command = (HelpCommand) help.getCommand()
                command.diagnostics = held.messages()
                command.complete = held.recordUsed
            } else {
                held.replay(root)
            }
        }
        if (!document) {
            ProjectTools.said(commandLine, commandOf(args), commandOf(expanded))
        }
        return commandLine.execute(expanded)
    }

    /**
     * Whether a command line goes by what the record of the project says of the application, and is
     * therefore told when a {@code grails-forge-cli.yml} beside {@code .grails} says something else:
     * the commands that create the classes of the application, those of its features among them, every
     * command of {@code feature}, {@code project show} and {@code project regenerate}, and the
     * document of {@code help --json}, which has every diagnostic of the directory. No other command
     * says it: one that runs the build or looks for plugins decides nothing by the record, and
     * {@code project health} has a finding for it. This is the one place that decides it.
     */
    private static boolean goesByRecord(CommandLine commandLine, String... expanded) {
        List<String> options = optionsOf(expanded)
        List<String> words = options.findAll { String arg -> !arg.startsWith('-') }
        if (words.isEmpty()) {
            return false
        }
        if (words[0] == HelpCommand.NAME) {
            return options.contains('--json')
        }
        if (options.contains('-h') || options.contains('--help')) {
            return false
        }
        Class<?> type = declared(expanded)
        if (type == FeatureCommand) {
            return true
        }
        if (type == ProjectGroupCommand) {
            return words.size() > 1 && words[1] in [ProjectShowCommand.NAME, ProjectRegenerateCommand.NAME]
        }
        CommandLine named = commandLine.getSubcommands().get(words[0])
        Object command = named == null ? null : named.getCommandSpec().userObject()
        return command instanceof CodeGenCommand || command instanceof PackArtefactCommand
    }

    /**
     * What there is to say of a command line in a project of a version of Grails before the first this
     * command line works with, and {@code null} in any other directory and for the command lines that
     * are run there all the same: asking for the version, creating an application,
     * {@code project migrate}, which is how such a project gets the record of this version, and
     * {@code project health}, which says what this version asks of the project.
     */
    private static String refusedIn(String... args) {
        Class<?> type = declared(args)
        if (asksForVersion(args) || (type != null && CreateCommand.isAssignableFrom(type))) {
            return null
        }
        List<String> words = optionsOf(args).findAll { String arg -> !arg.startsWith('-') }
        if (words.size() > 1 && words[0] == ProjectGroupCommand.NAME && words[1] in [ProjectMigrateCommand.NAME, ProjectHealthCommand.NAME]) {
            return null
        }
        try {
            return EarlierGrailsProject.refusal(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
        } catch (IOException ignored) {
            // without a current directory there is no project to refuse
            return null
        }
    }

    // whether the command line asks for the version of the command line, which it then says and runs nothing:
    // -V or --version in front of the command. After the command, or after --, it is an argument of the
    // command, as it is of gradle and of the build a command runs
    private static boolean asksForVersion(String... args) {
        for (String arg : args) {
            if (arg == '-V' || arg == '--version') {
                return true
            }
            if (arg == '--' || !arg.startsWith('-')) {
                return false
            }
        }
        return false
    }

    /**
     * Whether a command line has to do with the project of the current directory, whose record is
     * then read for the code generation commands it has. A command that creates an application has
     * not, and neither has asking for the version: they do not read what the directory holds. Help
     * has, because it lists the commands there are in this directory.
     */
    static boolean readsProject(String... args) {
        Class<?> type = declared(args)
        if (commandOf(args) == null) {
            return !args.any { String arg -> arg == '-V' || arg == '--version' }
        }
        return type == null || type == HelpCommand
    }

    /**
     * Whether the tools of the application and its plugins are looked for, for a command line: for
     * every one that reads the project, and for {@code gradle} and {@code feature} as well, which
     * read no record of the project but have names a command of the build may have too, of which
     * something is then said.
     */
    static boolean listsApplication(String... args) {
        Class<?> type = declared(args)
        return readsProject(args) || (type != null && !CreateCommand.isAssignableFrom(type))
    }

    // the command of a command line that is declared with the command line itself; null for any other, and for none
    private static Class<?> declared(String... args) {
        String command = commandOf(args)
        for (Class<?> type : ForgeCommand.getAnnotation(CommandLine.Command).subcommands()) {
            CommandLine.Command each = type.getAnnotation(CommandLine.Command)
            if (command != null && each != null && (each.name() == command || command in each.aliases())) {
                return type
            }
        }
        return null
    }

    private static String commandOf(String... args) {
        return args.find { String arg -> !arg.startsWith('-') }
    }

    // whether the mistake is that what was typed where the command is expected is no command of the
    // command line, by its name or its short form: not that it is short for more than one
    private static boolean isNoCommand(CommandLine commandLine, CommandLine.ParameterException mistake, String typed) {
        if (typed == null || !mistake.getCommandLine().is(commandLine)) {
            return false
        }
        try {
            return new CommandCatalog(commandLine).find(commandLine, typed) == null
        } catch (CommandLine.ParameterException ignored) {
            return false
        }
    }

    // what can be an option: after -- nothing is
    private static List<String> optionsOf(String... args) {
        int end = args.findIndexOf { String arg -> arg == '--' }
        return (args as List<String>).subList(0, end < 0 ? args.length : end)
    }

    /**
     * Runs the interactive shell on a running context until the user leaves it.
     */
    static int interactive(ApplicationContext context) {
        return interactive(ForgeContext.of(context))
    }

    /**
     * Runs the interactive shell until the user leaves it, on a context that is started when a
     * command typed there first needs it.
     */
    static int interactive(ForgeContext context) {
        CommandLine commandLine = createCommandLine(context, true)
        // in the shell a command that starts an application returns to the prompt
        ConsoleCommands.setInteractive(true)
        try {
            InteractiveShell shell = new InteractiveShell(commandLine, { String[] commandArgs -> execute(context, commandArgs) }, EXCEPTION_HANDLER,
                    { createCommandLine(context, true) } as Supplier<CommandLine>)
            shell.endsJvm = context.endsJvm
            return shell.start()
        } finally {
            ConsoleCommands.setInteractive(false)
            // What belongs to the session ends with it, before this returns: where a program runs the console in its
            // own JVM, that JVM goes on. The applications run-app started there without --detach, and the connection
            // to the Gradle of the project its builds were asked of.
            RunAppCommand.stopWhatTheShellStarted()
            GradleBuild.closeConnections()
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
        return createCommandLine(context, noOpConsole ? ConsoleOutput.NOOP : null, readsProject, readsProject)
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
    private static CommandLine createCommandLine(ForgeContext context, ConsoleOutput console, boolean readsProject, boolean listsApplication) {
        GrailsPicocliFactory factory = new GrailsPicocliFactory(context)
        ForgeCommand root = factory.create(ForgeCommand)
        CommandLine commandLine = new CommandLine(root, factory)
        if (context.commandName != null) {
            commandLine.setCommandName(context.commandName)
        }
        // a mistake is answered with what is wrong and one line of how the command is used, not with the whole help
        commandLine.setParameterExceptionHandler({ CommandLine.ParameterException e, String[] given ->
            UsageHelp.answer(e, given)
        } as CommandLine.IParameterExceptionHandler)
        commandLine.executionExceptionHandler = { Exception ex, CommandLine failed, CommandLine.ParseResult parseResult ->
            EXCEPTION_HANDLER.apply(ex, failed)
        } as CommandLine.IExecutionExceptionHandler
        CodeGenConfig codeGenConfig = readsProject ? CodeGenConfig.load(context, console ?: root) : null
        if (readsProject) {
            // Where the record of no project was read, every one of them is there by its name and its
            // options: it is found by its short form, shows its help, is told of a mistake in its use,
            // and when it is run says what every command of a project says there.
            for (Class<? extends CodeGenCommand> type : CODE_GEN_COMMANDS) {
                CodeGenCommand command
                try {
                    command = type.getConstructor(CodeGenConfig).newInstance(codeGenConfig)
                }
                catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to create command ${type.name}", e)
                }
                if (codeGenConfig == null) {
                    // not among the commands the help lists there, which says that a project has more
                    commandLine.addSubcommand(command)
                    commandLine.getSubcommands().get(type.getAnnotation(CommandLine.Command).name()).getCommandSpec().usageMessage().hidden(true)
                } else if (command.applies()) {
                    commandLine.addSubcommand(command)
                }
            }
            if (codeGenConfig == null && !inProject()) {
                commandLine.getCommandSpec().usageMessage().footer('', 'A Grails project has more commands: those that create its classes, and those of the application.')
            }
        }
        commandLine.setExecutionStrategy({ CommandLine.ParseResult parsed -> ForgeCli.runAsRead(root, parsed) } as CommandLine.IExecutionStrategy)
        ConsoleCommands.addTo(commandLine, readsProject)
        // the plugins there are, in a project and outside one: only these two ask the plugin index for anything
        commandLine.addSubcommand(new ListPluginsCommand(context))
        commandLine.addSubcommand(new PluginInfoCommand(context))
        // the same two by their names among the commands of plugin, and the list by a text
        commandLine.addSubcommand(new PluginGroupCommand())
        // the commands the features of the application give, which the record of the project has: after every command of
        // the command line itself, whose names they do not take, and before those of the application, which do not take theirs
        if (codeGenConfig != null) {
            addCommandsOfFeatures(commandLine, codeGenConfig, console ?: root, factory)
        }
        // last: a tool of the application does not take the name of a command that is there
        ProjectTools.addTo(commandLine, listsApplication)
        // everything after gradle is Gradle's, and everything after a tool of the application is that tool's
        PlainOutput.notForWhatIsPassedOn(commandLine)
        // once every command is there: the width is given to the commands there are when it is set
        commandLine.setUsageHelpWidth(USAGE_WIDTH)
        commandLine.setHelpFactory({ CommandLine.Model.CommandSpec spec, CommandLine.Help.ColorScheme scheme ->
            new UsageHelp(spec, scheme)
        } as CommandLine.IHelpFactory)
        return commandLine
    }

    // A feature pack is refused a name the command line has when it is read, and two features of one application the
    // same name when the application is generated. What is left is a record that was written by hand, or by a version
    // with fewer commands of its own: the command that is there stays, the first of two features has the name, and the
    // one that is not a command is said.
    private static void addCommandsOfFeatures(CommandLine commandLine, CodeGenConfig config, ConsoleOutput console, CommandLine.IFactory factory) {
        for (FeatureArtefact artefact : config.getArtefacts()) {
            CommandLine taken = commandLine.getSubcommands().get(artefact.commandName)
            if (taken != null) {
                Object other = taken.getCommandSpec().userObject()
                console.warning("The command ${artefact.commandName} of the feature ${artefact.feature} is not a command here: ${artefact.commandName} is " +
                        (other instanceof PackArtefactCommand ? "a command of the feature ${((PackArtefactCommand) other).getArtefact().feature}" :
                                taken.getCommandName() == artefact.commandName ? 'a command of the command line itself' :
                                        "another name of ${taken.getCommandName()}, a command of the command line itself"))
                continue
            }
            commandLine.addSubcommand(artefact.commandName, new CommandLine(PackArtefactCommand.specOf(config, artefact, factory), factory))
        }
    }

    // Runs what the command line was read as. A command that writes what the record of a project says
    // is not run where none was read: it says so, in the words of every command of a project. While
    // the command runs, what it starts knows whether anything may wait for what a user types.
    private static int runAsRead(ForgeCommand root, CommandLine.ParseResult parsed) {
        CommandLine.ParseResult last = parsed
        boolean help = false
        while (true) {
            help = help || last.isUsageHelpRequested() || last.isVersionHelpRequested()
            if (!last.hasSubcommand()) {
                break
            }
            last = last.subcommand()
        }
        Object command = last.commandSpec().userObject()
        if (!help && command instanceof CodeGenCommand && !((CodeGenCommand) command).hasRecord()) {
            ((CodeGenCommand) command).err(UsageHelp.withoutProject(last.commandSpec()) ?: "${UsageHelp.typed(last.commandSpec())} is a command of a Grails project")
            return 1
        }
        GradleBuild.setNonInteractive(root.nonInteractive)
        try {
            return new CommandLine.RunLast().execute(parsed)
        } finally {
            GradleBuild.setNonInteractive(false)
        }
    }

    /** Keeps what is said until it is known whether it is to be said, and then says it in the order it came. */
    private static final class HeldBack implements ConsoleOutput, CodeGenConfig.RecordListener {

        private final List<Closure> said = []
        private final List<String> lines = []

        /** Whether a name that is no command may have the build list the tools of the application: once for a command line. */
        boolean mayList

        /** Whether the build listed them, after which the command line is put together and read again. */
        boolean listed

        /** Whether no record of the project was found that could not be used. */
        boolean recordUsed = true

        /** Gives what there is to say of a project of an earlier version of Grails, when a command asks for it. */
        Supplier<String> advice

        /** Gives the warning that the two records of the project differ, where the record was not read for the command line. */
        Supplier<String> difference

        private String differs

        @Override
        void recordsDiffer(String warning) {
            differs = warning
        }

        /** Says that the two records of the project differ, when they do: asked for by a command line that goes by the record. */
        void sayRecordsDiffer() {
            String message = differs ?: (difference == null ? null : difference.get())
            if (message != null) {
                warning(message)
            }
        }

        void advise() {
            String guidance = advice == null ? null : advice.get()
            if (guidance != null) {
                warning(guidance)
            }
        }

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
            // what reading the project has to say is no output of the command that is run after it
            said.add { ConsoleOutput to -> to instanceof BaseCommand ? ((BaseCommand) to).warned(message) : to.warning(message) }
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

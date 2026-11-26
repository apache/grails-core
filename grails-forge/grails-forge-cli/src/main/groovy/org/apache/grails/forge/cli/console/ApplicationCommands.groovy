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

import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.artefact.PackArtefactCommand
import org.grails.forge.application.OperatingSystem
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * The commands of the application and of its plugins on a command line: those the build of the
 * project of the current directory lists in {@link ApplicationCommandFile}, each added as a command
 * of the CLI, and what there is to say about the ones that are not there.
 *
 * <p>A command the CLI has itself keeps its name, also where it is another name of the command or
 * the short form of its name: a command of the build that would take it is not added, and that is
 * said wherever the commands are listed and when the name is typed. Every build that compiles the
 * project writes the file, and nothing is run to read it: without the file, or with one that cannot
 * be used, there are no such commands, and one line says which Gradle task writes it. Only for a name
 * that is typed and is no command, where the file is missing or may be out of date, is the build run
 * once to list the commands ({@link #listsFor}, {@link #list}), and the command line read again.</p>
 *
 * <p>The scripts of the application ({@link ScriptCommandFiles}) are commands in the same way and by
 * the same rules, after the commands the build lists: a script does not take the name of one of
 * those either.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ApplicationCommands {

    /**
     * The environment variable that turns the listing for a name that is typed off: with {@code false}
     * such a name is refused at once, and no build is run for it. With any other value, and without it,
     * the build lists the commands when the name is no command yet.
     */
    public static final String LISTING = 'GRAILS_LIST_COMMANDS'

    private static final String SECTION = 'applicationCommandList'
    private static final String RUN_COMMAND = 'run-command'
    private static final String OF_WHAT = 'The commands of the application and its plugins'
    private static final String OFF = 'false'
    // what can be the name of a command of the build, or the short form of the name of one: anything else is never one
    private static final Pattern NAME_OR_SHORT_FORM = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*|[A-Za-z][A-Za-z-]{0,63}')
    private static final int MAX_NAME = 64
    private static final Supplier<String> ENVIRONMENT = { System.getenv(LISTING) } as Supplier<String>

    private static volatile Supplier<String> listing = ENVIRONMENT

    // what there is to say about a command line, for as long as there is the command line
    private static final Map<CommandLine, ApplicationCommands> OF = Collections.synchronizedMap(new WeakHashMap<CommandLine, ApplicationCommands>())

    private final ConsoleProject project
    private final ApplicationCommandFile file
    private final ScriptCommandFiles scripts
    // by each name a command of the build would have had: what has the name, and the command that is not there for it
    private final Map<String, String> notAdded = new LinkedHashMap<>()
    private final Map<String, ApplicationCommandFile.Entry> notAddedBy = new LinkedHashMap<>()
    // what there is to say of the options the application and its plugins declare for run-app
    private List<String> aboutOptions = Collections.<String>emptyList()

    /** Whether the build listed the commands for what was typed just before the command line was put together. */
    boolean listedJustNow

    private ApplicationCommands(ConsoleProject project, ApplicationCommandFile file, ScriptCommandFiles scripts) {
        this.project = project
        this.file = file
        this.scripts = scripts
    }

    /**
     * Adds the commands of the application and of its plugins to a command line that has every
     * command of the CLI itself.
     *
     * @param root the command line of the CLI
     * @param readsProject whether the project of the current directory is looked at
     */
    static void addTo(CommandLine root, boolean readsProject) {
        ConsoleProject project = readsProject ? ConsoleProject.find(FileSystemOutputHandler.getDefaultBaseDirectory().toPath()) : null
        if (project == null) {
            return
        }
        ApplicationCommands commands = new ApplicationCommands(project, ApplicationCommandFile.read(project.getDirectory()), ScriptCommandFiles.list(project.getDirectory()))
        commands.add(root)
        // the build lists the test phases of the project in the same file
        TestAppCommand.phasesOf(root, commands.file)
        // and the options the application and its plugins declare for run-app
        commands.aboutOptions = DeclaredOptions.addTo(root, RunAppCommand.NAME, commands.file)
        OF.put(root, commands)
    }

    /**
     * @param root a command line of the CLI
     * @return what there is to say about the commands of the application on it, or {@code null}
     * when it is not in a project that is used
     */
    static ApplicationCommands of(CommandLine root) {
        return OF.get(root)
    }

    /**
     * @param root the command line of the CLI
     * @param name the name of a command
     * @return where the build of the project says the command of that name comes from, also for one
     * that is no command here because the CLI has one of its name; {@code null} when the build lists none
     */
    static String originOf(CommandLine root, String name) {
        ApplicationCommands commands = of(root)
        ApplicationCommandFile.Entry entry = commands == null ? null : commands.file.commands.find { ApplicationCommandFile.Entry each -> name in each.names() }
        return entry == null ? null : entry.origin
    }

    /**
     * What is said when something was typed for a command and is none: without a list of the
     * commands of the application that can be relied on, it may be one of those; after the build has
     * just listed them, that it is none of those either. Where the build could not list every one of
     * them, such as where the class path they run with does not resolve, it may be one it did not
     * list, and {@code help} says what that is.
     *
     * @param root the command line of the CLI
     * @param shown what was typed, as it can be shown
     * @return one line, or {@code null} when there is such a list or no project
     */
    static String whenUnknown(CommandLine root, String shown) {
        ApplicationCommands commands = of(root)
        if (commands == null) {
            return null
        }
        String about = commands.aboutFile(root)
        if (about != null) {
            return about
        }
        // what the build lists is current, and may still not have them all
        String notAll = commands.isComplete() ? null : "Not every command of the application and its plugins is listed, so ${shown} may be one that is not. " +
                "What is not listed, and why: ${UsageHelp.toType(root, 'help')}"
        if (commands.listedJustNow) {
            return "The build of the project has just listed the commands of the application and its plugins, and ${shown} is none of them" +
                    (notAll == null ? '' : ". ${notAll}")
        }
        return notAll
    }

    /**
     * Whether the build is run to list the commands of the application and its plugins for what was
     * typed where a command was expected, at the top of the command line, and is no command: in a
     * project that is used, whose Gradle wrapper is run, when the list is missing or may be out of
     * date, and when what was typed can be the name of a command of the build or the short form of
     * one. A name the Grails shell had for a command that is here under another name, or is gone, and
     * the name of an environment in front of a command are answered at once. {@link #LISTING} turns it off.
     *
     * @param root the command line of the CLI
     * @param typed what was typed
     * @return whether to list
     */
    static boolean listsFor(CommandLine root, String typed) {
        ApplicationCommands commands = of(root)
        if (commands == null || OFF.equalsIgnoreCase(listing.get()?.trim()) || typed == null || typed.length() > MAX_NAME ||
                !NAME_OR_SHORT_FORM.matcher(typed).matches()) {
            return false
        }
        if (!(commands.file.state in [ApplicationCommandFile.State.MISSING, ApplicationCommandFile.State.STALE])) {
            return false
        }
        FormerCommands.Former former = FormerCommands.find(typed)
        if ((former != null && former.status != FormerCommands.Status.PLUGIN) || FormerCommands.isEnvironment(typed)) {
            return false
        }
        // without a wrapper that is run nothing can be listed, and no command of the build run either
        return commands.project.gradleWrapper(isWindows(root)).file != null
    }

    /**
     * Has the build of the project list the commands of the application and its plugins with the Gradle
     * task {@link ApplicationCommandFile#TASK}, after one line on the error stream that says so. The build
     * is run as the build of every command is, by {@link GradleBuild}, with an input that has ended; the
     * task is run although Gradle may take it for up to date, so that the file is written again.
     *
     * @param root the command line of the CLI, in a project that is used
     * @param typed what was typed, which is no command yet
     * @return 0 when the build listed the commands, and 1 after what the build said otherwise
     */
    static int list(CommandLine root, String typed) {
        ApplicationCommands commands = of(root)
        BaseCommand command = (BaseCommand) root.getCommand()
        PrintWriter said = root.getErr()
        String shown = FeatureSelectionException.shown(typed)
        said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | Listing|@ the commands of the application and its plugins, of which ${shown} may be one, " +
                "with the Gradle task ${ApplicationCommandFile.TASK}"))
        said.flush()
        GradleBuild runner = GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> command.err(message) } as Consumer<String>, isWindows(root))
        boolean nonInteractive = GradleBuild.isNonInteractive()
        GradleBuild.setNonInteractive(true)
        int exit
        try {
            exit = runner.run(commands.project, [commands.project.task(ApplicationCommandFile.TASK), '--rerun'])
        } catch (GradleRunner.RefusedArgumentException e) {
            command.err(e.getMessage())
            exit = 1
        } finally {
            GradleBuild.setNonInteractive(nonInteractive)
        }
        if (exit != 0) {
            command.err("The commands of the application and its plugins could not be listed, so it is not known whether ${shown} is one of them. It was not run")
        }
        return exit
    }

    /**
     * Replaces what the environment says of {@link #LISTING}, for a specification, which cannot change
     * the environment it runs in.
     *
     * @param value what {@link #LISTING} is taken to be; {@code null} for what the environment says
     */
    static void setListing(String value) {
        listing = value == null ? ENVIRONMENT : ({ value } as Supplier<String>)
    }

    private static boolean isWindows(CommandLine root) {
        Object command = root.getCommand()
        return command instanceof BaseCommand ? ((BaseCommand) command).getOperatingSystem() == OperatingSystem.WINDOWS :
                System.getProperty('os.name', '').toLowerCase(Locale.ROOT).contains('windows')
    }

    /**
     * Says, when the name of a command of the application was typed that is not there because the
     * CLI has a command by that name, that the command of the CLI is the one that is run.
     *
     * @param root the command line of the CLI
     * @param typed what was typed for a command, as it was typed and as the name it is short for
     */
    static void said(CommandLine root, String... typed) {
        ApplicationCommands commands = of(root)
        String name = commands == null ? null : typed.find { String each -> each != null && commands.notAdded.containsKey(each) }
        if (name != null) {
            root.getErr().println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + commands.aboutNotAdded(root, name)))
        }
    }

    /**
     * @return whether every command of the application and its plugins is among the commands, as far
     * as can be told: there is the file of the build, nothing it is made from was changed since, and
     * the build listed everything, and every script of the application is listed. A command that is
     * not there because the CLI has one by its name is named in the diagnostics, and leaves this as it is
     */
    boolean isComplete() {
        return file.state == ApplicationCommandFile.State.CURRENT && file.complete && file.leftOut.isEmpty() && scripts.complete
    }

    /**
     * @param root the command line of the CLI
     * @return what there is to say about the commands of the application, one line each
     */
    List<String> diagnostics(CommandLine root) {
        List<String> lines = []
        String about = aboutFile(root)
        if (about != null) {
            lines.add(about)
        }
        for (String line : file.diagnostics) {
            lines.add("The Gradle task ${ApplicationCommandFile.TASK} says: ${line}".toString())
        }
        lines.addAll(file.leftOut)
        lines.addAll(scripts.diagnostics)
        lines.addAll(aboutOptions)
        // once for a command, whichever of its names are taken
        Set<ApplicationCommandFile.Entry> named = Collections.newSetFromMap(new IdentityHashMap<ApplicationCommandFile.Entry, Boolean>())
        for (String name : notAdded.keySet()) {
            if (named.add(notAddedBy.get(name))) {
                lines.add(aboutNotAdded(root, name))
            }
        }
        return lines
    }

    private void add(CommandLine root) {
        CommandCatalog builtIn = new CommandCatalog(root)
        List<ApplicationCommandFile.Entry> added = []
        for (ApplicationCommandFile.Entry entry : file.commands) {
            String taken = null
            for (String name : entry.names()) {
                taken = taken ?: takenBy(builtIn, name)
            }
            if (taken == null) {
                added.add(entry)
                continue
            }
            for (String name : entry.names()) {
                notAdded.put(name, taken)
                notAddedBy.put(name, entry)
            }
        }
        // a script comes after the commands the build lists, and does not take the name of one
        Map<String, ApplicationCommandFile.Entry> listed = new HashMap<>()
        for (ApplicationCommandFile.Entry entry : file.commands) {
            for (String name : entry.names()) {
                listed.put(name, entry)
            }
        }
        for (ApplicationCommandFile.Entry script : scripts.commands) {
            ApplicationCommandFile.Entry other = listed.get(script.name)
            String taken = takenBy(builtIn, script.name)
            if (taken == null && other != null) {
                taken = "${script.name} is ${other.name == script.name ? 'a command' : "another name of ${other.name}, a command"} the build lists (${other.origin})".toString()
            }
            if (taken == null) {
                added.add(script)
                continue
            }
            // Where a command the build lists is not there for the same name, that one is what is said
            // when the name is typed; the script is said with the others, under a key nothing is typed as.
            String key = notAdded.containsKey(script.name) ? script.name + ' ' : script.name
            notAdded.put(key, taken)
            notAddedBy.put(key, script)
        }
        // only now: a command of the build does not take a name from another one of the build
        for (ApplicationCommandFile.Entry entry : added) {
            CommandLine.Model.CommandSpec spec = ApplicationCommandCommand.specOf(entry, root.getFactory())
            root.addSubcommand(entry.name, new CommandLine(spec, root.getFactory()))
        }
        listSeparately(root)
    }

    // what has a name already, as the start of a sentence; null when the name is free for a command of the build
    private static String takenBy(CommandCatalog builtIn, String name) {
        CommandLine root = builtIn.getRoot()
        CommandLine named = root.getSubcommands().get(name)
        if (named != null && named.getCommandSpec().userObject() instanceof PackArtefactCommand) {
            return "${name} is a command of the feature ${((PackArtefactCommand) named.getCommandSpec().userObject()).getArtefact().feature} of the application"
        }
        if (named != null) {
            String own = named.getCommandName()
            return own == name ? "${name} is a command of the CLI itself (${CommandCatalog.BUILT_IN})" :
                    "${name} is another name of ${own}, a command of the CLI itself (${CommandCatalog.BUILT_IN})"
        }
        try {
            CommandLine shortFor = builtIn.find(root, name)
            if (shortFor != null && shortFor.getCommandSpec().userObject() instanceof PackArtefactCommand) {
                return "${name} is short for ${shortFor.getCommandName()}, a command of the feature " +
                        "${((PackArtefactCommand) shortFor.getCommandSpec().userObject()).getArtefact().feature} of the application"
            }
            return shortFor == null ? null : "${name} is short for ${shortFor.getCommandName()}, a command of the CLI itself (${CommandCatalog.BUILT_IN})"
        } catch (CommandLine.ParameterException ignored) {
            return "${name} is short for more than one command of the CLI itself (${CommandCatalog.BUILT_IN})"
        }
    }

    private String aboutNotAdded(CommandLine root, String name) {
        ApplicationCommandFile.Entry entry = notAddedBy.get(name)
        // the command that runs a command of the application by its name, where the command line has it
        if (entry.isScript()) {
            return "${notAdded.get(name)}, so the script ${entry.name} of ${ScriptCommandFiles.LOCATION} is not a command here. " +
                    "That one is run with: ${UsageHelp.toType(root, "gradle ${ScriptCommandFiles.TASK} --arg=${entry.name}")}"
        }
        String how = root.getSubcommands().containsKey(RUN_COMMAND) ? UsageHelp.toType(root, "${RUN_COMMAND} ${entry.name}") :
                UsageHelp.toType(root, "gradle ${ApplicationCommandCommand.TASK} --arg=${entry.name}")
        return "${notAdded.get(name)}, so the command ${entry.name} of ${entry.origin} is not a command here. That one is run with: ${how}"
    }

    // what keeps the list of the build from being relied on; null when nothing does
    private String aboutFile(CommandLine root) {
        String gradle = UsageHelp.toType(root, "gradle ${ApplicationCommandFile.TASK}")
        switch (file.state) {
            case ApplicationCommandFile.State.MISSING:
                return "${OF_WHAT} are not listed yet: a build that compiles the project lists them, and so does typing the name of one. To list them now: ${gradle}"
            case ApplicationCommandFile.State.REFUSED:
                return "${OF_WHAT} are not listed: ${ApplicationCommandFile.LOCATION} ${file.reason}. To write it again: ${gradle} --rerun"
            case ApplicationCommandFile.State.STALE:
                return "${OF_WHAT} may have changed: ${file.reason} is newer than ${ApplicationCommandFile.LOCATION}. To list them again: ${gradle} --rerun"
            default:
                return null
        }
    }

    // In the help of the command line the commands of the build have a list of their own, with where
    // each comes from, after the list of the commands of the CLI. Set for the command line alone:
    // its commands list their own commands as before.
    private void listSeparately(CommandLine root) {
        CommandLine.Model.UsageMessageSpec usage = root.getCommandSpec().usageMessage()
        Map<String, CommandLine.IHelpSectionRenderer> sections = new LinkedHashMap<>(usage.sectionMap())
        sections.put(CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST, { CommandLine.Help help ->
            help.commandList(help.subcommands().findAll { String name, CommandLine.Help each -> !isOfApplication(each) })
        } as CommandLine.IHelpSectionRenderer)
        sections.put(SECTION, { CommandLine.Help help -> list(root, help) } as CommandLine.IHelpSectionRenderer)
        List<String> keys = new ArrayList<String>(usage.sectionKeys())
        keys.add(keys.indexOf(CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST) + 1, SECTION)
        usage.sectionKeys(keys)
        usage.sectionMap(sections)
    }

    private static boolean isOfApplication(CommandLine.Help help) {
        return help.commandSpec().userObject() instanceof ApplicationCommandCommand
    }

    private String list(CommandLine root, CommandLine.Help help) {
        Map<String, CommandLine.Help> commands = help.subcommands().findAll { String name, CommandLine.Help each -> isOfApplication(each) }
        List<String> notes = diagnostics(root)
        if (commands.isEmpty() && notes.isEmpty()) {
            return ''
        }
        CommandLine.Help.ColorScheme scheme = help.colorScheme()
        int width = help.commandSpec().usageMessage().width()
        StringBuilder listed = new StringBuilder()
        listed.append(String.format('%n')).append(scheme.ansi().string('@|bold,underline Commands of the application and its plugins:|@')).append(String.format('%n'))
        if (!commands.isEmpty()) {
            int names = Math.min(commands.values().collect { CommandLine.Help each -> each.commandNamesText(', ').getCJKAdjustedLength() }.max(), width.intdiv(2))
            for (CommandLine.Help each : commands.values()) {
                ApplicationCommandFile.Entry entry = ((ApplicationCommandCommand) each.commandSpec().userObject()).getEntry()
                String origin = entry.isScript() ? 'script of the application' : entry.origin
                String description = entry.description.isEmpty() ? "(${origin})" : "${ApplicationCommandCommand.shown(entry.description)} (${origin})"
                listed.append(wrapped('  ' + each.commandNamesText(', ').plainString().padRight(names + 2), description, names + 4, width))
            }
        }
        for (String note : notes) {
            listed.append(wrapped('  ', note, 4, width))
        }
        return listed.toString()
    }

    // Lines for the help, broken between words only: coordinates, and what is to be typed, stay in one
    // piece. The text is written as it is, and is not read for the markup of the command line.
    private static String wrapped(String start, String text, int indent, int width) {
        StringBuilder wrapped = new StringBuilder()
        String line = start
        boolean first = true
        for (String word : text.split(' ')) {
            if (!first && line.length() + 1 + word.length() > width) {
                wrapped.append(line).append(String.format('%n'))
                line = ' ' * indent
                first = true
            }
            line = first ? line + word : line + ' ' + word
            first = false
        }
        return wrapped.append(line).append(String.format('%n')).toString()
    }
}

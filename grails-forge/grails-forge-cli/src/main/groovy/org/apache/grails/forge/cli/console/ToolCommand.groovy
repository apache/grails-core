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
import groovy.transform.CompileStatic
import org.grails.forge.application.OperatingSystem
import picocli.CommandLine

/**
 * A tool of the application or of one of its plugins, as a command of the CLI: one for each tool the
 * build of the project lists, under the name the build gives. A tool the build lists with the target
 * {@code application} is run by the Gradle task {@code runTool} of the project, with the application
 * started around it, or without the application when the build lists it with {@code startsApplication}
 * false. A script of the application is such a tool too: it is run by the build as well, by the
 * Gradle task {@code runScriptCommand}, without the application, and never by the CLI.
 *
 * <p>A tool the build lists with the target {@code standalone} is not run by Gradle: the CLI starts
 * a JVM for it, with the class path the build wrote down ({@link StandaloneToolsFile},
 * {@link StandaloneToolProcess}). Gradle is run for it only to bring that up to date, when
 * something it is made from was changed, and when the build says that it has to run the tool itself.
 * No code of a project or of a plugin runs in the JVM of the CLI.</p>
 *
 * <p>Everything after the name of the tool is the tool's and is given to it as the list of
 * arguments it is, one for one: the command of the CLI has no options of its own. The arguments are
 * handed to Gradle in a file only the user can read, see {@link CommandArgumentsFile}, so that none
 * of them is on a command line. Before Gradle is started one line on the error stream says which
 * tool of which origin is run, and one more line for each thing of the tool that is deprecated.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'tool', preprocessor = ToolCommand.EverythingIsTheTools)
@CompileStatic
class ToolCommand extends InProjectCommand {

    /** The Gradle task that runs a tool of the application. */
    public static final String TASK = 'runTool'

    /** The command of the CLI that runs a tool by its name with the Gradle task {@link #TASK}. */
    public static final String RUN_TOOL = RunToolCommand.NAME

    /** The environment a tool of the application runs in, which is the one {@code run-tool} has unless it is given another. */
    public static final String ENVIRONMENT = 'development'

    /** What names the environment of the build, which a tool of the application takes before its name with run-tool and not after it. */
    public static final String ENVIRONMENT_OPTION = '-Dgrails.env='

    /**
     * @param createTool how {@code create-tool} is typed where the help is shown
     * @return what the help of every script says: scripts are kept for the applications that have them, and
     * the task that runs one says it each time it runs
     */
    static String deprecatedScripts(String createTool) {
        return "Script commands are deprecated: write a tool, with ${createTool}".toString()
    }

    @CommandLine.Parameters(arity = '0..*', paramLabel = 'ARGS', description = 'The arguments of the tool, which are given to it as they are.')
    List<String> arguments = []

    private final ToolCatalogFile.Entry entry

    ToolCommand(ToolCatalogFile.Entry entry) {
        this.entry = entry
    }

    /**
     * @param entry a tool the build of the project lists
     * @param root the command line the command is added to
     * @return the tool as picocli has it, under the names the build gives
     */
    static CommandLine.Model.CommandSpec specOf(ToolCatalogFile.Entry entry, CommandLine root) {
        CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.forAnnotatedObject(new ToolCommand(entry), root.getFactory())
        // the options every command of the CLI has would be taken from what is meant for the tool
        for (CommandLine.Model.OptionSpec option : new ArrayList<CommandLine.Model.OptionSpec>(spec.options())) {
            spec.remove(option)
        }
        spec.name(entry.name)
        spec.aliases(entry.aliases as String[])
        // a description is text of a plugin, in which nothing stands for a variable of the command line
        spec.interpolateVariables(false)
        List<String> description = []
        if (!entry.description.isEmpty()) {
            description.add(shown(entry.description).replace('%', '%%'))
        }
        if (entry.isScript()) {
            description.add(("A script of the application, in ${ScriptCommandFiles.LOCATION}. It is run by the build of the project, without the application, " +
                    "and not by this program: it is started by the Gradle task ${ScriptCommandFiles.TASK} of the project.").toString())
            description.add(deprecatedScripts(UsageHelp.toType(root, 'create-tool')))
        } else {
            // where a tool comes from is the name of a jar or of a project: text, as the description is
            String of = "A tool of ${entry.origin == ToolCatalogFile.APPLICATION ? 'the application' : shown(entry.origin).replace('%', '%%')}, "
            description.add(entry.isStandalone() ?
                    (of + 'which runs as a process of its own, without Gradle and without the application: ' +
                            'it is started by this program, with the class path the build of the project wrote down for it.').toString() :
                    (of + (entry.startsApplication ? 'which runs in the application' : 'which the build runs without the application') +
                            ": it is started by the Gradle task ${TASK} of the project.").toString())
            for (String deprecation : deprecations(entry)) {
                description.add(deprecation.replace('%', '%%') + '.')
            }
        }
        description.add('Everything after the name of the tool is given to the tool as it is, the options too.')
        description.add('--help after the name is given to the tool as well, which runs it. This help is what the build of the project listed of the tool, ' +
                'and nothing was run for it.')
        if (entry.isStandalone()) {
            description.add("The Gradle task ${TASK} of the project runs it as well, without the application: ${RUN_TOOL} ${entry.name}".toString())
        }
        spec.usageMessage().description(description as String[])
        return spec
    }

    /**
     * @param entry a tool the build of the project lists
     * @return what of the tool is deprecated, one sentence each without the full stop, in the order the
     * build would have the source moved and then the type changed; none for a tool that is as it is to be
     */
    static List<String> deprecations(ToolCatalogFile.Entry entry) {
        List<String> said = []
        if (entry.isInDeprecatedFolder()) {
            said.add("${entry.name} is in grails-app/commands, which is deprecated: move it to src/cli/groovy".toString())
        }
        if (entry.isOfDeprecatedType()) {
            said.add("${entry.name} implements ApplicationCommand, which is deprecated: implement ApplicationTool".toString())
        }
        return said
    }

    /**
     * Writes one warning line on the error stream for each thing of a tool that is deprecated, as a
     * script is warned of, before the tool runs.
     *
     * @param said the error stream of the command line
     * @param entry the tool, or {@code null} for a name the build has not listed, of which nothing is known
     */
    static void warnOfDeprecations(PrintWriter said, ToolCatalogFile.Entry entry) {
        if (entry == null || entry.isScript()) {
            return
        }
        for (String deprecation : deprecations(entry)) {
            said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + GradleRunner.line(deprecation)))
        }
    }

    /**
     * @param description the description of a tool, which is one line
     * @return the description without what the command line would read as its markup
     */
    static String shown(String description) {
        return description.replace('@|', '@ |')
    }

    /**
     * @return the tool of the build this command of the CLI runs
     */
    ToolCatalogFile.Entry getEntry() {
        return entry
    }

    /**
     * @return the tool as the document for a tool of an IDE describes it: with what the build says of
     * it, and without options and parameters, which the build does not know. A script is a tool of the
     * application the build runs without the application.
     */
    Map<String, Object> describe() {
        Map<String, Object> described = new LinkedHashMap<>()
        described.put('name', entry.name)
        described.put('aliases', entry.aliases)
        described.put('description', entry.description)
        described.put('project', true)
        described.put('origin', entry.origin)
        described.put('target', entry.target)
        described.put('startsApplication', entry.startsApplication)
        described.put('registration', entry.registration)
        if (!entry.deprecated.isEmpty()) {
            described.put('deprecated', entry.deprecated)
        }
        described.put('commands', [])
        return described
    }

    @Override
    protected String notDone() {
        return 'The tool was not run'
    }

    /**
     * @return the Gradle task of the project that runs the tool
     */
    String task() {
        return entry.isScript() ? ScriptCommandFiles.TASK : TASK
    }

    @Override
    protected int run(ConsoleProject project) {
        List<String> vector = [entry.name]
        vector.addAll(arguments)
        String refused = CommandArgumentsFile.refusal(vector)
        if (refused != null) {
            throw new CommandLine.ParameterException(spec.commandLine(), "The tool cannot be given these arguments: ${refused}")
        }
        if (entry.isStandalone()) {
            Integer ended = standalone(project, vector)
            if (ended != null) {
                return ended.intValue()
            }
            // the build runs it, as it runs a tool that needs it
        }
        CommandArgumentsFile file
        try {
            file = CommandArgumentsFile.write(project, vector)
        } catch (IOException e) {
            err("The arguments of the tool could not be written for Gradle: ${GradleRunner.line(e.getMessage())}. Gradle was not run")
            return 1
        }
        try {
            // On the error stream, since what the tool writes is the output: what is typed can be
            // the name of a tool of a plugin by one letter, and this says what runs before it does.
            PrintWriter said = spec.commandLine().getErr()
            said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | Running|@ ${entry.isScript() ? "the script ${entry.name} of ${ScriptCommandFiles.LOCATION}, with the Gradle task ${task()}" : running(entry.name, entry.origin)}"))
            // the task that runs a script says itself, once, that scripts are deprecated
            if (!entry.isScript()) {
                warnOfDeprecations(said, entry)
                warnOfEnvironment(said)
            }
            said.flush()
            GradleBuild runner = GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> err(message) } as Consumer<String>, getOperatingSystem() == OperatingSystem.WINDOWS)
            return runner.run(project, gradleArguments(project, file))
        } catch (GradleRunner.RefusedArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        } finally {
            file.close()
        }
    }

    // Every argument after the name of a tool is the tool's, and so is this one. Who types
    // grails dbm-update -Dgrails.env=production means the environment, as after the name of every
    // command that runs the build, and is told that the tool runs for another one.
    private void warnOfEnvironment(PrintWriter said) {
        if (arguments.any { String argument -> argument.startsWith(ENVIRONMENT_OPTION) }) {
            said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + GradleRunner.line("${ENVIRONMENT_OPTION}... after the name of ${entry.name} is given " +
                    "to the tool as an argument, and the tool runs for the environment ${ENVIRONMENT}. To run it for another environment, name it before the tool: " +
                    UsageHelp.toType(spec, "${RUN_TOOL} ${ENVIRONMENT_OPTION}production ${entry.name}"))))
        }
    }

    // Runs a tool that is a process of its own, without Gradle: with the class path the build wrote
    // down, after the build has brought it up to date when something it is made from was changed. The
    // exit code of the tool, or null when the build is to run the tool as it runs any other.
    private Integer standalone(ConsoleProject project, List<String> vector) {
        PrintWriter said = spec.commandLine().getErr()
        boolean windows = getOperatingSystem() == OperatingSystem.WINDOWS
        String name = entry.name
        String byTheBuild = "The tool was not run; the build runs it with: ${UsageHelp.toType(spec, "${RUN_TOOL} ${name}")}"
        StandaloneToolsFile file = StandaloneToolsFile.read(project, ToolCatalogFile.read(project.getDirectory()), name)
        if (file.state == StandaloneToolsFile.State.OUT_OF_DATE) {
            said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | Building|@ the standalone tools of the project before ${name} is run, " +
                    "with the Gradle task ${StandaloneToolsFile.TASK}: ${file.reason}"))
            said.flush()
            if (build(project, windows) != 0) {
                err("The standalone tools of the project could not be built, so the tool ${name} was not run")
                return 1
            }
            ToolCatalogFile catalog = ToolCatalogFile.read(project.getDirectory())
            ToolCatalogFile.Entry listed = catalog.tools.find { ToolCatalogFile.Entry each -> each.name == name }
            if (listed == null) {
                err("The build of the project no longer lists a tool ${name}. Nothing was run")
                return 1
            }
            if (!listed.isStandalone()) {
                said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | The tool|@ ${name} is no longer one that runs without Gradle, as the build of the project lists it now"))
                return null
            }
            file = StandaloneToolsFile.read(project, catalog, name)
            if (file.state == StandaloneToolsFile.State.OUT_OF_DATE) {
                err("The build of the project did not write down a class path for the tool ${name}: ${file.reason}. ${byTheBuild}")
                return 1
            }
        }
        if (file.state == StandaloneToolsFile.State.REFUSED) {
            err("The tool ${name} is not started as a process of its own: ${file.reason}. ${byTheBuild}")
            return 1
        }
        if (file.state == StandaloneToolsFile.State.THROUGH_GRADLE) {
            said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | The tool|@ ${name} is run with Gradle: ${file.reason}"))
            said.flush()
            return null
        }
        StandaloneToolProcess.Java java = StandaloneToolProcess.java(windows, file.java)
        if (java.refusal != null) {
            err("${java.refusal}. ${byTheBuild}")
            return 1
        }
        CommandArgumentsFile written
        try {
            written = CommandArgumentsFile.write(project, vector)
        } catch (IOException e) {
            err("The arguments of the tool could not be written: ${GradleRunner.line(e.getMessage())}. The tool was not run")
            return 1
        }
        try {
            said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | Running|@ the tool ${name} of " +
                    "${entry.origin == ToolCatalogFile.APPLICATION ? 'the application' : entry.origin}, as a process of its own, without Gradle"))
            warnOfDeprecations(said, entry)
            warnOfEnvironment(said)
            said.flush()
            return StandaloneToolProcess.run(project, java.program, file.classpath, written, showStacktrace(), windows, { String message -> err(message) } as Consumer<String>)
        } finally {
            written.close()
        }
    }

    // has the build write down the class path of the tools again, which compiles what they are made from; the input of the build has ended
    private int build(ConsoleProject project, boolean windows) {
        GradleBuild runner = GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> err(message) } as Consumer<String>, windows)
        boolean nonInteractive = GradleBuild.isNonInteractive()
        GradleBuild.setNonInteractive(true)
        try {
            return runner.run(project, [project.task(StandaloneToolsFile.TASK)])
        } catch (GradleRunner.RefusedArgumentException e) {
            err(e.getMessage())
            return 1
        } finally {
            GradleBuild.setNonInteractive(nonInteractive)
        }
    }

    // What Gradle is run with. For a tool of the application it is what run-tool gives Gradle
    // for the same tool, argument for argument, so that the two are one build to Gradle and to its
    // configuration cache: the task, the file, the environment run-tool has by default, and what
    // --stacktrace in front of the tool stands for.
    private List<String> gradleArguments(ConsoleProject project, CommandArgumentsFile file) {
        List<String> all = [project.task(task()), file.option]
        if (!entry.isScript()) {
            all.add("-Dgrails.env=${ENVIRONMENT}".toString())
        }
        if (showStacktrace()) {
            all.add('--stacktrace')
            all.add('-Dgrails.full.stacktrace=true')
            // SCRIPT HOST: what tells the task that runs a script to show the full stack trace of the script goes here, for entry.isScript()
        }
        return all
    }

    /**
     * @param name the name of a tool of the application, held to what can be shown
     * @param origin where the build says it comes from, or {@code null} when the build has not listed it
     * @return what is said to be run, by the name of a tool and by {@code run-tool} alike
     */
    static String running(String name, String origin) {
        String from = origin == null ? ', which the build of the project has not listed' :
                " of ${origin == ToolCatalogFile.APPLICATION ? 'the application' : origin}"
        return "the tool ${name}${from}, with the Gradle task ${TASK}"
    }

    /**
     * Gives the tool every argument that follows its name, before the command line is read for
     * options: an argument that looks like an option, and {@code --} too, is the tool's.
     */
    static class EverythingIsTheTools implements CommandLine.IParameterPreprocessor {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            ToolCommand command = (ToolCommand) commandSpec.userObject()
            List<String> given = []
            while (!args.isEmpty()) {
                given.add(args.pop())
            }
            command.arguments = given
            return true
        }
    }
}

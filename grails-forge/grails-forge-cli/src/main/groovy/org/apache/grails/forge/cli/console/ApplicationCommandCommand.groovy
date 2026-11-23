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
 * A command of the application or of one of its plugins, as a command of the CLI: one for each
 * command the build of the project lists, under the name the build gives. The command itself runs in
 * the application, started by the Gradle task {@code runCommand} of the project; one the build lists
 * with the target {@code standalone} is started by the same task and runs without the application. A script of the
 * application is such a command too: it is run by the build as well, by the Gradle task
 * {@code runScriptCommand}, and never by the CLI.
 *
 * <p>Everything after the name of the command is the command's and is given to it as the list of
 * arguments it is, one for one: the command of the CLI has no options of its own. The arguments are
 * handed to Gradle in a file only the user can read, see {@link CommandArgumentsFile}, so that none
 * of them is on a command line. Before Gradle is started one line on the error stream says which
 * command of which origin is run.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'application-command', preprocessor = ApplicationCommandCommand.EverythingIsTheCommands)
@CompileStatic
class ApplicationCommandCommand extends InProjectCommand {

    /** The Gradle task that runs a command in the application. */
    public static final String TASK = 'runCommand'

    /** The environment a command of the application runs in, which is the one {@code run-command} has unless it is given another. */
    public static final String ENVIRONMENT = 'development'

    /** What names the environment of the build, which a command of the application takes before its name with run-command and not after it. */
    public static final String ENVIRONMENT_OPTION = '-Dgrails.env='

    /** What is said of every script: scripts are kept for the applications that have them. */
    public static final String DEPRECATED = 'Script commands are deprecated. Write a new command as a class of the application: create-command'

    @CommandLine.Parameters(arity = '0..*', paramLabel = 'ARGS', description = 'The arguments of the command, which are given to it as they are.')
    List<String> arguments = []

    private final ApplicationCommandFile.Entry entry

    ApplicationCommandCommand(ApplicationCommandFile.Entry entry) {
        this.entry = entry
    }

    /**
     * @param entry a command the build of the project lists
     * @param factory the factory of the command line the command is added to
     * @return the command as picocli has it, under the names the build gives
     */
    static CommandLine.Model.CommandSpec specOf(ApplicationCommandFile.Entry entry, CommandLine.IFactory factory) {
        CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.forAnnotatedObject(new ApplicationCommandCommand(entry), factory)
        // the options every command of the CLI has would be taken from what is meant for the command
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
            description.add(("A script of the application, in ${ScriptCommandFiles.LOCATION}. It is run by the build of the project and not by this program: " +
                    "it is started by the Gradle task ${ScriptCommandFiles.TASK} of the project.").toString())
            description.add(DEPRECATED)
        } else {
            // where a command comes from is the name of a jar or of a project: text, as the description is
            description.add(("A command of ${entry.origin == ApplicationCommandFile.APPLICATION ? 'the application' : shown(entry.origin).replace('%', '%%')}, " +
                    (entry.isStandalone() ? 'which runs without the application' : 'which runs in the application') +
                    ": it is started by the Gradle task ${TASK} of the project.").toString())
        }
        description.add('Everything after the name of the command is given to the command as it is, the options too.')
        spec.usageMessage().description(description as String[])
        return spec
    }

    /**
     * @param description the description of a command, which is one line
     * @return the description without what the command line would read as its markup
     */
    static String shown(String description) {
        return description.replace('@|', '@ |')
    }

    /**
     * @return the command of the build this command of the CLI runs
     */
    ApplicationCommandFile.Entry getEntry() {
        return entry
    }

    /**
     * @return the command as the document for a tool describes it: with what the build says of it,
     * and without options and parameters, which the build does not know
     */
    Map<String, Object> describe() {
        Map<String, Object> described = new LinkedHashMap<>()
        described.put('name', entry.name)
        described.put('aliases', entry.aliases)
        described.put('description', entry.description)
        described.put('project', true)
        described.put('origin', entry.origin)
        described.put('target', entry.isScript() ? ScriptCommandFiles.TARGET : entry.target)
        described.put('registration', entry.registration)
        described.put('commands', [])
        return described
    }

    @Override
    protected String notDone() {
        return 'The command was not run'
    }

    /**
     * @return the Gradle task of the project that runs the command
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
            throw new CommandLine.ParameterException(spec.commandLine(), "The command cannot be given these arguments: ${refused}")
        }
        CommandArgumentsFile file
        try {
            file = CommandArgumentsFile.write(project, vector)
        } catch (IOException e) {
            err("The arguments of the command could not be written for Gradle: ${GradleRunner.line(e.getMessage())}. Gradle was not run")
            return 1
        }
        try {
            // On the error stream, since what the command writes is the output: what is typed can be
            // the name of a command of a plugin by one letter, and this says what runs before it does.
            PrintWriter said = spec.commandLine().getErr()
            said.println(CommandLine.Help.Ansi.AUTO.string("@|bold | Running|@ ${entry.isScript() ? "the script ${entry.name} of ${ScriptCommandFiles.LOCATION}, with the Gradle task ${task()}" : running(entry.name, entry.origin)}"))
            if (entry.isScript()) {
                said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + DEPRECATED))
            } else if (arguments.any { String argument -> argument.startsWith(ENVIRONMENT_OPTION) }) {
                // Every argument after the name of a command is the command's, and so is this one. Who types
                // grails dbm-update -Dgrails.env=production means the environment, as after the name of every
                // command that runs the build, and is told that the command runs for another one.
                said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + GradleRunner.line("${ENVIRONMENT_OPTION}... after the name of ${entry.name} is given " +
                        "to the command as an argument, and the command runs for the environment ${ENVIRONMENT}. To run it for another environment, name it before the command: " +
                        UsageHelp.toType(spec, "run-command ${ENVIRONMENT_OPTION}production ${entry.name}"))))
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

    // What Gradle is run with. For a command of the application it is what run-command gives Gradle
    // for the same command, argument for argument, so that the two are one build to Gradle and to its
    // configuration cache: the task, the file, the environment run-command has by default, and what
    // --stacktrace in front of the command stands for.
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
     * @param name the name of a command of the application, held to what can be shown
     * @param origin where the build says it comes from, or {@code null} when the build has not listed it
     * @return what is said to be run, by the name of a command and by {@code run-command} alike
     */
    static String running(String name, String origin) {
        String from = origin == null ? ', which the build of the project has not listed' :
                " of ${origin == ApplicationCommandFile.APPLICATION ? 'the application' : origin}"
        return "the command ${name}${from}, with the Gradle task ${TASK}"
    }

    /**
     * Gives the command every argument that follows its name, before the command line is read for
     * options: an argument that looks like an option, and {@code --} too, is the command's.
     */
    static class EverythingIsTheCommands implements CommandLine.IParameterPreprocessor {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            ApplicationCommandCommand command = (ApplicationCommandCommand) commandSpec.userObject()
            List<String> given = []
            while (!args.isEmpty()) {
                given.add(args.pop())
            }
            command.arguments = given
            return true
        }
    }
}

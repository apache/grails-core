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

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Consumer
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.grails.forge.application.OperatingSystem
import picocli.CommandLine

/**
 * A command of the application or of one of its plugins, as a command of the CLI: one for each
 * command the build of the project lists, under the name the build gives. The command itself runs in
 * the application, started by the Gradle task {@code runCommand} of the project. A script of the
 * application is such a command too: it is run by the build as well, by the Gradle task
 * {@code runScriptCommand}, and never by the CLI.
 *
 * <p>Everything after the name of the command is the command's and is given to it as the list of
 * arguments it is, one for one: the command of the CLI has no options of its own. The arguments are
 * handed to Gradle in a file only the user can read, so that none of them is on a command line.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'application-command', preprocessor = ApplicationCommandCommand.EverythingIsTheCommands)
@CompileStatic
class ApplicationCommandCommand extends InProjectCommand {

    /** How many arguments the Gradle task takes, the name of the command among them. */
    public static final int MAX_ARGUMENTS = 4096

    /** How many bytes the file of the arguments has, at most, for the Gradle task to read it. */
    public static final int MAX_BYTES = 1024 * 1024

    /** The Gradle task that runs a command in the application. */
    public static final String TASK = 'runCommand'

    /** What is said of every script: scripts are kept for the applications that have them. */
    public static final String DEPRECATED = 'Script commands are deprecated. Write a new command as a class of the application: create-command'

    private static final String DIRECTORY_PREFIX = 'grails-command'
    private static final String FILE = 'arguments.json'

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
            description.add(("A command of ${entry.origin == ApplicationCommandFile.APPLICATION ? 'the application' : entry.origin}, " +
                    "which runs in the application: it is started by the Gradle task ${TASK} of the project.").toString())
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
        described.put('target', entry.isScript() ? ScriptCommandFiles.TARGET : ApplicationCommandFile.APPLICATION)
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
        String refused = refusal(vector)
        byte[] bytes = refused == null ? JsonOutput.toJson(vector).getBytes(StandardCharsets.UTF_8) : null
        if (refused == null && bytes.length > MAX_BYTES) {
            refused = "The arguments of ${entry.name} are more than the ${MAX_BYTES} bytes the Gradle task ${task()} takes"
        }
        if (refused != null) {
            throw new CommandLine.ParameterException(spec.commandLine(), refused)
        }
        Path directory
        Path file
        try {
            directory = privateDirectory()
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            err("No directory could be created for the file that gives Gradle the arguments of ${entry.name}: ${GradleRunner.line(e.getMessage())}. The command was not run")
            return 1
        }
        // when this JVM is ended while Gradle runs, the arguments are not left behind
        Thread whenEnded = new Thread({ delete(directory) } as Runnable, 'grails-command-arguments')
        Runtime.getRuntime().addShutdownHook(whenEnded)
        try {
            try {
                file = privateFile(directory)
                Files.write(file, bytes)
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                err("The file that gives Gradle the arguments of ${entry.name} could not be written: ${GradleRunner.line(e.getMessage())}. The command was not run")
                return 1
            }
            GradleRunner runner = new GradleRunner({ String message -> err(message) } as Consumer<String>, getOperatingSystem() == OperatingSystem.WINDOWS)
            if (entry.isScript()) {
                // on the error stream: what the script writes is the output of the command
                spec.commandLine().getErr().println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + DEPRECATED))
            }
            return runner.run(project, [task(), '--args-file=' + file])
        } catch (GradleRunner.RefusedArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        } finally {
            delete(directory)
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // what the Gradle task refuses is refused here, before a build is started for it, and without repeating the argument
    private String refusal(List<String> vector) {
        if (vector.size() > MAX_ARGUMENTS) {
            return "${entry.name} was given more than the ${MAX_ARGUMENTS - 1} arguments the Gradle task ${task()} takes"
        }
        for (int i = 1; i < vector.size(); i++) {
            String argument = vector.get(i)
            for (int at = 0; at < argument.length(); at++) {
                char character = argument.charAt(at)
                boolean half = Character.isHighSurrogate(character) ? at + 1 == argument.length() || !Character.isLowSurrogate(argument.charAt(at + 1)) :
                        Character.isLowSurrogate(character) && (at == 0 || !Character.isHighSurrogate(argument.charAt(at - 1)))
                if (character == (0 as char) || half) {
                    return "Argument ${i} of ${entry.name} has a character that cannot be given to a command: the character U+0000, or half of a character of two parts"
                }
            }
        }
        return null
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains('posix')
    }

    // a directory of the user's own, where no one else can put or read a file
    private static Path privateDirectory() throws IOException {
        return isPosix() ? Files.createTempDirectory(DIRECTORY_PREFIX, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rwx------'))) :
                Files.createTempDirectory(DIRECTORY_PREFIX)
    }

    private static Path privateFile(Path directory) throws IOException {
        Path file = directory.resolve(FILE)
        return isPosix() ? Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rw-------'))) : Files.createFile(file)
    }

    private static void delete(Path directory) {
        try {
            Files.deleteIfExists(directory.resolve(FILE))
            Files.deleteIfExists(directory)
        } catch (IOException | SecurityException ignored) {
            // what is left is in a directory only the user can read
        }
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

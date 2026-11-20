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
package org.grails.forge.cli.command

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.grails.forge.application.OperatingSystem
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * Runs Gradle in the project the current directory is in, with the Gradle wrapper of the project.
 * Everything after the name of the command is Gradle's and is passed on as the list of arguments it
 * is, one for one: the command has no options of its own, so that {@code -x}, {@code -v} or
 * {@code --help} mean what they mean to Gradle.
 *
 * @since 9.0
 */
@CommandLine.Command(name = GradleCommand.NAME,
        description = [
                'Runs Gradle in the Grails project of the current directory, with the Gradle wrapper of the project.',
                'Everything after @|bold gradle|@ is given to Gradle as it is, the options too.'
        ],
        preprocessor = GradleCommand.EverythingIsGradles)
@CompileStatic
class GradleCommand extends BaseCommand implements Callable<Integer> {

    public static final String NAME = 'gradle'

    // what cmd.exe reads as its own in the command line of a batch file
    private static final String NOT_FOR_A_BATCH_FILE = '"%&|<>^'
    private static final int STOP_SECONDS = 10
    private static final int OUTPUT_SECONDS = 5

    @CommandLine.Parameters(arity = '1..*', paramLabel = 'ARGS', description = 'The tasks to run and the options of Gradle, for example: build --offline')
    List<String> arguments = []

    /**
     * Takes the options every command of the CLI has out of this one. They would be taken from what
     * is meant for Gradle, where {@code -x} leaves a task out and {@code -v} asks for the version.
     */
    @CommandLine.Spec
    void setGradleSpec(CommandLine.Model.CommandSpec commandSpec) {
        for (CommandLine.Model.OptionSpec option : new ArrayList<CommandLine.Model.OptionSpec>(commandSpec.options())) {
            commandSpec.remove(option)
        }
    }

    @Override
    Integer call() throws Exception {
        if (arguments.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), 'Say what Gradle is to do: the tasks to run or an option of Gradle, as in gradle build')
        }
        boolean windows = getOperatingSystem() == OperatingSystem.WINDOWS
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject project = ConsoleProject.find(current)
        if (project == null) {
            err("There is no Grails project here: neither ${line(current)} nor a directory above it in the same repository has a grails-app directory " +
                    'or the record of a generated project. Gradle was not run')
            return 1
        }
        Path wrapper = project.findGradleWrapper(windows)
        if (wrapper == null) {
            err("The project in ${line(project.directory)} has no Gradle wrapper: there is no ${ConsoleProject.wrapperName(windows)} in it or in a directory above it " +
                    'in the same repository. Gradle was not run, since the command runs the Gradle of the project and no other')
            return 1
        }
        if (windows) {
            String refused = arguments.find { String argument -> notForABatchFile(argument) }
            if (refused != null) {
                throw new CommandLine.ParameterException(spec.commandLine(), "The argument ${FeatureSelectionException.shown(refused)} cannot be given to Gradle from here: " +
                        "the Gradle wrapper of Windows is a batch file, which would not get an argument with one of ${NOT_FOR_A_BATCH_FILE} or a control character as it is. " +
                        'Run gradlew.bat with it yourself')
            }
        }
        return run(command(wrapper, windows), wrapper, project.directory, windows)
    }

    private static boolean notForABatchFile(String argument) {
        for (int i = 0; i < argument.length(); i++) {
            char character = argument.charAt(i)
            if (character < (char) ' ' || NOT_FOR_A_BATCH_FILE.indexOf((int) character) > -1) {
                return true
            }
        }
        return false
    }

    private List<String> command(Path wrapper, boolean windows) {
        List<String> command = []
        // a wrapper that lost its executable bit, as in a project unpacked from an archive, is still the script it was
        if (!windows && !Files.isExecutable(wrapper)) {
            command.add('sh')
        }
        command.add(wrapper.toString())
        command.addAll(arguments)
        return command
    }

    private int run(List<String> command, Path wrapper, Path directory, boolean windows) {
        Process process
        try {
            process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start()
        } catch (IOException e) {
            err("The Gradle wrapper ${line(wrapper)} could not be started: ${line(e.getMessage())}")
            return 1
        }
        // when this JVM is ended while Gradle runs, Gradle is not left running without it
        Thread whenEnded = new Thread({ stop(process, windows) } as Runnable, 'grails-gradle-stop')
        Runtime.getRuntime().addShutdownHook(whenEnded)
        Thread output = copy(process.getInputStream(), spec.commandLine().getOut())
        Thread errors = copy(process.getErrorStream(), spec.commandLine().getErr())
        try {
            int exit = process.waitFor()
            // a Gradle daemon that was started keeps neither stream open; the wait is bounded all the same
            output.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            errors.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            if (exit != 0) {
                err("Gradle did not succeed: it ended with exit code ${exit}")
                return 1
            }
            return 0
        } catch (InterruptedException ignored) {
            stop(process, windows)
            err('Gradle was stopped before it finished, because the command was cancelled')
            Thread.currentThread().interrupt()
            return 1
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // Ends the Gradle client, which makes a Gradle daemon cancel the build it runs for the client. The
    // daemon is not ended from here: it serves other builds too. On Windows the process is the command
    // interpreter of the batch file, and the client is its child.
    private static void stop(Process process, boolean windows) {
        List<ProcessHandle> clients = windows ? process.children().toList() : Collections.<ProcessHandle>emptyList()
        process.destroy()
        clients.each { ProcessHandle client -> client.destroy() }
        boolean ended
        try {
            ended = process.waitFor(STOP_SECONDS, TimeUnit.SECONDS)
        } catch (InterruptedException ignored) {
            ended = false
        }
        if (!ended) {
            process.destroyForcibly()
            clients.each { ProcessHandle client -> client.destroyForcibly() }
        }
    }

    // what Gradle writes is written as it comes, not when a line or the build is complete
    private static Thread copy(InputStream from, PrintWriter to) {
        Thread thread = new Thread({
            try {
                Reader reader = new InputStreamReader(from, outputCharset())
                char[] buffer = new char[4096]
                int count
                while ((count = reader.read(buffer)) != -1) {
                    to.write(buffer, 0, count)
                    to.flush()
                }
            } catch (IOException ignored) {
                // the process ended, or was ended
            }
        } as Runnable, 'grails-gradle-output')
        thread.setDaemon(true)
        thread.start()
        return thread
    }

    private static Charset outputCharset() {
        String encoding = System.getProperty('native.encoding')
        try {
            return encoding == null ? Charset.defaultCharset() : Charset.forName(encoding)
        } catch (IllegalArgumentException ignored) {
            return Charset.defaultCharset()
        }
    }

    // a path or a message of the system in a message of the command: one line, and no markup of the command line
    private static String line(Object text) {
        return String.valueOf(text).replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?').replace('@|', '@ |')
    }

    /**
     * Gives the command every argument that follows its name, before the command line is read for
     * options: an argument that looks like an option, and {@code --} too, is Gradle's.
     */
    static class EverythingIsGradles implements CommandLine.IParameterPreprocessor {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            GradleCommand command = (GradleCommand) commandSpec.userObject()
            List<String> given = []
            while (!args.isEmpty()) {
                given.add(args.pop())
            }
            command.arguments = given
            return true
        }
    }
}

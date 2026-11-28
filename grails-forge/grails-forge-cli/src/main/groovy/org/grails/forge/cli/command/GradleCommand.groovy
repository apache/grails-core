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

import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProjectCommand
import org.grails.forge.application.OperatingSystem
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
                'Everything after @|bold gradle|@ is given to Gradle as it is, the options too.',
                'So @|bold gradle --help|@ is the help of Gradle, and this text is what @|bold help gradle|@ shows.'
        ],
        preprocessor = GradleCommand.EverythingIsGradles)
@CompileStatic
class GradleCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'gradle'

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
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        // Gradle is run in the current directory, so a directory of a Gradle build that is in no Grails
        // project, such as the root of a build with several, is one it can be run in as well
        ConsoleProject project = search.project ?: (search.untrusted == null ? ConsoleProject.build(current) : null)
        if (project == null) {
            err(search.none(NAME))
            return 1
        }
        if (arguments.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), 'Say what Gradle is to do: the tasks to run or an option of Gradle, as in gradle build')
        }
        // what follows the name of the command is Gradle's to read, so Gradle is run as it is on a command line of its own
        GradleBuild runner = GradleBuild.of(GradleBuild.Purpose.PASSED_ON, { String message -> err(message) } as Consumer<String>, getOperatingSystem() == OperatingSystem.WINDOWS)
        try {
            return runner.run(project, arguments)
        } catch (GradleRunner.RefusedArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
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

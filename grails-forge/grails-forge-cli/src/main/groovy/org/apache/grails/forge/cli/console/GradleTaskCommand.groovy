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
 * A command that is a Gradle build of the project: it names the tasks and turns its own options into
 * arguments of Gradle, one argument each. What follows {@code --} on the command line is not read by
 * the command and is given to Gradle after everything else, as it is.
 *
 * @since 9.0
 */
@CompileStatic
abstract class GradleTaskCommand extends InProjectCommand {

    /** What followed {@code --} on the command line. */
    List<String> passedOn = []

    @Override
    protected String notDone() {
        return 'Gradle was not run'
    }

    @Override
    protected int run(ConsoleProject project) {
        List<String> arguments = gradleArguments(project)
        if (arguments == null) {
            return 1
        }
        arguments = new ArrayList<String>(arguments)
        if (showStacktrace()) {
            arguments.add('--stacktrace')
            arguments.add('-Dgrails.full.stacktrace=true')
        }
        if (verbose()) {
            arguments.addAll(verboseArguments())
        }
        arguments.addAll(passedOn)
        GradleRunner runner = new GradleRunner({ String message -> err(message) } as Consumer<String>, getOperatingSystem() == OperatingSystem.WINDOWS)
        int exit
        try {
            exit = runner.run(project, arguments)
        } catch (GradleRunner.RefusedArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
        if (exit == 0) {
            succeeded(project)
        }
        return exit
    }

    /**
     * @param project the project Gradle is run in
     * @return the tasks and the arguments the options of the command stand for; {@code null} when the
     * command cannot be run in this project, which the command has then said
     */
    protected abstract List<String> gradleArguments(ConsoleProject project)

    /**
     * @return what {@code --verbose} of the command is for Gradle
     */
    protected List<String> verboseArguments() {
        return ['--debug']
    }

    /**
     * Called when Gradle ended with success, for what the command says then.
     */
    protected void succeeded(ConsoleProject project) {
    }

    /**
     * Takes {@code --} and what follows it from the command line before the command reads it: that
     * part is Gradle's, also where it looks like an option of the command.
     */
    static class PassedOn implements CommandLine.IParameterPreprocessor {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            // the next argument is the top of the stack, so the first -- is the one highest up
            int at = args.lastIndexOf('--')
            if (at > -1) {
                List<String> after = []
                for (int i = at - 1; i >= 0; i--) {
                    after.add(args.get(i))
                }
                args.subList(0, at + 1).clear()
                ((GradleTaskCommand) commandSpec.userObject()).passedOn = after
            }
            GradleEnvironmentCommand.checkSystemProperties(args, commandSpec)
            return false
        }
    }
}

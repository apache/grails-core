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

import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * {@code console}: the Gradle task {@code console} of the project, the Swing console of the
 * application. The command stays with it until the console is closed.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'console',
        description = [
                'Runs the Swing console of the Grails application: the Gradle task console.',
                'Stays with the console until it is closed. The environment is development unless -Dgrails.env names another one.',
                'What follows @|bold --|@ is given to Gradle as it is.'
        ],
        customSynopsis = ['${COMMAND-FULL-NAME} [-hvVx] [--non-interactive] [--plain-output]',
                '        [--debug-jvm] [-DNAME=VALUE]... [-- GRADLE-ARGUMENT...]'],
        preprocessor = GradleTaskCommand.PassedOn)
@CompileStatic
class ConsoleCommand extends GradleEnvironmentCommand {

    @CommandLine.Option(names = ['--debug-jvm'], description = 'Start the JVM of the console suspended, waiting for a debugger on port 5005.')
    boolean debugJvm

    // the option as the shell spells it, with one dash: taken, and not shown
    @CommandLine.Option(names = ['-debug-jvm'], hidden = true)
    boolean debugJvmOfShell

    @Override
    Integer call() throws Exception {
        if (nonInteractive()) {
            throw new CommandLine.ParameterException(spec.commandLine(), 'console opens a window and stays with it until it is closed, which --non-interactive rules out: ' +
                    'run it without the option, or use run-script for a script')
        }
        return super.call()
    }

    @Override
    protected String defaultEnvironment() {
        return 'development'
    }

    @Override
    protected List<String> verboseArguments() {
        // --verbose shows what the build says while the console starts, which is otherwise left out
        return []
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        List<String> arguments = [project.task('console')]
        if (debugJvm || debugJvmOfShell) {
            arguments.add('--debug-jvm')
        }
        if (!verbose()) {
            arguments.add('--quiet')
        }
        arguments.addAll(systemPropertyArguments())
        return arguments
    }
}

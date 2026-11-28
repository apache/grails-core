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
 * {@code assemble}, also {@code war} and {@code package}: the Gradle task {@code assemble} of the
 * project, for the production environment unless another one is given.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'assemble', aliases = ['war', 'package'],
        description = [
                'Creates the JAR or WAR archive of the Grails project for deployment.',
                'Runs the Gradle task assemble, for the production environment unless -Dgrails.env names another one.',
                'What follows @|bold --|@ is given to Gradle as it is.'
        ],
        preprocessor = GradleTaskCommand.PassedOn)
@CompileStatic
class AssembleCommand extends GradleEnvironmentCommand {

    @CommandLine.Option(names = ['--clean', '-clean'], description = 'Run the Gradle task clean before the archive is created.')
    boolean clean

    @Override
    protected String defaultEnvironment() {
        return 'production'
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        List<String> arguments = []
        if (clean) {
            arguments.add(project.task('clean'))
        }
        arguments.add(project.task('assemble'))
        arguments.addAll(systemPropertyArguments())
        return arguments
    }

    @Override
    protected void succeeded(ConsoleProject project) {
        out("| Built application to build/libs using environment: ${GradleRunner.line(environment())}")
    }
}

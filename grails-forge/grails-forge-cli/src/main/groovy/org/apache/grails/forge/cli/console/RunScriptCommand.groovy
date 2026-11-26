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

import java.nio.file.Files
import java.nio.file.InvalidPathException
import groovy.transform.CompileStatic
import org.grails.forge.feature.FeatureSelectionException
import picocli.CommandLine

/**
 * {@code run-script}: the Gradle task {@code runScript} of the project, which runs Groovy scripts
 * with the application started around them. The command is deprecated: it runs as it did, after one
 * warning line that names what takes its place, a tool of the application run by its name.
 *
 * <p>The task takes the files as one text, the project property {@code args}, and splits it at
 * spaces outside quotes. The command writes that text so that it is split into the files that were
 * given: each one is put between quotes of the kind it does not have, piece by piece where it has
 * both, which the task reads as one argument.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'run-script',
        description = [
                'Runs Groovy scripts in the Grails application: the Gradle task runScript.',
                'The application is started around the scripts, without a web server.',
                'The environment is development unless -Dgrails.env names another one.',
                'What follows @|bold --|@ is given to Gradle as it is.',
                'run-script is deprecated: write a tool of the application with create-tool, and run it by its name.'
        ],
        customSynopsis = ['${COMMAND-FULL-NAME} [-hvVx] [--non-interactive] [--plain-output]',
                '        [-DNAME=VALUE]... FILE... [-- GRADLE-ARGUMENT...]'],
        preprocessor = GradleTaskCommand.PassedOn)
@CompileStatic
class RunScriptCommand extends GradleEnvironmentCommand {

    @CommandLine.Parameters(arity = '1..*', paramLabel = 'FILE', description = 'A script to run: a file, by its path in the project or its full path. The scripts are run in the order given.')
    List<String> files = []

    @Override
    protected String defaultEnvironment() {
        return 'development'
    }

    /**
     * @param spec the command, for how the command line is typed where it runs
     * @return what is said, after a warning mark, each time the command runs
     */
    static String deprecation(CommandLine.Model.CommandSpec spec) {
        return "run-script is deprecated: write a tool (${UsageHelp.toType(spec, 'create-tool NAME')}) and run it by its name".toString()
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        PrintWriter said = spec.commandLine().getErr()
        said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + deprecation(spec)))
        said.flush()
        for (String file : files) {
            if (file.isEmpty() || file.indexOf(0) > -1) {
                throw new CommandLine.ParameterException(spec.commandLine(), "${FeatureSelectionException.shown(file)} is not the name of a file")
            }
            boolean there
            try {
                there = Files.isRegularFile(project.directory.resolve(file))
            } catch (InvalidPathException ignored) {
                there = false
            }
            if (!there) {
                err("There is no script ${FeatureSelectionException.shown(file)} in ${GradleRunner.line(project.directory)}: a script is named by its path in the project. Gradle was not run")
                return null
            }
        }
        List<String> arguments = [project.task('runScript'), '-Pargs=' + files.collect { String file -> quoted(file) }.join(' ')]
        arguments.addAll(systemPropertyArguments())
        return arguments
    }

    /**
     * @param argument an argument that is not empty
     * @return the argument as the task reads it back from the text it splits: as it is where nothing
     * in it splits or quotes, and otherwise between quotes
     */
    static String quoted(String argument) {
        if (!argument.contains(' ') && !argument.contains('"') && !argument.contains('\'')) {
            return argument
        }
        if (!argument.contains('\'')) {
            return "'${argument}'".toString()
        }
        // pieces without a double quote between double quotes, and each double quote between single ones
        return argument.split('"', -1).collect { String piece -> piece.isEmpty() ? '' : "\"${piece}\"".toString() }.join('\'"\'')
    }
}

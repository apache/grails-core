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
import org.grails.forge.feature.FeatureSelectionException
import picocli.CommandLine

/**
 * {@code run-command}: the Gradle task {@code runCommand} of the project, which runs a command of
 * the application or of one of its plugins with the application started around it, or without the
 * application when the command is one that needs none: the task knows which from the command.
 *
 * <p>Everything after the name of the command is the command's, whatever it looks like, and reaches
 * it as the arguments it was: the name and the arguments are written to a file the task reads, see
 * {@link CommandArgumentsFile}, and are not on the command line of Gradle.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'run-command',
        description = [
                'Runs a command of the Grails application: the Gradle task runCommand.',
                'NAME is a command of the application or of one of its plugins, which is run with the application started around it,',
                'or without the application when the command is one that needs none.',
                'Everything after NAME is given to that command as it is, also what looks like an option of this one.',
                'The environment is development unless -Dgrails.env names another one, before NAME.'
        ],
        customSynopsis = ['${COMMAND-FULL-NAME} [-hvVx] [--non-interactive] [--plain-output]',
                '        [-DNAME=VALUE]... NAME [ARG...]'],
        preprocessor = Arguments)
@CompileStatic
class RunCommandCommand extends GradleEnvironmentCommand {

    @CommandLine.Parameters(index = '0', paramLabel = 'NAME', description = 'The name of the command to run.')
    String name

    // read by the preprocessor, and declared for the usage text
    @CommandLine.Parameters(index = '1..*', arity = '0..*', paramLabel = 'ARG', description = 'The arguments of that command.')
    List<String> declared = []

    /** What followed the name of the command on the command line. */
    List<String> commandArguments = []

    private String argumentsOption

    @Override
    protected String defaultEnvironment() {
        return 'development'
    }

    @Override
    protected int run(ConsoleProject project) {
        List<String> all = [name]
        all.addAll(commandArguments)
        String refusal = CommandArgumentsFile.refusal(all)
        if (refusal != null) {
            throw new CommandLine.ParameterException(spec.commandLine(), "The command cannot be given these arguments: ${refusal}")
        }
        CommandArgumentsFile file
        try {
            file = CommandArgumentsFile.write(project, all)
        } catch (IOException e) {
            err("The arguments of the command could not be written for Gradle: ${GradleRunner.line(e.getMessage())}. Gradle was not run")
            return 1
        }
        try {
            argumentsOption = file.option
            // as when the command is run by its name: what runs and where it comes from, before Gradle is started
            PrintWriter said = spec.commandLine().getErr()
            said.println(CommandLine.Help.Ansi.AUTO.string('@|bold | Running|@ ' +
                    ApplicationCommandCommand.running(FeatureSelectionException.shown(name), ApplicationCommands.originOf(spec.root().commandLine(), name))))
            said.flush()
            return super.run(project)
        } finally {
            file.close()
        }
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        List<String> arguments = [project.task('runCommand'), argumentsOption]
        arguments.addAll(systemPropertyArguments())
        return arguments
    }

    /**
     * Takes what follows the name of the command from the command line before this command reads it:
     * that part is the other command's, also where it looks like an option of this one or is
     * {@code --}.
     */
    static class Arguments implements CommandLine.IParameterPreprocessor {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            // the next argument is the top of the stack
            boolean options = true
            for (int i = args.size() - 1; i >= 0; i--) {
                String arg = args.get(i)
                if (options && arg == '--') {
                    options = false
                    continue
                }
                if (options && arg == '-D') {
                    // its value is the argument after it
                    i--
                    continue
                }
                if (options && arg.startsWith('-') && arg.length() > 1) {
                    continue
                }
                List<String> after = []
                for (int j = i - 1; j >= 0; j--) {
                    after.add(args.get(j))
                }
                args.subList(0, i).clear()
                ((RunCommandCommand) commandSpec.userObject()).commandArguments = after
                break
            }
            GradleEnvironmentCommand.checkSystemProperties(args, commandSpec)
            return false
        }
    }
}

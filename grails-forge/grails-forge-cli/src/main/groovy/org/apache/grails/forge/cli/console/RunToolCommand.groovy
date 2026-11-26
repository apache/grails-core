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
 * {@code run-tool}: the Gradle task {@code runTool} of the project, which runs a tool of the
 * application or of one of its plugins with the application started around it, or without the
 * application when the tool is one that needs none: the task knows which from the tool. A tool that
 * runs as a process of its own when it is typed by its name is run by the build here, without the
 * application.
 *
 * <p>{@code run-command}, the name the command had before tools were called tools, is another name
 * of it, which is deprecated: typed by that name the command runs as it does, after one warning line
 * on the error stream.</p>
 *
 * <p>Everything after the name of the tool is the tool's, whatever it looks like, and reaches
 * it as the arguments it was: the name and the arguments are written to a file the task reads, see
 * {@link CommandArgumentsFile}, and are not on the command line of Gradle.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = RunToolCommand.NAME, aliases = [RunToolCommand.DEPRECATED_NAME],
        description = [
                'Runs a tool of the Grails application by its name: the Gradle task runTool.',
                'NAME is a tool of the application or of one of its plugins, which is run with the application started around it,',
                'or without the application when the tool is one that needs none. A tool that runs as a process of its own is run by the build as well.',
                'Everything after NAME is given to that tool as it is, also what looks like an option of this one.',
                'The environment is development unless -Dgrails.env names another one, before NAME.',
                'run-command is another name of this command, which is deprecated.'
        ],
        customSynopsis = ['${COMMAND-FULL-NAME} [-hvVx] [--non-interactive] [--plain-output]',
                '        [-DNAME=VALUE]... NAME [ARG...]'],
        preprocessor = Arguments)
@CompileStatic
class RunToolCommand extends GradleEnvironmentCommand {

    /** The name of the command. */
    public static final String NAME = 'run-tool'

    /** The name the command had before, which still runs it, after a warning. */
    public static final String DEPRECATED_NAME = 'run-command'

    /** What is said when the command is typed by {@link #DEPRECATED_NAME}. */
    public static final String DEPRECATION = DEPRECATED_NAME + ' is deprecated: use ' + NAME

    @CommandLine.Parameters(index = '0', paramLabel = 'NAME', description = 'The name of the tool to run.')
    String name

    // read by the preprocessor, and declared for the usage text
    @CommandLine.Parameters(index = '1..*', arity = '0..*', paramLabel = 'ARG', description = 'The arguments of that tool.')
    List<String> declared = []

    /** What followed the name of the tool on the command line. */
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
            throw new CommandLine.ParameterException(spec.commandLine(), "The tool cannot be given these arguments: ${refusal}")
        }
        CommandArgumentsFile file
        try {
            file = CommandArgumentsFile.write(project, all)
        } catch (IOException e) {
            err("The arguments of the tool could not be written for Gradle: ${GradleRunner.line(e.getMessage())}. Gradle was not run")
            return 1
        }
        try {
            argumentsOption = file.option
            // as when the tool is run by its name: what runs and where it comes from, before Gradle is started
            PrintWriter said = spec.commandLine().getErr()
            CommandLine root = spec.root().commandLine()
            if (isTypedAsDeprecated(root)) {
                said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' + DEPRECATION))
            }
            ToolCatalogFile.Entry entry = ProjectTools.entryOf(root, name)
            said.println(CommandLine.Help.Ansi.AUTO.string('@|bold | Running|@ ' +
                    ToolCommand.running(FeatureSelectionException.shown(name), entry?.origin)))
            ToolCommand.warnOfDeprecations(said, entry)
            said.flush()
            return super.run(project)
        } finally {
            file.close()
        }
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        List<String> arguments = [project.task(ToolCommand.TASK), argumentsOption]
        arguments.addAll(systemPropertyArguments())
        return arguments
    }

    // whether the command was typed by the name it had before: the first word of the command line, as
    // it was given to be read, after the options of the command line itself, which take no value
    private static boolean isTypedAsDeprecated(CommandLine root) {
        CommandLine.ParseResult parsed = root.getParseResult()
        if (parsed == null) {
            return false
        }
        String typed = parsed.originalArgs().find { String arg -> !arg.startsWith('-') }
        return typed == DEPRECATED_NAME
    }

    /**
     * Takes what follows the name of the tool from the command line before this command reads it:
     * that part is the other tool's, also where it looks like an option of this one or is
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
                ((RunToolCommand) commandSpec.userObject()).commandArguments = after
                break
            }
            GradleEnvironmentCommand.checkSystemProperties(args, commandSpec)
            return false
        }
    }
}

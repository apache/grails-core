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

import java.util.concurrent.Callable

import groovy.transform.CompileStatic

import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.PositionalParamSpec

/**
 * A command of the project, from the command manifest the project's build writes: one of the
 * application's own commands or a command of one of its plugins. It is offered and completed like
 * the Forge CLI's own commands, and runs through the project's {@code runCommand} Gradle task.
 *
 * <p>Arguments and options the command declares are checked and completed. A command that declares
 * no arguments takes any, and one that declares no options takes any options, so a command that does
 * not describe itself still runs with whatever it is given.</p>
 */
@CompileStatic
class ProjectCommand extends BaseCommand implements Callable<Integer> {

    /** The task that runs a command of the project */
    static final String RUN_COMMAND_TASK = 'runCommand'

    private static final String PASS_THROUGH_LABEL = 'ARGS'

    final String name

    private final ProjectGradle gradle
    private final CommandSpec commandSpec

    /**
     * @param command the command, as described in the manifest
     * @param gradle runs the project's build
     */
    ProjectCommand(Map<String, Object> command, ProjectGradle gradle) {
        this.name = command.name as String
        this.gradle = gradle
        this.commandSpec = createSpec(command)
        this.spec = commandSpec
    }

    /**
     * @return the picocli model of the command, which is what the command line runs and completes
     */
    CommandSpec getCommandSpec() {
        commandSpec
    }

    @Override
    Integer call() throws Exception {
        List<String> commandArguments = [name]
        commandArguments.addAll(givenArguments())
        gradle.run([RUN_COMMAND_TASK, "-Pargs=${join(commandArguments)}".toString()])
    }

    private List<String> givenArguments() {
        CommandLine.ParseResult parsed = commandSpec.commandLine().parseResult
        List<String> given = []
        for (PositionalParamSpec positional : commandSpec.positionalParameters()) {
            if (parsed.hasMatchedPositional(positional)) {
                given.addAll(positional.stringValues())
            }
        }
        for (OptionSpec option : commandSpec.options()) {
            if (option.usageHelp() || option.versionHelp() || !parsed.hasMatchedOption(option)) {
                continue
            }
            given << (option.arity().max() == 0
                    ? option.longestName()
                    : "${option.longestName()}=${option.stringValues().first()}".toString())
        }
        given.addAll(parsed.unmatched())
        given
    }

    private CommandSpec createSpec(Map<String, Object> command) {
        CommandSpec created = CommandSpec.wrapWithoutInspection(this)
        created.name(name)
        created.addOption(OptionSpec.builder('-h', '--help')
                .usageHelp(true)
                .description('Show this help message and exit.')
                .build())
        String description = command.description as String
        if (description) {
            created.usageMessage().description(description)
        }

        List<Map<String, Object>> arguments = (command.arguments ?: []) as List<Map<String, Object>>
        arguments.eachWithIndex { Map<String, Object> argument, int index ->
            boolean required = argument.required != false
            created.addPositional(PositionalParamSpec.builder()
                    .index(String.valueOf(index))
                    .paramLabel(argument.name as String)
                    .description((argument.description ?: '') as String)
                    .arity(required ? '1' : '0..1')
                    .required(required)
                    .type(String)
                    .completionCandidates(candidates(argument))
                    .build())
        }
        if (!arguments) {
            created.addPositional(PositionalParamSpec.builder()
                    .index('0..*')
                    .paramLabel(PASS_THROUGH_LABEL)
                    .description('The arguments of the command')
                    .arity('0..*')
                    .type(String[])
                    .build())
        }

        List<Map<String, Object>> options = (command.options ?: []) as List<Map<String, Object>>
        for (Map<String, Object> option : options) {
            boolean flag = option.flag == true
            created.addOption(OptionSpec.builder("--${option.name}".toString())
                    .description((option.description ?: '') as String)
                    .arity(flag ? '0' : '1')
                    .type(flag ? boolean : String)
                    .paramLabel((option.name as String).toUpperCase(Locale.ENGLISH))
                    .completionCandidates(candidates(option))
                    .build())
        }
        if (!options) {
            created.parser().unmatchedArgumentsAllowed(true)
        }
        created
    }

    private static Iterable<String> candidates(Map<String, Object> declaration) {
        List<String> values = (declaration.values ?: []) as List<String>
        values ? values.asImmutable() : null
    }

    /**
     * Joins the arguments into the one {@code args} property {@code runCommand} splits again, quoting
     * those that contain spaces or quotes.
     */
    private static String join(List<String> arguments) {
        arguments.collect { String argument ->
            if (argument && !(argument =~ /[\s"']/)) {
                return argument
            }
            argument.contains('"') && !argument.contains("'") ? "'${argument}'" : "\"${argument}\""
        }.join(' ')
    }
}

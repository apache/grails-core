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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable

import groovy.json.JsonOutput
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
 * no arguments takes any, and one that declares no options takes any options and is given the words of
 * its command line in the order they were typed, so a command that does not describe itself runs as it
 * does through {@code runCommand}.</p>
 *
 * <p>The arguments reach {@code runCommand} in a file, so they need no quoting for the shell or the
 * wrapper script. {@code -D} sets a system property of the command, such as {@code -Dgrails.env=test}:
 * it is passed among the arguments, as {@code -Dname=value}, and {@code runCommand} starts the
 * command with it set.</p>
 */
@CompileStatic
class ProjectCommand extends BaseCommand implements Callable<Integer> {

    /** The task that runs a command of the project */
    static final String RUN_COMMAND_TASK = 'runCommand'

    /** The project property {@code runCommand} reads the arguments of the command from */
    static final String ARGS_FILE_PROPERTY = 'argsFile'

    /**
     * The project property {@code runCommand} reads a command line from, before the file's arguments: it
     * is passed empty, so a value the build sets, as in {@code gradle.properties}, adds no words
     */
    static final String ARGS_PROPERTY = 'args'

    private static final String PASS_THROUGH_LABEL = 'ARGS'

    private static final String SYSTEM_PROPERTY_OPTION = '-D'

    final String name

    private final ProjectGradle gradle
    private final CommandSpec commandSpec
    private final OptionSpec systemProperties
    private final boolean declaresOptions

    /**
     * @param command the command, as described in the manifest
     * @param gradle runs the project's build
     */
    ProjectCommand(Map<String, Object> command, ProjectGradle gradle) {
        this.name = command.name as String
        this.gradle = gradle
        this.declaresOptions = command.options as boolean
        this.systemProperties = OptionSpec.builder(SYSTEM_PROPERTY_OPTION)
                .paramLabel('<property>=<value>')
                .type(Map)
                .auxiliaryTypes(String, String)
                .description('Sets a system property of the command, such as -Dgrails.env=test to run it in the test environment.')
                .build()
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
        // only its owner can read it, where the file system allows that, since the arguments can hold a password
        Path argumentsFile = Files.createTempFile('grails-command-arguments', '.json')
        try {
            Files.writeString(argumentsFile, JsonOutput.toJson(commandArguments), StandardCharsets.UTF_8)
            return gradle.run([RUN_COMMAND_TASK, "-P${ARGS_PROPERTY}=".toString(),
                               "-P${ARGS_FILE_PROPERTY}=${argumentsFile.toAbsolutePath()}".toString()])
        }
        finally {
            Files.deleteIfExists(argumentsFile)
        }
    }

    private List<String> givenSystemProperties() {
        Map<String, String> given = (systemProperties.getValue() ?: [:]) as Map<String, String>
        given.collect { String property, String value -> "${SYSTEM_PROPERTY_OPTION}${property}=${value}".toString() }
    }

    private List<String> givenArguments() {
        CommandLine.ParseResult parsed = commandSpec.commandLine().parseResult
        if (!declaresOptions) {
            return typedArguments(parsed.originalArgs())
        }
        List<String> given = []
        for (PositionalParamSpec positional : commandSpec.positionalParameters()) {
            if (parsed.hasMatchedPositional(positional)) {
                given.addAll(positional.stringValues())
            }
        }
        for (OptionSpec option : commandSpec.options()) {
            if (option.usageHelp() || option.versionHelp() || option.is(systemProperties) || !parsed.hasMatchedOption(option)) {
                continue
            }
            given << (option.arity().max() == 0
                    ? option.longestName()
                    : "${option.longestName()}=${option.stringValues().first()}".toString())
        }
        given.addAll(parsed.unmatched())
        given.addAll(givenSystemProperties())
        given
    }

    /**
     * The words typed after the command's name: picocli cannot tell which word of an undeclared option
     * is its value, so the command gets them in their order, as it does from {@code runCommand}. Only
     * {@code -D name=value}, given as two words, is joined into the one word {@code runCommand} reads.
     */
    private List<String> typedArguments(List<String> commandLine) {
        List<String> typed = []
        List<String> afterName = commandLine.subList(commandLine.indexOf(name) + 1, commandLine.size())
        for (int i = 0; i < afterName.size(); i++) {
            String word = afterName[i]
            if (word == SYSTEM_PROPERTY_OPTION && i + 1 < afterName.size()) {
                typed << SYSTEM_PROPERTY_OPTION + afterName[++i]
            }
            else {
                typed << word
            }
        }
        typed
    }

    private CommandSpec createSpec(Map<String, Object> command) {
        CommandSpec created = CommandSpec.wrapWithoutInspection(this)
        created.name(name)
        created.addOption(OptionSpec.builder('-h', '--help')
                .usageHelp(true)
                .description('Show this help message and exit.')
                .build())
        created.addOption(systemProperties)
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
}

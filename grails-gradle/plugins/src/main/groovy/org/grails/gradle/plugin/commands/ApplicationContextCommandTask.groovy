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
package org.grails.gradle.plugin.commands

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Runs a Grails command with the application context.
 *
 * <p>The arguments of the command are given in one of three ways, never two of them at once:</p>
 * <ul>
 *   <li>{@code --arg=<argument>}, once for each argument, in their order. Each value is one argument as
 *       it is, whatever it contains. Gradle does not take an empty value.</li>
 *   <li>{@code --args-file=<file>}: a UTF-8 file with a JSON array of strings, one for each argument,
 *       for callers that are programs and for what a command line cannot carry, an empty argument
 *       among it. A path that is not absolute is in the project directory. The task reads the file
 *       when it runs and leaves it where it is.</li>
 *   <li>the project property {@code args} ({@code -Pargs="..."}), one text that is split at spaces
 *       with single and double quotes read as a shell reads them, as it always was.</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Application command tasks produce no cacheable output')
abstract class ApplicationContextCommandTask extends JavaExec {

    /** The option that gives one argument of the command. */
    public static final String ARGUMENT_OPTION = 'arg'

    /** The option that names a file with the arguments of the command. */
    public static final String ARGUMENTS_FILE_OPTION = 'args-file'

    /** The most bytes a file of arguments has. */
    public static final int MAX_ARGUMENTS_FILE_BYTES = 1024 * 1024

    /** The most arguments a file of arguments has. */
    public static final int MAX_ARGUMENTS = 4096

    private static final String NOT_AN_ARRAY = 'is not a JSON array of strings in UTF-8'

    @Inject
    ApplicationContextCommandTask() {
        mainClass.set('grails.ui.command.GrailsApplicationContextCommandRunner')
        dependsOn('classes', 'findMainClass')
        systemProperties(System.properties.findAll { it.key.toString().startsWith('grails.') } as Map<String, Object>)
        argumentsFileBase.convention(project.layout.projectDirectory)
    }

    void setCommand(String commandName) {
        args(commandName)
    }

    /**
     * The arguments given with {@code --arg}, each as it was given.
     */
    @Input
    @Optional
    @Option(option = 'arg', description = 'One argument of the command, as it is. Repeat the option for each argument, in their order.')
    abstract ListProperty<String> getArg()

    /**
     * The file given with {@code --args-file}, as it was given.
     */
    @Internal
    @Option(option = 'args-file', description = 'A UTF-8 file with the arguments of the command as a JSON array of strings. A relative path is in the project directory.')
    abstract Property<String> getArgsFile()

    /**
     * The directory a path given with {@code --args-file} is in when it is not absolute: the project directory.
     */
    @Internal
    abstract DirectoryProperty getArgumentsFileBase()

    /**
     * Puts together what the command is run with: the arguments of the project property {@code args},
     * or those of {@code --arg} or {@code --args-file}, each of those the string it was given as.
     *
     * @param propertyArguments the arguments read from the project property {@code args}; empty when it is not set
     * @return the arguments of the command
     * @throws GradleException when arguments were given in more than one way, or when the file cannot be used
     */
    List<String> commandArguments(List<String> propertyArguments) {
        List<String> given = arg.getOrElse([])
        boolean file = argsFile.isPresent()
        int ways = (propertyArguments ? 1 : 0) + (given ? 1 : 0) + (file ? 1 : 0)
        if (ways > 1) {
            throw new GradleException("Task '${name}' was given arguments in more than one way. " +
                    "Use one of --${ARGUMENT_OPTION}, --${ARGUMENTS_FILE_OPTION} and the project property args (-Pargs).")
        }
        if (file) {
            return readArgumentsFile(argumentsFileBase.file(argsFile.get()).get().asFile)
        }
        given ? new ArrayList<String>(given) : propertyArguments
    }

    private List<String> readArgumentsFile(File file) {
        String what = "The file given to task '${name}' with --${ARGUMENTS_FILE_OPTION}"
        if (!file.isFile()) {
            throw new GradleException("${what} does not exist or is not a file.")
        }
        if (file.length() > MAX_ARGUMENTS_FILE_BYTES) {
            throw new GradleException("${what} has more than ${MAX_ARGUMENTS_FILE_BYTES} bytes.")
        }
        Object parsed
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.bytes)).toString()
            parsed = new JsonSlurper().parseText(text)
        } catch (CharacterCodingException | JsonException | IllegalArgumentException | StackOverflowError ignored) {
            // what the file holds is not repeated: it is the arguments, and they can be anything
            throw new GradleException("${what} ${NOT_AN_ARRAY}.")
        }
        if (!(parsed instanceof List) || !((List) parsed).every { Object argument -> argument instanceof String }) {
            throw new GradleException("${what} ${NOT_AN_ARRAY}.")
        }
        List<String> arguments = new ArrayList<String>((List<String>) parsed)
        if (arguments.size() > MAX_ARGUMENTS) {
            throw new GradleException("${what} has more than ${MAX_ARGUMENTS} arguments.")
        }
        // a process cannot be started with an argument that has this character in it
        if (arguments.any { String argument -> argument.indexOf(0) >= 0 }) {
            throw new GradleException("${what} has an argument with the character U+0000 in it, which no command can be given.")
        }
        // nor with half of a character, which JSON can spell (\ud800) and no encoding can carry
        if (arguments.any { String argument -> !StandardCharsets.UTF_8.newEncoder().canEncode(argument) }) {
            throw new GradleException("${what} has an argument that is not text: half of a surrogate pair.")
        }
        arguments
    }
}

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
package org.apache.grails.core.cli

import groovy.json.JsonOutput
import groovy.transform.CompileStatic

/**
 * Writes the commands an application can run, with their descriptions, arguments and options, to a
 * JSON file that command line tools read to offer and complete those commands without loading them.
 *
 * <p>The commands are those {@code runCommand} runs: every {@link ApplicationCommand} the
 * {@link ApplicationContextCommandRegistry} finds on the classpath of the process, which the Grails
 * Gradle plugin makes the application's classpath plus its {@code grailsCli} configuration.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ApplicationCommandManifestWriter {

    /**
     * The version of the file's format, written to the file, raised when a reader could misread a
     * file of the new format.
     */
    static final int FORMAT_VERSION = 1

    private ApplicationCommandManifestWriter() {
    }

    /**
     * Writes the manifest of the commands on the classpath.
     *
     * @param args the path of the file to write
     */
    static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException('Expected the path of the manifest file to write as the only argument')
        }
        write(new ApplicationContextCommandRegistry().findCommands(), new File(args[0]))
    }

    /**
     * Writes the manifest of the given commands, sorted by name.
     *
     * @param commands the commands to describe
     * @param file the file to write, whose directory is created when it does not exist
     */
    static void write(Collection<ApplicationCommand> commands, File file) {
        List<Map<String, Object>> described = commands
                .sort(false) { ApplicationCommand command -> command.name }
                .collect { ApplicationCommand command -> describe(command) }
        file.parentFile?.mkdirs()
        file.setText(JsonOutput.prettyPrint(JsonOutput.toJson([version: FORMAT_VERSION, commands: described])), 'UTF-8')
    }

    private static Map<String, Object> describe(ApplicationCommand command) {
        Class<?> type = command.class
        List<Map<String, Object>> arguments = declaredArguments(type).collect { CommandParameter argument ->
            [name: argument.name(), description: argument.description(), required: argument.required(),
             values: argument.values().toList()] as Map<String, Object>
        }
        List<Map<String, Object>> options = declaredOptions(type).collect { CommandOption option ->
            [name: option.name(), description: option.description(), flag: option.flag(),
             values: option.values().toList()] as Map<String, Object>
        }
        // the class lets a build tool find the command's source, whose changes may change this description
        [name: command.name, description: command.description ?: '', className: type.name, arguments: arguments,
         options: options] as Map<String, Object>
    }

    // Class#getAnnotationsByType would read both forms at once, but the static compiler links it with an
    // Object[] return type, which the JDK method does not have, so the call fails with NoSuchMethodError.
    // getAnnotation returns a single annotation and links correctly (GROOVY-12793).
    private static List<CommandParameter> declaredArguments(Class<?> type) {
        CommandParameter single = type.getAnnotation(CommandParameter)
        single ? [single] : (type.getAnnotation(CommandParameters)?.value()?.toList() ?: [])
    }

    private static List<CommandOption> declaredOptions(Class<?> type) {
        CommandOption single = type.getAnnotation(CommandOption)
        single ? [single] : (type.getAnnotation(CommandOptions)?.value()?.toList() ?: [])
    }
}

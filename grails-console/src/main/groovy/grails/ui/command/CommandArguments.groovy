/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.command

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/**
 * The arguments a command JVM is started with, where the Grails Gradle plugin hands it the file of the
 * {@code argsFile} project property by its path, so the arguments in the file, a password among them,
 * are on neither the JVM's command line nor in the configuration cache of the build.
 *
 * @since 9.0
 */
@CompileStatic
final class CommandArguments {

    /** The argument naming a JSON file whose array of strings takes the argument's place */
    static final String ARGS_FILE_ARGUMENT = '--grails-args-file='

    private CommandArguments() {
    }

    /**
     * Returns the arguments with each {@link #ARGS_FILE_ARGUMENT} replaced by the arguments of its file,
     * in their place.
     *
     * @param args the arguments the JVM was started with
     * @return the arguments of the command
     * @throws IllegalArgumentException when a file cannot be read or does not hold a JSON array of strings
     */
    static String[] expand(String[] args) {
        List<String> expanded = []
        for (String arg : args) {
            if (arg != null && arg.startsWith(ARGS_FILE_ARGUMENT)) {
                expanded.addAll(read(Paths.get(arg.substring(ARGS_FILE_ARGUMENT.length()))))
            }
            else {
                expanded << arg
            }
        }
        expanded as String[]
    }

    private static List<String> read(Path file) {
        Object parsed
        try {
            parsed = new JsonSlurper().parseText(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
        }
        catch (IOException | JsonException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not read the arguments of the command from ${file}: ${e.message}", e)
        }
        if (!(parsed instanceof List) || ((List) parsed).any { !(it instanceof String) }) {
            throw new IllegalArgumentException("The arguments file ${file} does not hold a JSON array of strings")
        }
        (List<String>) parsed
    }
}

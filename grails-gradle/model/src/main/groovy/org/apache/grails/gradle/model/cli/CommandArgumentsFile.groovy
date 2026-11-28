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
package org.apache.grails.gradle.model.cli

import groovy.transform.CompileStatic

/**
 * A file with the arguments of a command: UTF-8, one JSON array of strings, one string for each
 * argument.
 *
 * <p>The build that runs a command does not put what the file holds on the command line of the JVM
 * of the command. It gives that JVM the path of the file, as the last argument, after the class of
 * the application, and the class that runs the command reads the file ({@link #expand}). The
 * arguments are therefore in no process listing and in no log of the build, and their size is not
 * held to what a command line carries. A class that runs commands and does not know the argument,
 * as the one of a Grails before 9.0, takes it for the class of the application and ends saying
 * that there is no such class: it does not run the command without its arguments.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandArgumentsFile {

    /** What the argument that names the file starts with; the rest of it is the absolute path of the file. */
    public static final String ARGUMENT = '--grails-args-file='

    /** The most bytes the file has. */
    public static final int MAX_BYTES = 1024 * 1024

    /** The most arguments the file has. */
    public static final int MAX_ARGUMENTS = 4096

    private CommandArgumentsFile() {
    }

    /**
     * Says why a file of arguments cannot be used. The message finishes the sentence
     * "The file ... " and repeats nothing of what the file holds, which is the arguments.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * @param file the file
     * @return the arguments it holds
     * @throws Refused when it is not a file of at most {@link #MAX_BYTES} bytes that holds a JSON array
     * of at most {@link #MAX_ARGUMENTS} strings in UTF-8, none of them with the character U+0000
     */
    static List<String> read(File file) throws Refused {
        if (!file.isFile()) {
            throw new Refused('does not exist or is not a file.')
        }
        if (file.length() > MAX_BYTES) {
            throw new Refused("has more than ${MAX_BYTES} bytes.")
        }
        byte[] bytes
        try {
            InputStream input = new FileInputStream(file)
            try {
                // the length was asked for a moment ago, and the file is somebody else's
                bytes = input.readNBytes(MAX_BYTES + 1)
            } finally {
                input.close()
            }
        } catch (IOException ignored) {
            throw new Refused('cannot be read.')
        }
        if (bytes.length > MAX_BYTES) {
            throw new Refused("has more than ${MAX_BYTES} bytes.")
        }
        List<String> arguments
        try {
            arguments = StrictJson.stringArray(bytes)
        } catch (StrictJson.Refused ignored) {
            throw new Refused('is not a JSON array of strings in UTF-8.')
        }
        if (arguments.size() > MAX_ARGUMENTS) {
            throw new Refused("has more than ${MAX_ARGUMENTS} arguments.")
        }
        for (String argument : arguments) {
            if (argument.indexOf(0) >= 0) {
                throw new Refused('has an argument with the character U+0000 in it, which no command can be given.')
            }
        }
        arguments
    }

    /**
     * Puts the arguments of a file where they belong in what the class that runs a command was
     * started with. Without {@link #ARGUMENT} in last place, that is what it was started with.
     *
     * @param args what the JVM was started with: the arguments, the class of the application, and
     * after it the argument that names a file, if there is one
     * @return the arguments, those of the file after the others, and the class of the application last
     * @throws Refused when the file cannot be used, or when the class of the application is missing
     */
    static String[] expand(String[] args) throws Refused {
        if (args.length == 0 || !args[args.length - 1].startsWith(ARGUMENT)) {
            return args
        }
        if (args.length < 2) {
            throw new Refused('is not given after the class of the application.')
        }
        List<String> fromFile = read(new File(args[args.length - 1].substring(ARGUMENT.length())))
        List<String> expanded = new ArrayList<String>()
        for (int index = 0; index < args.length - 2; index++) {
            expanded.add(args[index])
        }
        expanded.addAll(fromFile)
        expanded.add(args[args.length - 2])
        expanded as String[]
    }
}

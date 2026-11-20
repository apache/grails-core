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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Reads {@code grails-forge-cli.yml}, or {@code grails-cli.yml} before it: the record of an
 * application that was written before there was a state under {@code .grails}, and still is. The file
 * of a project that was cloned is as little to be trusted as the state is, and is read as the state
 * is: its size, its lines and what it holds are bounded before any of it is built, it has no aliases,
 * anchors or tags, and every single value of it is text. What a value has to be is for whoever uses
 * it to say.
 *
 * @since 9.0
 */
@CompileStatic
final class EarlierRecord {

    /** The most bytes the file can have */
    public static final int MAX_BYTES = 256 * 1024

    /** The most lines the file can have */
    public static final int MAX_LINES = 8192

    /** The most characters a line of the file can have: its features are written in one line */
    public static final int MAX_LINE = 64 * 1024

    /** What the name of a feature is */
    public static final Pattern FEATURE_NAME = ProjectManifest.FEATURE_NAME

    private EarlierRecord() {
    }

    /**
     * @param file the file
     * @return the mapping the file holds, every single value of it text
     * @throws ProjectStateException with what is wrong with the file, in one line that is safe to show
     */
    static Map<String, Object> read(Path file) {
        String name = file.getFileName().toString()
        try {
            if (!Files.isRegularFile(file)) {
                throw new ProjectStateException("${name} is not a file")
            }
            if (Files.size(file) > MAX_BYTES) {
                throw new ProjectStateException("${name} is larger than it can be: it has ${Files.size(file)} bytes, and can have ${MAX_BYTES}")
            }
            byte[] bytes
            try (InputStream input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_BYTES + 1)
            }
            if (bytes.length > MAX_BYTES) {
                throw new ProjectStateException("${name} is larger than it can be: it can have ${MAX_BYTES} bytes")
            }
            return StateYaml.load(bytes, name, MAX_LINES, MAX_LINE)
        } catch (IOException e) {
            throw new ProjectStateException("${name} cannot be read: ${e.class.simpleName}")
        }
    }

    /**
     * @return whether a text is one line that is safe to keep and to show: not empty, no longer than
     * the limit, and without a character that is not shown or that a terminal acts on
     */
    static boolean isLine(String text, int limit) {
        return StateYaml.isLine(text, limit)
    }

    /**
     * @return the text as a message can repeat it: cut, and with nothing in it that a terminal or the
     * command line would act on
     */
    static String shown(String text) {
        return StateYaml.shown(text, StateYaml.MAX_ECHO)
    }
}

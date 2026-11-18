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
package org.apache.grails.datamapping.core.cli.querytail

import groovy.transform.CompileStatic

/**
 * The options of one run of the {@code query-tail} command.
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailOptions {

    /** How many rows a summary shows unless told otherwise. */
    public static final int DEFAULT_TOP = 30

    /** The capture file to read. */
    File file

    /** What to show. */
    QueryTailMode mode = QueryTailMode.PAGES

    /** How many rows a summary shows, or 0 for all. */
    int top = DEFAULT_TOP

    /** Whether to read what the file already holds rather than only what is written from now on. */
    boolean all

    /** Whether to print the summary once and exit rather than keep refreshing it. */
    boolean once

    /** Only the statements of this page, as {@code controller/action} or a controller name. */
    String page

    /** How many columns the output may use. */
    int width = QueryTailRenderer.DEFAULT_WIDTH

    /**
     * Builds the options from the flags of a command line, as the shell and the Gradle task parse
     * them: a flag given bare is {@code true}, one given with a value is its value.
     *
     * @param flags the flag values by name ({@code statements}, {@code stream}, {@code page}, {@code top},
     * {@code all}, {@code once}, {@code file}); a missing or null flag was not given
     * @param defaultFile the capture file to read when {@code file} is not given
     * @param baseDir the application directory, which a relative {@code file} is read against, as the
     * application reads a relative {@code grails.gorm.queryTail.file}; without one, it is read against the
     * working directory
     * @return the options
     * @throws IllegalArgumentException when {@code top} is not a number of rows
     */
    static QueryTailOptions from(Map<String, ?> flags, File defaultFile, File baseDir = null) {
        QueryTailOptions options = new QueryTailOptions()
        Object file = flags.get('file')
        options.file = file && !(file instanceof Boolean) ? inDirectory(new File(file.toString()), baseDir) : defaultFile
        options.all = enabled(flags.get('all'))
        options.once = enabled(flags.get('once'))
        Object page = flags.get('page')
        options.page = page && !(page instanceof Boolean) ? page.toString() : null
        Object top = flags.get('top')
        if (top && !(top instanceof Boolean)) {
            String rows = top.toString().trim()
            if (!rows.isInteger() || rows.toInteger() < 0) {
                throw new IllegalArgumentException("--top takes a number of rows, such as --top=10, not '${rows}'")
            }
            options.top = rows.toInteger()
        }
        if (enabled(flags.get('stream'))) {
            options.mode = QueryTailMode.STREAM
        }
        else if (enabled(flags.get('statements')) || options.page) {
            options.mode = QueryTailMode.STATEMENTS
        }
        options
    }

    private static File inDirectory(File file, File baseDir) {
        file.absolute || baseDir == null ? file : new File(baseDir, file.path)
    }

    /**
     * @param value a flag value
     * @return whether the flag was given, bare or as {@code true}
     */
    static boolean enabled(Object value) {
        value instanceof Boolean ? (Boolean) value : (value != null && Boolean.parseBoolean(value.toString()))
    }
}

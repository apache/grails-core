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
package org.grails.datastore.gorm.jdbc.sqltail.cli

import groovy.transform.CompileStatic

/**
 * The options of one run of the {@code sql-tail} command.
 *
 * @since 8.1
 */
@CompileStatic
class SqlTailOptions {

    /** How many rows a summary shows unless told otherwise. */
    public static final int DEFAULT_TOP = 30

    /** The capture file to read. */
    File file

    /** What to show. */
    SqlTailMode mode = SqlTailMode.PAGES

    /** How many rows a summary shows, or 0 for all. */
    int top = DEFAULT_TOP

    /** Whether to read what the file already holds rather than only what is written from now on. */
    boolean all

    /** Whether to print the summary once and exit rather than keep refreshing it. */
    boolean once

    /** Only the statements of this page, as {@code controller/action} or a controller name. */
    String page

    /** How many columns the output may use. */
    int width = SqlTailRenderer.DEFAULT_WIDTH

    /**
     * Builds the options from the flags of a command line, as the shell and the Gradle task parse
     * them: a flag given bare is {@code true}, one given with a value is its value.
     *
     * @param flags the flag values by name ({@code statements}, {@code stream}, {@code page}, {@code top},
     * {@code all}, {@code once}, {@code file}); a missing or null flag was not given
     * @param defaultFile the capture file to read when {@code file} is not given
     * @return the options
     */
    static SqlTailOptions from(Map<String, ?> flags, File defaultFile) {
        SqlTailOptions options = new SqlTailOptions()
        Object file = flags.get('file')
        options.file = file ? new File(file.toString()) : defaultFile
        options.all = enabled(flags.get('all'))
        options.once = enabled(flags.get('once'))
        Object page = flags.get('page')
        options.page = page && !(page instanceof Boolean) ? page.toString() : null
        Object top = flags.get('top')
        if (top && !(top instanceof Boolean)) {
            options.top = top.toString().toInteger()
        }
        if (enabled(flags.get('stream'))) {
            options.mode = SqlTailMode.STREAM
        }
        else if (enabled(flags.get('statements')) || options.page) {
            options.mode = SqlTailMode.STATEMENTS
        }
        options
    }

    /**
     * @param value a flag value
     * @return whether the flag was given, bare or as {@code true}
     */
    static boolean enabled(Object value) {
        value instanceof Boolean ? (Boolean) value : (value != null && Boolean.parseBoolean(value.toString()))
    }
}

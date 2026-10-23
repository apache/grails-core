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
package org.apache.grails.data.core.querytail

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * One captured statement, SQL, Cypher or a MongoDB command: when it ran, which data source ran it,
 * the web request it belongs to and the page (controller and action) that request was serving. A
 * record is one line of the capture file, tab separated, which is what the {@code query-tail} command
 * reads back. The first line of the file is not a record but its {@link #header(String) header},
 * which names the run that wrote it.
 *
 * @since 8.1
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true)
class QueryTailRecord {

    /** Separates the fields of a line. No field ever contains it. */
    public static final String FIELD_SEPARATOR = '\t'

    /** Written for a field that has no value. */
    public static final String EMPTY = '-'

    /** The page of a statement run outside a web request is the thread, with this prefix. */
    public static final String THREAD_PAGE_PREFIX = 'thread:'

    /** Starts the first line of a capture file, which the id of the run that wrote the file follows. */
    public static final String HEADER_PREFIX = '# query-tail capture '

    private static final Pattern WHITESPACE = Pattern.compile('\\s+')

    private static final int FIELD_COUNT = 6

    /** When the statement was handed to the driver, in milliseconds since the epoch. */
    long timestamp

    /** The GORM connection source name; {@code ConnectionSource.DEFAULT} for the default data source. */
    String dataSource

    /** Identifies the web request, the same for every statement of one request; null outside a request. */
    String requestId

    /** {@code controller/action} (with a {@code namespace:} prefix when there is one), or {@code thread:name} outside a request. */
    String page

    /** The HTTP method and path of the request, such as {@code GET /book/show/42}; null outside a request. */
    String request

    /** The statement, without its values, with its whitespace collapsed to single spaces. */
    String sql

    /**
     * @return whether the statement ran while serving a web request
     */
    boolean isWebRequest() {
        requestId != null
    }

    /**
     * @return this record as one line of the capture file, without a line terminator
     */
    String toLine() {
        [String.valueOf(timestamp), field(dataSource), field(requestId), field(page), field(request), field(sql)]
                .join(FIELD_SEPARATOR)
    }

    /**
     * Reads a record back from a line of the capture file.
     *
     * @param line the line
     * @return the record, or null when the line is not a record (a partial write, or not a capture file)
     */
    static QueryTailRecord parse(String line) {
        if (line == null) {
            return null
        }
        String[] fields = line.split(FIELD_SEPARATOR, -1)
        if (fields.length != FIELD_COUNT || !fields[0].isLong()) {
            return null
        }
        new QueryTailRecord(
                timestamp: fields[0].toLong(),
                dataSource: value(fields[1]),
                requestId: value(fields[2]),
                page: value(fields[3]),
                request: value(fields[4]),
                sql: value(fields[5]))
    }

    /**
     * @param run identifies the run of the application that writes the file
     * @return the first line of a capture file, without a line terminator
     */
    static String header(String run) {
        HEADER_PREFIX + normalize(run)
    }

    /**
     * Reads the run back from the first line of a capture file.
     *
     * @param line the first line
     * @return the run that wrote the file, or null when the line is not a header
     */
    static String parseHeader(String line) {
        boolean header = line != null && line.startsWith(HEADER_PREFIX) && line.length() > HEADER_PREFIX.length()
        header ? line.substring(HEADER_PREFIX.length()) : null
    }

    /**
     * Normalizes a value for a field: whitespace runs, including tabs and line breaks, become one
     * space so that a value can never break the line format.
     */
    static String normalize(String value) {
        value == null ? null : WHITESPACE.matcher(value).replaceAll(' ').trim()
    }

    private static String field(String value) {
        String normalized = normalize(value)
        normalized ? normalized : EMPTY
    }

    private static String value(String field) {
        field == EMPTY || field.isEmpty() ? null : field
    }
}

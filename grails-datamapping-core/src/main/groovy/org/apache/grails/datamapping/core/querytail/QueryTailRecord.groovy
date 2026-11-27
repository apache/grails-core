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
package org.apache.grails.datamapping.core.querytail

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * One captured statement, SQL, Cypher, a MongoDB command or an operation of the simple datastore:
 * when it ran, which data source ran it,
 * the web request it belongs to and the page (controller and action) that request was serving. A
 * record is one line of the capture file, tab separated, which is what the {@code query-tail} command
 * reads back. The first line of the file is not a record but its {@link #header(String) header},
 * which names the run that wrote it.
 *
 * <p>The whitespace of a statement is collapsed to single spaces in its line, but for that inside its
 * quoted names and literals, which is kept as written: two names that differ only in their spaces
 * are two names. A tab or a line break kept that way is written as {@code \t}, {@code \n} or
 * {@code \r}, and a backslash anywhere in the statement as two, so that the line format holds and
 * the statement reads back as it was written. A {@link #document} statement, one recorded without
 * its values already, as a MongoDB command, whose names are quoted as JSON quotes them rather than as
 * SQL does, and an operation of the simple datastore are, is written as it was recorded, with only
 * those characters escaped, and its line says so, so that it is never read as SQL.</p>
 *
 * @since 9.0
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

    private static final int FIELD_COUNT = 7

    /** Written in the field before the statement of a {@link #document} statement. */
    private static final String DOCUMENT = 'document'

    /** How much of a capture file its first line, the header naming its run, and its line break are looked for in. */
    private static final int HEADER_LIMIT = 256

    /** Starts the name of a run too long for a header, which names its digest instead. */
    private static final String DIGEST_PREFIX = 'sha256:'

    private static final byte NEWLINE = (byte) 10

    private static final char ESCAPE = '\\'
    private static final char TAB = '\t'
    private static final char LINE_FEED = '\n'
    private static final char CARRIAGE_RETURN = '\r'

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

    /**
     * The statement, without its values. Read from a line, its whitespace is collapsed to single
     * spaces, but for that inside its quoted names and literals, unless it is a {@link #document}.
     */
    String sql

    /**
     * Whether the statement is a document rather than text, recorded without its values already: a
     * MongoDB command, which is quoted as JSON is, or an operation of the simple datastore, which is
     * built from its query. It is kept and shown as it was recorded, and is never read as SQL.
     */
    boolean document

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
        [String.valueOf(timestamp), field(dataSource), field(requestId), field(page), field(request),
         document ? DOCUMENT : EMPTY, document ? documentField(sql) : statementField(sql)].join(FIELD_SEPARATOR)
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
                document: fields[5] == DOCUMENT,
                sql: unescape(value(fields[6])))
    }

    /**
     * @param run identifies the run of the application that writes the file
     * @return the first line of a capture file, without a line terminator, which names the run as
     * {@link #runName} does
     */
    static String header(String run) {
        HEADER_PREFIX + runName(run)
    }

    /**
     * The run as the header of a capture file names it: with its whitespace collapsed, as
     * {@link #normalize} collapses it, and, when that is too long for the header to be read back whole,
     * as its SHA-256 digest, so that the run a capture appends to is the one the header was written for.
     *
     * @param run identifies the run of the application that writes the file
     * @return the run as a header names it, or null for none
     */
    static String runName(String run) {
        String normalized = normalize(run)
        if (!normalized) {
            return null
        }
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8)
        if (HEADER_PREFIX.length() + bytes.length < HEADER_LIMIT) {
            return normalized
        }
        DIGEST_PREFIX + MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex()
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
     * Reads the run back from the first line of a capture file, as the capture that appends to a run
     * and the {@code query-tail} command that notices a restart both do.
     *
     * @param file the capture file
     * @return the run that wrote the file, or null when the file is not there, or not a regular file, or
     * its first line is not a header or not written whole yet
     */
    static String readRun(File file) {
        // a pipe is not read, since opening one waits for a writer
        if (!file.isFile()) {
            return null
        }
        byte[] head = new byte[HEADER_LIMIT]
        int read
        try {
            // a stream rather than a channel, which an interrupt of the reading thread would close
            read = new FileInputStream(file).withCloseable { InputStream input -> input.readNBytes(head, 0, head.length) }
        }
        catch (IOException ignored) {
            return null
        }
        for (int i = 0; i < read; i++) {
            if (head[i] == NEWLINE) {
                return parseHeader(new String(head, 0, i, StandardCharsets.UTF_8))
            }
        }
        null
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

    /**
     * Writes a statement for its field: whitespace runs outside quotes become one space, and a quoted
     * name or literal is kept as written, with its tabs and line breaks escaped.
     */
    private static String statementField(String statement) {
        if (statement == null) {
            return EMPTY
        }
        StringBuilder out = new StringBuilder(statement.length())
        boolean space = false
        int i = 0
        while (i < statement.length()) {
            if (Character.isWhitespace(statement.charAt(i))) {
                space = true
                i++
                continue
            }
            if (space && out.length() > 0) {
                out.append(' ' as char)
            }
            space = false
            int end = quotedEnd(statement, i)
            for (int j = i; j < end; j++) {
                escape(out, statement.charAt(j))
            }
            i = end
        }
        out.length() > 0 ? out.toString() : EMPTY
    }

    /**
     * Writes a document for its field as it is, with its tabs, line breaks and backslashes escaped.
     */
    private static String documentField(String statement) {
        if (!statement) {
            return EMPTY
        }
        StringBuilder out = new StringBuilder(statement.length())
        for (int i = 0; i < statement.length(); i++) {
            escape(out, statement.charAt(i))
        }
        out.toString()
    }

    private static void escape(StringBuilder out, char c) {
        switch (c) {
            case ESCAPE:
                out.append(ESCAPE).append(ESCAPE)
                break
            case TAB:
                out.append(ESCAPE).append('t' as char)
                break
            case LINE_FEED:
                out.append(ESCAPE).append('n' as char)
                break
            case CARRIAGE_RETURN:
                out.append(ESCAPE).append('r' as char)
                break
            default:
                out.append(c)
        }
    }

    private static String unescape(String field) {
        if (field == null || field.indexOf(ESCAPE as int) < 0) {
            return field
        }
        StringBuilder out = new StringBuilder(field.length())
        int i = 0
        while (i < field.length()) {
            char c = field.charAt(i)
            if (c == ESCAPE && i + 1 < field.length()) {
                i++
                c = field.charAt(i)
                if (c == ('t' as char)) {
                    c = TAB
                }
                else if (c == ('n' as char)) {
                    c = LINE_FEED
                }
                else if (c == ('r' as char)) {
                    c = CARRIAGE_RETURN
                }
            }
            out.append(c)
            i++
        }
        out.toString()
    }

    /**
     * Reads past a quoted name or literal: one quoted with single or double quotes, backticks or
     * square brackets, in which the closing quote written twice stands for itself. Both the capture,
     * as it writes a statement, and the {@code query-tail} command, as it shortens one, read quoting
     * this way, so that they agree on where a quoted name ends.
     *
     * @param statement a statement
     * @param start an index into it
     *
     * @return the index after the quoted run that opens at an index, or the end of the statement when
     * it is not closed; the index after the character there when it opens none
     */
    static int quotedEnd(String statement, int start) {
        char open = statement.charAt(start)
        char close
        if (open == ('[' as char)) {
            close = ']' as char
        }
        else if (open == ('\'' as char) || open == ('"' as char) || open == ('`' as char)) {
            close = open
        }
        else {
            return start + 1
        }
        int i = start + 1
        while (i < statement.length()) {
            if (statement.charAt(i) == close) {
                if (i + 1 == statement.length() || statement.charAt(i + 1) != close) {
                    return i + 1
                }
                i++
            }
            i++
        }
        statement.length()
    }
}

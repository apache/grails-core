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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Cypher, the query language of Neo4j: single and double quotes delimit a string, in which a
 * backslash escapes the character after it and a quote written twice is two strings; backticks
 * delimit a name, in which a backtick is written twice; {@code //} starts a line comment, and
 * {@code --} and {@code #} do not, as {@code -->} draws a relationship; block comments do not nest;
 * and a dollar starts a parameter. Neo4j 5 ends a line comment at a carriage return, which Neo4j 3.5
 * does not, so a statement with a carriage return that no line feed follows is read both ways.
 * Neo4j 5 also decodes every {@code \u} escape that no backslash escapes before it parses, wherever
 * it is written, where 3.5 decodes one only inside a string. A statement with an escape that decodes
 * into a quote, a backtick, a backslash, a line break, an asterisk or a slash, any of which may end a
 * string, a comment or a name elsewhere, has no reading, and a backslash outside a string, a comment
 * or a name, which may decode into a value, withholds the statement.
 */
@CompileStatic
@PackageScope
final class CypherSyntax extends StandardSqlSyntax {

    private static final char BACKSLASH = '\\'
    private static final char QUOTE = '\''
    private static final char DOUBLE_QUOTE = '"'
    private static final char BACKTICK = '`'
    private static final char UNICODE_ESCAPE = 'u'
    private static final String DELIMITERS = '\'"`\\\n\r*/'
    private static final int ESCAPE_DIGITS = 4
    private static final int HEXADECIMAL = 16
    private static final int NO_CHARACTER = -1

    private final boolean carriageReturnEndsLine

    CypherSyntax(boolean carriageReturnEndsLine) {
        super('Cypher')
        this.carriageReturnEndsLine = carriageReturnEndsLine
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
        if (decodesDelimiter(sql)) {
            return List.<QueryTailSyntax>of()
        }
        if (!hasLoneCarriageReturn(sql)) {
            return List.<QueryTailSyntax>of(this)
        }
        List.<QueryTailSyntax>of(new CypherSyntax(false), new CypherSyntax(true))
    }

    @Override
    int endOfComment(String sql, int index) {
        if (sql.startsWith('//', index)) {
            return endOfLine(sql, index)
        }
        if (sql.startsWith('/*', index)) {
            return endOfBlockComment(sql, index)
        }
        NONE
    }

    @Override
    int endOfValue(String sql, int index) {
        char c = sql.charAt(index)
        if (c == BACKSLASH) {
            return UNTERMINATED
        }
        if (c == QUOTE || c == DOUBLE_QUOTE) {
            return endOfQuoted(sql, index, c, true, false)
        }
        super.endOfValue(sql, index)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        sql.charAt(index) == BACKTICK ? endOfQuoted(sql, index, BACKTICK, false, true) : NONE
    }

    @Override
    protected boolean carriageReturnEndsLine() {
        carriageReturnEndsLine
    }

    @Override
    protected boolean backslashEscapes() {
        true
    }

    @Override
    protected boolean doubleQuotedStrings() {
        true
    }

    /**
     * @return whether the statement holds an escape Neo4j 5 decodes into a character that may end a
     * string, a comment or a name: a backslash that no backslash escapes, a {@code u}, or several, as
     * the JavaCC parser of Neo4j 5 reads them, and four hexadecimal digits, which Neo4j 5 takes from any
     * script
     */
    private static boolean decodesDelimiter(String sql) {
        int i = sql.indexOf('\\')
        while (i >= 0) {
            int end = i
            while (end < sql.length() && sql.charAt(end) == BACKSLASH) {
                end++
            }
            if ((end - i) % 2 == 1 && DELIMITERS.indexOf(decoded(sql, end)) >= 0) {
                return true
            }
            i = sql.indexOf('\\', end)
        }
        false
    }

    /**
     * @return the character the escape whose {@code u} is at the index decodes into, or
     * {@link #NO_CHARACTER} when none is written there
     */
    private static int decoded(String sql, int start) {
        int i = start
        while (i < sql.length() && sql.charAt(i) == UNICODE_ESCAPE) {
            i++
        }
        if (i == start || i + ESCAPE_DIGITS > sql.length()) {
            return NO_CHARACTER
        }
        int c = 0
        for (int end = i + ESCAPE_DIGITS; i < end; i++) {
            int digit = Character.digit(sql.charAt(i), HEXADECIMAL)
            if (digit < 0) {
                return NO_CHARACTER
            }
            c = c * HEXADECIMAL + digit
        }
        c
    }
}

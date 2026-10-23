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

/**
 * Takes the values out of a statement before it is captured. Every string, number and other
 * literal becomes {@link #PLACEHOLDER} and every comment is dropped, so that a value written into
 * the SQL text, a password in a native query or a key in a comment, never reaches the capture file.
 * Only the recorded copy is redacted: the statement the database runs is not changed.
 *
 * <p>Where a value or a comment ends is a question for the {@link QueryTailSyntax} of the database the
 * statement runs on, and the redactor asks it about each index of the statement in turn. A statement
 * is redacted under every {@linkplain QueryTailSyntax#readings reading} the syntax offers, and is
 * recorded only when all of them give the same text, whitespace aside: a reading that takes a
 * carriage return for the end of a comment and one that takes it for whitespace agree. When they
 * do not, or when a literal, a quoted name or a comment never ends, the statement is withheld:
 * {@link #WITHHELD} is recorded in its place, never the statement.</p>
 *
 * @since 8.1
 */
@CompileStatic
final class QueryTailRedactor {

    /** What a literal is replaced with. */
    static final String PLACEHOLDER = '?'

    /** What is recorded in place of a statement whose values cannot be told apart from its SQL. */
    static final String WITHHELD = '(statement withheld: its values could not be redacted)'

    private static final Pattern WHITESPACE = ~/\s+/

    private QueryTailRedactor() {
    }

    /**
     * Redacts a statement.
     *
     * @param sql the statement
     * @param syntax the syntax of the database it runs on, or null when that is not known
     * @return the statement with its literals replaced and its comments dropped, or {@link #WITHHELD}
     */
    static String redact(String sql, QueryTailSyntax syntax) {
        if (sql == null) {
            return null
        }
        String redacted = null
        String agreed = null
        for (QueryTailSyntax reading in (syntax ?: QueryTailSyntax.UNKNOWN).readings(sql)) {
            String candidate = read(sql, reading)
            if (candidate == null) {
                return WITHHELD
            }
            String collapsed = WHITESPACE.matcher(candidate).replaceAll(' ').trim()
            if (agreed != null && agreed != collapsed) {
                return WITHHELD
            }
            agreed = collapsed
            if (redacted == null) {
                redacted = candidate
            }
        }
        redacted
    }

    /**
     * @return the statement as one reading finds it, or null when a literal, a quoted name or a
     * comment never ends under it
     */
    private static String read(String sql, QueryTailSyntax reading) {
        int length = sql.length()
        StringBuilder out = new StringBuilder(length)
        int i = 0
        while (i < length) {
            int end = reading.endOfComment(sql, i)
            if (end != QueryTailSyntax.NONE) {
                out.append(' ')
            }
            else {
                end = reading.endOfValue(sql, i)
                if (end != QueryTailSyntax.NONE) {
                    out.append(PLACEHOLDER)
                }
                else {
                    end = reading.endOfQuotedName(sql, i)
                    if (end > 0) {
                        out.append(sql, i, end)
                    }
                    else if (end == QueryTailSyntax.NONE) {
                        out.append(sql.charAt(i))
                        end = i + 1
                    }
                }
            }
            if (end == QueryTailSyntax.UNTERMINATED) {
                return null
            }
            i = end
        }
        out.toString()
    }
}

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
 * do not, when the statement has no reading, or when a literal, a quoted name or a comment never
 * ends or ends where the syntax cannot tell, the statement is withheld: {@link #WITHHELD} is recorded
 * in its place, never the statement.</p>
 *
 * <p>A value is not always a literal. A statement in which a reading finds that its database may take
 * a bare word for one, as SQL Server takes the arguments of a procedure, is
 * {@linkplain QueryTailSyntax#takesBareValues withheld}. Nor is a password always a literal: Oracle
 * takes the one {@code IDENTIFIED BY} gives as a name, quoted or bare, SAP HANA takes the
 * {@code PASSWORD} of a user as one, and a {@code CONNECT} may give one bare too, so a statement that
 * gives a password, whatever its database, is withheld whole: one with {@code IDENTIFIED BY}, one
 * that creates or alters a user, a role or a login and gives it a {@code PASSWORD}, or a
 * {@code PASSWD} as Sybase spells it, wherever in the statement it does, as a block or a procedure
 * may, and one that connects, other than by the {@code CONNECT BY} of a hierarchical query. Nor is the
 * value of a setting a literal: PostgreSQL, MySQL, Oracle, Db2 and SQL Server, among others, take the
 * value a statement gives a setting as a bare word, so a statement that sets one is withheld whole: a
 * {@code SET} that opens the statement, follows a semicolon or opens a block, an {@code ALTER} of the
 * system, the session, a database, a role, a user, a login or a routine that sets one, and a function
 * or a procedure created with one.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class QueryTailRedactor {

    /** What a literal is replaced with. */
    static final String PLACEHOLDER = '?'

    /** What is recorded in place of a statement whose values cannot be told apart from its SQL. */
    static final String WITHHELD = '(statement withheld: its values could not be redacted)'

    private static final Pattern WHITESPACE = ~/\s+/

    /**
     * The clause that gives a password in Oracle, MySQL and MariaDB, read once comments are spaces and
     * literals placeholders; a quoted name that holds it withholds a statement that needs no withholding.
     */
    private static final Pattern IDENTIFIED_BY = Pattern.compile('(?i)\\bIDENTIFIED\\s+BY\\b')

    /**
     * A statement that gives a user, a role or a login a {@code PASSWORD}, or a {@code PASSWD} as Sybase
     * spells it, anywhere in it, as a block may; one that sets a Sybase role with its password; or one
     * that connects, which may give one, other than by the {@code CONNECT BY} of a hierarchical query.
     */
    private static final Pattern GIVES_PASSWORD = Pattern.compile(
            '(?is)\\b(?:CREATE|ALTER)\\s+(?:(?:OR\\s+REPLACE|RESTRICTED|CURRENT)\\s+)?(?:USER|ROLE|LOGIN)\\b.*\\bPASSW(?:OR)?D\\b' +
            '|\\bSET\\s+ROLE\\b.*\\bWITH\\s+PASSWD\\b|\\bCONNECT\\b(?!\\s+BY\\b)')

    /**
     * A statement that sets a setting, whose value a database may take as a bare word: a {@code SET} that opens
     * it, follows a semicolon or opens a block, rather than the {@code SET} of an update, which follows a table; an
     * {@code ALTER} of the system, the session, a database, a role, a user, a login or a routine that sets one; and
     * a function or a procedure created with one.
     */
    private static final Pattern SETS_A_SETTING = Pattern.compile(
            '(?is)(?:^|;|\\b(?:BEGIN|DO)\\b)\\s*SET\\b' +
            '|\\bALTER\\s+(?:SYSTEM|SESSION|DATABASE|ROLE|USER|LOGIN|FUNCTION|PROCEDURE|ROUTINE)\\b.*\\bSET\\b' +
            '|\\bCREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:FUNCTION|PROCEDURE)\\b.*\\bSET\\b')

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
        String redacted = WITHHELD
        String agreed = null
        for (QueryTailSyntax reading in (syntax ?: QueryTailSyntax.UNKNOWN).readings(sql)) {
            String candidate = read(sql, reading)
            if (candidate == null || reading.takesBareValues(candidate)) {
                return WITHHELD
            }
            String collapsed = WHITESPACE.matcher(candidate).replaceAll(' ').trim()
            if (agreed == null) {
                redacted = candidate
            }
            else if (agreed != collapsed) {
                return WITHHELD
            }
            agreed = collapsed
        }
        agreed != null && (givesPassword(agreed) || SETS_A_SETTING.matcher(agreed).find()) ? WITHHELD : redacted
    }

    /**
     * @param redacted a statement as redaction left it, its comments spaces and its literals placeholders
     * @return whether it gives a password, which it may give as a name or a bare word rather than a literal,
     * anywhere in it
     */
    private static boolean givesPassword(String redacted) {
        IDENTIFIED_BY.matcher(redacted).find() || GIVES_PASSWORD.matcher(redacted).find()
    }

    /**
     * @return the statement as one reading finds it, or null when a literal, a quoted name or a
     * comment never ends under it or ends where the reading cannot tell
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

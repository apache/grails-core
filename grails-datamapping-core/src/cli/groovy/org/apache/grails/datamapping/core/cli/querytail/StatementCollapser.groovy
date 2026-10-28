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

import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailRecord

/**
 * Shortens a statement to the part that identifies it: the table a select reads and its where
 * clause, along with the common table expressions it opens with, the table an update changes and
 * its where clause, the table an insert writes to. The column lists Hibernate generates are long
 * and say nothing about which code ran the statement, and dropping them lets the same query count
 * as one statement wherever it is run from. A select whose list calls a function, as a count or
 * another aggregate does, is kept whole, since what it computes is what tells it from the select
 * that lists the rows it counts, and so is one whose list reads a subquery, as a formula property
 * does, since the tables the subquery reads tell it from another select of the same table; the
 * {@code distinct} of a select is kept too. A Cypher statement is shown whole: its pattern names
 * what it reads or writes, and GORM generates nothing long in it. So is a MongoDB command, which is
 * recorded as its name, its collection and the document that shapes it, and is shown as it was
 * recorded: its names are quoted as JSON quotes them, so nothing in them is read as SQL. So is a
 * statement of the simple datastore, which is built from the query, and is as short as it gets.
 *
 * <p>A statement is shortened as the capture recorded it, which has dropped its comments and
 * collapsed its whitespace outside quotes already.</p>
 *
 * @since 9.0
 */
@CompileStatic
class StatementCollapser {

    private static final String ELLIPSIS = '...'

    private static final String SELECT = 'select'

    private static final Pattern DISTINCT = Pattern.compile('(?i)\\s*distinct\\b')

    /**
     * @param record a captured statement
     * @return its statement as the command shows it: shortened, or as it was recorded when it is a
     * document, a MongoDB command or a statement of the simple datastore
     */
    static String statementOf(QueryTailRecord record) {
        record.document ? (record.sql ?: '') : collapse(record.sql)
    }

    /**
     * @param sql a statement as it was recorded
     * @return the shortened statement, or the statement as it was recorded when it is not a SQL select,
     * update or insert
     */
    static String collapse(String sql) {
        String text = sql ?: ''
        if (text.isEmpty() || StatementType.isCypher(text)) {
            return text
        }
        int statement = StatementType.outerStatement(text)
        if (statement != 0) {
            // the common table expressions a statement opens with are kept whole, as their tables and conditions are
            // what it reads, and so is any statement they are written for but a select
            return statement > 0 && StatementType.of(text.substring(statement)) == StatementType.SELECT ?
                    collapseSelect(text, statement) : text
        }
        switch (StatementType.of(text)) {
            case StatementType.SELECT:
                return collapseSelect(text, 0)
            case StatementType.UPDATE:
                return collapseUpdate(text)
            case StatementType.INSERT:
                return collapseInsert(text)
            default:
                return text
        }
    }

    /**
     * Drops the column list of the select at the index: the one a statement opens with, or the one its
     * common table expressions are written for, unless the list computes what it selects.
     */
    private static String collapseSelect(String text, int select) {
        int from = topLevelIndexOf(text, ' from ')
        if (from <= select) {
            return text
        }
        String list = text.substring(select + SELECT.length(), from)
        if (computes(list)) {
            return text
        }
        String distinct = DISTINCT.matcher(list).lookingAt() ? 'distinct ' : ''
        "${text.substring(0, select)}${SELECT} ${distinct}${ELLIPSIS}${text.substring(from)}".toString()
    }

    /**
     * @return whether a select list computes what it selects: calls a function outside its subqueries, which a
     * parenthesis that follows a name does, as that of a count or another aggregate, or reads a subquery, at any
     * depth, which a parenthesis that opens a select does
     */
    private static boolean computes(String list) {
        int depth = 0
        int i = 0
        while (i < list.length()) {
            int quoted = QueryTailRecord.quotedEnd(list, i)
            if (quoted > i + 1) {
                i = quoted
                continue
            }
            char c = list.charAt(i)
            if (c == ('(' as char)) {
                if ((depth == 0 && followsName(list, i)) || opensSelect(list, i)) {
                    return true
                }
                depth++
            }
            else if (c == (')' as char)) {
                depth = Math.max(0, depth - 1)
            }
            i++
        }
        false
    }

    /** @return whether the parenthesis at the index opens a select, whitespace aside, as a subquery does */
    private static boolean opensSelect(String text, int index) {
        int after = index + 1
        while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
            after++
        }
        int end = after + SELECT.length()
        text.regionMatches(true, after, SELECT, 0, SELECT.length()) &&
                (end == text.length() || !(Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == ('_' as char)))
    }

    /** @return whether the parenthesis at the index follows a name, whitespace aside */
    private static boolean followsName(String text, int index) {
        int before = index - 1
        while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
            before--
        }
        before >= 0 && (Character.isLetterOrDigit(text.charAt(before)) || text.charAt(before) == ('_' as char))
    }

    private static String collapseUpdate(String text) {
        int set = topLevelIndexOf(text, ' set ')
        if (set < 0) {
            return text
        }
        int where = topLevelIndexOf(text, ' where ')
        String head = text.substring(0, set)
        where > set ? "${head} ${ELLIPSIS}${text.substring(where)}".toString() : "${head} ${ELLIPSIS}".toString()
    }

    private static String collapseInsert(String text) {
        int columns = topLevelIndexOf(text, '(')
        int values = topLevelIndexOf(text, ' values ')
        int cut = columns < 0 ? values : (values < 0 ? columns : Math.min(columns, values))
        cut < 0 ? text : "${text.substring(0, cut).trim()} ${ELLIPSIS}".toString()
    }

    /**
     * Finds a keyword outside parentheses, string literals and quoted names, so that a subquery, a
     * value or a table named like a clause never stands in for the outer statement's clause.
     */
    static int topLevelIndexOf(String text, String needle) {
        String haystack = text.toLowerCase(Locale.ROOT)
        if (haystack.length() != text.length()) {
            haystack = text
        }
        int depth = 0
        int i = 0
        while (i < haystack.length()) {
            int quoted = QueryTailRecord.quotedEnd(haystack, i)
            if (quoted > i + 1) {
                i = quoted
                continue
            }
            char c = haystack.charAt(i)
            if (c == ('(' as char)) {
                if (depth == 0 && needle == '(') {
                    return i
                }
                depth++
            }
            else if (c == (')' as char)) {
                depth = Math.max(0, depth - 1)
            }
            else if (depth == 0 && haystack.startsWith(needle, i)) {
                return i
            }
            i++
        }
        -1
    }
}

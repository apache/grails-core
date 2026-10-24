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
package org.apache.grails.data.core.cli.querytail

import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * Shortens a statement to the part that identifies it: the table a select reads and its where
 * clause, the table an update changes and its where clause, the table an insert writes to. The
 * column lists Hibernate generates are long and say nothing about which code ran the statement,
 * and dropping them lets the same query count as one statement wherever it is run from. A Cypher
 * statement is shown whole: its pattern names what it reads or writes, and GORM generates nothing
 * long in it. So is a MongoDB command, which is recorded as its name, its collection and the
 * document that shapes it.
 *
 * @since 8.1
 */
@CompileStatic
class StatementCollapser {

    private static final Pattern COMMENT = Pattern.compile('/\\*.*?\\*/', Pattern.DOTALL)
    private static final Pattern WHITESPACE = Pattern.compile('\\s+')
    private static final String ELLIPSIS = '...'

    /**
     * @param sql a statement
     * @return the shortened statement, or the statement with its comments and extra whitespace
     * removed when it is not a SQL select, update or insert
     */
    static String collapse(String sql) {
        String text = normalize(sql)
        if (text.isEmpty() || StatementType.isCypher(text)) {
            return text
        }
        switch (StatementType.of(text)) {
            case StatementType.SELECT:
                return collapseSelect(text)
            case StatementType.UPDATE:
                return collapseUpdate(text)
            case StatementType.INSERT:
                return collapseInsert(text)
            default:
                return text
        }
    }

    /**
     * @param sql a statement
     * @return the statement without comments, its whitespace collapsed to single spaces
     */
    static String normalize(String sql) {
        if (sql == null) {
            return ''
        }
        WHITESPACE.matcher(COMMENT.matcher(sql).replaceAll(' ')).replaceAll(' ').trim()
    }

    private static String collapseSelect(String text) {
        int from = topLevelIndexOf(text, ' from ')
        from < 0 ? text : "select ${ELLIPSIS}${text.substring(from)}".toString()
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
     * Finds a keyword outside parentheses and string literals, so that a subquery or a value
     * never stands in for the outer statement's clause.
     */
    static int topLevelIndexOf(String text, String needle) {
        String haystack = text.toLowerCase(Locale.ROOT)
        if (haystack.length() != text.length()) {
            haystack = text
        }
        int depth = 0
        boolean quoted = false
        for (int i = 0; i < haystack.length(); i++) {
            char c = haystack.charAt(i)
            if (c == ('\'' as char)) {
                quoted = !quoted
            }
            else if (!quoted) {
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
            }
        }
        -1
    }
}

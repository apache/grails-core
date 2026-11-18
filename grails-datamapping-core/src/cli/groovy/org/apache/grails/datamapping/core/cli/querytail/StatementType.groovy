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

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailRecord

/**
 * The kind of a statement, as it was recorded: without comments, and with its whitespace collapsed.
 * A SQL statement is told by the keyword that opens it, and a MongoDB command by its name, which is
 * the first word of a recorded command. A statement that opens with {@code WITH} is of the kind of
 * the statement its common table expressions are written for, the first verb outside them, so a
 * delete that reads them is a delete. A Cypher statement opens with a clause rather than a verb,
 * and may open with {@code WITH}, so it is told by the clauses that write in it: one that deletes is
 * a delete, else one that creates or merges is an insert, else one that sets or removes is an update,
 * and one that does none of those is a select. A statement that opens with {@code EXPLAIN} or
 * {@code PROFILE} is Cypher when what follows is; else it is the plan of a SQL statement, or a
 * MongoDB {@code explain} command, which runs nothing it plans and is of no other kind.
 *
 * @since 9.0
 */
@CompileStatic
enum StatementType {

    SELECT, INSERT, UPDATE, DELETE, OTHER

    /** The clauses only Cypher opens a statement with. */
    private static final Set<String> CYPHER_CLAUSES = ['match', 'optional', 'unwind', 'return', 'foreach'] as Set<String>

    /**
     * The words a Cypher statement may open with ahead of its first clause, which a SQL statement and a
     * MongoDB command may open with too: the statement is Cypher only when what follows them is.
     */
    private static final Set<String> PLAN_PREFIXES = ['explain', 'profile'] as Set<String>

    private static final Pattern CYPHER_WRITING_CLAUSE = Pattern.compile('(?i)\\b(create|merge|set|remove|delete)\\b')

    /** The verbs of the statement common table expressions are written for, which ends them. */
    private static final Pattern VERB = Pattern.compile('(?i)(?:select|insert|update|delete|merge)\\b')

    private static final String WITH = 'with'

    /**
     * @param sql a statement as it was recorded
     * @return its kind
     */
    static StatementType of(String sql) {
        if (sql == null) {
            return OTHER
        }
        String keyword = firstKeyword(sql)
        if (isCypher(sql, keyword)) {
            return ofCypher(sql)
        }
        if (PLAN_PREFIXES.contains(keyword)) {
            // a plan of a SQL statement, or a MongoDB explain command: neither runs what it plans
            return OTHER
        }
        if (keyword == WITH) {
            // a statement with no verb outside its common table expressions is Cypher, which may open with WITH
            int statement = outerStatement(sql)
            return statement > 0 ? of(sql.substring(statement)) : ofCypher(sql)
        }
        switch (keyword) {
            case 'select':
            case 'find':
            case 'aggregate':
            case 'count':
            case 'distinct':
            case 'getmore':
                return SELECT
            case 'insert':
                return INSERT
            case 'update':
            case 'findandmodify':
                return UPDATE
            case 'delete':
                return DELETE
            default:
                return OTHER
        }
    }

    /**
     * @param sql a statement as it was recorded
     * @return whether it is Cypher: it opens with a clause only Cypher has, or with {@code CREATE} or
     * {@code MERGE} followed by a pattern
     */
    static boolean isCypher(String sql) {
        sql != null && isCypher(sql, firstKeyword(sql))
    }

    /**
     * Finds the statement the common table expressions a statement opens with are written for: the
     * first verb outside their parentheses, string literals and quoted names.
     *
     * @param sql a statement as it was recorded
     * @return the index of that statement, 0 when the statement does not open with {@code WITH}, or -1
     * when no verb follows its common table expressions
     */
    static int outerStatement(String sql) {
        if (firstKeyword(sql) != WITH) {
            return 0
        }
        Matcher verb = VERB.matcher(sql)
        int depth = 0
        int i = WITH.length()
        while (i < sql.length()) {
            char c = sql.charAt(i)
            if (c == ('(' as char)) {
                depth++
            }
            else if (c == (')' as char)) {
                depth = Math.max(0, depth - 1)
            }
            else if (depth == 0 && !isNamePart(sql.charAt(i - 1)) && verb.region(i, sql.length()).lookingAt()) {
                return i
            }
            i = QueryTailRecord.quotedEnd(sql, i)
        }
        -1
    }

    private static boolean isCypher(String text, String keyword) {
        if (CYPHER_CLAUSES.contains(keyword)) {
            return true
        }
        if (PLAN_PREFIXES.contains(keyword)) {
            String planned = text.substring(keyword.length()).trim()
            return isCypher(planned, firstKeyword(planned))
        }
        if (keyword == 'create' || keyword == 'merge') {
            int next = keyword.length()
            while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
                next++
            }
            return next < text.length() && text.charAt(next) == '('
        }
        false
    }

    private static StatementType ofCypher(String text) {
        StatementType type = SELECT
        Matcher clause = CYPHER_WRITING_CLAUSE.matcher(text)
        while (clause.find()) {
            switch (clause.group(1).toLowerCase(Locale.ROOT)) {
                case 'delete':
                    return DELETE
                case 'create':
                case 'merge':
                    type = INSERT
                    break
                default:
                    if (type == SELECT) {
                        type = UPDATE
                    }
            }
        }
        type
    }

    private static boolean isNamePart(char c) {
        Character.isLetterOrDigit(c) || c == ('_' as char) || c == ('$' as char) || c == ('@' as char) || c == ('#' as char)
    }

    private static String firstKeyword(String text) {
        int end = 0
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++
        }
        text.substring(0, end).toLowerCase(Locale.ROOT)
    }
}

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

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * The kind of a statement. A SQL statement is told by the keyword that opens it, and a MongoDB
 * command by its name, which is the first word of a recorded command. A Cypher statement opens with
 * a clause rather than a verb, so it is told by the clauses that write in it: one that deletes is a
 * delete, else one that creates or merges is an insert, else one that sets or removes is an update,
 * and one that does none of those is a select.
 *
 * @since 8.1
 */
@CompileStatic
enum StatementType {

    SELECT, INSERT, UPDATE, DELETE, OTHER

    private static final Pattern LEADING_COMMENT = Pattern.compile('^(\\s*/\\*.*?\\*/)*\\s*', Pattern.DOTALL)

    /** The clauses only Cypher opens a statement with. */
    private static final Set<String> CYPHER_CLAUSES = ['match', 'optional', 'unwind', 'return', 'foreach', 'profile',
                                                       'explain'] as Set<String>

    private static final Pattern CYPHER_WRITING_CLAUSE = Pattern.compile('(?i)\\b(create|merge|set|remove|delete)\\b')

    /**
     * @param sql a statement, with or without leading comments
     * @return its kind
     */
    static StatementType of(String sql) {
        if (sql == null) {
            return OTHER
        }
        String text = LEADING_COMMENT.matcher(sql).replaceFirst('')
        String keyword = firstKeyword(text)
        if (isCypher(text, keyword)) {
            return ofCypher(text)
        }
        switch (keyword) {
            case 'select':
            case 'with':
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
     * @param sql a statement, with or without leading comments
     * @return whether it is Cypher: it opens with a clause only Cypher has, or with {@code CREATE} or
     * {@code MERGE} followed by a pattern
     */
    static boolean isCypher(String sql) {
        if (sql == null) {
            return false
        }
        String text = LEADING_COMMENT.matcher(sql).replaceFirst('')
        isCypher(text, firstKeyword(text))
    }

    private static boolean isCypher(String text, String keyword) {
        if (CYPHER_CLAUSES.contains(keyword)) {
            return true
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

    private static String firstKeyword(String text) {
        int end = 0
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++
        }
        text.substring(0, end).toLowerCase(Locale.ROOT)
    }
}

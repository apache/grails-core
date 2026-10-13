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

import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * The kind of a SQL statement, by its first keyword.
 *
 * @since 8.1
 */
@CompileStatic
enum SqlStatementType {

    SELECT, INSERT, UPDATE, DELETE, OTHER

    private static final Pattern LEADING_COMMENT = Pattern.compile('^(\\s*/\\*.*?\\*/)*\\s*', Pattern.DOTALL)

    /**
     * @param sql a statement, with or without leading comments
     * @return its kind
     */
    static SqlStatementType of(String sql) {
        if (sql == null) {
            return OTHER
        }
        String text = LEADING_COMMENT.matcher(sql).replaceFirst('')
        int end = 0
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++
        }
        switch (text.substring(0, end).toLowerCase(Locale.ROOT)) {
            case 'select':
            case 'with':
                return SELECT
            case 'insert':
                return INSERT
            case 'update':
                return UPDATE
            case 'delete':
                return DELETE
            default:
                return OTHER
        }
    }
}

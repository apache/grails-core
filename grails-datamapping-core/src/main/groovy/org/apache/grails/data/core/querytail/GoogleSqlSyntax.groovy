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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * GoogleSQL, the dialect of Cloud Spanner: single and double quotes delimit strings, in which a
 * backslash escapes the character after it, even in a raw string, and a quote written twice is two
 * strings; a string that opens with its quote written three times holds any other quote and ends
 * only at its quote written three times again; backticks delimit a name, in which a backslash
 * escapes; {@code #} starts a line comment; and {@code @} is part of a name.
 */
@CompileStatic
@PackageScope
final class GoogleSqlSyntax extends StandardSqlSyntax {

    GoogleSqlSyntax() {
        super('GoogleSQL')
    }

    @Override
    int endOfComment(String sql, int index) {
        sql.charAt(index) == '#' ? endOfLine(sql, index) : super.endOfComment(sql, index)
    }

    @Override
    int endOfValue(String sql, int index) {
        char quote = sql.charAt(index)
        if (quote != '\'' && quote != '"') {
            return super.endOfValue(sql, index)
        }
        if (index + 2 < sql.length() && sql.charAt(index + 1) == quote && sql.charAt(index + 2) == quote) {
            return endOfTripleQuoted(sql, index, quote)
        }
        endOfQuoted(sql, index, quote, true, false)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        sql.charAt(index) == '`' ? endOfQuoted(sql, index, '`' as char, true, false) : NONE
    }

    @Override
    protected boolean backslashEscapes() {
        true
    }

    @Override
    protected boolean doubleQuotedStrings() {
        true
    }

    @Override
    protected boolean isNamePart(char c) {
        super.isNamePart(c) || c == '#' || c == '@'
    }

    private static int endOfTripleQuoted(String sql, int start, char quote) {
        int i = start + 3
        while (i < sql.length()) {
            char c = sql.charAt(i)
            if (c == '\\') {
                i += 2
            }
            else if (c == quote && i + 2 < sql.length() && sql.charAt(i + 1) == quote && sql.charAt(i + 2) == quote) {
                return i + 3
            }
            else {
                i++
            }
        }
        UNTERMINATED
    }
}

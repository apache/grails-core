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
 * Cypher, the query language of Neo4j: single and double quotes delimit a string, in which a
 * backslash escapes the character after it and a quote written twice is two strings; backticks
 * delimit a name, in which a backtick is written twice; {@code //} starts a line comment, and
 * {@code --} and {@code #} do not, as {@code -->} draws a relationship; block comments do not nest;
 * and a dollar starts a parameter. Neo4j 5 ends a line comment at a carriage return, which Neo4j 3.5
 * does not, so a statement with a carriage return that no line feed follows is read both ways. Neo4j 5
 * decodes a {@code \u} escape before it parses, where 3.5 reads it only inside a string, so a
 * backslash outside a string or a comment, which may spell a quote, withholds the statement.
 */
@CompileStatic
@PackageScope
final class CypherSyntax extends StandardSqlSyntax {

    private static final char BACKSLASH = '\\'
    private static final char QUOTE = '\''
    private static final char DOUBLE_QUOTE = '"'
    private static final char BACKTICK = '`'

    private final boolean carriageReturnEndsLine

    CypherSyntax(boolean carriageReturnEndsLine) {
        super('Cypher')
        this.carriageReturnEndsLine = carriageReturnEndsLine
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
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
        if (sql.charAt(index) != BACKTICK) {
            return NONE
        }
        int end = endOfQuoted(sql, index, BACKTICK, false, true)
        if (end > 0) {
            int backslash = sql.indexOf(BACKSLASH as String, index)
            if (backslash >= 0 && backslash < end) {
                return UNTERMINATED
            }
        }
        end
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
}

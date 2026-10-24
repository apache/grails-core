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
 * H2: block comments nest, {@code //} starts a line comment, {@code $$} quotes a string without a
 * tag, backticks delimit a name in every compatibility mode, and square brackets delimit a name in
 * the SQL Server mode, where they are never a subscript, which is what a bracket after a name, a
 * closing parenthesis or a closing bracket is in the other modes. A bracket after whitespace or a
 * comment, with or without whitespace between the comment and the bracket, is a name in the SQL
 * Server mode but opens an array literal after the {@code ARRAY} keyword, which may stand any
 * distance before it, so a statement with one is read both ways: a name, which is recorded as
 * written, agrees with itself, while an array literal, whose elements are values, does not and
 * withholds the statement.
 */
@CompileStatic
@PackageScope
final class H2SqlSyntax extends StandardSqlSyntax {

    private final boolean bracketAfterGapIsName

    H2SqlSyntax() {
        this(true)
    }

    private H2SqlSyntax(boolean bracketAfterGapIsName) {
        super('H2')
        this.bracketAfterGapIsName = bracketAfterGapIsName
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
        if (!hasBracketAfterGap(sql)) {
            return List.<QueryTailSyntax>of(this)
        }
        List.<QueryTailSyntax>of(new H2SqlSyntax(true), new H2SqlSyntax(false))
    }

    @Override
    int endOfComment(String sql, int index) {
        sql.startsWith('//', index) ? endOfLine(sql, index) : super.endOfComment(sql, index)
    }

    @Override
    int endOfValue(String sql, int index) {
        int end = endOfDollarQuoted(sql, index, false)
        end != NONE ? end : super.endOfValue(sql, index)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        char c = sql.charAt(index)
        if (c == '`') {
            return endOfQuoted(sql, index, '`' as char, false, true)
        }
        if (c == '[' && !subscript(sql, index) && (bracketAfterGapIsName || !afterGap(sql, index))) {
            return endOfQuoted(sql, index, ']' as char, false, false)
        }
        super.endOfQuotedName(sql, index)
    }

    @Override
    protected boolean nestedComments() {
        true
    }

    private boolean subscript(String sql, int index) {
        char previous = previous(sql, index)
        isNamePart(previous) || previous == ')' || previous == ']'
    }

    /**
     * Whether whitespace or a comment ends right before the bracket. A line comment ends with a line
     * break, so the two characters that close a block comment are the only other ending to look for.
     */
    private static boolean afterGap(String sql, int index) {
        if (index == 0) {
            return false
        }
        Character.isWhitespace(sql.charAt(index - 1)) || (index > 1 && sql.startsWith('*/', index - 2))
    }

    private static boolean hasBracketAfterGap(String sql) {
        for (int i = sql.indexOf('['); i >= 0; i = sql.indexOf('[', i + 1)) {
            if (afterGap(sql, i)) {
                return true
            }
        }
        false
    }
}

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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * MySQL and MariaDB: double quotes delimit a string unless {@code ANSI_QUOTES} is set, so they are
 * always read as one; backticks delimit a name, in which a backtick is written twice; a backslash
 * escapes unless {@code NO_BACKSLASH_ESCAPES} is set, which the redactor cannot see, so a statement
 * with a backslash is read both ways; {@code #} starts a line comment, and so does {@code --} when
 * whitespace or a control character follows it, a character outside ASCII being whitespace or not
 * by the connection's character set, which the redactor cannot see either, so a statement with
 * one after {@code --} is read both ways; a line comment ends only at a line feed; {@code #} and
 * {@code @} are part of a name; and {@code /*!} or {@code /*M!} opens a comment whose content runs
 * as SQL on the server versions it names, so a statement with one is withheld.
 */
@CompileStatic
@PackageScope
final class MySqlSyntax extends StandardSqlSyntax {

    private final boolean backslashEscapes
    private final boolean nonAsciiAfterDashesComment

    MySqlSyntax(boolean backslashEscapes, boolean nonAsciiAfterDashesComment) {
        super('MySQL')
        this.backslashEscapes = backslashEscapes
        this.nonAsciiAfterDashesComment = nonAsciiAfterDashesComment
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
        boolean backslash = sql.indexOf('\\') >= 0
        boolean nonAscii = hasNonAsciiAfterDashes(sql)
        if (!backslash && !nonAscii) {
            return List.<QueryTailSyntax>of(this)
        }
        List<QueryTailSyntax> readings = []
        for (boolean escapes in (backslash ? [false, true] : [false])) {
            for (boolean comment in (nonAscii ? [false, true] : [false])) {
                readings.add(new MySqlSyntax(escapes, comment))
            }
        }
        readings
    }

    @Override
    int endOfComment(String sql, int index) {
        if (sql.startsWith('/*!', index) || sql.startsWith('/*M!', index)) {
            return UNTERMINATED
        }
        if (sql.charAt(index) == '#') {
            return endOfLine(sql, index)
        }
        if (sql.startsWith('--', index) && !dashesStartComment(sql, index)) {
            return NONE
        }
        super.endOfComment(sql, index)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        if (sql.charAt(index) == '`') {
            return endOfQuoted(sql, index, '`' as char, false, true)
        }
        super.endOfQuotedName(sql, index)
    }

    @Override
    protected boolean carriageReturnEndsLine() {
        false
    }

    @Override
    protected boolean backslashEscapes() {
        backslashEscapes
    }

    @Override
    protected boolean doubleQuotedStrings() {
        true
    }

    @Override
    protected boolean isNamePart(char c) {
        super.isNamePart(c) || c == '#' || c == '@'
    }

    private boolean dashesStartComment(String sql, int index) {
        if (index + 2 >= sql.length()) {
            return true
        }
        char after = sql.charAt(index + 2)
        if (isControlOrSpace(after)) {
            return true
        }
        after >= 0x80 && nonAsciiAfterDashesComment
    }

    private static boolean hasNonAsciiAfterDashes(String sql) {
        for (int i = sql.indexOf('--'); i >= 0; i = sql.indexOf('--', i + 1)) {
            if (i + 2 < sql.length() && sql.charAt(i + 2) >= 0x80) {
                return true
            }
        }
        false
    }
}

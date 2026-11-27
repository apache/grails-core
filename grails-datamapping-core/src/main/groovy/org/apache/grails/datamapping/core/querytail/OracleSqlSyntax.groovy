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
 * Oracle: {@code q'[...]'}, also written {@code nq'[...]'}, quotes a string with a delimiter of its
 * own, which closes as {@code ]'}, {@code )'}, {@code }'} or {@code >'} after a bracket and as the
 * delimiter written again before the quote otherwise, the quote itself included; {@code #} and
 * {@code @} are part of a name; and a line comment ends only at a line feed.
 */
@CompileStatic
@PackageScope
final class OracleSqlSyntax extends StandardSqlSyntax {

    OracleSqlSyntax() {
        super('Oracle')
    }

    @Override
    protected boolean carriageReturnEndsLine() {
        false
    }

    @Override
    protected boolean isNamePart(char c) {
        super.isNamePart(c) || c == '#' || c == '@'
    }

    @Override
    protected int endOfString(String sql, int index) {
        char prefix = previous(sql, index)
        boolean alternative = (prefix == 'q' || prefix == 'Q') &&
                (standalone(sql, index - 1) || nationalPrefix(sql, index - 2)) &&
                index + 1 < sql.length() && !Character.isWhitespace(sql.charAt(index + 1))
        if (!alternative) {
            return super.endOfString(sql, index)
        }
        int close = sql.indexOf(closingDelimiter(sql.charAt(index + 1)) + '\'', index + 2)
        close < 0 ? UNTERMINATED : close + 2
    }

    private boolean nationalPrefix(String sql, int index) {
        index >= 0 && (sql.charAt(index) == 'n' || sql.charAt(index) == 'N') && standalone(sql, index)
    }

    private static String closingDelimiter(char open) {
        switch (open) {
            case '[': return ']'
            case '(': return ')'
            case '{': return '}'
            case '<': return '>'
            default: return String.valueOf(open)
        }
    }
}

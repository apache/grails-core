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
 * Microsoft SQL Server and Sybase ASE: square brackets delimit a name, in which the closing bracket
 * is written twice; block comments nest; a dollar before digits is an amount; {@code #} and
 * {@code @} are part of a name; and double quotes delimit a string when {@code QUOTED_IDENTIFIER}
 * is off, as it is by default on Sybase, so they are always read as one.
 */
@CompileStatic
@PackageScope
final class TransactSqlSyntax extends StandardSqlSyntax {

    TransactSqlSyntax() {
        super('Transact-SQL')
    }

    @Override
    int endOfValue(String sql, int index) {
        if (sql.charAt(index) == '$' && standalone(sql, index) && startsNumber(sql, index + 1)) {
            return endOfNumber(sql, index + 1)
        }
        super.endOfValue(sql, index)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        if (sql.charAt(index) == '[') {
            return endOfQuoted(sql, index, ']' as char, false, true)
        }
        super.endOfQuotedName(sql, index)
    }

    @Override
    protected boolean nestedComments() {
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
}

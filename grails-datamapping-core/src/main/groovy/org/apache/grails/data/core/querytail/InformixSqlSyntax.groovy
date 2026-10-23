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
 * Informix: double quotes delimit a string unless {@code DELIMIDENT} is set, so they are always
 * read as one, braces delimit a comment, which does not nest, and a line comment ends only at a
 * line feed.
 */
@CompileStatic
@PackageScope
final class InformixSqlSyntax extends StandardSqlSyntax {

    InformixSqlSyntax() {
        super('Informix')
    }

    @Override
    int endOfComment(String sql, int index) {
        if (sql.charAt(index) == '{') {
            int close = sql.indexOf('}', index + 1)
            return close < 0 ? UNTERMINATED : close + 1
        }
        super.endOfComment(sql, index)
    }

    @Override
    protected boolean carriageReturnEndsLine() {
        false
    }

    @Override
    protected boolean doubleQuotedStrings() {
        true
    }
}

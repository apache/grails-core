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
 * PostgreSQL: block comments nest, {@code $tag$} quotes a string, an {@code E} before a string
 * makes backslashes escape in it, and so does {@code standard_conforming_strings} being off in every
 * string, which the redactor cannot see, so a statement with a backslash is read both ways.
 */
@CompileStatic
@PackageScope
class PostgreSqlSyntax extends StandardSqlSyntax {

    private final boolean backslashEscapes

    PostgreSqlSyntax(boolean backslashEscapes) {
        this('PostgreSQL', backslashEscapes)
    }

    protected PostgreSqlSyntax(String name, boolean backslashEscapes) {
        super(name)
        this.backslashEscapes = backslashEscapes
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
        if (sql.indexOf('\\') < 0) {
            return List.<QueryTailSyntax>of(this)
        }
        List.<QueryTailSyntax>of(new PostgreSqlSyntax(false), new PostgreSqlSyntax(true))
    }

    @Override
    int endOfValue(String sql, int index) {
        int end = endOfDollarQuoted(sql, index, true)
        end != NONE ? end : super.endOfValue(sql, index)
    }

    @Override
    protected boolean nestedComments() {
        true
    }

    @Override
    protected boolean backslashEscapes() {
        backslashEscapes
    }

    @Override
    protected int endOfString(String sql, int index) {
        char prefix = previous(sql, index)
        boolean escapeString = (prefix == 'e' || prefix == 'E') && standalone(sql, index - 1)
        escapeString ? endOfQuoted(sql, index, '\'' as char, true, true) : super.endOfString(sql, index)
    }
}

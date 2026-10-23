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
 * CockroachDB: PostgreSQL's syntax, except that a backslash never escapes in a plain string, as
 * {@code standard_conforming_strings} cannot be turned off, and a line comment ends only at a line
 * feed.
 */
@CompileStatic
@PackageScope
final class CockroachSqlSyntax extends PostgreSqlSyntax {

    CockroachSqlSyntax() {
        super('CockroachDB', false)
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
        List.<QueryTailSyntax>of(this)
    }

    @Override
    protected boolean carriageReturnEndsLine() {
        false
    }
}

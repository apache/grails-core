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
 * HSQLDB: standard SQL, and backticks delimit a name in its MySQL syntax mode, where a backtick in
 * the name is written twice.
 */
@CompileStatic
@PackageScope
final class HsqldbSqlSyntax extends StandardSqlSyntax {

    HsqldbSqlSyntax() {
        super('HSQLDB')
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        if (sql.charAt(index) == '`') {
            return endOfQuoted(sql, index, '`' as char, false, true)
        }
        super.endOfQuotedName(sql, index)
    }
}

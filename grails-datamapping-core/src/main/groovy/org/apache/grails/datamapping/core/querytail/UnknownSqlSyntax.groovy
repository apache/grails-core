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
 * A SQL database that is not known: a statement is read under the readings of every known SQL
 * syntax, and recorded only when all of them agree. Read under this syntax alone, every statement
 * is withheld, as every character is a value that never ends.
 */
@CompileStatic
@PackageScope
final class UnknownSqlSyntax implements QueryTailSyntax {

    @Override
    List<QueryTailSyntax> readings(String sql) {
        List<QueryTailSyntax> readings = []
        for (QueryTailSyntax known in [H2, POSTGRESQL, COCKROACH, ORACLE, DB2, HSQLDB, HANA, INFORMIX, MYSQL,
                TRANSACT_SQL, GOOGLE_SQL]) {
            readings.addAll(known.readings(sql))
        }
        readings
    }

    @Override
    int endOfComment(String sql, int index) {
        NONE
    }

    @Override
    int endOfValue(String sql, int index) {
        UNTERMINATED
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        NONE
    }

    @Override
    String toString() {
        'unknown'
    }
}

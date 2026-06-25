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

package org.grails.orm.hibernate.query

import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.SQLServerDialect
import spock.lang.Specification
import spock.lang.Unroll

class RegexDialectPatternSpec extends Specification {

    @Unroll
    void "test findPatternForDialect for #dialect.class.simpleName"() {
        expect:
        RegexDialectPattern.findPatternForDialect(dialect) == expectedPattern

        where:
        dialect                  | expectedPattern
        new MySQLDialect()       | "?1 RLIKE ?2"
        new MariaDBDialect()     | "?1 RLIKE ?2"
        new PostgreSQLDialect()  | "?1 ~ ?2"
        new OracleDialect()      | "REGEXP_LIKE(?1, ?2)"
        new H2Dialect()          | "REGEXP_LIKE(?1, ?2)"
        new SQLServerDialect()   | "?1 LIKE ?2" // Fallback
    }
}

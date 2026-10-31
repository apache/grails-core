/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.dialect

import org.hibernate.dialect.CockroachDialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.DB2zDialect
import org.hibernate.dialect.DB2iDialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.SQLServerDialect
import spock.lang.Specification
import spock.lang.Unroll

class UniqueIndexNullsSpec extends Specification {

    @Unroll
    void 'the unique index of #dialect.class.simpleName treats a null in its key as #expected'() {
        expect:
        UniqueIndexNulls.forDialect(dialect) == expected

        where:
        dialect                  | expected
        new OracleDialect()      | UniqueIndexNulls.COMPARED
        new H2Dialect()          | UniqueIndexNulls.DISTINCT
        new PostgreSQLDialect()  | UniqueIndexNulls.DISTINCT
        new CockroachDialect()   | UniqueIndexNulls.DISTINCT
        new MySQLDialect()       | UniqueIndexNulls.DISTINCT
        new MariaDBDialect()     | UniqueIndexNulls.DISTINCT
        new SQLServerDialect()   | UniqueIndexNulls.DISTINCT
        new DB2Dialect()         | UniqueIndexNulls.COMPARED
        new DB2iDialect()        | UniqueIndexNulls.DISTINCT
        new DB2zDialect()        | UniqueIndexNulls.DISTINCT
    }
}

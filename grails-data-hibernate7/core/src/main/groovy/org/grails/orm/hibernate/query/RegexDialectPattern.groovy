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

import groovy.transform.CompileStatic
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect

@CompileStatic
enum RegexDialectPattern {

    MYSQL(MySQLDialect, '?1 RLIKE ?2'),
    MARIADB(MariaDBDialect, '?1 REGEXP ?2'),
    POSTGRES(PostgreSQLDialect, '?1 ~ ?2'),
    ORACLE(OracleDialect, 'REGEXP_LIKE(?1, ?2)'),
    H2(H2Dialect, 'REGEXP_LIKE(?1, ?2)'),
    // Default fallback
    DEFAULT(Dialect, '?1 LIKE ?2')

    private final Class<? extends Dialect> dialectClass
    private final String sqlPattern

    RegexDialectPattern(Class<? extends Dialect> dialectClass, String sqlPattern) {
        this.dialectClass = dialectClass
        this.sqlPattern = sqlPattern
    }

    /**
     * Resolves the pattern by checking if the runtime dialect is an instance of the supported dialect
     * class.
     */
    static String findPatternForDialect(Dialect runtimeDialect) {
        for (RegexDialectPattern p : values()) {
            if (p != DEFAULT && p.dialectClass.isInstance(runtimeDialect)) {
                return p.sqlPattern
            }
        }
        return DEFAULT.sqlPattern
    }

    String getSqlPattern() {
        return sqlPattern
    }

}

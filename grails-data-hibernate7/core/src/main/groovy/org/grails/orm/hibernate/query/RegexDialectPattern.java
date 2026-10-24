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
package org.grails.orm.hibernate.query;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;

import org.grails.orm.hibernate.dialect.DialectKeyed;

/**
 * The SQL that matches a value against a regular expression on each database, chosen for the dialect the way
 * every {@link DialectKeyed} enum is.
 */
public enum RegexDialectPattern implements DialectKeyed<RegexDialectPattern> {
    MYSQL(MySQLDialect.class, "?1 RLIKE ?2"),
    MARIADB(MariaDBDialect.class, "?1 REGEXP ?2"),
    POSTGRES(PostgreSQLDialect.class, "?1 ~ ?2"),
    ORACLE(OracleDialect.class, "REGEXP_LIKE(?1, ?2)"),
    H2(H2Dialect.class, "REGEXP_LIKE(?1, ?2)"),
    // Default fallback
    DEFAULT(Dialect.class, "?1 LIKE ?2");

    private final Class<? extends Dialect>[] dialectClasses;
    private final String sqlPattern;

    @SuppressWarnings("unchecked")
    RegexDialectPattern(Class<? extends Dialect> dialectClass, String sqlPattern) {
        this.dialectClasses = new Class[] { dialectClass };
        this.sqlPattern = sqlPattern;
    }

    /** Returns the pattern of the database the runtime dialect is for, {@link #DEFAULT}'s unless its dialect is listed. */
    public static String findPatternForDialect(Dialect runtimeDialect) {
        return DialectKeyed.forDialect(RegexDialectPattern.class, runtimeDialect).getSqlPattern();
    }

    @Override
    public Class<? extends Dialect>[] dialectClasses() {
        return dialectClasses.clone();
    }

    public String getSqlPattern() {
        return sqlPattern;
    }
}

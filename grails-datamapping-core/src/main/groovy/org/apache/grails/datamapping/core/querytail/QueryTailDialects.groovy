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

/**
 * The syntax the statements of a Hibernate session factory are redacted in, told by its dialect: that of
 * the dialect's database, where the redactor knows the database's lexical rules. A dialect is told apart
 * by the names of the classes it extends, so that the one table serves Hibernate 5 and Hibernate 7, whose
 * dialects are named differently, and the community dialects of Hibernate 7, which are not a dependency.
 *
 * <p>A dialect of SQL Server or Sybase, MySQL or MariaDB, CockroachDB, PostgreSQL, H2, Oracle, Db2, HSQLDB,
 * SAP HANA or Informix, or Hibernate 7's Cloud Spanner dialect, is redacted in the syntax of its database.
 * Derby's dialect, which extends Db2's in Hibernate 5, is redacted as Db2, whose rules its database shares.
 * Hibernate 7's dialect for a Cloud Spanner PostgreSQL database is not known, as the parser of such a
 * database has not been verified, and neither is SQL Anywhere's, a Transact-SQL dialect whose database
 * reads {@code //} as a comment. Any other dialect, such as Teradata's, SQLite's, Firebird's or one that is
 * not Hibernate's, as the Cloud Spanner dialect for Hibernate 5 is, is redacted as for a database that is
 * not known: a statement is then recorded only when every known syntax reads it the same way.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class QueryTailDialects {

    private static final String CORE = 'org.hibernate.dialect.'

    private static final String COMMUNITY = 'org.hibernate.community.dialect.'

    /**
     * The syntax of the dialects of each database, by class name, in the order they are told apart in: a dialect
     * is redacted in the syntax of the first it is or extends. SQL Anywhere's dialect extends Sybase's, and the
     * dialect for a Cloud Spanner PostgreSQL database extends PostgreSQL's, so they come first.
     */
    private static final Map<String, QueryTailSyntax> DIALECTS = Collections.unmodifiableMap([
            (CORE + 'SpannerPostgreSQLDialect'): QueryTailSyntax.UNKNOWN,
            (CORE + 'SybaseAnywhereDialect'): QueryTailSyntax.UNKNOWN,
            (COMMUNITY + 'SybaseAnywhereDialect'): QueryTailSyntax.UNKNOWN,
            (CORE + 'AbstractTransactSQLDialect'): QueryTailSyntax.TRANSACT_SQL,
            (CORE + 'SQLServerDialect'): QueryTailSyntax.TRANSACT_SQL,
            (CORE + 'SybaseDialect'): QueryTailSyntax.TRANSACT_SQL,
            (CORE + 'SpannerDialect'): QueryTailSyntax.GOOGLE_SQL,
            (CORE + 'MySQLDialect'): QueryTailSyntax.MYSQL,
            (CORE + 'CockroachDialect'): QueryTailSyntax.COCKROACH,
            (CORE + 'CockroachDB192Dialect'): QueryTailSyntax.COCKROACH,
            (CORE + 'PostgreSQLDialect'): QueryTailSyntax.POSTGRESQL,
            (CORE + 'PostgreSQL81Dialect'): QueryTailSyntax.POSTGRESQL,
            (CORE + 'H2Dialect'): QueryTailSyntax.H2,
            (CORE + 'OracleDialect'): QueryTailSyntax.ORACLE,
            (CORE + 'Oracle8iDialect'): QueryTailSyntax.ORACLE,
            (CORE + 'Oracle9Dialect'): QueryTailSyntax.ORACLE,
            (CORE + 'DB2Dialect'): QueryTailSyntax.DB2,
            (CORE + 'HSQLDialect'): QueryTailSyntax.HSQLDB,
            (CORE + 'HANADialect'): QueryTailSyntax.HANA,
            (CORE + 'AbstractHANADialect'): QueryTailSyntax.HANA,
            (CORE + 'InformixDialect'): QueryTailSyntax.INFORMIX,
            (COMMUNITY + 'InformixDialect'): QueryTailSyntax.INFORMIX,
    ] as LinkedHashMap<String, QueryTailSyntax>)

    private QueryTailDialects() {
    }

    /**
     * @param dialect the class of the dialect of a session factory, or null when it is not known
     * @return the syntax the statements of the session factory are redacted in
     */
    static QueryTailSyntax syntaxOf(Class<?> dialect) {
        Set<String> names = new HashSet<>()
        for (Class<?> type = dialect; type != null; type = type.superclass) {
            names.add(type.name)
        }
        for (Map.Entry<String, QueryTailSyntax> known : DIALECTS.entrySet()) {
            if (names.contains(known.key)) {
                return known.value
            }
        }
        QueryTailSyntax.UNKNOWN
    }
}

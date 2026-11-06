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
package org.grails.orm.hibernate.dialect;

import org.hibernate.dialect.CockroachDialect;
import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.DB2iDialect;
import org.hibernate.dialect.DB2zDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;

import org.grails.datastore.mapping.internal.ClassKeyed;
import org.grails.orm.hibernate.query.SqlSyntax;

/**
 * <p><strong>Internal API. Do not use it from application or plugin code.</strong> It is public only because the
 * packages of this module that share it are separate and Java has no narrower visibility between them. It is not part
 * of the supported Grails API: it carries no compatibility promise, and its constants, their properties and its methods
 * can change or be removed in any release, including a patch release, without a deprecation period. A class you
 * write against it can stop compiling, or start behaving differently, on the next upgrade.</p>
 *
 * <p>The one declaration of the SQL databases this module supports, and of every way they differ that Grails has to
 * know. Whatever depends on the database asks it here, by the Hibernate dialect the session factory connects with,
 * so that two readers of the same fact cannot disagree:</p>
 * <ul>
 * <li>{@link #sqlSyntax()}: how the database reads native SQL;</li>
 * <li>{@link #regexPattern()}: the SQL that matches a value against a regular expression;</li>
 * <li>{@link #uniqueIndexNulls()}: how the unique index Hibernate creates treats a {@code null} in the key.</li>
 * </ul>
 * <p>To support another database, add a constant here with its dialect classes and its properties. The constants are
 * tried in their declared order, so a constant for a dialect subclass is declared before the constant for its
 * superclass, and {@link #DEFAULT}, keyed on {@link Object}, stands for every database not listed and for a dialect that is not known. A store that is not SQL declares its own
 * definitions in its own module and does not use this one.</p>
 *
 * <p>The null handling of the unique index, by database:</p>
 * <ul>
 * <li>{@link UniqueIndexNulls#COMPARED}: the row of a key that is {@code null} in some columns is indexed and its
 * other columns are compared, so {@code ('en', null)} twice is a violation. Oracle indexes that way. On Db2 for Linux,
 * UNIX and Windows Hibernate creates the index with {@code exclude null keys}, which leaves out only a key that is
 * {@code null} in every column, as Oracle does.</li>
 * <li>{@link UniqueIndexNulls#DISTINCT}: H2, PostgreSQL, MySQL and MariaDB treat nulls as distinct, so such a row
 * never conflicts. SQL Server compares them, but Hibernate creates the index over nullable columns as a filtered
 * unique index that leaves the rows with a {@code null} out, which comes to the same. On Db2 for IBM i and z/OS
 * Hibernate creates {@code create unique where not null index}, under which nulls are not equal.</li>
 * </ul>
 *
 * @since 8.0.1
 */
public enum SupportedDatabase implements ClassKeyed<SupportedDatabase> {

    // MariaDB's dialect extends MySQL's, so it is declared first; REGEXP and RLIKE are synonyms on both
    MARIADB(SqlSyntax.MYSQL, "?1 REGEXP ?2", UniqueIndexNulls.DISTINCT, MariaDBDialect.class),

    MYSQL(SqlSyntax.MYSQL, "?1 RLIKE ?2", UniqueIndexNulls.DISTINCT, MySQLDialect.class),

    POSTGRESQL(SqlSyntax.POSTGRESQL, "?1 ~* ?2", UniqueIndexNulls.DISTINCT, PostgreSQLDialect.class),

    // reads native SQL as PostgreSQL does, and has no regular expression operator here
    COCKROACH(SqlSyntax.POSTGRESQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, CockroachDialect.class),

    ORACLE(SqlSyntax.ORACLE, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.COMPARED, OracleDialect.class),

    SQL_SERVER(SqlSyntax.TRANSACT_SQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, SQLServerDialect.class),

    // reads native SQL as SQL Server does, and has no regular expression operator here
    SYBASE(SqlSyntax.TRANSACT_SQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, SybaseDialect.class),

    H2(SqlSyntax.H2, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.DISTINCT, H2Dialect.class),

    // Db2 for IBM i and z/OS: their dialects extend the one for Linux, UNIX and Windows, so they are declared first
    DB2_IBM_I(SqlSyntax.DB2_NESTED_COMMENTS, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.DISTINCT, DB2iDialect.class),

    // runs REGEXP_LIKE only through the Db2 Analytics Accelerator, so it falls back as other databases do
    DB2_ZOS(SqlSyntax.DB2_NESTED_COMMENTS, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, DB2zDialect.class),

    DB2(SqlSyntax.SQL, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.COMPARED, DB2Dialect.class),

    DEFAULT(SqlSyntax.SQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, Object.class);

    private final SqlSyntax sqlSyntax;
    private final String regexPattern;
    private final UniqueIndexNulls uniqueIndexNulls;
    private final Class<?>[] dialectClasses;

    SupportedDatabase(SqlSyntax sqlSyntax, String regexPattern, UniqueIndexNulls uniqueIndexNulls,
            Class<?>... dialectClasses) {
        this.sqlSyntax = sqlSyntax;
        this.regexPattern = regexPattern;
        this.uniqueIndexNulls = uniqueIndexNulls;
        this.dialectClasses = dialectClasses;
    }

    /** Returns the database the dialect is for: {@link #DEFAULT} unless its dialect is listed. */
    public static SupportedDatabase forDialect(Dialect dialect) {
        return ClassKeyed.forInstance(SupportedDatabase.class, dialect);
    }

    /** How the database reads native SQL. */
    public SqlSyntax sqlSyntax() {
        return sqlSyntax;
    }

    /** The SQL that matches a value against a regular expression: {@code ?1} is the value and {@code ?2} the pattern. */
    public String regexPattern() {
        return regexPattern;
    }

    /** How the unique index of the database treats a {@code null} in its key. */
    public UniqueIndexNulls uniqueIndexNulls() {
        return uniqueIndexNulls;
    }

    @Override
    public Class<?>[] keyClasses() {
        return dialectClasses.clone();
    }
}

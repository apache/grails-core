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

import org.hibernate.dialect.CockroachDB192Dialect;
import org.hibernate.dialect.DB2390Dialect;
import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.Oracle8iDialect;
import org.hibernate.dialect.Oracle9Dialect;
import org.hibernate.dialect.PostgreSQL81Dialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;

import org.grails.datastore.mapping.config.UniqueNulls;
import org.grails.datastore.mapping.internal.ClassKeyed;

/**
 * <p><strong>Internal API. Do not use it from application or plugin code.</strong> It is public only because the
 * packages of this module that share it are separate and Java has no narrower visibility between them. It is not part
 * of the supported Grails API: it carries no compatibility promise, and its constants, their properties and its methods
 * can change or be removed in any release, including a patch release, without a deprecation period. A class you
 * write against it can stop compiling, or start behaving differently, on the next upgrade. What an application can
 * rely on is the {@code unique} constraint, the {@code grails.gorm.unique.nulls} setting and
 * {@link UniqueNulls}.</p>
 *
 * <p>The one declaration of the SQL databases this module supports, and of every way they differ that Grails has to
 * know, for Hibernate 5. The Hibernate 7 module declares its own: the two Hibernate versions render different SQL
 * for some databases, so the readings differ and are not shared. Whatever depends on the database asks it here, by
 * the Hibernate dialect the session factory connects with:</p>
 * <ul>
 * <li>{@link #regexPattern()}: the SQL that matches a value against a regular expression, which is {@code like}
 * where the database has no regular expression operator that Grails uses;</li>
 * <li>{@link #uniqueNulls()} and {@link #isEntirelyNullKeyExempt()}: how the unique index Hibernate creates treats a
 * {@code null} in the key.</li>
 * </ul>
 * <p>To support another database, add a constant here with its dialect classes and its properties. The constants are
 * tried in their declared order, so a constant for a dialect subclass is declared before the constant for its
 * superclass, and {@link #DEFAULT}, keyed on {@link Object}, stands for every database not listed and for a dialect that is not known.</p>
 *
 * <p>The null handling of the unique index, by database:</p>
 * <ul>
 * <li>{@link #ORACLE}: Oracle indexes a row that has a {@code null} in some of the key columns and compares its
 * other columns, so {@code ('en', null)} twice is a violation, and only a key that is {@code null} in every column is
 * left out. A {@code null} is a value: {@link UniqueNulls#VALUE}. The deprecated {@code OracleDialect} and
 * {@code Oracle9Dialect} extend {@code Dialect} directly, so they are matched as well as {@code Oracle8iDialect}.</li>
 * <li>{@link #SQL_SERVER} and {@link #SYBASE}: Hibernate 5.6 has no filtered-index delegate, so these dialects get
 * the plain {@code alter table ... add constraint ... unique (...)} of {@code DefaultUniqueDelegate}, and a plain
 * unique constraint compares {@code null}s on SQL Server and, for Sybase, treats them as equal as well:
 * {@link UniqueNulls#VALUE}.</li>
 * <li>{@link #DB2} and {@link #DB2_ZOS}: the {@code DB2UniqueDelegate} of every Db2 dialect of Hibernate 5 renders
 * the constraint over a nullable key as a plain {@code create unique index}, which treats a {@code null} as any other
 * value: {@link UniqueNulls#VALUE}.</li>
 * <li>Every other database, H2, PostgreSQL, MySQL and MariaDB among them, treats {@code null}s as distinct, so such
 * a row never conflicts: {@link UniqueNulls#SKIP}.</li>
 * </ul>
 * <p>H2 in a compatibility mode is not read here: on Hibernate 5 a query with a row limit fails in the modes that
 * change how a unique index treats a {@code null}, so H2 is always read as it is in its regular mode.</p>
 *
 * @since 9.0
 */
public enum SupportedDatabase implements ClassKeyed<SupportedDatabase> {

    MYSQL("?1 rlike ?2", UniqueNulls.SKIP, false, MySQLDialect.class),

    POSTGRESQL("?1 ~* ?2", UniqueNulls.SKIP, false, PostgreSQL81Dialect.class),

    // has no regular expression operator here, so rlike falls back to like as it does for every database not listed
    COCKROACH("?1 LIKE ?2", UniqueNulls.SKIP, false, CockroachDB192Dialect.class),

    ORACLE(" REGEXP_LIKE (?1, ?2)", UniqueNulls.VALUE, true, Oracle8iDialect.class, Oracle9Dialect.class),

    SQL_SERVER("?1 LIKE ?2", UniqueNulls.VALUE, false, SQLServerDialect.class),

    SYBASE("?1 LIKE ?2", UniqueNulls.VALUE, false, SybaseDialect.class),

    H2("?1 REGEXP ?2", UniqueNulls.SKIP, false, H2Dialect.class),

    // the dialect of Db2 for z/OS extends the one for Linux, UNIX and Windows, so it is declared first; it runs
    // REGEXP_LIKE only through the Db2 Analytics Accelerator, so rlike falls back to like there
    DB2_ZOS("?1 LIKE ?2", UniqueNulls.VALUE, false, DB2390Dialect.class),

    // Db2 for Linux, UNIX and Windows (11.1 and later) and Db2 for IBM i
    DB2(" REGEXP_LIKE (?1, ?2)", UniqueNulls.VALUE, false, DB2Dialect.class),

    DEFAULT("?1 LIKE ?2", UniqueNulls.SKIP, false, Object.class);

    private final String regexPattern;
    private final UniqueNulls uniqueNulls;
    private final boolean entirelyNullKeyExempt;
    private final Class<?>[] dialectClasses;

    SupportedDatabase(String regexPattern, UniqueNulls uniqueNulls, boolean entirelyNullKeyExempt,
            Class<?>... dialectClasses) {
        this.regexPattern = regexPattern;
        this.uniqueNulls = uniqueNulls;
        this.entirelyNullKeyExempt = entirelyNullKeyExempt;
        this.dialectClasses = dialectClasses;
    }

    /** Returns the database the dialect is for: {@link #DEFAULT} unless its dialect is listed. */
    public static SupportedDatabase forDialect(Dialect dialect) {
        return ClassKeyed.forInstance(SupportedDatabase.class, dialect);
    }

    /**
     * The SQL that matches a value against a regular expression: {@code ?1} is the value and {@code ?2} the pattern.
     * It is {@code like} for a database that has no regular expression operator that Grails uses.
     */
    public String regexPattern() {
        return regexPattern;
    }

    /** How a unique key treats a {@code null} on this database: a compared one is a value, a distinct one is skipped. */
    public UniqueNulls uniqueNulls() {
        return uniqueNulls;
    }

    /** Whether the unique index of the database leaves out a key that is {@code null} in every column. */
    public boolean isEntirelyNullKeyExempt() {
        return entirelyNullKeyExempt;
    }

    @Override
    public Class<?>[] keyClasses() {
        return dialectClasses.clone();
    }
}

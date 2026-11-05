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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.dialect.CockroachDialect;
import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.DB2iDialect;
import org.hibernate.dialect.DB2zDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;

import org.grails.datastore.mapping.config.UniqueNulls;
import org.grails.datastore.mapping.internal.ClassKeyed;
import org.grails.orm.hibernate.query.SqlSyntax;

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
 * know. Whatever depends on the database asks it here, by the Hibernate dialect the session factory connects with,
 * so that two readers of the same fact cannot disagree:</p>
 * <ul>
 * <li>{@link #sqlSyntax()}: how the database reads native SQL;</li>
 * <li>{@link #regexPattern()}: the SQL that matches a value against a regular expression;</li>
 * <li>{@link #uniqueIndexNulls()} and {@link #isEntirelyNullKeyExempt()}: how the unique index Hibernate creates
 * treats a {@code null} in the key.</li>
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
 * {@code null} in every column, as Oracle does ({@link #isEntirelyNullKeyExempt()}).</li>
 * <li>{@link UniqueIndexNulls#DISTINCT}: H2, PostgreSQL, MySQL and MariaDB treat nulls as distinct, so such a row
 * never conflicts. SQL Server compares them, but Hibernate creates the index over nullable columns as a filtered
 * unique index that leaves the rows with a {@code null} out, which comes to the same. On Db2 for IBM i and z/OS
 * Hibernate creates {@code create unique where not null index}, under which nulls are not equal.</li>
 * </ul>
 *
 * @since 9.0
 */
public enum SupportedDatabase implements ClassKeyed<SupportedDatabase> {

    // MariaDB's dialect extends MySQL's, and both read RLIKE
    MYSQL(SqlSyntax.MYSQL, "?1 RLIKE ?2", UniqueIndexNulls.DISTINCT, false, MySQLDialect.class),

    POSTGRESQL(SqlSyntax.POSTGRESQL, "?1 ~* ?2", UniqueIndexNulls.DISTINCT, false, PostgreSQLDialect.class),

    // reads native SQL as PostgreSQL does, and has no regular expression operator here
    COCKROACH(SqlSyntax.POSTGRESQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, false, CockroachDialect.class),

    ORACLE(SqlSyntax.ORACLE, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.COMPARED, true, OracleDialect.class),

    SQL_SERVER(SqlSyntax.TRANSACT_SQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, false,
            SQLServerDialect.class, SybaseDialect.class),

    H2(SqlSyntax.H2, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.DISTINCT, false, H2Dialect.class),

    // Db2 for IBM i and z/OS: their dialects extend the one for Linux, UNIX and Windows, so they are declared first
    DB2_IBM_I(SqlSyntax.DB2_NESTED_COMMENTS, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.DISTINCT, false, DB2iDialect.class),

    // runs REGEXP_LIKE only through the Db2 Analytics Accelerator, so it falls back as other databases do
    DB2_ZOS(SqlSyntax.DB2_NESTED_COMMENTS, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, false, DB2zDialect.class),

    DB2(SqlSyntax.SQL, "REGEXP_LIKE(?1, ?2)", UniqueIndexNulls.COMPARED, true, DB2Dialect.class),

    DEFAULT(SqlSyntax.SQL, "?1 LIKE ?2", UniqueIndexNulls.DISTINCT, false, Object.class);

    private static final Pattern H2_MODE_IN_URL = Pattern.compile("(?i)[;?&]MODE=([a-zA-Z0-9_]+)");

    private final SqlSyntax sqlSyntax;
    private final String regexPattern;
    private final UniqueIndexNulls uniqueIndexNulls;
    private final boolean entirelyNullKeyExempt;
    private final Class<?>[] dialectClasses;

    SupportedDatabase(SqlSyntax sqlSyntax, String regexPattern, UniqueIndexNulls uniqueIndexNulls,
            boolean entirelyNullKeyExempt, Class<?>... dialectClasses) {
        this.sqlSyntax = sqlSyntax;
        this.regexPattern = regexPattern;
        this.uniqueIndexNulls = uniqueIndexNulls;
        this.entirelyNullKeyExempt = entirelyNullKeyExempt;
        this.dialectClasses = dialectClasses;
    }

    /** Returns the database the dialect is for: {@link #DEFAULT} unless its dialect is listed. */
    public static SupportedDatabase forDialect(Dialect dialect) {
        return ClassKeyed.forInstance(SupportedDatabase.class, dialect);
    }

    /**
     * How a unique key treats a {@code null} on the database the dialect is for, as the validation has to read it to
     * agree with the index. H2 changes this with its compatibility mode, so for H2 the mode decides: the one the
     * database reports, or else the one written in the JDBC URL. A mode that does not change how a unique index treats
     * a {@code null} leaves H2 as it is.
     *
     * @param dialect the dialect of the session factory, or {@code null} when it is not known
     * @param reportedH2Mode the mode H2 reports, or {@code null}
     * @param jdbcUrl the JDBC URL, or {@code null}
     */
    public static UniqueKeyNulls uniqueKeyNulls(Dialect dialect, String reportedH2Mode, String jdbcUrl) {
        if (isH2(dialect)) {
            String spec = reportedH2Mode != null && !reportedH2Mode.isBlank() ? reportedH2Mode : jdbcUrl;
            H2Mode mode = H2Mode.of(spec);
            if (mode != null) {
                return mode.uniqueKeyNulls;
            }
        }
        SupportedDatabase database = forDialect(dialect);
        return new UniqueKeyNulls(database.uniqueIndexNulls, database.entirelyNullKeyExempt);
    }

    /** Whether the dialect is H2's, or a subclass or a copy of it. */
    public static boolean isH2(Dialect dialect) {
        return dialect != null && (dialect instanceof H2Dialect || dialect.getClass().getSimpleName().startsWith("H2"));
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

    /** Whether the unique index of the database leaves out a key that is {@code null} in every column. */
    public boolean isEntirelyNullKeyExempt() {
        return entirelyNullKeyExempt;
    }

    @Override
    public Class<?>[] keyClasses() {
        return dialectClasses.clone();
    }

    /**
     * How a unique key treats a {@code null}, as the validation reads it.
     *
     * @param uniqueIndexNulls how the index treats a {@code null} in its key
     * @param entirelyNullKeyExempt whether a key that is {@code null} in every column is left out of the index
     */
    public record UniqueKeyNulls(UniqueIndexNulls uniqueIndexNulls, boolean entirelyNullKeyExempt) {

        /** The reading the validation takes: a compared {@code null} is a value, a distinct one is skipped. */
        public UniqueNulls uniqueNulls() {
            return uniqueIndexNulls == UniqueIndexNulls.COMPARED ? UniqueNulls.VALUE : UniqueNulls.SKIP;
        }
    }

    /**
     * The H2 compatibility modes that change how a unique index treats a {@code null}: H2 follows the index of the
     * platform in these and keeps nulls distinct in every other mode, such as {@code DB2}, {@code PostgreSQL} and
     * {@code MySQL}, which carry the syntax of the platform they are named after and not its index.
     */
    private enum H2Mode {
        // a partly null key is compared, and an entirely null one is left out
        ORACLE(new UniqueKeyNulls(UniqueIndexNulls.COMPARED, true)),
        // every key is compared, an entirely null one too
        MSSQLSERVER(new UniqueKeyNulls(UniqueIndexNulls.COMPARED, false)),
        DERBY(new UniqueKeyNulls(UniqueIndexNulls.COMPARED, false));

        private final UniqueKeyNulls uniqueKeyNulls;

        H2Mode(UniqueKeyNulls uniqueKeyNulls) {
            this.uniqueKeyNulls = uniqueKeyNulls;
        }

        /** The mode written as {@code MODE=<name>} in a JDBC URL, or as the bare name, or {@code null} for another. */
        static H2Mode of(String jdbcUrlOrMode) {
            if (jdbcUrlOrMode == null) {
                return null;
            }
            String name = jdbcUrlOrMode;
            Matcher matcher = H2_MODE_IN_URL.matcher(jdbcUrlOrMode);
            if (matcher.find()) {
                name = matcher.group(1);
            }
            String normalized = name.trim().toUpperCase(Locale.ROOT);
            for (H2Mode candidate : values()) {
                if (candidate.name().equals(normalized)) {
                    return candidate;
                }
            }
            return null;
        }
    }
}

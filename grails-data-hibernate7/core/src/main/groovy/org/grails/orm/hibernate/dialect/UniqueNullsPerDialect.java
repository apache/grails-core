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

import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.DB2iDialect;
import org.hibernate.dialect.DB2zDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.OracleDialect;

import org.grails.datastore.mapping.config.DatabasePlatform;
import org.grails.datastore.mapping.config.UniqueNulls;

/**
 * How the unique index Hibernate creates for a {@code unique} constraint with a group treats a {@code null} in the key,
 * on each database, so that the validation can read {@code null}s the way the index will.
 * <ul>
 * <li>{@link #ORACLE} and {@link #DB2}: Oracle indexes a row that has a {@code null} in some of the key columns and
 * compares its other columns, so {@code ('en', null)} twice is a violation, and only a key that is {@code null} in
 * every column is left out. Db2 for Linux, UNIX and Windows comes to the same: Hibernate creates the index with
 * {@code exclude null keys}. A {@code null} is a value: {@link UniqueNulls#VALUE}.</li>
 * <li>{@link #DB2_IBM_I}, {@link #DB2_ZOS} and {@link #DEFAULT}: H2, PostgreSQL, MySQL and MariaDB treat {@code null}s
 * as distinct, so such a row never conflicts. SQL Server compares them, but Hibernate renders the index for a nullable
 * key as a filtered unique index that leaves the rows with a {@code null} out, which comes to the same, and on Db2 for
 * IBM i and z/OS as {@code create unique where not null index}, under which {@code null}s are not equal.
 * {@link UniqueNulls#SKIP}.</li>
 * </ul>
 *
 * @since 9.0
 */
public enum UniqueNullsPerDialect implements DialectKeyed<UniqueNullsPerDialect> {

    ORACLE(UniqueNulls.VALUE, true, OracleDialect.class),

    // Db2 for IBM i and z/OS: `create unique where not null index`, under which nulls are not equal; their dialects
    // extend the one for Linux, UNIX and Windows, so they come first
    DB2_IBM_I(UniqueNulls.SKIP, false, DB2iDialect.class),

    DB2_ZOS(UniqueNulls.SKIP, false, DB2zDialect.class),

    // Db2 for Linux, UNIX and Windows: `exclude null keys` leaves out only a key that is null in every column
    DB2(UniqueNulls.VALUE, true, DB2Dialect.class),

    // Compatibility modes (e.g. H2 in MODE=MSSQLServer or MODE=Derby) where all keys including all-null are compared
    COMPARE_ALL(UniqueNulls.VALUE, false),

    DEFAULT(UniqueNulls.SKIP, false, Dialect.class);

    private final UniqueNulls uniqueNulls;
    private final boolean entirelyNullKeyExempt;
    private final Class<? extends Dialect>[] dialectClasses;

    @SafeVarargs
    UniqueNullsPerDialect(UniqueNulls uniqueNulls, boolean entirelyNullKeyExempt, Class<? extends Dialect>... dialectClasses) {
        this.uniqueNulls = uniqueNulls;
        this.entirelyNullKeyExempt = entirelyNullKeyExempt;
        this.dialectClasses = dialectClasses;
    }

    /** The reading that fits the dialect, {@link #DEFAULT} unless the dialect is listed. */
    public static UniqueNullsPerDialect forDialect(Dialect dialect) {
        return forDialect(dialect, null, null);
    }

    /**
     * The reading that fits the dialect and optional JDBC URL, resolving compatibility modes (such as H2
     * in {@code MODE=Oracle} or {@code MODE=MSSQLServer}) when applicable.
     */
    public static UniqueNullsPerDialect forDialect(Dialect dialect, String jdbcUrl) {
        return forDialect(dialect, jdbcUrl, null);
    }

    /**
     * The reading that fits the dialect, optional JDBC URL, and optional runtime H2 mode, resolving
     * compatibility modes (such as H2 in {@code MODE=Oracle} or {@code MODE=MSSQLServer}) when applicable.
     */
    public static UniqueNullsPerDialect forDialect(Dialect dialect, String jdbcUrl, String h2Mode) {
        if (dialect != null && isH2(dialect)) {
            String modeSpec = (h2Mode != null && !h2Mode.trim().isEmpty()) ? h2Mode : jdbcUrl;
            if (modeSpec != null) {
                DatabasePlatform platform = DatabasePlatform.resolveForH2(modeSpec);
                if (platform == DatabasePlatform.ORACLE || platform == DatabasePlatform.DB2_LUW) {
                    return ORACLE;
                }
                if (platform == DatabasePlatform.SQL_SERVER || platform == DatabasePlatform.DERBY || platform == DatabasePlatform.SYBASE) {
                    return COMPARE_ALL;
                }
            }
        }
        return DialectKeyed.forDialect(UniqueNullsPerDialect.class, dialect);
    }

    private static boolean isH2(Dialect dialect) {
        return dialect instanceof org.hibernate.dialect.H2Dialect ||
                dialect.getClass().getSimpleName().startsWith("H2");
    }

    @Override
    public Class<? extends Dialect>[] dialectClasses() {
        return dialectClasses.clone();
    }

    public UniqueNulls uniqueNulls() {
        return uniqueNulls;
    }

    public boolean isEntirelyNullKeyExempt() {
        return entirelyNullKeyExempt;
    }
}

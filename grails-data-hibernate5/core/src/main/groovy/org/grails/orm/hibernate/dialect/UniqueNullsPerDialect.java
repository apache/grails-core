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

import java.util.Arrays;

import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.Oracle8iDialect;
import org.hibernate.dialect.Oracle9Dialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;

import org.grails.datastore.mapping.config.UniqueNulls;

/**
 * How the unique index Hibernate creates for a {@code unique} constraint with a group treats a {@code null} in the key,
 * on each database, so that the validation can read {@code null}s the way the index will.
 * <ul>
 * <li>{@link #ORACLE}: Oracle indexes a row that has a {@code null} in some of the key columns and compares its
 * other columns, so {@code ('en', null)} twice is a violation, and only a key that is {@code null} in every column is
 * left out. A {@code null} is a value: {@link UniqueNulls#VALUE}. The deprecated {@code OracleDialect} and
 * {@code Oracle9Dialect} extend {@code Dialect} directly, so they are matched as well as {@code Oracle8iDialect}.</li>
 * <li>{@link #SQL_SERVER} and {@link #SYBASE}: Hibernate 5.6 has no filtered-index delegate, so these dialects get
 * the plain {@code alter table ... add constraint ... unique (...)} of {@code DefaultUniqueDelegate}, and a plain
 * unique constraint compares {@code null}s on SQL Server and, for Sybase, treats them as equal as well, so
 * {@code ('en', null)} twice is a violation: {@link UniqueNulls#VALUE}.</li>
 * <li>{@link #DB2}: the {@code DB2UniqueDelegate} of every Db2 dialect of Hibernate 5 renders the constraint over a
 * nullable key as a plain {@code create unique index}, which treats a {@code null} as any other value on Db2 for
 * Linux, UNIX and Windows, IBM i and z/OS alike: {@link UniqueNulls#VALUE}.</li>
 * <li>{@link #DEFAULT}: H2, PostgreSQL, MySQL and MariaDB treat {@code null}s as distinct, so such a row never
 * conflicts. {@link UniqueNulls#SKIP}.</li>
 * </ul>
 *
 * @since 9.0
 */
public enum UniqueNullsPerDialect {

    ORACLE(UniqueNulls.VALUE, true, Oracle8iDialect.class, Oracle9Dialect.class),

    SQL_SERVER(UniqueNulls.VALUE, false, SQLServerDialect.class),

    SYBASE(UniqueNulls.VALUE, false, SybaseDialect.class),
    // every Db2 dialect of Hibernate 5 creates a plain unique index, which compares null values
    DB2(UniqueNulls.VALUE, false, DB2Dialect.class),

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
        return Arrays.stream(values())
                .filter(entry -> entry != DEFAULT && Arrays.stream(entry.dialectClasses).anyMatch(clazz -> clazz.isInstance(dialect)))
                .findFirst()
                .orElse(DEFAULT);
    }

    public UniqueNulls uniqueNulls() {
        return uniqueNulls;
    }

    public boolean isEntirelyNullKeyExempt() {
        return entirelyNullKeyExempt;
    }
}

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

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.OracleDialect;

/**
 * How the unique index Hibernate creates over nullable columns treats a null in the key, by database. This is one
 * fact about each database, and everything that has to agree with the index asks it here:
 * <ul>
 * <li>{@link #COMPARED}: Oracle indexes a row that has a null in some of the key columns and compares its other
 * columns, so {@code ('en', null)} twice is a violation, and only a key that is null in every column is left out.</li>
 * <li>{@link #DISTINCT}: H2, PostgreSQL, MySQL and MariaDB treat nulls as distinct, so a row with a null in its key never
 * conflicts. SQL Server compares them, but Hibernate creates the index over nullable columns as a filtered unique
 * index that leaves the rows with a null out, which comes to the same. DB2 is listed here without having been run:
 * Hibernate creates its index with {@code exclude null keys}, which may leave out only a key that is null in every
 * column, in which case DB2 belongs with {@link #COMPARED} (apache/grails-core#16746).</li>
 * </ul>
 *
 * @since 8.0.1
 */
public enum UniqueIndexNulls implements DialectKeyed<UniqueIndexNulls> {

    COMPARED(OracleDialect.class),

    DISTINCT(Dialect.class);

    private final Class<? extends Dialect>[] dialectClasses;

    @SafeVarargs
    UniqueIndexNulls(Class<? extends Dialect>... dialectClasses) {
        this.dialectClasses = dialectClasses;
    }

    /** Returns how the unique index of the database the dialect is for treats a null in its key. */
    public static UniqueIndexNulls forDialect(Dialect dialect) {
        return DialectKeyed.forDialect(UniqueIndexNulls.class, dialect);
    }

    @Override
    public Class<? extends Dialect>[] dialectClasses() {
        return dialectClasses.clone();
    }
}

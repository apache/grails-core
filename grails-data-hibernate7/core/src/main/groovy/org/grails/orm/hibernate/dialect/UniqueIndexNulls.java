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

/**
 * How the unique index Hibernate creates over nullable columns treats a null in the key, by database. This is one
 * fact about each database, and everything that has to agree with the index asks it here:
 * <ul>
 * <li>{@link #COMPARED}: the row of a key that is null in some columns is indexed and its other columns are
 * compared, so {@code ('en', null)} twice is a violation, and only a key that is null in every column is left out.
 * Oracle indexes that way. On Db2 for Linux, UNIX and Windows Hibernate creates the index with
 * {@code exclude null keys}: a unique index treats a null as any other value, and {@code exclude null keys} leaves out
 * only a key that is null in every column, which comes to the same.</li>
 * <li>{@link #DISTINCT}: H2, PostgreSQL, MySQL and MariaDB treat nulls as distinct, so a row with a null in its key never
 * conflicts. SQL Server compares them, but Hibernate creates the index over nullable columns as a filtered unique
 * index that leaves the rows with a null out, which comes to the same. On Db2 for IBM i and Db2 for z/OS Hibernate
 * creates it as {@code create unique where not null index}, under which nulls are not equal to each other.</li>
 * </ul>
 * The Db2 dialects for IBM i and z/OS extend the one for Linux, UNIX and Windows, so they are listed first.
 *
 * @since 8.0.1
 */
public enum UniqueIndexNulls implements DialectKeyed<UniqueIndexNulls> {

    DISTINCT(DB2iDialect.class, DB2zDialect.class, Dialect.class),

    COMPARED(OracleDialect.class, DB2Dialect.class);

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

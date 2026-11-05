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

import org.grails.datastore.mapping.internal.ClassKeyed;

/**
 * <p><strong>Internal API. Do not use it from application or plugin code.</strong> It is public only because the
 * packages of this module that share it are separate and Java has no narrower visibility between them. It is not part
 * of the supported Grails API: it carries no compatibility promise, and its constants, their properties and its methods
 * can change or be removed in any release, including a patch release, without a deprecation period. A class you
 * write against it can stop compiling, or start behaving differently, on the next upgrade.</p>
 *
 * <p>The one declaration of the SQL databases this module supports, and of every way they differ that Grails has to
 * know, for Hibernate 5. The Hibernate 7 module declares its own: the two Hibernate versions render different SQL
 * for some databases, so the readings differ and are not shared. Whatever depends on the database asks it here, by
 * the Hibernate dialect the session factory connects with:</p>
 * <ul>
 * <li>{@link #regexPattern()}: the SQL that matches a value against a regular expression, which is {@code like}
 * where the database has no regular expression operator that Grails uses.</li>
 * </ul>
 * <p>To support another database, add a constant here with its dialect classes and its properties. The constants are
 * tried in their declared order, so a constant for a dialect subclass is declared before the constant for its
 * superclass, and {@link #DEFAULT}, keyed on {@link Object}, stands for every database not listed and for a dialect that is not known.</p>
 *
 * @since 8.0.1
 */
public enum SupportedDatabase implements ClassKeyed<SupportedDatabase> {

    MYSQL("?1 rlike ?2", MySQLDialect.class),

    POSTGRESQL("?1 ~* ?2", PostgreSQL81Dialect.class),

    // has no regular expression operator here, so rlike falls back to like as it does for every database not listed
    COCKROACH("?1 LIKE ?2", CockroachDB192Dialect.class),

    ORACLE(" REGEXP_LIKE (?1, ?2)", Oracle8iDialect.class, Oracle9Dialect.class),

    SQL_SERVER("?1 LIKE ?2", SQLServerDialect.class),

    SYBASE("?1 LIKE ?2", SybaseDialect.class),

    H2("?1 REGEXP ?2", H2Dialect.class),

    // the dialect of Db2 for z/OS extends the one for Linux, UNIX and Windows, so it is declared first; it runs
    // REGEXP_LIKE only through the Db2 Analytics Accelerator, so rlike falls back to like there
    DB2_ZOS("?1 LIKE ?2", DB2390Dialect.class),

    // Db2 for Linux, UNIX and Windows (11.1 and later) and Db2 for IBM i
    DB2(" REGEXP_LIKE (?1, ?2)", DB2Dialect.class),

    DEFAULT("?1 LIKE ?2", Object.class);

    private final String regexPattern;
    private final Class<?>[] dialectClasses;

    SupportedDatabase(String regexPattern, Class<?>... dialectClasses) {
        this.regexPattern = regexPattern;
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

    @Override
    public Class<?>[] keyClasses() {
        return dialectClasses.clone();
    }
}

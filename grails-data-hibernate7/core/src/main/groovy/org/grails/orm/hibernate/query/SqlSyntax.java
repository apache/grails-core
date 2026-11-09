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

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;

import org.hibernate.SessionFactory;
import org.hibernate.dialect.CockroachDialect;
import org.hibernate.dialect.DB2iDialect;
import org.hibernate.dialect.DB2zDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.sqm.NodeBuilder;

import org.grails.orm.hibernate.dialect.DialectKeyed;

/**
 * How a query text is read: what, besides string literals in single quotes, identifiers in double quotes or
 * backquotes and block comments, it may hold. The syntax of native SQL is the database's, resolved from its Hibernate
 * dialect the way {@link RegexDialectPattern} resolves the regular expression operator, so that every reader of
 * native SQL in this package reads it as the database will. The databases differ in what a character means:
 * <ul>
 * <li>square brackets quote an identifier on SQL Server and Sybase, and subscript an array elsewhere;</li>
 * <li>a backslash escapes in a MySQL or MariaDB string, and in a PostgreSQL {@code E'...'} string only;</li>
 * <li>{@code $tag$...$tag$} is a string on PostgreSQL, {@code $$...$$} on H2, and {@code q'[...]'} on Oracle;</li>
 * <li>{@code #} starts a comment on MySQL, which also needs a space or a control character after {@code --};</li>
 * <li>block comments nest on PostgreSQL, SQL Server, Db2 for IBM i and Db2 for z/OS, and not elsewhere;</li>
 * <li>{@code //} starts a comment on H2, and an alias may be written as a string on MySQL and SQL Server.</li>
 * </ul>
 * The defaults of each server are assumed. The modes that change the lexical rules, such as {@code NO_BACKSLASH_ESCAPES}
 * and {@code ANSI_QUOTES} of MySQL, are not read from the server.
 * HQL has block comments only: Hibernate's parser rejects {@code --} and {@code //}, so {@code b.height--1} is a
 * subtraction there.
 *
 * @since 8.0.1
 */
public enum SqlSyntax implements DialectKeyed<SqlSyntax> {

    /**
     * Transact-SQL, as SQL Server and Sybase read it: {@code [square brackets]} quote an identifier, in which
     * {@code ]]} stands for a bracket, and block comments nest.
     */
    TRANSACT_SQL(EnumSet.of(Feature.LINE_COMMENTS, Feature.BRACKET_IDENTIFIERS, Feature.NESTED_BLOCK_COMMENTS,
            Feature.STRING_ALIASES),
            SQLServerDialect.class, SybaseDialect.class),

    /**
     * PostgreSQL and CockroachDB: {@code $tag$...$tag$} is a string, an {@code E'...'} string escapes with a
     * backslash, block comments nest, and the driver reads {@code ??} as a literal question mark.
     */
    POSTGRESQL(EnumSet.of(Feature.LINE_COMMENTS, Feature.DOLLAR_QUOTES, Feature.ESCAPE_STRINGS,
            Feature.NESTED_BLOCK_COMMENTS, Feature.DOUBLE_QUESTION_MARK_ESCAPE),
            PostgreSQLDialect.class, CockroachDialect.class),

    /**
     * MySQL and MariaDB: a backslash escapes in a string in single or double quotes, {@code #} starts a comment, and
     * {@code --} starts one only when whitespace or a control character follows.
     */
    MYSQL(EnumSet.of(Feature.LINE_COMMENTS, Feature.HASH_COMMENTS, Feature.DASH_COMMENT_NEEDS_SPACE,
            Feature.BACKSLASH_ESCAPES, Feature.STRING_ALIASES), MySQLDialect.class),

    /** Oracle: {@code q'[...]'}, with the delimiter of choice, is a string. */
    ORACLE(EnumSet.of(Feature.LINE_COMMENTS, Feature.ALTERNATIVE_QUOTES), OracleDialect.class),

    /** H2: {@code $$...$$} is a string, and {@code //} starts a comment as {@code --} does. */
    H2(EnumSet.of(Feature.LINE_COMMENTS, Feature.DOUBLE_SLASH_COMMENTS, Feature.UNTAGGED_DOLLAR_QUOTES), H2Dialect.class),

    /** Db2 for IBM i and Db2 for z/OS: block comments nest. */
    DB2_NESTED_COMMENTS(EnumSet.of(Feature.LINE_COMMENTS, Feature.NESTED_BLOCK_COMMENTS),
            DB2iDialect.class, DB2zDialect.class),

    /** The SQL standard, for every other database: line comments and block comments that do not nest. */
    SQL(EnumSet.of(Feature.LINE_COMMENTS), Dialect.class),

    /**
     * HQL, which has block comments only, neither bracket identifiers, dollar-quoted strings nor escapes; square
     * brackets index.
     */
    HQL(EnumSet.noneOf(Feature.class));

    /** A lexical rule that differs between the syntaxes; only the scanner of this package reads them. */
    enum Feature {
        /** {@code --} starts a comment that runs to the end of the line. */
        LINE_COMMENTS,
        /** {@code [square brackets]} quote an identifier, in which {@code ]]} stands for a bracket. */
        BRACKET_IDENTIFIERS,
        /** {@code $tag$...$tag$}, with or without a tag, is a string. */
        DOLLAR_QUOTES,
        /** {@code $$...$$} is a string, and a tag is not allowed. */
        UNTAGGED_DOLLAR_QUOTES,
        /** {@code //} starts a comment that runs to the end of the line. */
        DOUBLE_SLASH_COMMENTS,
        /** An alias may be written as a string in single quotes. */
        STRING_ALIASES,
        /** A string in single or double quotes escapes with a backslash. */
        BACKSLASH_ESCAPES,
        /** A string in single quotes written {@code E'...'} escapes with a backslash. */
        ESCAPE_STRINGS,
        /** {@code q'[...]'} is a string, closed by the matching delimiter and a quote. */
        ALTERNATIVE_QUOTES,
        /** {@code #} starts a comment that runs to the end of the line. */
        HASH_COMMENTS,
        /** {@code --} starts a comment only when whitespace or a control character follows it. */
        DASH_COMMENT_NEEDS_SPACE,
        /** A block comment may contain block comments. */
        NESTED_BLOCK_COMMENTS,
        /** {@code ??} is a literal question mark, not two placeholders. */
        DOUBLE_QUESTION_MARK_ESCAPE
    }

    private final Set<Feature> features;
    private final Class<? extends Dialect>[] dialectClasses;

    @SafeVarargs
    SqlSyntax(Set<Feature> features, Class<? extends Dialect>... dialectClasses) {
        this.features = features;
        this.dialectClasses = dialectClasses;
    }

    /** Returns the syntax of the native SQL of the database the dialect is for: {@link #SQL} unless its dialect is listed. */
    public static SqlSyntax forDialect(Dialect dialect) {
        return DialectKeyed.forDialect(SqlSyntax.class, dialect);
    }

    /** Returns the syntax of the native SQL of the database the session factory connects to. */
    public static SqlSyntax forSessionFactory(SessionFactory sessionFactory) {
        return forDialect(sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect());
    }

    /**
     * Returns the syntax of the native SQL of the database the criteria builder builds queries for, or {@link #SQL}
     * for a criteria builder that is not Hibernate's.
     */
    public static SqlSyntax forCriteriaBuilder(CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder instanceof NodeBuilder nodeBuilder ? forDialect(nodeBuilder.getQueryEngine().getDialect()) : SQL;
    }

    /** Whether this syntax has the lexical rule. */
    boolean has(Feature feature) {
        return features.contains(feature);
    }

    @Override
    public Class<? extends Dialect>[] dialectClasses() {
        return dialectClasses.clone();
    }
}

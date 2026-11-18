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
 * The lexical syntax of a database, as far as finding the values and the comments of a statement
 * goes: where a string, a number, a quoted name or a comment that starts at a given index ends.
 * {@link QueryTailRedactor} scans a statement by asking its syntax about each index in turn, so the
 * rules of a database live in its syntax alone.
 *
 * <p>Every SQL database shares the rules of standard SQL: a single-quoted string in which the quote
 * is written twice, a double-quoted name, a {@code --} comment to the end of the line and a
 * {@code /* ... *}{@code /} comment that does not nest. A syntax adds the rules of its database, as
 * verified against the database; the syntax of Cypher, which is not SQL, has rules of its own.
 * Where a rule depends on a server setting the redactor cannot see, such as whether a backslash
 * escapes in MySQL and PostgreSQL, the syntax offers one {@linkplain #readings reading} per value
 * of the setting, and a statement is recorded only when every reading finds the same values.</p>
 *
 * @since 9.0
 */
@CompileStatic
interface QueryTailSyntax {

    /** Returned by the {@code endOf...} methods when nothing of that kind starts at the index. */
    int NONE = 0

    /**
     * Returned by the {@code endOf...} methods when the token that starts at the index never ends, or
     * when the syntax cannot tell where the database ends it. Either way the statement is withheld.
     */
    int UNTERMINATED = -1

    /**
     * Not known, or a SQL database none of the other syntaxes reads exactly, such as SQLite, Firebird
     * or SQL Anywhere: its readings are those of every SQL syntax, so a statement is recorded only when
     * all of them agree, and it withholds every statement read under it alone.
     */
    QueryTailSyntax UNKNOWN = new UnknownSqlSyntax()

    /**
     * H2: double quotes and backticks delimit names, and so do square brackets in its SQL Server
     * compatibility mode, block comments nest, {@code //} starts a line comment, and {@code $$} quotes
     * a string. A bracket after whitespace or a comment, which is a name in that mode but opens an
     * array literal after {@code ARRAY}, is read both ways.
     */
    QueryTailSyntax H2 = new H2SqlSyntax()

    /**
     * PostgreSQL and EDB Postgres: block comments nest, {@code $tag$} quotes a string, an {@code E}
     * before a string makes backslashes escape in it, and so does {@code standard_conforming_strings}
     * being off in every string, which is read both ways.
     */
    QueryTailSyntax POSTGRESQL = new PostgreSqlSyntax(false)

    /**
     * CockroachDB: PostgreSQL's syntax, except that a backslash never escapes in a plain string and a
     * line comment ends only at a line feed.
     */
    QueryTailSyntax COCKROACH = new CockroachSqlSyntax()

    /**
     * Oracle: {@code q'[...]'} quotes a string with a delimiter of its own, which can be the quote
     * itself, {@code #} and {@code @} are part of a name, and a line comment ends only at a line feed.
     */
    QueryTailSyntax ORACLE = new OracleSqlSyntax()

    /**
     * Db2, and Derby with Hibernate 5: block comments nest, {@code #} and {@code @} are part of a
     * name, and a line comment ends only at a line feed.
     */
    QueryTailSyntax DB2 = new Db2SqlSyntax()

    /** HSQLDB: standard SQL, and backticks delimit a name in its MySQL syntax mode. */
    QueryTailSyntax HSQLDB = new HsqldbSqlSyntax()

    /**
     * SAP HANA: {@code #} and {@code @} are part of a name. Whether a line comment ends at a carriage
     * return is read both ways.
     */
    QueryTailSyntax HANA = new HanaSqlSyntax(false)

    /**
     * Informix: double quotes delimit a string unless {@code DELIMIDENT} is set, braces delimit a
     * comment, and a line comment ends only at a line feed.
     */
    QueryTailSyntax INFORMIX = new InformixSqlSyntax()

    /**
     * MySQL and MariaDB: double quotes delimit a string unless {@code ANSI_QUOTES} is set, backticks
     * delimit a name, a backslash escapes unless {@code NO_BACKSLASH_ESCAPES} is set, which is read
     * both ways, {@code #} starts a comment and so does {@code --} followed by whitespace or a control
     * character, a line comment ends only at a line feed, {@code #} and {@code @} are part of a name,
     * and {@code /*!} opens a comment whose content runs as SQL on some server versions, which withholds
     * the statement.
     */
    QueryTailSyntax MYSQL = new MySqlSyntax(false, false)

    /**
     * Microsoft SQL Server and Sybase ASE: square brackets delimit a name, block comments nest, a
     * dollar before digits is an amount, {@code #} and {@code @} are part of a name, and double quotes
     * delimit a string when {@code QUOTED_IDENTIFIER} is off, as it is by default on Sybase, so they
     * are always read as one. A procedure takes a bare word as a string, so a statement that runs one,
     * with {@code EXEC}, with the JDBC {@code call} escape a driver turns into it, or by naming it first
     * in the batch, takes {@linkplain #takesBareValues bare values}.
     */
    QueryTailSyntax TRANSACT_SQL = new TransactSqlSyntax()

    /**
     * GoogleSQL, the dialect of Cloud Spanner: backticks delimit a name, single and double quotes
     * delimit strings, a string that opens with its quote written three times ends only at the quote
     * written three times again and holds any other quote, a backslash always escapes the character
     * after it, a quote written twice is two strings, {@code #} starts a comment, and {@code @} is part
     * of a name.
     */
    QueryTailSyntax GOOGLE_SQL = new GoogleSqlSyntax()

    /**
     * Cypher, the query language of Neo4j: single and double quotes delimit a string, in which a
     * backslash escapes the character after it and a quote written twice is two strings, backticks
     * delimit a name, in which a backtick is written twice, {@code //} starts a line comment and
     * {@code --} does not, block comments do not nest, and a dollar starts a parameter. Whether a line
     * comment ends at a carriage return is read both ways, as Neo4j 5 ends it there and Neo4j 3.5 does
     * not. Neo4j 5 decodes a {@code \u} escape that no backslash escapes before it parses, wherever it
     * is written, so a statement with an escape that decodes into a quote, a backtick, a backslash, a
     * line break, an asterisk or a slash, which may end a string, a comment or a name elsewhere, is
     * withheld, and so is one with a backslash outside a string, a comment or a name, which may decode
     * into a value.
     */
    QueryTailSyntax CYPHER = new CypherSyntax(false)

    /**
     * The readings a statement has under this syntax: one for each value of every server setting the
     * redactor cannot see that changes where a value or a comment of the statement ends. A syntax whose
     * rules the statement does not depend on is its own only reading. A statement that a version of the
     * database reads in a way no reading follows has none, and is withheld.
     *
     * @param sql the statement
     * @return the syntaxes the statement is read under, or an empty list when it has no reading
     */
    default List<QueryTailSyntax> readings(String sql) {
        List.of(this)
    }

    /**
     * Whether the database may take a bare word of the statement for a value, as SQL Server takes the
     * arguments of a procedure: a word no syntax can tell from a name, so the statement is withheld. A
     * syntax whose database takes no value that way finds none.
     *
     * @param redacted the statement as this syntax redacted it: its comments spaces, its values
     * placeholders, and its names, quoted or not, as they are written
     * @return whether the statement takes a value from a bare word
     */
    default boolean takesBareValues(String redacted) {
        false
    }

    /**
     * Ends the comment that starts at the index.
     *
     * @param sql the statement
     * @param index an index into it
     * @return the index after the comment, {@link #NONE} when no comment starts there, or
     * {@link #UNTERMINATED} when the comment never ends or runs as SQL on some versions of the database
     */
    int endOfComment(String sql, int index)

    /**
     * Ends the value that starts at the index: a string, with the prefix before it left alone, a
     * number, or another literal of the database.
     *
     * @param sql the statement
     * @param index an index into it
     * @return the index after the value, {@link #NONE} when no value starts there, or
     * {@link #UNTERMINATED} when the value never ends or the syntax cannot tell where it ends
     */
    int endOfValue(String sql, int index)

    /**
     * Ends the quoted name that starts at the index.
     *
     * @param sql the statement
     * @param index an index into it
     * @return the index after the name, {@link #NONE} when no quoted name starts there, or
     * {@link #UNTERMINATED} when the name never ends
     */
    int endOfQuotedName(String sql, int index)
}

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

import spock.lang.Specification
import spock.lang.Unroll

import static org.apache.grails.datamapping.core.querytail.QueryTailRedactor.WITHHELD
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.COCKROACH
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.CYPHER
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.DB2
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.GOOGLE_SQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.H2
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.HANA
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.HSQLDB
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.INFORMIX
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.MYSQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.ORACLE
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.POSTGRESQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.TRANSACT_SQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.UNKNOWN

/**
 * The expectations here were verified against the databases: H2, HSQLDB, Derby and SQLite in
 * memory, and PostgreSQL, CockroachDB, MySQL, MariaDB, SQL Server, Oracle, Db2, Firebird, Informix
 * and the Cloud Spanner emulator in containers. SAP HANA is read from its documentation. Cypher was
 * verified against Neo4j 5 in a container and the embedded Neo4j 3.5 the tests run on.
 */
class QueryTailRedactorSpec extends Specification {

    static final String SECRET = 'example-secret'

    static final List<QueryTailSyntax> KNOWN = [H2, POSTGRESQL, COCKROACH, ORACLE, DB2, HSQLDB, HANA, INFORMIX, MYSQL,
                                              TRANSACT_SQL, GOOGLE_SQL]

    @Unroll
    void "every syntax reads standard SQL: #sql in #syntax"() {
        expect:
        redact(sql, syntax) == redacted

        where:
        [syntax, sql, redacted] << KNOWN.collectMany { QueryTailSyntax syntax ->
            [
                    ["select * from account where password='example-secret'", 'select * from account where password=?'],
                    ['select b1_0.id,b1_0.title from book b1_0 where b1_0.id=? fetch first ? rows only',
                     'select b1_0.id,b1_0.title from book b1_0 where b1_0.id=? fetch first ? rows only'],
                    ['select * from t where x=1.5e-3 or y=.5 or z=0x1F or w=-7 or v=12abc',
                     'select * from t where x=? or y=? or z=? or w=-? or v=?'],
                    ["/* from Account where password = 'example-secret' */ select a1_0.id from account a1_0",
                     'select a1_0.id from account a1_0'],
                    ['select 1 -- key=abc123\nfrom t', 'select ? from t'],
                    ["select N'example-secret', X'0F' from t", 'select N?, X? from t'],
            ].collect { [syntax] + it }
        }
    }

    @Unroll
    void "H2: #sql"() {
        expect:
        redact(sql, H2) == redacted

        where:
        sql                                                       || redacted
        'select 1 // key=abc123\nfrom t'                          || 'select ? from t'
        "select 1 /* a /* b */ 'example-secret' */ from t"        || 'select ? from t'
        'select "user".id, `it\'s`, [order] from "user"'           || 'select "user".id, `it\'s`, [order] from "user"'
        'select x,[it\'s],[order] from [user]'                     || 'select x,[it\'s],[order] from [user]'
        'select array[7,8][1], (a)[2] from t'                     || 'select array[?,?][?], (a)[?] from t'
        'select "ARR"[2], `arr`[7], arr[3] from t where y = ?[4]' || 'select "ARR"[?], `arr`[?], arr[?] from t where y = ?[?]'
        "select 'abc'['example-secret'], N'x'[5] from t"          || 'select ?[?], N?[?] from t'
        'select ARRAY[1, 2], [a] from t'                          || 'select ARRAY[?, ?], [a] from t'
        "select \$\$it's\$\$, \$tag\$, 'x' from t"                || 'select ?, $tag$, ? from t'
        "select 'C:\\temp\\', 'x' from t"                         || 'select ?, ? from t'
        'select 1 -- a\r, 2 from t'                               || 'select ? , ? from t'
        'select 1 # 2, @1, x#1 from t'                            || 'select ? # ?, @?, x#? from t'
    }

    @Unroll
    void "PostgreSQL: #sql"() {
        expect:
        redact(sql, POSTGRESQL) == redacted

        where:
        sql                                                                    || redacted
        "select E'it\\'s', \$\$example-secret\$\$, \$tag\$it's\$tag\$, \$1 from t" || 'select E?, ?, ?, $1 from t'
        "select 1 /* a /* b */ 'example-secret' */ from t"                     || 'select ? from t'
        'select 1 # 2, account_id#987654321, @987654321 from t'                || 'select ? # ?, account_id#?, @? from t'
        "select 'C:\\temp', 'x' from t"                                        || 'select ?, ? from t'
        'select 1 -- a\r, 2 from t'                                            || 'select ? , ? from t'
        'select "user".id from "user" where "user".name = \'x\''               || 'select "user".id from "user" where "user".name = ?'
        'select $\u20ac$example-secret$\u20ac$, $\u00e9_1$x$\u00e9_1$ from t'    || 'select ?, ? from t'
        'select $\u00a0$example-secret$\u00a0$, $_$x$_$ from t'              || 'select ?, ? from t'
        'select $\u20ac, $1 from t'                                           || 'select $\u20ac, $1 from t'
    }

    @Unroll
    void "CockroachDB: #sql"() {
        expect:
        redact(sql, COCKROACH) == redacted

        where:
        sql                                                       || redacted
        "select E'it\\'s', 'C:\\temp\\', \$tag\$x\$tag\$ from t"  || 'select E?, ?, ? from t'
        'select $\u20ac$example-secret$\u20ac$ from t'          || 'select ? from t'
        'select 1 -- a\r, 2 from t'                               || 'select ?'
        "select 1 /* a /* b */ 'example-secret' */ from t"        || 'select ? from t'
    }

    @Unroll
    void "Oracle: #sql"() {
        expect:
        redact(sql, ORACLE) == redacted

        where:
        sql                                                                                || redacted
        "select q'[it's example-secret]', nq'{x}', q''example-secret'', q'!it's!' from dual" || 'select q?, nq?, q?, q? from dual'
        'select t.col#, account_id#987654321 from t, emp@remote e where t.id = 1'          || 'select t.col#, account_id#987654321 from t, emp@remote e where t.id = ?'
        "select 'C:\\temp\\', 'x' from dual"                                               || 'select ?, ? from dual'
        "select 1 /* a /* b */ 'example-secret' */ from dual"                              || 'select ? ? */ from dual'
        'select 1 -- key=abc123\r, 2\nfrom dual'                                           || 'select ? from dual'
    }

    @Unroll
    void "Db2: #sql"() {
        expect:
        redact(sql, DB2) == redacted

        where:
        sql                                                                || redacted
        'select account_id#987654321, @v from t'                           || 'select account_id#987654321, @v from t'
        "select 1 /* a /* b */ 'example-secret' */ from sysibm.sysdummy1"  || 'select ? from sysibm.sysdummy1'
        "select 'C:\\temp\\', 'x' from t"                                  || 'select ?, ? from t'
        'select 1 -- a\r, 2\nfrom t'                                       || 'select ? from t'
    }

    @Unroll
    void "HSQLDB: #sql"() {
        expect:
        redact(sql, HSQLDB) == redacted

        where:
        sql                                                       || redacted
        "select 1 /* a /* b */ 'example-secret' */ from t"        || 'select ? ? */ from t'
        'select 1 // 2, `a``b`, "c" from t'                       || 'select ? // ?, `a``b`, "c" from t'
        "select U&'\\0041', B'101' from t"                        || 'select U&?, B? from t'
        'select 1 -- a\r, 2 from t'                               || 'select ? , ? from t'
    }

    @Unroll
    void "SAP HANA: #sql"() {
        expect:
        redact(sql, HANA) == redacted

        where:
        sql                                                       || redacted
        'select account_id#987654321 from #temp'                  || 'select account_id#987654321 from #temp'
        "select 1 /* a /* b */ 'example-secret' */ from dummy"    || 'select ? ? */ from dummy'
        'select 1 -- a\r\n, 2 from dummy'                         || 'select ? , ? from dummy'
    }

    @Unroll
    void "Informix: #sql"() {
        expect:
        redact(sql, INFORMIX) == redacted

        where:
        sql                                                       || redacted
        'select 1 { api-key=abc123 } from systables'              || 'select ? from systables'
        'select "example-secret", \'x\' from t'                   || 'select ?, ? from t'
        'select 1 -- a\r, 2\nfrom t'                              || 'select ? from t'
        "select 1 /* a /* b */ 'example-secret' */ from t"        || 'select ? ? */ from t'
    }

    @Unroll
    void "MySQL: #sql"() {
        expect:
        redact(sql, MYSQL) == redacted

        where:
        sql                                                                      || redacted
        'select * from `account` where password = "example-secret"'              || 'select * from `account` where password = ?'
        'select 1 # key=abc123\nfrom t'                                          || 'select ? from t'
        'select 1 -- key=abc123\nfrom t'                                         || 'select ? from t'
        'select 1 --\tkey=abc123\nfrom t'                                        || 'select ? from t'
        'select 1 --\u0001key=abc123\nfrom t'                                    || 'select ? from t'
        'select 1 --\u007fkey=abc123\nfrom t'                                    || 'select ? from t'
        'select 5--3'                                                            || 'select ?--?'
        'select 1 --'                                                            || 'select ?'
        'select 1 -- a\r, 2\nfrom t'                                             || 'select ? from t'
        'select 1 # a\r, 2\nfrom t'                                              || 'select ? from t'
        "select _utf8mb4'example-secret', x'0F', \"it's\", 0x1F, b'101' from t" || 'select _utf8mb4?, x?, ?, ?, b? from t'
        'select @1, @@rowcount, `a``b` from t'                                   || 'select @1, @@rowcount, `a``b` from t'
        'select /*+ MAX_EXECUTION_TIME(1000) */ 1 from t'                        || 'select ? from t'
        "select 'C:\\temp', 'x' from t"                                          || 'select ?, ? from t'
    }

    @Unroll
    void "Transact-SQL: #sql"() {
        expect:
        redact(sql, TRANSACT_SQL) == redacted

        where:
        sql                                                                      || redacted
        "select [order].[id] from [order] where [it's] = N'example-secret'"      || 'select [order].[id] from [order] where [it\'s] = N?'
        "select 1 /* outer /* inner */ 'example-secret' */ from #temp"           || 'select ? from #temp'
        'select $987654321, $.5, -$12.34, a$1, @v, @@rowcount from #temp'        || 'select ?, ?, -?, a$1, @v, @@rowcount from #temp'
        'select account_id#987654321 from account'                               || 'select account_id#987654321 from account'
        'SELECT "example-secret";'                                              || 'SELECT ?;'
        'select "it\'s ""example-secret""" from [t]'                             || 'select ? from [t]'
        'select 1 as [a]]b] -- a\r, 2'                                           || 'select ? as [a]]b] , ?'
        'select 1 --\u0001key=abc123\n, 2'                                       || 'select ? , ?'
        'select executed_at, [exec_id] from job_execution where id = 5'          || 'select executed_at, [exec_id] from job_execution where id = ?'
        ';with x as (select 1 as a) select a from x'                             || ';with x as (select ? as a) select a from x'
        '/* load */ select @exec from t'                                         || 'select @exec from t'
    }

    @Unroll
    void "GoogleSQL: #sql"() {
        expect:
        redact(sql, GOOGLE_SQL) == redacted

        where:
        sql                                                                      || redacted
        'select """a"example-secret"b""" from t'                                 || 'select ? from t'
        "select '''it's bob's example-secret''', 'x' from t"                     || 'select ?, ? from t'
        'select `account`.`id` from `account` where password = "example-secret"' || 'select `account`.`id` from `account` where password = ?'
        "select 'it\\'s', \"say \\\"hi\\\"\", `we\\`ird` from t"                || 'select ?, ?, `we\\`ird` from t'
        "select r'\\d+', b'ab', rb'x', R\"\"\"a\"\"\" from t"                   || 'select r?, b?, rb?, R? from t'
        "select '', 'it''s' from t"                                              || 'select ?, ?? from t'
        'select 1 # key=abc123\nfrom t'                                          || 'select ? from t'
        'select 1--key=abc123\nfrom t'                                           || 'select ? from t'
        "select 1 /* a /* b */ 'example-secret' */"                              || 'select ? ? */'
        'select 1 -- a\r, 2 from t'                                              || 'select ? , ? from t'
        'select 1 # a\r, 2 from t'                                               || 'select ? , ? from t'
        'select @p, 0x1F from t'                                                 || 'select @p, ? from t'
    }

    @Unroll
    void "Cypher: #sql"() {
        expect:
        redact(sql, CYPHER) == redacted

        where:
        sql                                                                      || redacted
        "MATCH (n:Book) WHERE n.title = 'example-secret' RETURN n"               || 'MATCH (n:Book) WHERE n.title = ? RETURN n'
        'MATCH (n:Book) WHERE n.title = "example-secret" RETURN n'               || 'MATCH (n:Book) WHERE n.title = ? RETURN n'
        'MATCH (n:Book) WHERE n.title = $1 RETURN n SKIP 10 LIMIT 10'            || 'MATCH (n:Book) WHERE n.title = $1 RETURN n SKIP ? LIMIT ?'
        'MATCH (n:Book) WHERE n.id = {id} RETURN n'                              || 'MATCH (n:Book) WHERE n.id = {id} RETURN n'
        'MATCH (a:Book)-->(b) WHERE a.pages > 1 RETURN b'                        || 'MATCH (a:Book)-->(b) WHERE a.pages > ? RETURN b'
        'RETURN 1 -- 2, 3 # 4'                                                   || 'RETURN ? -- ?, ? # ?'
        'RETURN 1 // key=abc123\nAS a'                                           || 'RETURN ? AS a'
        'RETURN 1 // key=abc123\r\n, 2'                                          || 'RETURN ? , ?'
        "RETURN 1 /* a /* b */ 'example-secret' */"                              || 'RETURN ? ? */'
        "RETURN 'it\\'s', \"say \\\"hi\\\"\", 'a''b'"                           || 'RETURN ?, ?, ??'
        "RETURN 1 AS `a``b`, 2 AS `it's`"                                        || "RETURN ? AS `a``b`, ? AS `it's`"
        'RETURN 0x1F, 0o17, 1.5e3, .5, -7, x1'                                   || 'RETURN ?, ?, ?, ?, -?, x1'
        "RETURN 'a\nb', 'a\\tb', 'C:\\\\users' /* C:\\\\users */ AS a"           || 'RETURN ?, ?, ? AS a'
        "RETURN 'caf" + '\\' + "u00e9' /* caf" + '\\' + "u00e9 */ AS a"          || 'RETURN ? AS a'
        'RETURN 1 AS `caf' + '\\' + 'u00e9`, 2 AS `C:' + '\\\\' + 'users`'       || 'RETURN ? AS `caf' + '\\' + 'u00e9`, ? AS `C:' + '\\\\' + 'users`'
        'RETURN 1 // caf' + '\\' + 'u00e9\nAS a, 2 // C:' + '\\\\' + 'u\nAS b'   || 'RETURN ? AS a, ? AS b'
        'RETURN 1 // C:' + '\\' + 'users\nAS a, 2 /* ' + '\\' + 'uzzzz */ AS b'  || 'RETURN ? AS a, ? AS b'
        "RETURN 'it" + '\\' + "U0027s', '" + '\\' + "u0041'"                     || 'RETURN ?, ?'
        'UNWIND $rows AS row CREATE (n:Book) SET n += row'                       || 'UNWIND $rows AS row CREATE (n:Book) SET n += row'
    }

    @Unroll
    void "a statement whose values cannot be told apart from its SQL is withheld: #reason"() {
        expect:
        QueryTailRedactor.redact(sql, syntax) == WITHHELD

        where:
        reason                                                           | syntax       | sql
        'a literal that never ends'                                      | H2           | "select * from account where password='example-secret"
        'a quoted name that never ends'                                  | POSTGRESQL   | 'select "example-secret from t'
        'a comment that never ends'                                      | ORACLE       | 'select 1 /* example-secret'
        'a dollar-quoted string that never ends'                         | POSTGRESQL   | 'select $$example-secret'
        'a bracketed name that never ends'                               | TRANSACT_SQL | 'select [example-secret from t'
        'a brace comment that never ends'                                | INFORMIX     | 'select 1 { example-secret'
        'a quote a backslash may or may not escape'                      | POSTGRESQL   | "select 'it\\'s', 'example-secret'"
        'a quote a backslash escapes unless the server says not'         | MYSQL        | "select 'it\\'s'"
        'a comment that starts or not by the connection character set'   | MYSQL        | 'select 1 --\u00a0example-secret\nfrom t'
        'a comment whose content runs as SQL on some MySQL versions'     | MYSQL        | "select 1 /*!50700 + length('example-secret') */"
        'a comment whose content runs as SQL on MariaDB'                 | MYSQL        | "select 1 /*M! + length('example-secret') */"
        'a line comment a lone carriage return may or may not end'       | HANA         | 'select 1 -- example-secret\r, 2'
        'a triple-quoted string that never ends'                         | GOOGLE_SQL   | "select '''example-secret'' from t"
        'a quoted name a backslash escapes in'                           | GOOGLE_SQL   | 'select `example-secret\\` from t'
        'double quotes before the dialect is known'                      | UNKNOWN      | 'select "example-secret" from t'
        'a hash before the dialect is known'                             | UNKNOWN      | 'select 1 # example-secret'
        'two slashes before the dialect is known'                        | UNKNOWN      | 'select 1 // example-secret'
        'braces before the dialect is known'                             | UNKNOWN      | 'select 1 { example-secret }'
        'a comment that nests or not before the dialect is known'        | UNKNOWN      | "select 1 /* a /* b */ 'example-secret' */"
        'a hash that is an operator or part of a name'                   | UNKNOWN      | 'select account_id#987654321 from account'
        'an at sign that is an operator or part of a name'               | UNKNOWN      | 'select @987654321 from t'
        'a dollar that prefixes a parameter or an amount'                | UNKNOWN      | 'select $987654321 from t'
        'an alternatively quoted string before the dialect is known'     | UNKNOWN      | "select q'[it's]' from dual"
        'a lone carriage return in a comment before the dialect is known' | UNKNOWN     | 'select 1 -- a\r example-secret'
        'a triple-quoted string before the dialect is known'             | UNKNOWN      | "select '''a'example-secret'b''' from t"
        'a doubled quote before the dialect is known'                    | UNKNOWN      | "select 'it''s', 'example-secret' from t"
        'a backslash before the dialect is known'                        | UNKNOWN      | "select 'C:\\temp\\' from t"
        'an H2 array literal after whitespace, or a name'                | H2           | "select ARRAY ['example-secret'] from t"
        'an H2 array literal after a comment, or a name'                 | H2           | "select ARRAY /* array */ ['example-secret'] from t"
        'an H2 array literal right after a comment, or a name'           | H2           | "select ARRAY/**/['example-secret'] from t"
        'an H2 array literal after a comment and no space, or a name'    | H2           | "select ARRAY /* array */['example-secret'] from t"
        'an H2 array literal after a line break, or a name'              | H2           | "select ARRAY -- array\n['example-secret'] from t"
        'an H2 name after whitespace that holds a quote, or an array'    | H2           | "select [it's] from t"
        'a Cypher string that never ends'                                | CYPHER       | "RETURN 'example-secret"
        'a Cypher comment that never ends'                               | CYPHER       | 'RETURN 1 /* example-secret'
        'a Cypher name that never ends'                                  | CYPHER       | 'RETURN 1 AS `example-secret'
        'a Cypher comment a lone carriage return may or may not end'     | CYPHER       | 'RETURN 1 // example-secret\r, 2'
        'a backslash outside a Cypher string, which may spell a quote'   | CYPHER       | 'RETURN ' + '\\' + 'u0027example-secret' + '\\' + 'u0027'
        'a Unicode escape in a Cypher name, which may end it'            | CYPHER       | 'RETURN 1 AS `a' + '\\' + 'u0060 + example-secret`'
        'a Unicode escape in a Cypher string, which may end it'          | CYPHER       | "RETURN 'a" + '\\' + "u0027 + 'example-secret' + " + '\\' + "u0027b'"
        'a Unicode escape of two u in a Cypher string, which may end it' | CYPHER       | 'RETURN "a' + '\\' + 'uu0022 + "example-secret" + ' + '\\' + 'uu0022b"'
        'a Unicode escape after an escaped backslash in a Cypher string' | CYPHER       | "RETURN 'a" + '\\\\\\' + "u0027 + 'example-secret' + " + '\\' + "u0027b'"
        'a Unicode escape in a Cypher line comment, which may end it'    | CYPHER       | 'RETURN 1 // ' + '\\' + "u000a ' \n example-secret //'"
        'a Unicode escape in a Cypher block comment, which may end it'   | CYPHER       | 'RETURN 1 /* ' + '\\' + "u002a/ ' */ example-secret //'"
        'a Unicode escape of a slash in a Cypher block comment'          | CYPHER       | 'RETURN 1 /* *' + '\\' + "u002f ' */ example-secret //'"
        'a Unicode escape in upper case in a Cypher block comment'       | CYPHER       | 'RETURN 1 /* ' + '\\' + "u002A/ ' */ example-secret //'"
        'a Unicode escape after an escaped backslash in a block comment' | CYPHER       | 'RETURN 1 /* ' + '\\\\\\' + "u002a/ ' */ example-secret //'"
        'a Unicode escape of a carriage return in a Cypher line comment' | CYPHER       | 'RETURN 1 // ' + '\\' + "u000d ' \n example-secret //'"
        'a Unicode escape of a backslash in a Cypher string'             | CYPHER       | "RETURN 'a" + '\\' + "u005c', example-secret, 'b'"
        'a Unicode escape whose digits are of another script'            | CYPHER       | "RETURN 'a" + '\\' + "u\u0660\u0660\u0662\u0667 + 'example-secret' + " + '\\' + "u\u0660\u0660\u0662\u0667b'"
        'a Unicode escape in a comment a lone carriage return may end'   | CYPHER       | 'RETURN 1 // caf' + '\\' + 'u00e9 example-secret\r, 2'
        'an Oracle password given as a quoted name'                      | ORACLE       | 'ALTER USER app_user IDENTIFIED BY "ExampleSecret123"'
        'an Oracle password given bare'                                  | ORACLE       | 'ALTER USER app_user IDENTIFIED BY ExampleSecret123'
        'an Oracle password replacing the one it was given before'       | ORACLE       | 'alter user app_user identified by "x" replace ExampleSecret123'
        'an Oracle password after a comment that splits its clause'      | ORACLE       | 'CREATE USER app_user IDENTIFIED/* by */BY ExampleSecret123'
        'an Oracle password on lines of its own'                         | ORACLE       | 'CREATE USER app_user\n  IDENTIFIED\n  BY ExampleSecret123'
        'an Oracle role password'                                        | ORACLE       | 'SET ROLE app_role IDENTIFIED BY ExampleSecret123'
        'an Oracle database link password'                               | ORACLE       | "CREATE DATABASE LINK remote CONNECT TO app IDENTIFIED BY ExampleSecret123 USING 'remote'"
        'an Oracle password before the database is known'                | UNKNOWN      | 'ALTER USER app_user IDENTIFIED BY "ExampleSecret123"'
        'a MySQL password, which is a literal'                           | MYSQL        | "CREATE USER 'app'@'%' IDENTIFIED BY 'ExampleSecret123'"
        'a SAP HANA password given as a name'                            | HANA         | 'CREATE USER app_user PASSWORD ExampleSecret123 NO FORCE_FIRST_PASSWORD_CHANGE'
        'a SAP HANA password given as a quoted name'                     | HANA         | 'ALTER USER app_user PASSWORD "ExampleSecret123"'
        'a SAP HANA restricted user password'                            | HANA         | 'create restricted user app_user password ExampleSecret123'
        'a SAP HANA connection with a password'                          | HANA         | 'CONNECT app_user PASSWORD ExampleSecret123'
        'a Db2 connection with a password'                               | DB2          | 'CONNECT TO sample USER app_user USING ExampleSecret123'
        'a PostgreSQL role password, which is a literal'                 | POSTGRESQL   | "/* role */ ALTER ROLE app_role WITH LOGIN PASSWORD 'ExampleSecret123'"
        'a Neo4j password, which is a literal'                           | CYPHER       | "CREATE OR REPLACE USER app SET PASSWORD 'ExampleSecret123'"
        'a SAP HANA password given in a block'                           | HANA         | 'DO BEGIN CREATE USER app_user PASSWORD ExampleSecret123; END'
        'a SAP HANA password given in a procedure'                       | HANA         | 'CREATE PROCEDURE p AS BEGIN ALTER USER app_user PASSWORD ExampleSecret123; END'
        'a SAP HANA password given in parentheses'                       | HANA         | '(ALTER USER app_user PASSWORD ExampleSecret123)'
        'a SAP HANA connection in a block'                               | HANA         | 'DO BEGIN CONNECT app_user PASSWORD ExampleSecret123; END'
        'a Sybase role password'                                         | TRANSACT_SQL | 'create role app_role with passwd ExampleSecret123'
        'a Sybase role password added'                                   | TRANSACT_SQL | 'alter role app_role add passwd ExampleSecret123'
        'a Sybase role set with its password'                            | TRANSACT_SQL | 'set role app_role with passwd ExampleSecret123 on'
        'a Transact-SQL procedure given a password bare'                 | TRANSACT_SQL | 'EXEC sp_addlogin app_user, ExampleSecret123'
        'a Transact-SQL procedure run with EXECUTE'                      | TRANSACT_SQL | 'execute sp_password NULL, ExampleSecret123, app_user'
        'a Transact-SQL procedure whose status is kept'                  | TRANSACT_SQL | 'declare @s int; exec @s = dbo.find_user ExampleSecret123'
        'a Transact-SQL procedure run after another statement'           | TRANSACT_SQL | 'set nocount on; exec sp_addlogin app_user, ExampleSecret123'
        'a Transact-SQL procedure run in a block'                        | TRANSACT_SQL | 'begin exec dbo.find_user ExampleSecret123 end'
        'a Transact-SQL procedure first in its batch'                    | TRANSACT_SQL | 'sp_addlogin app_user, ExampleSecret123'
        'a Transact-SQL procedure first in its batch after a comment'    | TRANSACT_SQL | '/* add */ dbo.sp_addlogin app_user, ExampleSecret123'
        'a bracketed Transact-SQL procedure first in its batch'          | TRANSACT_SQL | '[dbo].[sp_addlogin] app_user, ExampleSecret123'
        'a Transact-SQL procedure called through the JDBC escape'        | TRANSACT_SQL | '{call dbo.find_user(ExampleSecret123)}'
        'a Transact-SQL procedure whose result is called for'            | TRANSACT_SQL | '{ ? = CALL dbo.find_user(ExampleSecret123) }'
        'a Transact-SQL procedure before the database is known'          | UNKNOWN      | 'EXEC sp_addlogin app_user, ExampleSecret123'
        'a PostgreSQL setting given a bare value'                        | POSTGRESQL   | 'SET app.api_key = ExampleSecret123'
        'a PostgreSQL setting given a bare value with TO'                | POSTGRESQL   | 'set session myapp.tenant_secret to ExampleSecret123'
        'a PostgreSQL setting after a comment'                           | POSTGRESQL   | '/* tenant */ SET LOCAL app.tenant = ExampleSecret123'
        'a PostgreSQL setting after another statement'                   | POSTGRESQL   | 'select 1; SET app.api_key = ExampleSecret123'
        'a PostgreSQL setting of a role'                                 | POSTGRESQL   | 'ALTER ROLE app_role SET app.api_key = ExampleSecret123'
        'a PostgreSQL setting of a role in a database'                   | POSTGRESQL   | 'alter role app_role in database app set app.api_key to ExampleSecret123'
        'a PostgreSQL setting of a database'                             | POSTGRESQL   | 'ALTER DATABASE app SET app.api_key TO ExampleSecret123'
        'a PostgreSQL setting of the server'                             | POSTGRESQL   | 'ALTER SYSTEM SET app.api_key = ExampleSecret123'
        'a PostgreSQL setting of a function'                             | POSTGRESQL   | 'ALTER FUNCTION f() SET app.api_key = ExampleSecret123'
        'a PostgreSQL function created with a setting'                   | POSTGRESQL   | 'CREATE OR REPLACE FUNCTION f() RETURNS int SET app.api_key = ExampleSecret123 AS $$ select 1 $$ LANGUAGE sql'
        'a MySQL setting given a bare value'                             | MYSQL        | 'SET GLOBAL app_secret = ExampleSecret123'
        'a MySQL setting run by an event'                                | MYSQL        | 'CREATE EVENT e ON SCHEDULE EVERY 1 DAY DO SET GLOBAL app_secret = ExampleSecret123'
        'an Oracle session setting'                                      | ORACLE       | 'ALTER SESSION SET current_schema = ExampleSecret123'
        'a Db2 special register'                                         | DB2          | 'SET CURRENT SCHEMA = ExampleSecret123'
        'a SAP HANA setting in a block'                                  | HANA         | 'DO BEGIN SET SCHEMA ExampleSecret123; END'
        'a Transact-SQL session option'                                  | TRANSACT_SQL | 'SET QUOTED_IDENTIFIER OFF; SELECT "ExampleSecret123";'
        'a setting before the database is known'                         | UNKNOWN      | 'SET app.api_key = ExampleSecret123'
    }

    @Unroll
    void "a statement that only names a password, sets columns or connects mid-statement is redacted as any other: #sql"() {
        expect:
        redact(sql, syntax) == redacted

        where:
        syntax  | sql                                                                         || redacted
        H2      | "select u.password from app_user u where u.password = 'example-secret'"     || 'select u.password from app_user u where u.password = ?'
        H2      | "update app_user set password = 'example-secret' where id = 1"              || 'update app_user set password = ? where id = ?'
        H2      | 'create table app_user (id bigint, password varchar(255))'                  || 'create table app_user (id bigint, password varchar(?))'
        H2      | 'alter table app_user add column password varchar(255)'                     || 'alter table app_user add column password varchar(?)'
        ORACLE  | 'select id from staff start with id = 1 connect by prior id = manager_id'    || 'select id from staff start with id = ? connect by prior id = manager_id'
        ORACLE  | 'select "IDENTIFIED", by from t'                                            || 'select "IDENTIFIED", by from t'
        ORACLE  | 'select connect_by_root id from staff connect\n  by prior id = manager_id'   || 'select connect_by_root id from staff connect by prior id = manager_id'
        H2      | "update app_user set role = 'admin', password = 'example-secret' where id = 1" || 'update app_user set role = ?, password = ? where id = ?'
        TRANSACT_SQL | "update app_user set login = 'app', password = 'example-secret' where id = 1" || 'update app_user set login = ?, password = ? where id = ?'
        POSTGRESQL   | "insert into book (id, title) values (1, 'x') on conflict (id) do update set title = excluded.title" || 'insert into book (id, title) values (?, ?) on conflict (id) do update set title = excluded.title'
        H2           | 'merge into book b using src s on b.id = s.id when matched then update set title = s.title' || 'merge into book b using src s on b.id = s.id when matched then update set title = s.title'
        POSTGRESQL   | "with s as (select 1 as id) update book set title = 'x' from s where book.id = s.id" || 'with s as (select ? as id) update book set title = ? from s where book.id = s.id'
        H2           | 'alter table book alter column title set default null'                     || 'alter table book alter column title set default null'
        CYPHER       | "MATCH (b:Book) WHERE b.id = 1 SET b.title = 'x'"                          || 'MATCH (b:Book) WHERE b.id = ? SET b.title = ?'
        CYPHER       | "MERGE (b:Book {id: 1}) ON CREATE SET b.title = 'x'"                      || 'MERGE (b:Book {id: ?}) ON CREATE SET b.title = ?'
    }

    void "only the databases that take a bare word for a value find one"() {
        given:
        String call = 'exec dbo.find_user app_user'

        expect:
        KNOWN.findAll { QueryTailSyntax syntax -> syntax.takesBareValues(call) } == [TRANSACT_SQL]
        !CYPHER.takesBareValues(call)
        !UNKNOWN.takesBareValues(call)
        !TRANSACT_SQL.takesBareValues('')
        !TRANSACT_SQL.takesBareValues('(select ?) union (select ?)')
        !TRANSACT_SQL.takesBareValues(' ; Select ? from t')
    }

    @Unroll
    void "a statement every reading agrees on is redacted before the dialect is known: #sql"() {
        expect:
        redact(sql, UNKNOWN) == redacted
        redact(sql, null) == redacted

        where:
        sql                                                               || redacted
        "select b1_0.id from book b1_0 where b1_0.title='example-secret'" || 'select b1_0.id from book b1_0 where b1_0.title=?'
        "/* comment */ insert into book (id,title) values (1,'x')"        || 'insert into book (id,title) values (?,?)'
        'select 1 -- key=abc123\r\nfrom t'                                || 'select ? from t'
    }

    @Unroll
    void "a value written into a statement never survives redaction in #syntax"() {
        given:
        List<String> statements = [
                "select * from account where password='${SECRET}'",
                "select 1 /* ${SECRET} */",
                "select 1 -- ${SECRET}\nfrom t",
                "select 'a''${SECRET}'",
                "select 'it\\'s', '${SECRET}'",
                "select 1 /* a /* b */ '${SECRET}' */",
                "select '${SECRET}",
                "select 1 /*! + length('${SECRET}') */",
                "select 1 --\u0001${SECRET}\nfrom t",
                "select 1 --\u00a0${SECRET}\nfrom t",
                "select 1 -- x\r'${SECRET}'",
        ]*.toString()

        expect:
        statements.every { !QueryTailRedactor.redact(it, syntax).contains(SECRET) }

        where:
        syntax << [UNKNOWN] + KNOWN
    }

    @Unroll
    void "a value written into a statement of its own syntax never survives redaction in #syntax"() {
        expect: 'in the syntax, and before it is known'
        statements*.toString().every { !QueryTailRedactor.redact(it, syntax).contains(SECRET) }
        statements*.toString().every { !QueryTailRedactor.redact(it, UNKNOWN).contains(SECRET) }

        where:
        syntax     | statements
        H2         | ["select 1 // ${SECRET}\nfrom t", "select \$\$${SECRET}\$\$", "select [a'b] '${SECRET}' from t",
                      "select ARRAY ['${SECRET}'] from t", "select ARRAY /* c */ ['${SECRET}']", "select ARRAY\t['${SECRET}', 'x']",
                      "select ARRAY ['a', '${SECRET}'][1]", "select [x] ['${SECRET}']", "select ARRAY/**/['${SECRET}']",
                      "select ARRAY /* c */['${SECRET}']", "select ARRAY -- c\n['${SECRET}']"]
        POSTGRESQL | ["select \$\$${SECRET}\$\$", "select \$q\$${SECRET}\$q\$", "select E'\\'${SECRET}'", "select 1 # '${SECRET}'",
                      "select \$\u20ac\$${SECRET}\$\u20ac\$", "select \$\u00e9_1\$${SECRET}\$\u00e9_1\$",
                      "select \$\u00a0\$${SECRET}\$\u00a0\$", "select \$\ud83d\udd11\$${SECRET}\$\ud83d\udd11\$"]
        COCKROACH  | ["select \$\u20ac\$${SECRET}\$\u20ac\$", "select \$k\u00e9\$${SECRET}\$k\u00e9\$"]
        ORACLE     | ["select q'<${SECRET}>' from dual", "select q''${SECRET}'' from dual", "select nq'[${SECRET}]' from dual"]
        INFORMIX   | ["select 1 { ${SECRET} }", "select \"${SECRET}\" from t"]
        MYSQL      | ["select \"${SECRET}\"", "select 1 # ${SECRET}", "select 1 --\t${SECRET}", "select 1 /*M! '${SECRET}' */"]
        TRANSACT_SQL | ["select \"${SECRET}\"", "select 1 /* a /* b */ '${SECRET}' */ from [t]"]
        GOOGLE_SQL | ["select '''it's ${SECRET}''' from t", "select '''it's bob's ${SECRET}''' from t",
                      "select \"\"\"a\"${SECRET}\"b\"\"\" from t", "select \"${SECRET}\" from t",
                      "select 'it\\'s ${SECRET}' from t", "select r'${SECRET}\\' from t", "select 'a', '''${SECRET}",
                      "select 1 # ${SECRET}"]
    }

    void "a value written into a Cypher statement never survives redaction"() {
        given:
        List<String> statements = [
                "MATCH (n) WHERE n.key = '${SECRET}' RETURN n",
                "RETURN \"${SECRET}\"",
                "RETURN 1 // ${SECRET}",
                "RETURN 1 /* ${SECRET} */",
                "RETURN 'it\\'s ${SECRET}'",
                "RETURN 'a''${SECRET}'",
                "RETURN 1 AS `a``b` + '${SECRET}'",
                "RETURN '${SECRET}",
                "RETURN 1 /* a /* b */ '${SECRET}' */",
                "RETURN 1 // x\r'${SECRET}'",
                'RETURN ' + '\\' + "u0027${SECRET}" + '\\' + 'u0027',
                'RETURN 1 AS `a' + '\\' + "u0060 + '${SECRET}'`",
                "RETURN 'a" + '\\' + "u0027 + '${SECRET}' + " + '\\' + "u0027b'",
                "RETURN 'a" + '\\\\\\' + "u0027 + '${SECRET}' + " + '\\' + "u0027b'",
                'RETURN 1 // ' + '\\' + "u000a ' \n ${SECRET} //'",
                'RETURN 1 /* ' + '\\' + "u002a/ ' */ ${SECRET} //'",
                'RETURN "a' + '\\' + "uu0022 + \"${SECRET}\" + " + '\\' + 'uu0022b"',
                'RETURN 1 /* ' + '\\\\\\' + "u002a/ ' */ ${SECRET} //'",
                'RETURN 1 // ' + '\\' + "u000d ' \n ${SECRET} //'",
                "RETURN 'a" + '\\' + "u\u0660\u0660\u0662\u0667 + '${SECRET}' + " + '\\' + "u\u0660\u0660\u0662\u0667b'",
                'RETURN 1 // caf' + '\\' + "u00e9 ${SECRET}\r, 2",
        ]*.toString()

        expect:
        statements.every { !QueryTailRedactor.redact(it, CYPHER).contains(SECRET) }
    }

    void "a syntax is its own reading unless the statement depends on a setting it cannot see"() {
        expect:
        KNOWN.every { it.readings('select 1 from t') == [it] }
        H2.readings('select x,[a] from t') == [H2]
        H2.readings('select [a] from t').size() == 2
        CYPHER.readings('RETURN 1') == [CYPHER]
        CYPHER.readings('RETURN 1 // x\r, 2').size() == 2
        CYPHER.readings('RETURN 1 // x\r\n, 2') == [CYPHER]
        CYPHER.readings("RETURN 'caf" + '\\' + "u00e9'") == [CYPHER]
        CYPHER.readings("RETURN 'C:" + '\\\\' + "u0027'") == [CYPHER]
        UNKNOWN.readings('select 1 from t').size() == KNOWN.size()
        POSTGRESQL.readings("select '\\\\' from t").size() == 2
        COCKROACH.readings("select '\\\\' from t") == [COCKROACH]
        MYSQL.readings("select '\\\\' from t --\u00a0x").size() == 4
        HANA.readings('select 1 -- x\r from t').size() == 2
        HANA.readings('select 1 -- x\r\nfrom t') == [HANA]
        UNKNOWN.readings("select '\\\\' from t").size() == KNOWN.size() + 2
    }

    void "a Cypher statement with an escape Neo4j 5 decodes into a delimiter has no reading, and is withheld"() {
        given:
        String sql = "RETURN 'a" + '\\' + "u0027 + 'example-secret' + " + '\\' + "u0027b' // x\r, 2"

        expect:
        CYPHER.readings(sql).isEmpty()
        QueryTailRedactor.redact(sql, CYPHER) == WITHHELD
    }

    void "an unknown syntax withholds every statement read under it alone"() {
        expect:
        UNKNOWN.endOfComment('select 1 /* x */', 9) == QueryTailSyntax.NONE
        UNKNOWN.endOfValue('select 1', 7) == QueryTailSyntax.UNTERMINATED
        UNKNOWN.endOfQuotedName('select "x"', 7) == QueryTailSyntax.NONE
        UNKNOWN.toString() == 'unknown'
        CYPHER.toString() == 'Cypher'
        KNOWN*.toString() == ['H2', 'PostgreSQL', 'CockroachDB', 'Oracle', 'Db2', 'HSQLDB', 'SAP HANA', 'Informix', 'MySQL',
                              'Transact-SQL', 'GoogleSQL']
    }

    void "nothing is nothing"() {
        expect:
        QueryTailRedactor.redact(null, H2) == null
        QueryTailRedactor.redact('', H2) == ''
        QueryTailRedactor.redact('', null) == ''
    }

    /** The redacted statement as the capture records it and the command reads it, its whitespace outside quotes collapsed. */
    private static String redact(String sql, QueryTailSyntax syntax) {
        QueryTailRecord.parse(new QueryTailRecord(sql: QueryTailRedactor.redact(sql, syntax)).toLine()).sql
    }
}

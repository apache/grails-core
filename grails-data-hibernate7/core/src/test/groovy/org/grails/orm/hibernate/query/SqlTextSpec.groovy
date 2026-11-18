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
package org.grails.orm.hibernate.query

import spock.lang.Specification
import spock.lang.Unroll

import static org.grails.orm.hibernate.query.SqlSyntax.H2
import static org.grails.orm.hibernate.query.SqlSyntax.HQL
import static org.grails.orm.hibernate.query.SqlSyntax.MYSQL
import static org.grails.orm.hibernate.query.SqlSyntax.ORACLE
import static org.grails.orm.hibernate.query.SqlSyntax.POSTGRESQL
import static org.grails.orm.hibernate.query.SqlSyntax.SQL
import static org.grails.orm.hibernate.query.SqlSyntax.TRANSACT_SQL

/**
 * The one table of awkward inputs every scanner of the package is measured against: what a string literal, a quoted
 * or bracket identifier, a dollar-quoted string and a comment hide, what parentheses and array brackets nest, and
 * how the same text reads in each syntax.
 */
class SqlTextSpec extends Specification {

    static final List<SqlSyntax> NATIVE = [TRANSACT_SQL, POSTGRESQL, MYSQL, ORACLE, H2, SQL]

    static final List<SqlSyntax> BRACKETS_ARE_ARRAYS = [POSTGRESQL, MYSQL, ORACLE, H2, SQL]

    @Unroll
    void "the SQL #sql splits at the top-level commas into #parts in every SQL syntax"() {
        expect:
        NATIVE.every { SqlText.of(sql, it).splitTopLevel(',' as char) == parts }

        where:
        sql                                       | parts
        'width'                                   | ['width']
        'width, sum(height) as total'             | ['width', 'sum(height) as total']
        "coalesce(label, 'a,b'), label"           | ["coalesce(label, 'a,b')", 'label']
        "'it''s, not', c"                         | ["'it''s, not'", 'c']
        '"odd,name", other'                       | ['"odd,name"', 'other']
        '`odd,name`, other'                       | ['`odd,name`', 'other']
        'a /* b, c */, d'                         | ['a /* b, c */', 'd']
        'a -- b, c\n, d'                          | ['a -- b, c', 'd']
        'concat(a, concat(b, c)), d'              | ['concat(a, concat(b, c))', 'd']
        'a$b, $1'                                 | ['a$b', '$1']
    }

    @Unroll
    void "the SQL #sql splits into #parts where brackets hold arrays"() {
        expect:
        BRACKETS_ARE_ARRAYS.every { SqlText.of(sql, it).splitTopLevel(',' as char) == parts }

        where:
        sql                                       | parts
        'arr[f(a, b)], ARRAY[ARRAY[1, 2]], c'     | ['arr[f(a, b)]', 'ARRAY[ARRAY[1, 2]]', 'c']
        "ARRAY['['], height"                      | ["ARRAY['[']", 'height']
        "ARRAY[']', ','], [a,b]"                  | ["ARRAY[']', ',']", '[a,b]']
        'ARRAY [a, b], c'                         | ['ARRAY [a, b]', 'c']
        '"items"[1], c'                           | ['"items"[1]', 'c']
        "[a'b], c"                                | ["[a'b], c"]
    }

    @Unroll
    void "the Transact-SQL #sql splits into #parts where brackets quote an identifier"() {
        expect:
        SqlText.of(sql, TRANSACT_SQL).splitTopLevel(',' as char) == parts

        where:
        sql                                       | parts
        "width as [box's width], height as total" | ["width as [box's width]", 'height as total']
        'width as [a"b], c'                       | ['width as [a"b]', 'c']
        'sum(height) as [total (cm)], c'          | ['sum(height) as [total (cm)]', 'c']
        'sum(height) as [a]]b], c'                | ['sum(height) as [a]]b]', 'c']
        '[a[b], c'                                | ['[a[b]', 'c']
        '[a,b], c'                                | ['[a,b]', 'c']
        '$$a,b$$, c'                              | ['$$a', 'b$$', 'c']
    }

    @Unroll
    void "the dollar quoted #sql splits into #parts on #syntax"() {
        expect:
        SqlText.of(sql, syntax).splitTopLevel(',' as char) == parts

        where:
        sql                    | syntax       || parts
        '$$a,b$$, c'           | POSTGRESQL   || ['$$a,b$$', 'c']
        '$$a,b$$, c'           | H2           || ['$$a,b$$', 'c']
        '$$a,b$$, c'           | MYSQL        || ['$$a', 'b$$', 'c']
        '$$a,b$$, c'           | ORACLE       || ['$$a', 'b$$', 'c']
        '$$a,b$$, c'           | TRANSACT_SQL || ['$$a', 'b$$', 'c']
        '$$a,b$$, c'           | SQL          || ['$$a', 'b$$', 'c']
        '$tag$a,$$,b$tag$, c'  | POSTGRESQL   || ['$tag$a,$$,b$tag$', 'c']
        '$tag$a,$$,b$tag$, c'  | H2           || ['$tag$a', '$$,b$tag$, c']
        '$tag$a,$$,b$tag$, c'  | MYSQL        || ['$tag$a', '$$', 'b$tag$', 'c']
        'a$b, $1'              | POSTGRESQL   || ['a$b', '$1']
        'a$b, $1'              | H2           || ['a$b', '$1']
    }

    void "HQL has no line comments: a double minus is a subtraction, so what follows it is read, while SQL ends the line there"() {
        expect:
        SqlText.hql('a--b, c').splitTopLevel(',' as char) == ['a--b', 'c']
        SqlText.hql('a -- b, c\n, d').splitTopLevel(',' as char) == ['a -- b', 'c', 'd']
        SqlText.hql('from B b where b.h--1 > 0 select b.w').indexOfTopLevel('select ', 0) == 26
        SqlText.of('from B b where b.h--1 > 0 select b.w', SQL).indexOfTopLevel('select ', 0) == -1
        SqlText.of('from B b where b.h--1 > 0 select b.w', TRANSACT_SQL).indexOfTopLevel('select ', 0) == -1
        SqlText.hql('a /* b, c */, d').splitTopLevel(',' as char) == ['a /* b, c */', 'd']
    }

    void "HQL has no bracket identifiers or dollar quotes: brackets group an index expression, the rest is plain"() {
        expect:
        SqlText.hql('a[f(1, 2)], b').splitTopLevel(',' as char) == ['a[f(1, 2)]', 'b']
        SqlText.hql("[a'b], c").splitTopLevel(',' as char) == ["[a'b], c"]
        SqlText.hql('$$a,b$$, c').splitTopLevel(',' as char) == ['$$a', 'b$$', 'c']
        SqlText.of("[a'b], c", HQL).splitTopLevel(',' as char) == SqlText.hql("[a'b], c").splitTopLevel(',' as char)
    }

    @Unroll
    void "the keyword '#keyword' is found at the top level of '#text' at #index"() {
        expect:
        SqlText.hql(text).indexOfTopLevel(keyword, 0) == index

        where:
        text                                                  | keyword  | index
        'select a from B'                                     | 'select '| 0
        'select a from B'                                     | ' from ' | 8
        'from B where x in (select y from C)'                 | 'select '| -1
        "from B where name = 'select '"                       | 'select '| -1
        'from B where name = "select "'                       | 'select '| -1
        'from B where name = `select `'                       | 'select '| -1
        'from B /* select a */ where x = 1'                   | 'select '| -1
        'from B -- select a\nwhere x = 1'                     | 'select '| 10
        'from B where preselect = 1'                          | 'select '| -1
        'from B where b.select = 1'                           | 'select '| -1
        "from B where name = 'it''s' and 1 = (select count(c) from C) select"  | 'select' | 61
        'with x as (select 1) select a from B'                | 'select '| 21
    }

    void "a search starts at the given index"() {
        expect:
        SqlText.hql('select a from B where b in (select c from D) and e = 1').indexOfTopLevel(' from ', 9) == -1
        SqlText.hql('select a from B order by a').indexOfTopLevel(' from ', 0) == 8
    }

    @Unroll
    void "the '?' placeholders of '#sql' are at #indexes in every SQL syntax"() {
        expect:
        NATIVE.every { SqlText.of(sql, it).plainIndexes('?' as char) == indexes }

        where:
        sql                                   | indexes
        'a = ?'                               | [4]
        "a = ? and b = '?' and c = ?"         | [4, 26]
        'a = ? /* ? */ and b = ?'             | [4, 22]
        'a = ? -- ?\nand b = ?'               | [4, 19]
        'a = (?)'                             | [5]
        'a = "?" and b = ?'                   | [16]
    }

    @Unroll
    void "the '?' placeholders of '#sql' are at #indexes where brackets hold arrays"() {
        expect:
        BRACKETS_ARE_ARRAYS.every { SqlText.of(sql, it).plainIndexes('?' as char) == indexes }

        where:
        sql                                   | indexes
        'id = ANY(ARRAY[?, ?])'               | [15, 18]
        'id = ANY(ARRAY [CAST(? AS INTEGER)])'| [21]
        '"items"[?] = ?'                      | [8, 13]
        "a = ARRAY['?', ?][1]"                | [15]
        'a = ARRAY[1, 2][?]'                  | [16]
        'a = [?] and b = ?'                   | [5, 16]
    }

    @Unroll
    void "the '?' placeholders of the Transact-SQL '#sql' are at #indexes, brackets quoting an identifier"() {
        expect:
        SqlText.of(sql, TRANSACT_SQL).plainIndexes('?' as char) == indexes

        where:
        sql                                   | indexes
        'a = [?] and b = ?'                   | [16]
        '[a[b] = ?'                           | [8]
        '[a?b] = ?'                           | [8]
        '[a]]?] = ?'                          | [9]
        '[a?'                                 | []
        'a = $$?$$ and b = ?'                 | [6, 18]
    }

    @Unroll
    void "the '?' placeholders of '#sql' are at #indexes on #syntax"() {
        expect:
        SqlText.of(sql, syntax).plainIndexes('?' as char) == indexes

        where:
        sql                                   | syntax     || indexes
        'a = $$?$$ and b = ?'                 | POSTGRESQL || [18]
        'a = $$?$$ and b = ?'                 | H2         || [18]
        'a = $$?$$ and b = ?'                 | MYSQL      || [6, 18]
        'a = $tag$?$tag$ and b = ?'           | POSTGRESQL || [24]
        'a = $tag$?$tag$ and b = ?'           | H2         || [9, 24]
        'a // ? \n, ?'                         | H2         || [10]
        'a // ? \n, ?'                         | POSTGRESQL || [5, 10]
        'a /* // */ ?'                        | H2         || [11]
        "doc ?? 'key' and b = ?"              | POSTGRESQL || [21]
        "doc ?? 'key' and b = ?"              | MYSQL      || [4, 5, 21]
        "doc ? 'key'"                         | POSTGRESQL || [4]
        "a = 'it\\'s ?' and b = ?"            | MYSQL      || [22]
        "a = 'it\\'s ?' and b = ?"            | POSTGRESQL || [11]
        "a = \"it\\\"s ?\" and b = ?"        | MYSQL      || [22]
        "a = E'it\\'s ?' and b = ?"           | POSTGRESQL || [23]
        "a = e'it\\'s ?' and b = ?"           | POSTGRESQL || [23]
        "a = nonE'it\\'s ?' and b = ?"        | POSTGRESQL || [15]
        "a = 'C:\\\\' and b = ?"              | MYSQL      || [19]
        "a = q'[it's ?]' and b = ?"           | ORACLE     || [24]
        "a = Q'!it's ?!' and b = ?"           | ORACLE     || [24]
        "a = nq'{it's ?}' and b = ?"          | ORACLE     || [25]
        "a = q'(it's ?)' and b = ?"           | ORACLE     || [24]
        "a = q'<it's ?>' and b = ?"           | ORACLE     || [24]
        "a = q'[it's ?]' and b = ?"           | POSTGRESQL || [12]
        "a = seq'[x ?]' and b = ?"            | ORACLE     || [23]
        "a = 1 # is it? \nand b = ?"          | MYSQL      || [24]
        "a = 1 # is it? \nand b = ?"          | ORACLE     || [13, 24]
        'a--1 = ?'                            | MYSQL      || [7]
        'a--1 = ?'                            | POSTGRESQL || []
        'a-- 1 = ?'                           | MYSQL      || []
        'a--\t1 = ?'                          | MYSQL      || []
        'a = 1 /* a /* b */ ? */ x = ?'       | POSTGRESQL || [28]
        'a = 1 /* a /* b */ ? */ x = ?'       | TRANSACT_SQL || [28]
        'a = 1 /* a /* b */ ? */ x = ?'       | MYSQL      || [19, 28]
        'a = 1 /* a /* b */ ? */ x = ?'       | ORACLE     || [19, 28]
        'a = 1 /* a /* b */ ? */ x = ?'       | H2         || [19, 28]
    }

    @Unroll
    void "'#column' without the alias '#alias' is '#expected' on #syntax"() {
        expect:
        SqlText.of(column, syntax).withoutTrailingAlias(alias) == expected

        where:
        column                       | alias | syntax       || expected
        'count(*)as y0_'             | 'y0_' | SQL          || 'count(*)'
        '"x"as y0_'                  | 'y0_' | SQL          || '"x"'
        "'lit'as y0_"                | 'y0_' | SQL          || "'lit'"
        'bias y0_'                   | 'y0_' | SQL          || 'bias y0_'
        'cast as y0_'                | 'y0_' | SQL          || 'cast'
        'x as /* c */ y0_'           | 'y0_' | SQL          || 'x'
        'x /* c */ as y0_'           | 'y0_' | SQL          || 'x'
        'x -- c\nas y0_'              | 'y0_' | SQL          || 'x'
        "x as 'y0_'"                 | 'y0_' | MYSQL        || 'x'
        "x as 'y0_'"                 | 'y0_' | TRANSACT_SQL || 'x'
        "x as 'y0_'"                 | 'y0_' | POSTGRESQL   || "x as 'y0_'"
        "x as 'it''s'"               | "it's" | MYSQL       || 'x'
        'x as"y0_"'                  | 'y0_' | SQL          || 'x'
        'arr[1]as foo'               | 'foo' | POSTGRESQL   || 'arr[1]'
        'x as \uD835\uDC9C'            | '\uD835\uDC9C' | SQL    || 'x'
        'x as cafe\u0301'            | 'cafe\u0301' | SQL      || 'x'
        'x as a\u200Db'              | 'a\u200Db' | SQL        || 'x'
    }

    @Unroll
    void "the text #sql splits into #parts on #syntax"() {
        expect:
        SqlText.of(sql, syntax).splitTopLevel(',' as char) == parts

        where:
        sql                           | syntax     || parts
        '$$a,b$$$$c,d$$, e'           | POSTGRESQL || ['$$a,b$$$$c,d$$', 'e']
        '$$a,b$$$$c,d$$, e'           | H2         || ['$$a,b$$$$c,d$$', 'e']
        '$é$a,b$é$, c'                | POSTGRESQL || ['$é$a,b$é$', 'c']
        "a#q'x, y', z"                | ORACLE     || ["a#q'x, y'", 'z']
        'a[b, c'                      | SQL        || ['a[b, c']
        'a`b, c`, d'                  | SQL        || ['a`b, c`', 'd']
        'a`b, c`, d'                  | MYSQL      || ['a`b, c`', 'd']
    }

    void "a delimiter of choice closes an Oracle alternative quoted string, and an escape string of PostgreSQL ends at its unescaped quote"() {
        expect:
        SqlText.of("a q'!x, y!', z", ORACLE).splitTopLevel(',' as char) == ["a q'!x, y!'", 'z']
        SqlText.of("a q'x, y', z", ORACLE).splitTopLevel(',' as char) == ["a q'x, y', z"]
        SqlText.of('$$a,b$$' + "E'" + '\\' + "', c', d", POSTGRESQL).splitTopLevel(',' as char) ==
                ['$$a,b$$' + "E'" + '\\' + "', c'", 'd']
    }

    void "an unterminated string or comment runs to the end of the text on every syntax"() {
        expect:
        NATIVE.every { SqlText.of("a = 'x ?", it).plainIndexes('?' as char) == [] }
        NATIVE.every { SqlText.of('a = /* x ?', it).plainIndexes('?' as char) == [] }
        [MYSQL, POSTGRESQL].every { SqlText.of("a = 'x\\", it).plainIndexes('?' as char) == [] }
        SqlText.of("a = q'[x ?", ORACLE).plainIndexes('?' as char) == []
        SqlText.of('a = $tag$ x ?', POSTGRESQL).plainIndexes('?' as char) == []
    }

    @Unroll
    void "'#sql' #description"() {
        expect:
        NATIVE.every { SqlText.of(sql, it).endsInLineComment() == ends }

        where:
        sql                      | ends  | description
        'a = 1 -- done'          | true  | 'ends in a line comment'
        'a = 1 -- done\n'        | false | 'has its line comment closed by the newline'
        'a = 1 /* done */'       | false | 'ends in a block comment, which is closed'
        "a = '-- not'"           | false | 'has the dashes inside a literal'
        'a = 1'                  | false | 'has no comment'
    }

    @Unroll
    void "'#sql' ends in a line comment on #syntax: #ends"() {
        expect:
        SqlText.of(sql, syntax).endsInLineComment() == ends

        where:
        sql                | syntax     || ends
        'a = 1 # done'     | MYSQL      || true
        'a = 1 # done'     | POSTGRESQL || false
        'a = 1 --done'     | MYSQL      || false
        'a = 1 --done'     | POSTGRESQL || true
        'a = 1 -- done'    | MYSQL      || true
        'a = 1 --'         | MYSQL      || true
        'a = 1 -- done'    | HQL        || false
    }

    @Unroll
    void "'#column' without the alias '#alias' is '#expected' in every SQL syntax"() {
        expect:
        NATIVE.every { SqlText.of(column, it).withoutTrailingAlias(alias) == expected }

        where:
        column                          | alias          | expected
        'sum(height) as total'          | 'total'        | 'sum(height)'
        'sum(height) AS Total'          | 'total'        | 'sum(height)'
        'sum(height)  as  total  '      | 'total'        | 'sum(height)'
        'sum(height) as "total"'        | 'total'        | 'sum(height)'
        'sum(height) as `total`'        | 'total'        | 'sum(height)'
        'sum(height) as "total height"' | 'total height' | 'sum(height)'
        'sum(height) as "say ""hi"""'   | 'say "hi"'     | 'sum(height)'
        'sum(height) as total /* c */'  | 'total'        | 'sum(height)'
        'sum(height) as total -- c'     | 'total'        | 'sum(height)'
        'cast(width as integer) as w'   | 'w'            | 'cast(width as integer)'
        'cast(width as integer)'        | 'integer'      | 'cast(width as integer)'
        'sum(height) as total'          | 'other'        | 'sum(height) as total'
        'sum(height) as "total height"' | 'total'        | 'sum(height) as "total height"'
        'sum(height) as "total]'        | 'total'        | 'sum(height) as "total]'
        'sum(height) has total'         | 'total'        | 'sum(height) has total'
        'as total'                      | 'total'        | 'as total'
        'width'                         | 'width'        | 'width'
        ''                              | 'width'        | ''
    }

    void "a bracket alias is removed only where brackets quote an identifier"() {
        expect:
        SqlText.of('sum(height) as [total height]', TRANSACT_SQL).withoutTrailingAlias('total height') == 'sum(height)'
        SqlText.of('sum(height) as [a]]b]', TRANSACT_SQL).withoutTrailingAlias('a]b') == 'sum(height)'
        SqlText.of('sum(height) as [total height]', SQL).withoutTrailingAlias('total height') == 'sum(height) as [total height]'
        SqlText.of('sum(height) as [total]', SQL).withoutTrailingAlias('total') == 'sum(height) as [total]'
    }

    @Unroll
    void "'#identifier' unquoted is '#expected' in every SQL syntax"() {
        expect:
        NATIVE.every { SqlText.of(identifier, it).unquoted() == expected }

        where:
        identifier        | expected
        '"total"'         | 'total'
        '`total`'         | 'total'
        '"say ""hi"""'    | 'say "hi"'
        'total'           | 'total'
        '"total'          | '"total'
        '"a" "b"'         | '"a" "b"'
        '""'              | ''
        '"'               | '"'
        ''                | ''
    }

    void "a bracket identifier is unquoted only where brackets quote an identifier"() {
        expect:
        SqlText.of('[total]', TRANSACT_SQL).unquoted() == 'total'
        SqlText.of('[a]]b]', TRANSACT_SQL).unquoted() == 'a]b'
        SqlText.of('[total', TRANSACT_SQL).unquoted() == '[total'
        SqlText.of('[total]', SQL).unquoted() == '[total]'
    }

    void "a string in single quotes is a literal, not an identifier, in every SQL syntax"() {
        expect:
        NATIVE.every { SqlText.of("'total'", it).unquoted() == "'total'" }
    }
}

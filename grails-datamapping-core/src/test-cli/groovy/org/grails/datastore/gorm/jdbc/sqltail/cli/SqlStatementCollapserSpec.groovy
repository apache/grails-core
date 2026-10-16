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
package org.grails.datastore.gorm.jdbc.sqltail.cli

import spock.lang.Specification

class SqlStatementCollapserSpec extends Specification {

    void "a select keeps its from and where clauses and drops the column list"() {
        expect:
        SqlStatementCollapser.collapse(sql) == collapsed

        where:
        sql                                                                                              | collapsed
        'select b1_0.id,b1_0.title,b1_0.version from book b1_0 where b1_0.id=?'                         | 'select ... from book b1_0 where b1_0.id=?'
        'select b1_0.id, a1_0.name from book b1_0 join author a1_0 on a1_0.id=b1_0.author_id'          | 'select ... from book b1_0 join author a1_0 on a1_0.id=b1_0.author_id'
        'select (select count(*) from review r where r.book_id=b1_0.id) as c, b1_0.id from book b1_0'  | 'select ... from book b1_0'
        "select b1_0.id from book b1_0 where b1_0.title=' from nowhere '"                               | "select ... from book b1_0 where b1_0.title=' from nowhere '"
        'with recent as (select id from book) select id from recent'                                     | 'select ... from recent'
        'select 1'                                                                                       | 'select 1'
    }

    void "an update keeps the table and the where clause"() {
        expect:
        SqlStatementCollapser.collapse('update book set title=?,version=? where id=? and version=?') ==
                'update book ... where id=? and version=?'
        SqlStatementCollapser.collapse('update book set title=?') == 'update book ...'
        SqlStatementCollapser.collapse("update book set title=' set where ' where id=?") == 'update book ... where id=?'
    }

    void "an insert keeps the table"() {
        expect:
        SqlStatementCollapser.collapse('insert into book (id,title,version) values (?,?,?)') == 'insert into book ...'
        SqlStatementCollapser.collapse('insert into book values (?)') == 'insert into book ...'
    }

    void "a delete and anything else is kept whole"() {
        expect:
        SqlStatementCollapser.collapse('delete from book where id=?') == 'delete from book where id=?'
        SqlStatementCollapser.collapse('call next value for hibernate_sequence') == 'call next value for hibernate_sequence'
        SqlStatementCollapser.collapse('select next value for book_seq') == 'select next value for book_seq'
    }

    void "comments and whitespace are removed first"() {
        expect:
        SqlStatementCollapser.collapse('/* load Book */ select\n    b1_0.id\n  from\n    book b1_0 /* trailing */ where b1_0.id=?') ==
                'select ... from book b1_0 where b1_0.id=?'
        SqlStatementCollapser.collapse(null) == ''
        SqlStatementCollapser.collapse('   ') == ''
    }

    void "the kind of a statement is its first keyword, whatever its case and comments"() {
        expect:
        SqlStatementType.of(sql) == type

        where:
        sql                                       | type
        'select 1'                                | SqlStatementType.SELECT
        '/* load */ SELECT 1'                     | SqlStatementType.SELECT
        'with x as (select 1) select * from x'    | SqlStatementType.SELECT
        'insert into book values (?)'             | SqlStatementType.INSERT
        'Update book set a=?'                     | SqlStatementType.UPDATE
        'delete from book'                        | SqlStatementType.DELETE
        'call next value for s'                   | SqlStatementType.OTHER
        'alter table book add x int'              | SqlStatementType.OTHER
        null                                      | SqlStatementType.OTHER
    }
}

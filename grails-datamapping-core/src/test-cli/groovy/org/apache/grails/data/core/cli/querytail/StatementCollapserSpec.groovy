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
package org.apache.grails.data.core.cli.querytail

import spock.lang.Specification

class StatementCollapserSpec extends Specification {

    void "a select keeps its from and where clauses and drops the column list"() {
        expect:
        StatementCollapser.collapse(sql) == collapsed

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
        StatementCollapser.collapse('update book set title=?,version=? where id=? and version=?') ==
                'update book ... where id=? and version=?'
        StatementCollapser.collapse('update book set title=?') == 'update book ...'
        StatementCollapser.collapse("update book set title=' set where ' where id=?") == 'update book ... where id=?'
    }

    void "an insert keeps the table"() {
        expect:
        StatementCollapser.collapse('insert into book (id,title,version) values (?,?,?)') == 'insert into book ...'
        StatementCollapser.collapse('insert into book values (?)') == 'insert into book ...'
    }

    void "a delete and anything else is kept whole"() {
        expect:
        StatementCollapser.collapse('delete from book where id=?') == 'delete from book where id=?'
        StatementCollapser.collapse('call next value for hibernate_sequence') == 'call next value for hibernate_sequence'
        StatementCollapser.collapse('select next value for book_seq') == 'select next value for book_seq'
    }

    void "comments and whitespace are removed first"() {
        expect:
        StatementCollapser.collapse('/* load Book */ select\n    b1_0.id\n  from\n    book b1_0 /* trailing */ where b1_0.id=?') ==
                'select ... from book b1_0 where b1_0.id=?'
        StatementCollapser.collapse(null) == ''
        StatementCollapser.collapse('   ') == ''
    }

    void "the kind of a statement is its first keyword, whatever its case and comments"() {
        expect:
        StatementType.of(sql) == type

        where:
        sql                                       | type
        'select 1'                                | StatementType.SELECT
        '/* load */ SELECT 1'                     | StatementType.SELECT
        'with x as (select 1) select * from x'    | StatementType.SELECT
        'insert into book values (?)'             | StatementType.INSERT
        'Update book set a=?'                     | StatementType.UPDATE
        'delete from book'                        | StatementType.DELETE
        'call next value for s'                   | StatementType.OTHER
        'alter table book add x int'              | StatementType.OTHER
        null                                      | StatementType.OTHER
    }

    void "a Cypher statement is told by the clauses that write in it, and a MongoDB command by its name"() {
        expect:
        StatementType.of(statement) == type

        where:
        statement                                                           | type
        'MATCH (n:Book) RETURN n'                                           | StatementType.SELECT
        'OPTIONAL MATCH (n:Book) WHERE n.id = $1 RETURN n'                  | StatementType.SELECT
        'MATCH (n:Asset) RETURN n.offset'                                   | StatementType.SELECT
        'UNWIND $rows AS row CREATE (n:Book) SET n += row'                  | StatementType.INSERT
        'MERGE (n:Book {id: $1}) SET n.title = $2'                          | StatementType.INSERT
        'CREATE (n:Book) RETURN n'                                          | StatementType.INSERT
        'MATCH (n:Book) WHERE n.id = $1 SET n.title = $2'                   | StatementType.UPDATE
        'MATCH (n:Book) REMOVE n.title'                                     | StatementType.UPDATE
        'MATCH (n:Book) WHERE n.id = $1 DETACH DELETE n'                    | StatementType.DELETE
        'create table book (id int)'                                        | StatementType.OTHER
        'merge into book using x on y when matched then update set a=?'     | StatementType.OTHER
        'find shop.book {"filter": {"title": ?}}'                           | StatementType.SELECT
        'aggregate shop.book {"pipeline": [{"$match": {"title": ?}}]}'      | StatementType.SELECT
        'getMore shop.book {"batchSize": ?}'                                | StatementType.SELECT
        'count shop.book {"query": {}}'                                     | StatementType.SELECT
        'insert shop.book {"documents": [{"title": ?}]}'                    | StatementType.INSERT
        'update shop.book {"updates": [{"q": {"_id": ?}, "u": {"$set": {"title": ?}}}]}' | StatementType.UPDATE
        'findAndModify shop.book {"query": {"_id": ?}, "update": {"$set": {"title": ?}}}' | StatementType.UPDATE
        'delete shop.book {"deletes": [{"q": {"_id": ?}, "limit": ?}]}'     | StatementType.DELETE
        'createIndexes shop.book {"indexes": [{"key": {"title": ?}}]}'      | StatementType.OTHER
    }

    void "a Cypher statement and a MongoDB command are shown whole"() {
        expect:
        StatementCollapser.collapse(statement) == statement

        where:
        statement << [
                'MATCH (n:Book) WHERE n.id = $1 SET n.title = $2',
                'CREATE (n:Book {title: $1}) RETURN n',
                'MATCH (n:Book) WHERE n.from = $1 RETURN n',
                'find shop.book {"filter": {"from": ?}, "limit": ?}',
                'insert shop.book {"documents": [{"title": ?}]}',
                'update shop.book {"updates": [{"q": {"_id": ?}, "u": {"$set": {"title": ?}}}]}',
        ]
    }
}

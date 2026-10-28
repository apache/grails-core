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
package org.apache.grails.datamapping.core.cli.querytail

import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRedactor
import org.apache.grails.datamapping.core.querytail.QueryTailSyntax
import spock.lang.Specification

class StatementCollapserSpec extends Specification {

    void "a select keeps its from and where clauses and drops the column list"() {
        expect:
        StatementCollapser.collapse(sql) == collapsed

        where:
        sql                                                                                              | collapsed
        'select b1_0.id,b1_0.title,b1_0.version from book b1_0 where b1_0.id=?'                         | 'select ... from book b1_0 where b1_0.id=?'
        'select b1_0.id, a1_0.name from book b1_0 join author a1_0 on a1_0.id=b1_0.author_id'          | 'select ... from book b1_0 join author a1_0 on a1_0.id=b1_0.author_id'
        'select (b1_0.pages * 2) as p, b1_0.id from book b1_0'                                          | 'select ... from book b1_0'
        "select b1_0.id from book b1_0 where b1_0.title=' from nowhere '"                               | "select ... from book b1_0 where b1_0.title=' from nowhere '"
        'with recent as (select id from book) select id from recent'                                     | 'with recent as (select id from book) select ... from recent'
        'select 1'                                                                                       | 'select 1'
        'select distinct b1_0.id,b1_0.title from book b1_0 where b1_0.author_id=?'                      | 'select distinct ... from book b1_0 where b1_0.author_id=?'
        'SELECT DISTINCT b1_0.id FROM book b1_0'                                                         | 'select distinct ... FROM book b1_0'
        'select distinctive.id from distinctive'                                                         | 'select ... from distinctive'
    }

    void "a select whose list calls a function, as a count does, is kept whole, so that it never collapses into the select of the rows it counts"() {
        expect:
        StatementCollapser.collapse(sql) == sql

        where:
        sql << ['select count(*) from book b1_0 where b1_0.author_id=?',
                'select count(b1_0.id) as y0_ from book b1_0 where b1_0.author_id=?',
                'select count (distinct b1_0.title) from book b1_0',
                'select b1_0.author_id, sum(b1_0.pages) from book b1_0 group by b1_0.author_id',
                'with recent as (select id from book) select count(*) from recent',
                'select upper(b1_0.title) from book b1_0']
    }

    void "a select whose list reads a subquery, as a formula property does, is kept whole: #sql"() {
        expect:
        StatementCollapser.collapse(sql) == sql

        where:
        sql << ['select (select count(*) from review r where r.book_id=b1_0.id) as c, b1_0.id from book b1_0',
                'select b1_0.id, (select max(r.stars) from rating r where r.book_id=b1_0.id) from book b1_0',
                'select b1_0.id, b1_0.pages + ( SELECT count(*) from review r) from book b1_0',
                'select b1_0.id, ((select max(r.stars) from rating r)) from book b1_0',
                'SELECT b1_0.id, (SELECT\tmax(r.stars) FROM rating r) FROM book b1_0']
    }

    void "selects of one table whose lists read subqueries of different tables stay two statements"() {
        expect:
        StatementCollapser.collapse('select b1_0.id, (select count(*) from review r where r.book_id=b1_0.id) from book b1_0') !=
                StatementCollapser.collapse('select b1_0.id, (select max(t.stars) from rating t where t.book_id=b1_0.id) from book b1_0')
    }

    void "a parenthesis that does not open a select is no subquery"() {
        expect:
        StatementCollapser.collapse('select b1_0.id, (b1_0.selected) from book b1_0') == 'select ... from book b1_0'
        StatementCollapser.collapse('select b1_0.id, (selection.x) from book b1_0') == 'select ... from book b1_0'
        StatementCollapser.collapse("select b1_0.id, '(select 1)' from book b1_0") == 'select ... from book b1_0'
    }

    void "a count and the list it counts stay two statements"() {
        expect:
        StatementCollapser.collapse('select count(*) from book b1_0 where b1_0.author_id=?') !=
                StatementCollapser.collapse('select b1_0.id,b1_0.title from book b1_0 where b1_0.author_id=? limit ?')
    }

    void "a function name quoted in a select list, or a parenthesis in a quoted name, is no call"() {
        expect:
        StatementCollapser.collapse('select o1_0."count(x)" from orders o1_0') == 'select ... from orders o1_0'
        StatementCollapser.collapse('select o1_0.id, [sum (y)] from orders o1_0') == 'select ... from orders o1_0'
    }

    void "a select keeps the common table expressions it opens with, so statements that read different tables through them stay apart"() {
        expect:
        StatementCollapser.collapse(sql) == collapsed

        where:
        sql                                                                                        | collapsed
        'with selected as (select id from book) select id from selected'                          | 'with selected as (select id from book) select ... from selected'
        'with selected as (select id from author) select id from selected'                        | 'with selected as (select id from author) select ... from selected'
        'with recursive t(n) as (select 1 union all select n+1 from t where n<?) select n, n*2 from t' | 'with recursive t(n) as (select 1 union all select n+1 from t where n<?) select ... from t'
        'with a as (select id from book), b as materialized (select id from author) select a.id, b.id from a join b on a.id=b.id' | 'with a as (select id from book), b as materialized (select id from author) select ... from a join b on a.id=b.id'
        'with gone as (delete from book where id=? returning id) select id from gone'             | 'with gone as (delete from book where id=? returning id) select ... from gone'
        'WITH recent AS (SELECT id FROM book) SELECT id, title FROM recent'                       | 'WITH recent AS (SELECT id FROM book) select ... FROM recent'
        'with recent as (select id from book)select id from recent'                               | 'with recent as (select id from book)select ... from recent'
        'with "delete" as (select id from book) select id from "delete"'                          | 'with "delete" as (select id from book) select ... from "delete"'
    }

    void "a statement written for common table expressions that is not a select is kept whole"() {
        expect:
        StatementCollapser.collapse(sql) == sql

        where:
        sql << ['with recent as (select id from book) insert into archive select id, title from recent',
                'with recent as (select id from book) update book set title=? where id in (select id from recent)',
                'with recent as (select id from book) delete from book where id in (select id from recent)',
                'with recent as (select id from book) merge into archive a using recent r on a.id=r.id when matched then delete',
                'with recent as (select id from book)delete from book where id in (select id from recent)',
                'with recent as (select id from book) values (1)']
    }

    void "an update keeps the table and the where clause"() {
        expect:
        StatementCollapser.collapse('update book set title=?,version=? where id=? and version=?') ==
                'update book ... where id=? and version=?'
        StatementCollapser.collapse('update book set title=?') == 'update book ...'
        StatementCollapser.collapse("update book set title=' set where ' where id=?") == 'update book ... where id=?'
    }

    void "a name quoted like a clause is read as a name, so statements on two such tables stay apart"() {
        expect:
        StatementCollapser.collapse(sql) == collapsed

        where:
        sql                                                                   | collapsed
        'update "order set lines" set title=? where id=?'                     | 'update "order set lines" ... where id=?'
        'update "order set headers" set title=? where id=?'                   | 'update "order set headers" ... where id=?'
        'update "order "" set lines" set title=? where id=?'                  | 'update "order "" set lines" ... where id=?'
        'update `order set lines` set title=? where id=?'                     | 'update `order set lines` ... where id=?'
        'update `order `` set lines` set title=? where id=?'                  | 'update `order `` set lines` ... where id=?'
        'update [order set lines] set title=? where id=?'                     | 'update [order set lines] ... where id=?'
        'update [order]] set lines] set title=? where id=?'                   | 'update [order]] set lines] ... where id=?'
        'update book set "where"=? where id=?'                                | 'update book ... where id=?'
        'select o1_0."from" from "order from lines" o1_0 where o1_0.id=?'     | 'select ... from "order from lines" o1_0 where o1_0.id=?'
        'select o1_0.id from "order (lines)" o1_0 where o1_0.id=?'            | 'select ... from "order (lines)" o1_0 where o1_0.id=?'
        'insert into "order (lines)" (id,title) values (?,?)'                 | 'insert into "order (lines)" ...'
        'insert into "order values" values (?)'                               | 'insert into "order values" ...'
        "update book set title='it''s set where' where id=?"                  | 'update book ... where id=?'
        'update "order set lines set title=? where id=?'                      | 'update "order set lines set title=? where id=?'
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

    void "a statement is shortened as the capture recorded it, without its comments and with its whitespace collapsed"() {
        expect:
        StatementCollapser.statementOf(recorded('/* load Book */ select\n    b1_0.id\n  from\n    book b1_0 /* trailing */ where b1_0.id=?')) ==
                'select ... from book b1_0 where b1_0.id=?'
        StatementCollapser.statementOf(recorded('   ')) == ''
        StatementCollapser.collapse(null) == ''
        StatementCollapser.collapse('') == ''
    }

    void "a quoted name keeps its spaces and what reads as a comment in it"() {
        expect:
        StatementCollapser.statementOf(recorded('select  x.id\n from "order  /* lines */  x" x where x.id=?')) ==
                'select ... from "order  /* lines */  x" x where x.id=?'
        StatementCollapser.statementOf(recorded('select x.id from "order  lines" x')) !=
                StatementCollapser.statementOf(recorded('select x.id from "order lines" x'))
        StatementCollapser.statementOf(recorded('select x  /* c */ from [t  u]', QueryTailSyntax.TRANSACT_SQL)) == 'select ... from [t  u]'
    }

    void "the kind of a statement is its first keyword, whatever its case"() {
        expect:
        StatementType.of(sql) == type

        where:
        sql                                       | type
        'select 1'                                | StatementType.SELECT
        'SELECT 1'                                | StatementType.SELECT
        'with x as (select 1) select * from x'    | StatementType.SELECT
        'insert into book values (?)'             | StatementType.INSERT
        'Update book set a=?'                     | StatementType.UPDATE
        'delete from book'                        | StatementType.DELETE
        'call next value for s'                   | StatementType.OTHER
        'alter table book add x int'              | StatementType.OTHER
        null                                      | StatementType.OTHER
    }

    void "a statement that opens with common table expressions is of the kind of the statement they are written for: #sql"() {
        expect:
        StatementType.of(sql) == type

        where:
        sql                                                                                 | type
        'with x as (select id from book) insert into archive select id from x'              | StatementType.INSERT
        'with x as (select id from book) update book set a=? where id in (select id from x)' | StatementType.UPDATE
        'With x As (select id from book) DELETE from book where id in (select id from x)'   | StatementType.DELETE
        'with x as (select id from book)delete from book'                                   | StatementType.DELETE
        'with gone as (delete from book where id=? returning id) select id from gone'       | StatementType.SELECT
        'with "delete" as (select id from book) select id from "delete"'                    | StatementType.SELECT
        'with deleted_rows as (select id from book) select id from deleted_rows'            | StatementType.SELECT
        "with x as (select 'delete' as a) select a from x"                                  | StatementType.SELECT
        'with x as (select id from book) merge into archive a using x on a.id=x.id when matched then delete' | StatementType.OTHER
        'WITH 1 AS x RETURN x'                                                              | StatementType.SELECT
        'WITH $rows AS rows UNWIND rows AS row CREATE (n:Book) SET n += row'                | StatementType.INSERT
        'WITH $id AS id MATCH (n:Book) WHERE n.id = id DETACH DELETE n'                     | StatementType.DELETE
        'WITH $id AS id MERGE (n:Book {id: id})'                                            | StatementType.INSERT
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
        'EXPLAIN MATCH (n:Book) RETURN n'                                   | StatementType.SELECT
        'PROFILE MATCH (n:Book) WHERE n.id = $1 SET n.title = $2'           | StatementType.UPDATE
        'explain select b1_0.id from book b1_0 where b1_0.id=?'             | StatementType.OTHER
        'EXPLAIN ANALYZE delete from book where id=?'                       | StatementType.OTHER
        'explain shop {"explain": {"delete": ?, "deletes": [{"q": {"_id": ?}}]}}' | StatementType.OTHER
    }

    void "the plan of a SQL statement is shown whole, and that of a Cypher statement as Cypher is"() {
        expect:
        StatementCollapser.collapse(statement) == statement
        StatementType.isCypher(statement) == cypher

        where:
        statement                                               | cypher
        'explain select b1_0.id,b1_0.title from book b1_0'      | false
        'EXPLAIN MATCH (n:Book) RETURN n'                       | true
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

    void "a captured document, a MongoDB command, is shown as it was recorded, whatever its names hold: #command"() {
        expect:
        StatementCollapser.statementOf(new QueryTailRecord(sql: command, document: true)) == command

        where:
        command << ['find db.book {"filter": {"a\\"  b": ?}}',
                    'find db.book {"filter": {"a\\" from b": ?}}',
                    'update shop.book {"updates": [{"q": {"a\\" set b where c": ?}, "u": {"$set": {"title": ?}}}]}',
                    'insert shop.book(archive) {"documents": [{"title": ?}]}',
                    'find db.book(archive) {"filter": {"a\\"  b": ?}}',
                    'find db.wide  collection',
                    'select  from  "x"']
    }

    void "a captured statement that is not a document is collapsed as text"() {
        expect:
        StatementCollapser.statementOf(new QueryTailRecord(sql: 'select b1_0.id, b1_0.title from book b1_0')) == 'select ... from book b1_0'
    }

    /** A statement as the capture records it and the command reads it back. */
    private static QueryTailRecord recorded(String sql, QueryTailSyntax syntax = QueryTailSyntax.POSTGRESQL) {
        QueryTailRecord.parse(new QueryTailRecord(timestamp: 1L, sql: QueryTailRedactor.redact(sql, syntax)).toLine())
    }
}

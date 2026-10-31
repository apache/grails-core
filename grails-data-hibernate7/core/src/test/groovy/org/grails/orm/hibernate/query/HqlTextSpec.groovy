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

/**
 * One reading of an HQL string answers every question about it: the projection count, the select clause, the root
 * alias, the normalized form and the result type come from the same positions, found once.
 */
class HqlTextSpec extends Specification {

    @Unroll
    void "one reading of '#hql' gives #count projection(s), clause '#clause', root alias #alias, normalized '#normalized' and target #target"() {
        given:
        HqlText reading = HqlText.of(hql)

        expect:
        reading.projectionCount() == count
        reading.singleProjectionClause() == clause
        reading.rootAlias() == alias
        reading.normalized() == normalized
        reading.target(Book) == target

        where:
        hql                                                    | count | clause        | alias | normalized                                              | target
        'from Book'                                            | 0     | null          | null  | 'from Book'                                             | Book
        'from Book b'                                          | 0     | null          | 'b'   | 'from Book b'                                           | Book
        'select b from Book b'                                 | 1     | 'b'           | 'b'   | 'select b from Book b'                                  | Book
        "select b, 'İİ' from Book b"                           | 2     | "b, 'ii'"     | 'b'   | "select b, 'İİ' from Book b"                            | Object[]
        'select Book from Book'                                | 1     | 'book'        | null  | 'select e from Book e'                                  | Book
        'select name from Book'                                | 1     | 'name'        | null  | 'select e.name from Book e'                             | Object
        'select distinct name from Book'                       | 1     | 'name'        | null  | 'select distinct e.name from Book e'                    | Object
        'select b.name, b.author from Book b'                  | 2     | 'b.name, b.author' | 'b' | 'select b.name, b.author from Book b'               | Object[]
        'select count(b) from Book b'                          | 1     | 'count(b)'    | 'b'   | 'select count(b) from Book b'                           | null
        'select new map(b.name as n) from Book b'              | 1     | 'new map(b.name as n)' | 'b' | 'select new map(b.name as n) from Book b'       | Object
        'select b from Book as b where b.name = \'x, y\''      | 1     | 'b'           | 'b'   | 'select b from Book as b where b.name = \'x, y\''      | Book
        'from Book b where b.author in (select a from Author a)' | 0   | null          | 'b'   | 'from Book b where b.author in (select a from Author a)' | Book
        'select b from Book b /* , not a projection */'        | 1     | 'b'           | 'b'   | 'select b from Book b /* , not a projection */'         | Book
        'select m[1], b from Book b'                           | 2     | 'm[1], b'     | 'b'   | 'select m[1], b from Book b'                            | Object[]
        'from Book b where b.height--1 > 0 select b.width'     | 1     | 'b.width'     | 'b'   | 'from Book b where b.height--1 > 0 select b.width'      | Object
        '(select b.title from Book b)'                         | 1     | 'b.title'     | 'b'   | '(select b.title from Book b)'                          | Object
        '((select b.title from Book b))'                       | 1     | 'b.title'     | 'b'   | '((select b.title from Book b))'                        | Object
        '( select b.title from Book b )'                       | 1     | 'b.title'     | 'b'   | '( select b.title from Book b )'                        | Object
        '(select b.title from Book b) union (select a.name from Author a)' | 1 | 'b.title' | 'b' | '(select b.title from Book b) union (select a.name from Author a)' | Object
        "(select b.title from Book b) union select 'extra' from Book" | 1 | 'b.title' | 'b' | "(select b.title from Book b) union select 'extra' from Book" | Object
        '(select b.title from Book b) union all (select a.name from Author a)' | 1 | 'b.title' | 'b' | '(select b.title from Book b) union all (select a.name from Author a)' | Object
        '((select b.title from Book b) union all (select a.name from Author a))' | 1 | 'b.title' | 'b' | '((select b.title from Book b) union all (select a.name from Author a))' | Object
        '(select b.title from Book b) /* ) select ignored from ignored */ union (select a.name from Author a)' | 1 | 'b.title' | 'b' | '(select b.title from Book b) /* ) select ignored from ignored */ union (select a.name from Author a)' | Object
        "(select b.title from Book b where b.title <> ')')"    | 1     | 'b.title'     | 'b'   | "(select b.title from Book b where b.title <> ')')"     | Object
        "(select 'it''s ), select x from y' from Book b)"      | 1     | "'it''s ), select x from y'" | 'b' | "(select 'it''s ), select x from y' from Book b)" | Object
        '(select b."title), from ignored" from Book b)'        | 1     | 'b."title), from ignored"' | 'b' | '(select b."title), from ignored" from Book b)' | Object
        '(select b.`title), from ignored` from Book b)'        | 1     | 'b.`title), from ignored`' | 'b' | '(select b.`title), from ignored` from Book b)' | Object
        '(/* ignore ) and ( select x from y */ select b.title from Book b)' | 1 | 'b.title' | 'b' | '(/* ignore ) and ( select x from y */ select b.title from Book b)' | Object
        '(select b.title /* ), ignored */ from Book b)'        | 1     | 'b.title /* ), ignored */' | 'b' | '(select b.title /* ), ignored */ from Book b)' | Object
        '(select b.height--1 from Book b)'                     | 1     | 'b.height--1' | 'b'   | '(select b.height--1 from Book b)'                      | Object
        '(select b.title, b.author from Book b)'               | 2     | 'b.title, b.author' | 'b' | '(select b.title, b.author from Book b)'           | Object[]
        '(select count(b) from Book b)'                        | 1     | 'count(b)'    | 'b'   | '(select count(b) from Book b)'                         | null
        '(select distinct b from Book b)'                      | 1     | 'b'           | 'b'   | '(select distinct b from Book b)'                       | Book
        '(select Book from Book)'                              | 1     | 'book'        | null  | '(select Book from Book)'                               | Book
        '(select b from Book b where b.id in (select a.id from Author a))' | 1 | 'b'    | 'b'   | '(select b from Book b where b.id in (select a.id from Author a))' | Book
        '(select b.title from Book b) limit 1'                 | 1     | 'b.title'     | 'b'   | '(select b.title from Book b) limit 1'                  | Object
        '(select b.title from Book b) unionized (select a.name from Author a)' | 1 | 'b.title' | 'b' | '(select b.title from Book b) unionized (select a.name from Author a)' | Object
        ''                                                     | 0     | null          | null  | ''                                                      | Book
    }

    void "a null query reads as an empty one"() {
        expect:
        HqlText.of(null).text() == ''
        HqlText.of(null).projectionCount() == 0
        HqlText.of(null).target(Book) == Book
    }

    static class Book {
    }

}

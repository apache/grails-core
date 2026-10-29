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

class HqlTextSpec extends Specification {

    void 'analyze the root projection without rewriting #hql'() {
        given:
        HqlText text = new HqlText(hql)

        expect:
        text.hql() == hql
        text.countHqlProjections() == projections
        text.singleProjectionClause == clause
        text.getTarget(Number) == target

        where:
        hql                                                                                                        | projections | clause                 | target
        null                                                                                                       | 0           | null                   | Number
        ''                                                                                                         | 0           | null                   | Number
        'from Book b'                                                                                              | 0           | null                   | Number
        '(select b.title from Book b)'                                                                             | 1           | 'b.title'              | Object
        '((select b.title from Book b))'                                                                           | 1           | 'b.title'              | Object
        '( select b.title from Book b )'                                                                           | 1           | 'b.title'              | Object
        '(select b.title from Book b) union (select a.name from Author a)'                                          | 1           | 'b.title'              | Object
        '(select b.title from Book b) union all (select a.name from Author a)'                                      | 1           | 'b.title'              | Object
        '(select b.title from Book b) intersect (select a.name from Author a)'                                      | 1           | 'b.title'              | Object
        '(select b.title from Book b) intersect all (select a.name from Author a)'                                  | 1           | 'b.title'              | Object
        '(select b.title from Book b) except (select a.name from Author a)'                                         | 1           | 'b.title'              | Object
        '(select b.title from Book b) except all (select a.name from Author a)'                                     | 1           | 'b.title'              | Object
        '((select b.title from Book b) UNION ALL (select a.name from Author a))'                                    | 1           | 'b.title'              | Object
        '(select b.title from Book b) /* ) select ignored from ignored */ union (select a.name from Author a)'      | 1           | 'b.title'              | Object
        '(select b.title from Book b) /* ) select ignored from ignored\n */ union (select a.name from Author a)'   | 1           | 'b.title'              | Object
        "(select b.title from Book b where b.title <> ')')"                                                        | 1           | 'b.title'              | Object
        "(select 'it''s ), select x from y' from Book b)"                                                          | 1           | "'it''s ), select x from y'" | Object
        '(select b."title), from ignored" from Book b)'                                                            | 1           | 'b."title), from ignored"' | Object
        '(select b.`title), from ignored` from Book b)'                                                            | 1           | 'b.`title), from ignored`' | Object
        '(/* ignore ) and ( select x from y */ select b.title from Book b)'                                        | 1           | 'b.title'              | Object
        '(/* ignore ) and ( select x from y\n */ select b.title from Book b)'                                      | 1           | 'b.title'              | Object
        '(select b.title /* ), ignored */ from Book b)'                                                            | 1           | 'b.title /* ), ignored */' | Object
        '(select b.pages--1 from Book b)'                                                                         | 1           | 'b.pages--1'           | Object
        '(select b.title, b.pages from Book b)'                                                                    | 2           | 'b.title, b.pages'     | Object[]
        '(select concat(b.title, b.title) from Book b)'                                                             | 1           | 'concat(b.title, b.title)' | Object
        '(select count(b) from Book b)'                                                                           | 1           | 'count(b)'             | null
        '(select distinct b from Book b)'                                                                         | 1           | 'b'                    | Number
        '(select all b from Book as b)'                                                                           | 1           | 'b'                    | Number
        '(select Book from Book)'                                                                                 | 1           | 'book'                 | Number
        'from Book b where b.id in (select a.id from Author a)'                                                    | 0           | null                   | Number
        '(select b from Book b where b.id in (select a.id from Author a))'                                         | 1           | 'b'                    | Number
        '(select b.title from Book b) unionized (select a.name from Author a)'                                     | 0           | null                   | Number
    }
}

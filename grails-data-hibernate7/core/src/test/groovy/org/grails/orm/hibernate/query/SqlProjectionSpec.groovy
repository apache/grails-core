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

import org.hibernate.type.StandardBasicTypes
import spock.lang.Specification
import spock.lang.Unroll

class SqlProjectionSpec extends Specification {

    @Unroll
    void 'the SQL #sql is split into the columns #columns'() {
        expect:
        SqlGroupProjection.of(sql)*.sql == columns
        SqlProjection.of(sql, columns.collect { null }, columns.collect { null })*.sql == columns

        where:
        sql                                             | columns
        'width'                                         | ['width']
        'width, sum(height) as total'                   | ['width', 'sum(height) as total']
        "coalesce(label, 'a,b'), label"                 | ["coalesce(label, 'a,b')", 'label']
        'concat(a, concat(b, c)), d'                    | ['concat(a, concat(b, c))', 'd']
        '"odd,name", other'                             | ['"odd,name"', 'other']
        '[odd,name], ARRAY[a, b]'                       | ['[odd,name]', 'ARRAY[a, b]']
        'sum(height) as [total (cm)], c'                | ['sum(height) as [total (cm)]', 'c']
        'sum(height) as [a]]b], c'                      | ['sum(height) as [a]]b]', 'c']
        'arr[f(a, b)], ARRAY[ARRAY[1, 2]], c'           | ['arr[f(a, b)]', 'ARRAY[ARRAY[1, 2]]', 'c']
        'a /* b, c */, d'                               | ['a /* b, c */', 'd']
        'a -- b, c\n, d'                                | ['a -- b, c', 'd']
        '$$a,b$$, c'                                    | ['$$a,b$$', 'c']
        '$tag$a,$$,b$tag$, c'                           | ['$tag$a,$$,b$tag$', 'c']
        'a$b, $1'                                       | ['a$b', '$1']
    }

    @Unroll
    void 'the alias #alias is removed from #column'() {
        expect:
        SqlProjection.of(column, [alias], [null])*.sql == [expected]

        where:
        column                          | alias       | expected
        'sum(height) as total'          | 'total'     | 'sum(height)'
        'sum(height) AS Total'          | 'total'     | 'sum(height)'
        'sum(height) as "total"'        | 'total'     | 'sum(height)'
        'sum(height) as `total`'        | 'total'     | 'sum(height)'
        'sum(height) as [total]'        | 'total'     | 'sum(height)'
        'cast(width as integer) as w'   | 'w'         | 'cast(width as integer)'
        'cast(width as integer)'        | 'integer'   | 'cast(width as integer)'
        'sum(height) as total /* c */'  | 'total'     | 'sum(height)'
        'sum(height) as total -- c'     | 'total'     | 'sum(height)'
        'sum(height) as total'          | 'other'     | 'sum(height) as total'
        'sum(height) as "total]'        | 'total'     | 'sum(height) as "total]'
        'width'                         | 'width'     | 'width'
        'sum(height) as total'          | null        | 'sum(height) as total'
    }

    @Unroll
    void 'the quoted alias #alias, which may hold any character, is removed from #column'() {
        expect:
        SqlProjection.of(column, [alias], [null])*.sql == [expected]

        where:
        column                          | alias          | expected
        'sum(height) as "total height"' | 'total height' | 'sum(height)'
        'sum(height) as `total height`' | 'total height' | 'sum(height)'
        'sum(height) as [total-height]' | 'total-height' | 'sum(height)'
        'sum(height) as "say ""hi"""'   | 'say "hi"'     | 'sum(height)'
        'sum(height) as "total height"' | 'total'        | 'sum(height) as "total height"'
    }

    void 'each column becomes a projection with its alias and type'() {
        when:
        List<SqlProjection> projections = SqlProjection.of('width, sum(height) as total', ['width', 'total'],
                [StandardBasicTypes.INTEGER, Long])

        then:
        projections*.sql == ['width', 'sum(height)']
        projections*.columnAlias == ['width', 'total']
        projections*.type == [Integer, Long]
        projections*.declaredType == [StandardBasicTypes.INTEGER, Long]
    }

    void 'a projection without a type projects a value of any type'() {
        expect:
        new SqlProjection('width', 'width', null).type == Object
        new SqlProjection('width', 'width', null).declaredType == null
    }

    void 'the group by clause is split into one expression each'() {
        expect:
        SqlGroupProjection.of("date_trunc('day', created_at), kind")*.sql == ["date_trunc('day', created_at)", 'kind']
    }

    void 'a type that is not a type constant, a Hibernate type or a class is rejected'() {
        when:
        SqlProjection.of('width', ['width'], ['integer'])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('must be a StandardBasicTypes constant')
    }
}

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

import static org.grails.orm.hibernate.query.SqlSyntax.H2
import static org.grails.orm.hibernate.query.SqlSyntax.MYSQL
import static org.grails.orm.hibernate.query.SqlSyntax.ORACLE
import static org.grails.orm.hibernate.query.SqlSyntax.POSTGRESQL
import static org.grails.orm.hibernate.query.SqlSyntax.SQL
import static org.grails.orm.hibernate.query.SqlSyntax.TRANSACT_SQL

class SqlProjectionSpec extends Specification {

    static final List<SqlSyntax> NATIVE = [TRANSACT_SQL, POSTGRESQL, MYSQL, ORACLE, H2, SQL]

    @Unroll
    void 'the SQL #sql is split into the columns #columns in every SQL syntax'() {
        expect:
        NATIVE.every { SqlSyntax syntax ->
            SqlGroupProjection.of(sql, syntax)*.sql == columns &&
                    SqlProjection.of(sql, columns.collect { null }, columns.collect { null }, syntax)*.sql == columns
        }

        where:
        sql                                             | columns
        'width'                                         | ['width']
        'width, sum(height) as total'                   | ['width', 'sum(height) as total']
        "coalesce(label, 'a,b'), label"                 | ["coalesce(label, 'a,b')", 'label']
        'concat(a, concat(b, c)), d'                    | ['concat(a, concat(b, c))', 'd']
        '"odd,name", other'                             | ['"odd,name"', 'other']
        'a /* b, c */, d'                               | ['a /* b, c */', 'd']
        'a -- b, c\n, d'                                | ['a -- b, c', 'd']
        'a$b, $1'                                       | ['a$b', '$1']
    }

    @Unroll
    void 'the SQL #sql is split into the columns #columns where brackets hold arrays'() {
        expect:
        SqlGroupProjection.of(sql, SQL)*.sql == columns
        SqlProjection.of(sql, columns.collect { null }, columns.collect { null }, SQL)*.sql == columns

        where:
        sql                                             | columns
        'arr[f(a, b)], ARRAY[ARRAY[1, 2]], c'           | ['arr[f(a, b)]', 'ARRAY[ARRAY[1, 2]]', 'c']
        'ARRAY [a, b], "items"[1]'                      | ['ARRAY [a, b]', '"items"[1]']
        "ARRAY['['], height"                            | ["ARRAY['[']", 'height']
    }

    @Unroll
    void 'the SQL #sql is split into the columns #columns on #syntax, by its dollar quoted strings'() {
        expect:
        SqlGroupProjection.of(sql, syntax)*.sql == columns

        where:
        sql                      | syntax     || columns
        '$$a,b$$, c'             | POSTGRESQL || ['$$a,b$$', 'c']
        '$$a,b$$, c'             | H2         || ['$$a,b$$', 'c']
        '$tag$a,$$,b$tag$, c'    | POSTGRESQL || ['$tag$a,$$,b$tag$', 'c']
        '$tag$a,$$,b$tag$, c'    | H2         || ['$tag$a', '$$,b$tag$, c']
    }

    @Unroll
    void 'the SQL #sql is split into the columns #columns on #syntax'() {
        expect:
        SqlGroupProjection.of(sql, syntax)*.sql == columns

        where:
        sql                              | syntax || columns
        "concat('it\\'s, ', b), c"       | MYSQL  || ["concat('it\\'s, ', b)", 'c']
        "concat('it\\'s, ', b), c"       | H2     || ["concat('it\\'s, ', b), c"]
        "q'[it's, ]' as a, c"            | ORACLE || ["q'[it's, ]' as a", 'c']
        'a # one, two\n, c'              | MYSQL  || ['a # one, two', 'c']
        'a /* /* x, y */ z, */, c'       | POSTGRESQL || ['a /* /* x, y */ z, */', 'c']
        'a /* /* x, y */ z, */, c'       | TRANSACT_SQL || ['a /* /* x, y */ z, */', 'c']
    }

    @Unroll
    void 'the Transact-SQL #sql is split into the columns #columns where brackets quote an identifier'() {
        expect:
        SqlGroupProjection.of(sql, TRANSACT_SQL)*.sql == columns
        SqlProjection.of(sql, columns.collect { null }, columns.collect { null }, TRANSACT_SQL)*.sql == columns

        where:
        sql                                             | columns
        '[odd,name], [a[b]'                             | ['[odd,name]', '[a[b]']
        'sum(height) as [total (cm)], c'                | ['sum(height) as [total (cm)]', 'c']
        'sum(height) as [a]]b], c'                      | ['sum(height) as [a]]b]', 'c']
        "width as [box's width], height as total"       | ["width as [box's width]", 'height as total']
        'width as [a"b], c'                             | ['width as [a"b]', 'c']
    }

    @Unroll
    void 'the alias #alias is removed from #column in every SQL syntax'() {
        expect:
        NATIVE.every { SqlProjection.of(column, [alias], [null], it)*.sql == [expected] }

        where:
        column                          | alias          | expected
        'sum(height) as total'          | 'total'        | 'sum(height)'
        'sum(height) AS Total'          | 'total'        | 'sum(height)'
        'sum(height) as "total"'        | 'total'        | 'sum(height)'
        'sum(height) as `total`'        | 'total'        | 'sum(height)'
        'cast(width as integer) as w'   | 'w'            | 'cast(width as integer)'
        'cast(width as integer)'        | 'integer'      | 'cast(width as integer)'
        'sum(height) as total /* c */'  | 'total'        | 'sum(height)'
        'sum(height) as total -- c'     | 'total'        | 'sum(height)'
        'sum(height) as total'          | 'other'        | 'sum(height) as total'
        'sum(height) as "total]'        | 'total'        | 'sum(height) as "total]'
        'sum(height) as "total height"' | 'total height' | 'sum(height)'
        'sum(height) as `total height`' | 'total height' | 'sum(height)'
        'sum(height) as "say ""hi"""'   | 'say "hi"'     | 'sum(height)'
        'sum(height) as "total height"' | 'total'        | 'sum(height) as "total height"'
        'width'                         | 'width'        | 'width'
        'sum(height) as total'          | null           | 'sum(height) as total'
    }

    void 'a bracket alias is removed only where brackets quote an identifier'() {
        expect:
        SqlProjection.of('sum(height) as [total]', ['total'], [null], TRANSACT_SQL)*.sql == ['sum(height)']
        SqlProjection.of('sum(height) as [total-height]', ['total-height'], [null], TRANSACT_SQL)*.sql == ['sum(height)']
        SqlProjection.of('sum(height) as [total]', ['total'], [null], SQL)*.sql == ['sum(height) as [total]']
    }

    void 'each column becomes a projection with its alias and type'() {
        when:
        List<SqlProjection> projections = SqlProjection.of('width, sum(height) as total', ['width', 'total'],
                [StandardBasicTypes.INTEGER, Long], SQL)

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
        SqlGroupProjection.of("date_trunc('day', created_at), kind", SQL)*.sql == ["date_trunc('day', created_at)", 'kind']
    }

    void 'a type that is not a type constant, a Hibernate type or a class is rejected'() {
        when:
        SqlProjection.of('width', ['width'], ['integer'], SQL)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('must be a StandardBasicTypes constant')
    }

    void 'the numbers of columns, aliases and types must match'() {
        when:
        SqlProjection.of('width, height', ['width'], [null], SQL)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('selects 2 columns but 1 column aliases')

        when:
        SqlProjection.of('width', ['width'], [], SQL)

        then:
        e = thrown()
        e.message.contains('as many types as column aliases')

        when:
        SqlGroupProjection.of(' ', SQL)

        then:
        e = thrown()
        e.message.contains('must not be empty')
    }
}

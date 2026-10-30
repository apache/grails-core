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

import static org.grails.orm.hibernate.query.SqlSyntax.SQL
import static org.grails.orm.hibernate.query.SqlSyntax.TRANSACT_SQL

class SqlRestrictionSpec extends Specification {

    @Unroll
    void 'the restriction #sql with the values #values is accepted in the #syntax syntax'() {
        when:
        SqlRestriction restriction = SqlRestriction.of(sql, values, syntax)

        then:
        restriction.sql() == sql
        restriction.values() == (values ?: [])

        where:
        sql                                      | values     | syntax
        'a = ?'                                  | [1]        | SQL
        'a = ?'                                  | [1]        | TRANSACT_SQL
        "a = ? and b = '?' -- ?"                 | [1]        | SQL
        'id = ANY(ARRAY [CAST(? AS INTEGER)])'   | [1]        | SQL
        '"items"[?] = ?'                         | [1, 2]     | SQL
        'a = [?] and b = ?'                      | [1, 2]     | SQL
        '[a?b] = ?'                              | [1]        | TRANSACT_SQL
        '[a[b] = ?'                              | [1]        | TRANSACT_SQL
        'a = [?] and b = ?'                      | [1]        | TRANSACT_SQL
        'a = 1'                                  | []         | SQL
        'a = $$?$$ and b = ?'                    | [1]        | SqlSyntax.POSTGRESQL
        'a = $$?$$ and b = ?'                    | [1]        | SqlSyntax.H2
        'a = $$?$$ and b = ?'                    | [1, 2]     | SqlSyntax.MYSQL
        "a = 'it\\'s ?' and b = ?"               | [1]        | SqlSyntax.MYSQL
        "a = q'[it's ?]' and b = ?"              | [1]        | SqlSyntax.ORACLE
        "doc ?? 'key' and b = ?"                 | [1]        | SqlSyntax.POSTGRESQL
        'a = 1 # is it? \nand b = ?'             | [1]        | SqlSyntax.MYSQL
        'a = 1'                                  | null       | SQL
    }

    @Unroll
    void 'the restriction #sql with #count values is rejected in the #syntax syntax, which reads #placeholders placeholders'() {
        when:
        SqlRestriction.of(sql, (1..count).toList(), syntax)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The SQL of a sqlRestriction has $placeholders ? placeholders but $count values: $sql"

        where:
        sql                                      | count | syntax       | placeholders
        'a = ?'                                  | 2     | SQL          | 1
        '[a?b] = ?'                              | 1     | SQL          | 2
        'a = [?] and b = ?'                      | 2     | TRANSACT_SQL | 1
        '"items"[?] = ?'                         | 2     | TRANSACT_SQL | 1
    }

    void 'the constructor does not count the placeholders, which depend on the database, and the rendering does'() {
        expect: 'a mismatch is accepted where no connection is known'
        new SqlRestriction('a = ?', []).values() == []
        new SqlRestriction('a = 1', [1]).values() == [1]

        when: 'the null checks still apply'
        new SqlRestriction('a = ?', [null])

        then:
        thrown(IllegalArgumentException)
    }

    void 'the restriction function of 8.0.0, built without a syntax, reads SQL'() {
        given:
        def typeConfiguration = Stub(org.hibernate.type.spi.TypeConfiguration) {
            getBasicTypeRegistry() >> Stub(org.hibernate.type.BasicTypeRegistry) {
                resolve(org.hibernate.type.StandardBasicTypes.BOOLEAN) >> Stub(org.hibernate.type.BasicType)
            }
        }

        expect:
        new GrailsSqlRestrictionFunction(typeConfiguration).name == GrailsSqlRestrictionFunction.NAME
    }

    void 'the SQL and the values must not be null'() {
        when:
        SqlRestriction.of(null, [], SQL)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The SQL of a sqlRestriction must not be null'

        when:
        SqlRestriction.of('a = ?', [null], SQL)

        then:
        e = thrown()
        e.message == 'The values of a sqlRestriction must not be null: a = ?'
    }

    void 'the values are copied, a character sequence as a string, and cannot be changed'() {
        given:
        List<Object> values = [new StringBuilder('a'), 2]

        when:
        SqlRestriction restriction = SqlRestriction.of('a = ? and b = ?', values, SQL)
        values << 3

        then:
        restriction.values() == ['a', 2]
        restriction.values()[0] instanceof String

        when:
        restriction.values() << 4

        then:
        thrown(UnsupportedOperationException)
    }
}

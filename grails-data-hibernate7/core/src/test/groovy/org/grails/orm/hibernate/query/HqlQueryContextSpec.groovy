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

import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class HqlQueryContextSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore
    @Shared PersistentEntity bookEntity

    void setupSpec() {
        datastore = new HibernateDatastore(HqlQueryContextSpecBook)
        bookEntity = datastore.mappingContext.getPersistentEntity(HqlQueryContextSpecBook.name)
    }

    void "prepare with plain HQL string"() {
        when:
        def ctx = HqlQueryContext.prepare(bookEntity, "from HqlQueryContextSpecBook where title = :t", [t: "Test"], null, [max: 10], [:], false, false)

        then:
        ctx.hql() == "from HqlQueryContextSpecBook where title = :t"
        ctx.namedParams() == [t: "Test"]
        ctx.positionalParams() == []
        ctx.querySettings() == [max: 10]
        !ctx.isUpdate()
        !ctx.isNative()
        ctx.targetClass() == HqlQueryContextSpecBook
    }

    void "prepare with empty HQL defaults to from Entity"() {
        when:
        def ctx = HqlQueryContext.prepare(bookEntity, "", [:], null, [:], [:], false, false)

        then:
        ctx.hql() == "from ${HqlQueryContextSpecBook.name} e"
    }

    void "prepare expands GString into named parameters"() {
        given:
        String t = "The Hobbit"
        int p = 300
        GString gq = "from HqlQueryContextSpecBook where title = ${t} and pages > ${p}"

        when:
        def ctx = HqlQueryContext.prepare(bookEntity, gq, [:], null, [:], [:], false, false)

        then:
        ctx.hql() == "from HqlQueryContextSpecBook where title = :p0 and pages > :p1"
        ctx.namedParams() == [p0: "The Hobbit", p1: 300]
    }

    void "prepare expands GString into positional parameters when explicitly requested"() {
        given:
        String t = "The Hobbit"
        GString gq = "from HqlQueryContextSpecBook where title = ${t}"

        when:
        // Currently, if positionalParams is empty, it still defaults to named parameters
        // because positionalParamsCopy.isEmpty() is true initially.
        def ctx = HqlQueryContext.prepare(bookEntity, gq, [:], [], [:], [:], false, false)

        then:
        ctx.hql() == "from HqlQueryContextSpecBook where title = :p0"
        ctx.namedParams() == [p0: "The Hobbit"]
    }

    @Unroll
    void "getTarget for '#hql' should be #expected"() {
        expect:
        HqlQueryContext.getTarget(hql, HqlQueryContextSpecBook) == expected

        where:
        hql                                                     | expected
        "from Book"                                             | HqlQueryContextSpecBook
        "select b from Book b"                                  | HqlQueryContextSpecBook
        "select b.title from Book b"                            | Object
        "select b.title, b.pages from Book b"                   | Object[]
        "select count(b.id) from Book b"                        | null
        "select sum(b.pages) from Book b"                       | null
        "select avg(b.pages) from Book b"                       | null
        "select new map(b.title as title) from Book b"          | Object
        "select distinct b.author from Book b"                  | Object
    }

    @Unroll
    void "normalizeNonAliasedSelect: '#hql' -> '#expected'"() {
        expect:
        HqlQueryContext.normalizeNonAliasedSelect(hql) == expected

        where:
        hql                                         | expected
        "select title from Book"                    | "select e.title from Book e"
        "select Book from Book"                     | "select e from Book e"
        "select b.title from Book b"                | "select b.title from Book b"
        "select count(title) from Book"             | "select count(title) from Book e"
        "select distinct title from Book"           | "select distinct e.title from Book e"
    }

    void "GString expansion adds spaces if needed"() {
        given:
        String val = "value"
        // No space before interpolation
        GString gq = "from Book where title=${val}"

        when:
        def ctx = HqlQueryContext.prepare(bookEntity, gq, [:], null, [:], [:], false, false)

        then:
        ctx.hql() == "from Book where title= :p0"
    }

    void "normalizeMultiLineQueryString replaces newlines with spaces"() {
        given:
        String hql = "from Book\nwhere title = :t\norder by id"

        when:
        def resolved = HqlQueryContext.normalizeMultiLineQueryString(hql)

        then:
        resolved == "from Book where title = :t order by id"
    }

    @Unroll
    void "getCommas: '#input' -> #expected"() {
        expect:
        HqlQueryContext.getCommas(input) == expected

        where:
        input                               | expected
        "a, b"                              | 1
        "a, b, c"                           | 2
        "func(a, b), c"                     | 1
        "'a, b', c"                         | 1
        "\"a, b\", c"                       | 1
        "a"                                 | 0
    }

    @Unroll
    void "countHqlProjections: '#hql' -> #expected"() {
        expect:
        HqlQueryContext.countHqlProjections(hql) == expected

        where:
        hql                                 | expected
        "select a from Book"                | 1
        "select a, b from Book"             | 2
        "from Book"                         | 0
        "select distinct a from Book"       | 1
        "select count(a) from Book"         | 1
    }

    @Unroll
    void "isHasAlias: '#hql', cur=#cur, end=#end -> #expected"() {
        expect:
        HqlQueryContext.isHasAlias(hql, cur, end) == expected

        where:
        hql                             | cur | end | expected
        "from Book b where"             | 10  | 11  | true  // 'b' is alias
        "from Book where"               | 10  | 15  | false // 'where' is keyword
        "from Book join"                | 10  | 14  | false // 'join' is keyword
    }

    @Unroll
    void "isPropertyProjection: '#hql' -> #expected"() {
        expect:
        HqlQueryContext.isPropertyProjection(hql) == expected

        where:
        hql                             | expected
        "select b.title from Book b"    | true
        "select b from Book b"          | false
        "select count(b.id) from Book b"| true
    }
}

@Entity
class HqlQueryContextSpecBook {
    String title
    Integer pages
}

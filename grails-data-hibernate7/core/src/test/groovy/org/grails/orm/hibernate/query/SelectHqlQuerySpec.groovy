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


import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.FlushMode
import spock.lang.Unroll

import org.grails.datastore.mapping.query.Query

class SelectHqlQuerySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([SelectHqlQuerySpecBook, SelectHqlQuerySpecAuthor])
    }

    def setup() {
        def author = new SelectHqlQuerySpecAuthor(name: "Tolkien").save(flush: true)
        new SelectHqlQuerySpecBook(title: "The Hobbit",     pages: 310, author: author).save()
        new SelectHqlQuerySpecBook(title: "Fellowship",     pages: 423, author: author).save()
        new SelectHqlQuerySpecBook(title: "The Two Towers", pages: 352, author: author).save(flush: true)
    }

    private Query buildHqlQuery(CharSequence hql, Map namedParams = [:], List positionalParams = null, Map args = [:], boolean isUpdate = false) {
        def entity = mappingContext.getPersistentEntity(SelectHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, hql, namedParams, positionalParams, args, [:], false, isUpdate)
        HibernateHqlQueryCreator.createHqlQuery(datastore, sessionFactory, entity, ctx)
    }

    // ─── countHqlProjections ────────────────────────────────────────────────

    void "countHqlProjections returns 0 for null"() {
        expect: HqlQueryContext.countHqlProjections(null) == 0
    }

    void "countHqlProjections returns 0 for empty string"() {
        expect: HqlQueryContext.countHqlProjections("") == 0
    }

    void "countHqlProjections returns 0 when no SELECT clause"() {
        expect: HqlQueryContext.countHqlProjections("from SelectHqlQuerySpecBook") == 0
    }

    void "countHqlProjections returns 1 for single projection"() {
        expect: HqlQueryContext.countHqlProjections("select b.title from SelectHqlQuerySpecBook b") == 1
    }

    void "countHqlProjections returns 2 for multiple top-level projections"() {
        expect: HqlQueryContext.countHqlProjections("select b.title, b.pages from SelectHqlQuerySpecBook b") == 2
    }

    void "countHqlProjections ignores commas inside function calls"() {
        expect: HqlQueryContext.countHqlProjections("select count(b.title) from SelectHqlQuerySpecBook b") == 1
    }

    void "countHqlProjections handles DISTINCT single projection"() {
        expect: HqlQueryContext.countHqlProjections("select distinct b.title from SelectHqlQuerySpecBook b") == 1
    }

    void "countHqlProjections handles constructor expression as single projection"() {
        expect: HqlQueryContext.countHqlProjections("select new map(b.title as t, b.pages as p) from SelectHqlQuerySpecBook b") == 1
    }

    // ─── getTarget ──────────────────────────────────────────────────────────

    void "getTarget returns entity class when no SELECT clause"() {
        expect:
        HqlQueryContext.getTarget("from SelectHqlQuerySpecBook", SelectHqlQuerySpecBook) == SelectHqlQuerySpecBook
    }

    void "getTarget returns entity class for single entity projection"() {
        expect:
        HqlQueryContext.getTarget("select b from SelectHqlQuerySpecBook b", SelectHqlQuerySpecBook) == SelectHqlQuerySpecBook
    }

    void "getTarget returns Object for single scalar projection"() {
        expect:
        HqlQueryContext.getTarget("select b.title from SelectHqlQuerySpecBook b", SelectHqlQuerySpecBook) == Object
    }

    void "getTarget returns Object array for multiple projections"() {
        expect:
        HqlQueryContext.getTarget("select b.title, b.pages from SelectHqlQuerySpecBook b", SelectHqlQuerySpecBook) == Object[].class
    }

    // ─── createHqlQuery + executeQuery ──────────────────────────────────────

    void "createHqlQuery with plain HQL returns all results"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook").list()
        then:
        results.size() == 3
    }

    void "createHqlQuery with named parameters filters correctly"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title = :title", [title: "The Hobbit"]).list()
        then:
        results.size() == 1
        results[0].title == "The Hobbit"
    }

    void "createHqlQuery with positional parameters filters correctly"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title = ?1", [:], ["Fellowship"]).list()
        then:
        results.size() == 1
        results[0].title == "Fellowship"
    }

    void "createHqlQuery with max arg limits results"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook", [:], null, [max: 2]).list()
        then:
        results.size() == 2
    }

    void "createHqlQuery with offset arg skips results"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook order by title", [:], null, [offset: 2]).list()
        then:
        results.size() == 1
    }

    void "createHqlQuery with empty query string defaults to full entity query"() {
        when:
        def results = buildHqlQuery("").list()
        then:
        results.size() == 3
    }

    void "createHqlQuery executes update"() {
        when:
        int updated = buildHqlQuery("update SelectHqlQuerySpecBook set pages = 999 where title = :t",
                [t: "The Hobbit"], null, [:], true).executeUpdate()
        then:
        updated == 1
    }

    void "createHqlQuery with GString builds named parameters automatically"() {
        given:
        String titleVal = "The Two Towers"
        GString gq = "from SelectHqlQuerySpecBook b where b.title = ${titleVal}"
        when:
        def hqlQuery = buildHqlQuery(gq)
        def results = hqlQuery.list()
        then:
        results.size() == 1
        results[0].title == "The Two Towers"
    }

    void "createHqlQuery with GString can build positional parameters if explicitly requested"() {
        given:
        String titleVal = "The Two Towers"
        GString gq = "from SelectHqlQuerySpecBook b where b.title = ${titleVal}"
        when: "positionalParams is provided as non-null (triggering positional branch in prepare)"
        // We pass an empty but non-null list to trigger the positional branch
        def hqlQuery = buildHqlQuery(gq, [:], [])
        def results = hqlQuery.list()

        then:
        results.size() == 1
        results[0].title == "The Two Towers"
    }

    void "createHqlQuery with multiline query normalizes whitespace"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b\nwhere b.pages > :p", [p: 350]).list()
        then:
        results.size() == 2
    }

    // ─── setFlushMode ───────────────────────────────────────────────────────

    @Unroll
    void "setFlushMode maps Hibernate #hibernateMode correctly"() {
        when:
        buildHqlQuery("from SelectHqlQuerySpecBook", [:], null, [flushMode: hibernateMode])
        then:
        noExceptionThrown()
        where:
        hibernateMode << [FlushMode.AUTO, FlushMode.ALWAYS, FlushMode.COMMIT, FlushMode.MANUAL]
    }

    // ─── parameter handling ─────────────────────────────────────────────────

    void "createHqlQuery with list parameter filters correctly"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title in (:titles)",
                [titles: ["The Hobbit", "Fellowship"]]).list()
        then:
        results.size() == 2
    }

    void "createHqlQuery with null parameter value handles correctly"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title = :t", [t: null]).list()
        then:
        results.size() == 0
    }

    void "createHqlQuery filters GORM internal settings from parameters"() {
        when: "passing internal GORM settings as named parameters"
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title = :t", 
                [t: "The Hobbit", flushMode: FlushMode.COMMIT, cache: true]).list()

        then: "no exception is thrown and results are returned"
        results.size() == 1
    }

    void "createHqlQuery handles array and CharSequence parameters"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title in (:titles)",
                [titles: ["The Hobbit"] as String[]]).list()
        then:
        results.size() == 1

        when:
        def results2 = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title = :t",
                [t: new StringBuilder("Fellowship")]).list()
        then:
        results2.size() == 1
    }

    void "createHqlQuery handles positional CharSequence parameters"() {
        when:
        def results = buildHqlQuery("from SelectHqlQuerySpecBook b where b.title = ?1",
                [:], [new StringBuilder("The Hobbit")]).list()
        then:
        results.size() == 1
    }

    // ─── delegate behaviour ─────────────────────────────────────────────────

    void "selectQuery is non-null for SELECT queries"() {
        expect:
        buildHqlQuery("from SelectHqlQuerySpecBook").selectQuery() != null
    }

    void "selectQuery is null for UPDATE/DELETE queries"() {
        expect:
        buildHqlQuery("update SelectHqlQuerySpecBook set pages = 1 where title = :t",
                [t: "The Hobbit"], null, [:], true).selectQuery() == null
    }

    void "populateQuerySettings silently ignores select-only args for mutation queries"() {
        when: "max/offset/cache args passed to an UPDATE query — should not throw"
        buildHqlQuery("update SelectHqlQuerySpecBook set pages = 1 where title = :t",
                [t: "The Hobbit"], null, [max: 2, offset: 1, cache: true, fetchSize: 10, readOnly: true], true)
        then:
        noExceptionThrown()
    }

    void "executeUpdate throws UnsupportedOperationException for SELECT query"() {
        when:
        buildHqlQuery("from SelectHqlQuerySpecBook").executeUpdate()
        then:
        thrown(UnsupportedOperationException)
    }

    void "list throws UnsupportedOperationException for UPDATE query"() {
        when:
        buildHqlQuery("update SelectHqlQuerySpecBook set pages = 1 where title = :t",
                [t: "The Hobbit"], null, [:], true).list()
        then:
        thrown(UnsupportedOperationException)
    }

    void "singleResult returns first result when multiple rows match"() {
        given: "a second author with multiple books matching the same HQL query"
        def author2 = new SelectHqlQuerySpecAuthor(name: "Tolkien2").save(flush: true)
        new SelectHqlQuerySpecBook(title: "Extra Book", pages: 200, author: author2).save(flush: true)

        when: "singleResult is called on an HQL query that returns multiple rows"
        def result = buildHqlQuery("from SelectHqlQuerySpecBook").singleResult()

        then: "first result is returned without throwing"
        result != null
        result instanceof SelectHqlQuerySpecBook
    }

    void "aggregate avg() query returns a Double result"() {
        when: "executing an avg aggregate HQL query"
        def result = buildHqlQuery("select avg(b.pages) from SelectHqlQuerySpecBook b").list()

        then: "result is returned as a Double without type mismatch exception"
        result.size() == 1
        result[0] instanceof Double
    }

    void "aggregate max() on Integer column returns a Number result"() {
        when: "executing a max aggregate HQL query on an Integer property"
        def result = buildHqlQuery("select max(b.pages) from SelectHqlQuerySpecBook b").list()

        then: "result is returned as a Number without type mismatch exception"
        result.size() == 1
        result[0] instanceof Number
    }

    void "aggregate min() on Integer column returns a Number result"() {
        when: "executing a min aggregate HQL query on an Integer property"
        def result = buildHqlQuery("select min(b.pages) from SelectHqlQuerySpecBook b").list()

        then: "result is returned as a Number without type mismatch exception"
        result.size() == 1
        result[0] instanceof Number
    }

    void "aggregate sum() on Integer column returns a Number result"() {
        when: "executing a sum aggregate HQL query on an Integer property"
        def result = buildHqlQuery("select sum(b.pages) from SelectHqlQuerySpecBook b").list()

        then: "result is returned as a Number without type mismatch exception"
        result.size() == 1
        result[0] instanceof Number
    }

    void "count() aggregate returns a Long result"() {
        when: "executing a count aggregate HQL query"
        def result = buildHqlQuery("select count(b) from SelectHqlQuerySpecBook b").list()

        then: "result is returned as a Long without type mismatch exception"
        result.size() == 1
        result[0] instanceof Long
    }

    // ─── Additional edge cases for coverage ───────────────────────────────────


    def "populateQuerySettings handles timeout, readOnly, and flushMode"() {
        when:
        buildHqlQuery("from SelectHqlQuerySpecBook", [:], null, [timeout: 10, readOnly: true, flushMode: FlushMode.ALWAYS])

        then:
        noExceptionThrown()
    }

    def "populateQuerySettings handles lock and cache interaction"() {
        when:
        buildHqlQuery("from SelectHqlQuerySpecBook", [:], null, [lock: true])

        then:
        noExceptionThrown()
    }


    def "buildQuery handles native query"() {
        given:
        def entity = mappingContext.getPersistentEntity(SelectHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, "SELECT * FROM select_hql_query_spec_book", [:], null, [:], [:], true, false)

        when:
        def hqlQuery = HibernateHqlQueryCreator.createHqlQuery(datastore, sessionFactory, entity, ctx)

        then:
        hqlQuery != null
    }

    void "buildQuery handles hints"() {
        given:
        def entity = mappingContext.getPersistentEntity(SelectHqlQuerySpecBook.name)
        def hql = "from SelectHqlQuerySpecBook"
        def hints = ["jakarta.persistence.query.timeout": 1000]
        def ctx = HqlQueryContext.prepare(entity, hql, [:], null, [:], hints, false, false)

        when:
        def hqlQuery = HibernateHqlQueryCreator.createHqlQuery(datastore, sessionFactory, entity, ctx)

        then:
        hqlQuery != null
    }
}

@Entity
class SelectHqlQuerySpecBook {
    String title
    Integer pages
    SelectHqlQuerySpecAuthor author

    static belongsTo = [author: SelectHqlQuerySpecAuthor]

    static constraints = {
        title nullable: false
        pages nullable: false
        author nullable: true
    }
}

@Entity
class SelectHqlQuerySpecAuthor {
    String name

    static hasMany = [books: SelectHqlQuerySpecBook]

    static constraints = {
        name nullable: false
    }
}

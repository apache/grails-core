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

import org.hibernate.query.QueryFlushMode

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.FlushMode
import spock.lang.Unroll

import org.grails.datastore.mapping.query.Query

class HibernateHqlQuerySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HibernateHqlQuerySpecBook, HibernateHqlQuerySpecAuthor])
    }

    def setup() {
        def author = new HibernateHqlQuerySpecAuthor(name: "Tolkien").save(flush: true)
        new HibernateHqlQuerySpecBook(title: "The Hobbit",     pages: 310, author: author).save()
        new HibernateHqlQuerySpecBook(title: "Fellowship",     pages: 423, author: author).save()
        new HibernateHqlQuerySpecBook(title: "The Two Towers", pages: 352, author: author).save(flush: true)
    }

    private Query buildHqlQuery(CharSequence hql, Map namedParams = [:], List positionalParams = null, Map args = [:], boolean isUpdate = false) {
        def entity = mappingContext.getPersistentEntity(HibernateHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, hql, namedParams, positionalParams, args, false, isUpdate)
        def session = sessionFactory.currentSession
        HibernateHqlQuery.buildQuery(session, datastore, sessionFactory, entity, ctx)
    }

    // ─── countHqlProjections ────────────────────────────────────────────────

    void "countHqlProjections returns 0 for null"() {
        expect: HqlQueryContext.countHqlProjections(null) == 0
    }

    void "countHqlProjections returns 0 for empty string"() {
        expect: HqlQueryContext.countHqlProjections("") == 0
    }

    void "countHqlProjections returns 0 when no SELECT clause"() {
        expect: HqlQueryContext.countHqlProjections("from HibernateHqlQuerySpecBook") == 0
    }

    void "countHqlProjections returns 1 for single projection"() {
        expect: HqlQueryContext.countHqlProjections("select b.title from HibernateHqlQuerySpecBook b") == 1
    }

    void "countHqlProjections returns 2 for multiple top-level projections"() {
        expect: HqlQueryContext.countHqlProjections("select b.title, b.pages from HibernateHqlQuerySpecBook b") == 2
    }

    void "countHqlProjections ignores commas inside function calls"() {
        expect: HqlQueryContext.countHqlProjections("select count(b.title) from HibernateHqlQuerySpecBook b") == 1
    }

    void "countHqlProjections handles DISTINCT single projection"() {
        expect: HqlQueryContext.countHqlProjections("select distinct b.title from HibernateHqlQuerySpecBook b") == 1
    }

    void "countHqlProjections handles constructor expression as single projection"() {
        expect: HqlQueryContext.countHqlProjections("select new map(b.title as t, b.pages as p) from HibernateHqlQuerySpecBook b") == 1
    }

    // ─── getTarget ──────────────────────────────────────────────────────────

    void "getTarget returns entity class when no SELECT clause"() {
        expect:
        HqlQueryContext.getTarget("from HibernateHqlQuerySpecBook", HibernateHqlQuerySpecBook) == HibernateHqlQuerySpecBook
    }

    void "getTarget returns entity class for single entity projection"() {
        expect:
        HqlQueryContext.getTarget("select b from HibernateHqlQuerySpecBook b", HibernateHqlQuerySpecBook) == HibernateHqlQuerySpecBook
    }

    void "getTarget returns Object for single scalar projection"() {
        expect:
        HqlQueryContext.getTarget("select b.title from HibernateHqlQuerySpecBook b", HibernateHqlQuerySpecBook) == Object
    }

    void "getTarget returns Object array for multiple projections"() {
        expect:
        HqlQueryContext.getTarget("select b.title, b.pages from HibernateHqlQuerySpecBook b", HibernateHqlQuerySpecBook) == Object[].class
    }

    // ─── createHqlQuery + executeQuery ──────────────────────────────────────

    void "createHqlQuery with plain HQL returns all results"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook").list()
        then:
        results.size() == 3
    }

    void "createHqlQuery with named parameters filters correctly"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = :title", [title: "The Hobbit"]).list()
        then:
        results.size() == 1
        results[0].title == "The Hobbit"
    }

    void "createHqlQuery with positional parameters filters correctly"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = ?1", [:], ["Fellowship"]).list()
        then:
        results.size() == 1
        results[0].title == "Fellowship"
    }

    void "createHqlQuery with max arg limits results"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook", [:], null, [max: 2]).list()
        then:
        results.size() == 2
    }

    void "createHqlQuery with offset arg skips results"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook order by title", [:], null, [offset: 2]).list()
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
        int updated = buildHqlQuery("update HibernateHqlQuerySpecBook set pages = 999 where title = :t",
                [t: "The Hobbit"], null, [:], true).executeUpdate()
        then:
        updated == 1
    }

    void "createHqlQuery with GString builds named parameters automatically"() {
        given:
        String titleVal = "The Two Towers"
        GString gq = "from HibernateHqlQuerySpecBook b where b.title = ${titleVal}"
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
        GString gq = "from HibernateHqlQuerySpecBook b where b.title = ${titleVal}"
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
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b\nwhere b.pages > :p", [p: 350]).list()
        then:
        results.size() == 2
    }

    // ─── setFlushMode ───────────────────────────────────────────────────────

    @Unroll
    void "setFlushMode maps Hibernate #hibernateMode correctly"() {
        when:
        buildHqlQuery("from HibernateHqlQuerySpecBook", [:], null, [flushMode: hibernateMode])
        then:
        noExceptionThrown()
        where:
        hibernateMode << [FlushMode.AUTO, FlushMode.ALWAYS, FlushMode.COMMIT, FlushMode.MANUAL]
    }

    // ─── parameter handling ─────────────────────────────────────────────────

    void "createHqlQuery with list parameter filters correctly"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title in (:titles)",
                [titles: ["The Hobbit", "Fellowship"]]).list()
        then:
        results.size() == 2
    }

    void "createHqlQuery with null parameter value handles correctly"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = :t", [t: null]).list()
        then:
        results.size() == 0
    }

    void "createHqlQuery filters GORM internal settings from parameters"() {
        when: "passing internal GORM settings as named parameters"
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = :t", 
                [t: "The Hobbit", flushMode: FlushMode.COMMIT, cache: true]).list()

        then: "no exception is thrown and results are returned"
        results.size() == 1
    }

    void "createHqlQuery handles array and CharSequence parameters"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title in (:titles)",
                [titles: ["The Hobbit"] as String[]]).list()
        then:
        results.size() == 1

        when:
        def results2 = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = :t",
                [t: new StringBuilder("Fellowship")]).list()
        then:
        results2.size() == 1
    }

    void "createHqlQuery handles positional CharSequence parameters"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = ?1",
                [:], [new StringBuilder("The Hobbit")]).list()
        then:
        results.size() == 1
    }

    // ─── delegate behaviour ─────────────────────────────────────────────────

    void "selectQuery is non-null for SELECT queries"() {
        expect:
        buildHqlQuery("from HibernateHqlQuerySpecBook").selectQuery() != null
    }

    void "selectQuery is null for UPDATE/DELETE queries"() {
        expect:
        buildHqlQuery("update HibernateHqlQuerySpecBook set pages = 1 where title = :t",
                [t: "The Hobbit"], null, [:], true).selectQuery() == null
    }

    void "populateQuerySettings silently ignores select-only args for mutation queries"() {
        when: "max/offset/cache args passed to an UPDATE query — should not throw"
        buildHqlQuery("update HibernateHqlQuerySpecBook set pages = 1 where title = :t",
                [t: "The Hobbit"], null, [max: 2, offset: 1, cache: true, fetchSize: 10, readOnly: true], true)
        then:
        noExceptionThrown()
    }

    void "executeUpdate throws UnsupportedOperationException for SELECT query"() {
        when:
        buildHqlQuery("from HibernateHqlQuerySpecBook").executeUpdate()
        then:
        thrown(UnsupportedOperationException)
    }

    void "list throws UnsupportedOperationException for UPDATE query"() {
        when:
        buildHqlQuery("update HibernateHqlQuerySpecBook set pages = 1 where title = :t",
                [t: "The Hobbit"], null, [:], true).list()
        then:
        thrown(UnsupportedOperationException)
    }

    void "singleResult returns first result when multiple rows match"() {
        given: "a second author with multiple books matching the same HQL query"
        def author2 = new HibernateHqlQuerySpecAuthor(name: "Tolkien2").save(flush: true)
        new HibernateHqlQuerySpecBook(title: "Extra Book", pages: 200, author: author2).save(flush: true)

        when: "singleResult is called on an HQL query that returns multiple rows"
        def result = buildHqlQuery("from HibernateHqlQuerySpecBook").singleResult()

        then: "first result is returned without throwing"
        result != null
        result instanceof HibernateHqlQuerySpecBook
    }

    void "aggregate avg() query returns a Double result"() {
        when: "executing an avg aggregate HQL query"
        def result = buildHqlQuery("select avg(b.pages) from HibernateHqlQuerySpecBook b").list()

        then: "result is returned as a Double without type mismatch exception"
        result.size() == 1
        result[0] instanceof Double
    }

    void "aggregate max() on Integer column returns a Number result"() {
        when: "executing a max aggregate HQL query on an Integer property"
        def result = buildHqlQuery("select max(b.pages) from HibernateHqlQuerySpecBook b").list()

        then: "result is returned as a Number without type mismatch exception"
        result.size() == 1
        result[0] instanceof Number
    }

    void "aggregate min() on Integer column returns a Number result"() {
        when: "executing a min aggregate HQL query on an Integer property"
        def result = buildHqlQuery("select min(b.pages) from HibernateHqlQuerySpecBook b").list()

        then: "result is returned as a Number without type mismatch exception"
        result.size() == 1
        result[0] instanceof Number
    }

    void "aggregate sum() on Integer column returns a Number result"() {
        when: "executing a sum aggregate HQL query on an Integer property"
        def result = buildHqlQuery("select sum(b.pages) from HibernateHqlQuerySpecBook b").list()

        then: "result is returned as a Number without type mismatch exception"
        result.size() == 1
        result[0] instanceof Number
    }

    void "count() aggregate returns a Long result"() {
        when: "executing a count aggregate HQL query"
        def result = buildHqlQuery("select count(b) from HibernateHqlQuerySpecBook b").list()

        then: "result is returned as a Long without type mismatch exception"
        result.size() == 1
        result[0] instanceof Long
    }

    // ─── Additional edge cases for coverage ───────────────────────────────────

    def "convertQueryFlushMode handles various inputs"() {
        expect:
        HibernateHqlQuery.convertQueryFlushMode(FlushMode.ALWAYS) == org.hibernate.query.QueryFlushMode.FLUSH
        HibernateHqlQuery.convertQueryFlushMode(FlushMode.MANUAL) == org.hibernate.query.QueryFlushMode.NO_FLUSH
        HibernateHqlQuery.convertQueryFlushMode(FlushMode.COMMIT) == org.hibernate.query.QueryFlushMode.NO_FLUSH
        HibernateHqlQuery.convertQueryFlushMode(FlushMode.AUTO) == org.hibernate.query.QueryFlushMode.DEFAULT
        HibernateHqlQuery.convertQueryFlushMode("ALWAYS") == org.hibernate.query.QueryFlushMode.FLUSH
        HibernateHqlQuery.convertQueryFlushMode("INVALID") == org.hibernate.query.QueryFlushMode.DEFAULT
        HibernateHqlQuery.convertQueryFlushMode(null) == org.hibernate.query.QueryFlushMode.DEFAULT
    }

    def "populateQuerySettings handles timeout, readOnly, and flushMode"() {
        when:
        buildHqlQuery("from HibernateHqlQuerySpecBook", [:], null, [timeout: 10, readOnly: true, flushMode: FlushMode.ALWAYS])

        then:
        noExceptionThrown()
    }

    def "populateQuerySettings handles lock and cache interaction"() {
        when:
        buildHqlQuery("from HibernateHqlQuerySpecBook", [:], null, [lock: true])

        then:
        noExceptionThrown()
    }


    def "buildQuery handles native query"() {
        given:
        def entity = mappingContext.getPersistentEntity(HibernateHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, "SELECT * FROM hibernate_hql_query_spec_book", [:], null, [:], true, false)
        def session = sessionFactory.currentSession

        when:
        def hqlQuery = HibernateHqlQuery.buildQuery(session, datastore, sessionFactory, entity, ctx)

        then:
        hqlQuery != null
    }
}

@Entity
class HibernateHqlQuerySpecBook {
    String title
    Integer pages
    HibernateHqlQuerySpecAuthor author

    static belongsTo = [author: HibernateHqlQuerySpecAuthor]

    static constraints = {
        title nullable: false
        pages nullable: false
        author nullable: true
    }
}

@Entity
class HibernateHqlQuerySpecAuthor {
    String name

    static hasMany = [books: HibernateHqlQuerySpecBook]

    static constraints = {
        name nullable: false
    }
}

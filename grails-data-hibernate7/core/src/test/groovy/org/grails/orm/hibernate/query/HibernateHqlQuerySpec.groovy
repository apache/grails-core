package org.grails.orm.hibernate.query

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.FlushMode
import spock.lang.Unroll

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

    private HibernateHqlQuery buildHqlQuery(String hql, Map namedParams = [:], List positionalParams = null, Map args = [:], boolean isUpdate = false) {
        def entity = mappingContext.getPersistentEntity(HibernateHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(hql, false, isUpdate, namedParams, entity)
        def session = sessionFactory.currentSession
        def hqlQuery = HibernateHqlQuery.buildQuery(session, datastore, sessionFactory, entity, ctx)
        if (args) hqlQuery.populateQuerySettings(new HashMap(args))
        if (ctx.namedParams()) hqlQuery.populateQueryWithNamedArguments(new HashMap(ctx.namedParams()))
        else if (positionalParams) hqlQuery.populateQueryWithIndexedArguments(positionalParams)
        hqlQuery
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
        def entity = mappingContext.getPersistentEntity(HibernateHqlQuerySpecBook.name)
        def ctx = HqlQueryContext.prepare(gq, false, false, [:], entity)
        def hqlQuery = HibernateHqlQuery.buildQuery(sessionFactory.currentSession, datastore, sessionFactory, entity, ctx)
        if (ctx.namedParams()) hqlQuery.populateQueryWithNamedArguments(new HashMap(ctx.namedParams()))
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
        buildHqlQuery("from HibernateHqlQuerySpecBook").setFlushMode(hibernateMode)
        then:
        noExceptionThrown()
        where:
        hibernateMode << [FlushMode.AUTO, FlushMode.ALWAYS, FlushMode.COMMIT, FlushMode.MANUAL]
    }

    // ─── named parameter edge cases ─────────────────────────────────────────

    void "populateQueryWithNamedArguments handles list parameter"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title in (:titles)",
                [titles: ["The Hobbit", "Fellowship"]]).list()
        then:
        results.size() == 2
    }

    void "populateQueryWithNamedArguments handles null value"() {
        when:
        def results = buildHqlQuery("from HibernateHqlQuerySpecBook b where b.title = :t", [t: null]).list()
        then:
        results.size() == 0
    }

    void "populateQueryWithNamedArguments throws for non-string key"() {
        when:
        buildHqlQuery("from HibernateHqlQuerySpecBook")
                .populateQueryWithNamedArguments([(42): "value"])
        then:
        thrown(Exception)
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

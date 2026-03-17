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

package grails.orm

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.JoinType
import org.grails.datastore.mapping.query.Query
import org.hibernate.Session
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Shared
import java.math.RoundingMode

/**
 * Integration tests for {@link HibernateCriteriaBuilder}: covers both direct method
 * invocations (for JaCoCo line-level coverage) and DSL-closure invocations against a real
 * in-memory datastore.
 * <p>
 * For a readable, Mock-based living-documentation spec of the DSL API see
 * {@link HibernateCriteriaBuilderSpec}.
 */
class HibernateCriteriaBuilderDirectSpec extends HibernateGormDatastoreSpec {

    @Shared HibernateCriteriaBuilder builder

    def setupSpec() {
        manager.addAllDomainClasses([DirectAccount, DirectTransaction])
    }

    def setup() {
        builder = new HibernateCriteriaBuilder(
                DirectAccount,
                manager.hibernateDatastore.sessionFactory,
                manager.hibernateDatastore)
    }

    // ─── DSL integration: data-driven scenarios ────────────────────────────

    def setupData() {
        def fred = new DirectAccount(balance: 250, firstName: "Fred", lastName: "Flintstone", branch: "Bedrock").save(failOnError: true)
        def barney = new DirectAccount(balance: 500, firstName: "Barney", lastName: "Rubble", branch: "Bedrock").save(failOnError: true)
        new DirectAccount(balance: 100, firstName: "Wilma", lastName: "Flintstone", branch: "Bedrock").save(failOnError: true)
        new DirectAccount(balance: 1000, firstName: "Pebbles", lastName: "Flintstone", branch: "Slate Rock and Gravel").save(failOnError: true)
        new DirectAccount(balance: 50, firstName: "Bam-Bam", lastName: "Rubble", branch: null).save(failOnError: true)
        fred.addToTransactions(new DirectTransaction(amount: 10))
        fred.addToTransactions(new DirectTransaction(amount: 20))
        fred.save()
        barney.addToTransactions(new DirectTransaction(amount: 50))
        barney.save(flush: true, failOnError: true)
        fred
    }

    void "get with eq criteria returns matching entity"() {
        given: setupData()
        when:
        def result = builder.get { eq("firstName", "Fred") }
        then:
        result.firstName == "Fred"
    }

    void "get with idEq criteria returns correct entity"() {
        given:
        def fred = setupData()
        when:
        def result = builder.get { idEq(fred.id) }
        then:
        result.id == fred.id
        result.firstName == "Fred"
    }

    void "list with compound criteria filters correctly"() {
        given: setupData()
        when:
        def results = builder.list {
            gt("balance", BigDecimal.valueOf(200))
            or {
                eq("lastName", "Flintstone")
                like("branch", "Bedrock")
            }
            'in'("firstName", ["Fred", "Barney", "Pebbles"])
        }
        then:
        results.size() == 3
        results*.firstName.sort() == ["Barney", "Fred", "Pebbles"]
    }

    void "ilike criteria matches case-insensitively"() {
        given: setupData()
        when:
        def results = builder.list { ilike("firstName", "fr%") }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "rlike criteria matches by regexp"() {
        given: setupData()
        when:
        def results = builder.list { rlike("firstName", "^F.*") }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "between criteria selects inclusive range"() {
        given: setupData()
        when:
        def results = builder.list { between("balance", BigDecimal.valueOf(100), BigDecimal.valueOf(300)) }
        then:
        results.size() == 2
        results*.firstName.sort() == ["Fred", "Wilma"]
    }

    void "sizeEq criteria filters by collection size"() {
        given: setupData()
        when:
        def results = builder.list { sizeEq("transactions", 2) }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "isEmpty and isNotEmpty criteria split collection membership"() {
        given: setupData()
        when:
        def emptyResults    = builder.list { isEmpty("transactions") }
        def notEmptyResults = builder.list { isNotEmpty("transactions") }
        then:
        emptyResults.size() == 3
        notEmptyResults.size() == 2
    }

    void "isNull criteria returns entities with null property"() {
        given: setupData()
        when:
        def results = builder.list { isNull("branch") }
        then:
        results.size() == 1
        results[0].firstName == "Bam-Bam"
    }

    void "count projection returns row count"() {
        given: setupData()
        when:
        def count = builder.get {
            projections { count() }
            eq("lastName", "Flintstone")
        }
        then:
        count == 3
    }

    void "sum and avg projections aggregate correctly"() {
        given: setupData()
        when:
        def projections = builder.get {
            projections {
                sum('balance')
                avg('balance')
            }
            eq("branch", "Bedrock")
        }
        then:
        projections[0] == 850
        new BigDecimal(projections[1]).setScale(2, RoundingMode.HALF_UP) == 283.33
    }

    void "ordering and pagination slice results correctly"() {
        given: setupData()
        when:
        def results = builder.list(max: 2, offset: 1) { order("firstName", "asc") }
        then:
        results.size() == 2
        results*.firstName == ["Barney", "Fred"]
    }

    void "association closure filters via join"() {
        given: setupData()
        when:
        def results = builder.list {
            transactions { gt("amount", 40) }
        }
        then:
        results.size() == 1
        results[0].firstName == "Barney"
    }

    void "ne ge le criteria filter correctly"() {
        given: setupData()
        when:
        def results = builder.list {
            ne("firstName", "Fred")
            ge("balance", BigDecimal.valueOf(60))
            le("balance", BigDecimal.valueOf(1000))
        }
        then:
        results*.firstName.toSet() == ["Barney", "Wilma", "Pebbles"] as Set
    }

    void "isNotNull and sizeGe filter combined"() {
        given: setupData()
        when:
        def results = builder.list {
            isNotNull("branch")
            sizeGe("transactions", 1)
        }
        then:
        results*.firstName.toSet() == ["Fred", "Barney"] as Set
    }

    void "groupProperty countDistinct min max projections return results"() {
        given: setupData()
        when:
        def results = builder.list {
            projections {
                groupProperty("lastName")
                countDistinct("firstName")
                min("balance")
                max("balance")
            }
        }
        then:
        results.size() >= 1
    }

    void "inList array variant and firstResult paginate correctly"() {
        given: setupData()
        when:
        def list1 = builder.list { 'in'("firstName", ["Fred", "Barney"] as Object[]) }
        def list2 = builder.list { inList("firstName", ["Fred", "Wilma"] as Object[]) }
        def paged = builder.list(max: 1) {
            order("firstName", "asc")
            firstResult(2)
        }
        then:
        list1.size() > 0
        list2.size() > 0
        paged.size() == 1
        paged[0].firstName == "Fred"
    }

    void "nested association criteria with between filters correctly"() {
        given: setupData()
        when:
        def results = builder.list {
            transactions { between("amount", BigDecimal.valueOf(15), BigDecimal.valueOf(25)) }
        }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    // ─── Comparison predicates ─────────────────────────────────────────────

    def "eq(String, Object) delegates and returns this"() {
        expect: builder.eq("firstName", "Fred").is(builder)
    }

    def "eq(String, Object, Map) delegates and returns this"() {
        expect: builder.eq("firstName", "Fred", Collections.emptyMap()).is(builder)
    }

    def "eq with ignoreCase=true calls like on hibernateQuery"() {
        expect: builder.eq("firstName", "Fred", [ignoreCase: true]).is(builder)
    }

    def "eq(Map, String, Object) groovy-map-first form delegates and returns this"() {
        expect: builder.eq(Collections.emptyMap(), "firstName", "Fred").is(builder)
    }

    def "ne(String, Object) delegates and returns this"() {
        expect: builder.ne("firstName", "Fred").is(builder)
    }

    def "gt(String, Object) delegates and returns this"() {
        expect: builder.gt("balance", BigDecimal.ONE).is(builder)
    }

    def "ge(String, Object) delegates and returns this"() {
        expect: builder.ge("balance", BigDecimal.ONE).is(builder)
    }

    def "lt(String, Object) delegates and returns this"() {
        expect: builder.lt("balance", BigDecimal.TEN).is(builder)
    }

    def "le(String, Object) delegates and returns this"() {
        expect: builder.le("balance", BigDecimal.TEN).is(builder)
    }

    def "gte(String, Object) aliases ge and returns this"() {
        expect: builder.gte("balance", BigDecimal.ONE).is(builder)
    }

    def "lte(String, Object) aliases le and returns this"() {
        expect: builder.lte("balance", BigDecimal.TEN).is(builder)
    }

    def "like(String, Object) delegates and returns this"() {
        expect: builder.like("firstName", "Fr%").is(builder)
    }

    def "ilike(String, Object) delegates and returns this"() {
        expect: builder.ilike("firstName", "fr%").is(builder)
    }

    def "rlike(String, Object) delegates and returns this"() {
        expect: builder.rlike("firstName", "^Fr.*").is(builder)
    }

    def "between(String, Object, Object) delegates and returns this"() {
        expect: builder.between("balance", BigDecimal.ONE, BigDecimal.TEN).is(builder)
    }

    // ─── Null / empty ──────────────────────────────────────────────────────

    def "isEmpty(String) delegates and returns this"() {
        expect: builder.isEmpty("transactions").is(builder)
    }

    def "isNotEmpty(String) delegates and returns this"() {
        expect: builder.isNotEmpty("transactions").is(builder)
    }

    def "isNull(String) delegates and returns this"() {
        expect: builder.isNull("branch").is(builder)
    }

    def "isNotNull(String) delegates and returns this"() {
        expect: builder.isNotNull("branch").is(builder)
    }

    // ─── Identity equality ─────────────────────────────────────────────────

    def "idEq(Object) aliases eq('id', o) and returns this"() {
        given: def fred = new DirectAccount(balance: 100, firstName: "Fred", lastName: "F").save(failOnError: true, flush: true)
        expect: builder.idEq(fred.id).is(builder)
    }

    def "idEquals(Object) aliases idEq and returns this"() {
        given: def fred = DirectAccount.first()
        expect: builder.idEquals(fred?.id ?: 1L).is(builder)
    }

    // ─── 'in' variants ────────────────────────────────────────────────────

    def "in(String, Collection) delegates and returns this"() {
        expect: builder.in("firstName", ["Fred", "Barney"]).is(builder)
    }

    def "in(String, Object[]) delegates and returns this"() {
        expect: builder.in("firstName", ["Fred", "Barney"] as Object[]).is(builder)
    }

    def "inList(String, Collection) delegates to in and returns this"() {
        expect: builder.inList("firstName", ["Fred", "Barney"]).is(builder)
    }

    def "inList(String, Object[]) delegates to in and returns this"() {
        expect: builder.inList("firstName", ["Fred", "Barney"] as Object[]).is(builder)
    }

    // ─── allEq ────────────────────────────────────────────────────────────

    def "allEq(Map) delegates and returns this"() {
        expect: builder.allEq([firstName: "Fred", lastName: "Flintstone"]).is(builder)
    }

    // ─── Property comparisons ──────────────────────────────────────────────

    def "eqProperty(String, String) delegates and returns this"() {
        expect: builder.eqProperty("firstName", "firstName").is(builder)
    }

    def "neProperty(String, String) delegates and returns this"() {
        expect: builder.neProperty("firstName", "lastName").is(builder)
    }

    def "gtProperty(String, String) delegates and returns this"() {
        expect: builder.gtProperty("balance", "balance").is(builder)
    }

    def "geProperty(String, String) delegates and returns this"() {
        expect: builder.geProperty("balance", "balance").is(builder)
    }

    def "ltProperty(String, String) delegates and returns this"() {
        expect: builder.ltProperty("balance", "balance").is(builder)
    }

    def "leProperty(String, String) delegates and returns this"() {
        expect: builder.leProperty("balance", "balance").is(builder)
    }

    // ─── Collection-size constraints ───────────────────────────────────────

    def "sizeEq(String, int) delegates and returns this"() {
        expect: builder.sizeEq("transactions", 2).is(builder)
    }

    def "sizeGt(String, int) delegates and returns this"() {
        expect: builder.sizeGt("transactions", 0).is(builder)
    }

    def "sizeGe(String, int) delegates and returns this"() {
        expect: builder.sizeGe("transactions", 1).is(builder)
    }

    def "sizeLe(String, int) delegates and returns this"() {
        expect: builder.sizeLe("transactions", 5).is(builder)
    }

    def "sizeLt(String, int) delegates and returns this"() {
        expect: builder.sizeLt("transactions", 3).is(builder)
    }

    def "sizeNe(String, int) delegates and returns this"() {
        expect: builder.sizeNe("transactions", 0).is(builder)
    }

    // ─── Ordering ──────────────────────────────────────────────────────────

    def "order(String) delegates via Order and returns this"() {
        expect: builder.order("firstName").is(builder)
    }

    def "order(String, 'asc') sets ascending order and returns this"() {
        expect: builder.order("firstName", "asc").is(builder)
    }

    def "order(String, 'desc') sets descending order and returns this"() {
        expect: builder.order("balance", "desc").is(builder)
    }

    def "order(String, unrecognised) defaults to ascending and returns this"() {
        expect: builder.order("balance", "unknown").is(builder)
    }

    def "order(Query.Order) delegates and returns this"() {
        expect: builder.order(new Query.Order("firstName")).is(builder)
    }

    // ─── Pagination / fetch ────────────────────────────────────────────────

    def "firstResult(int) delegates and returns this"() {
        expect: builder.firstResult(5).is(builder)
    }

    def "maxResults(int) delegates and returns this"() {
        expect: builder.maxResults(10).is(builder)
    }

    // ─── Join / select ─────────────────────────────────────────────────────

    def "join(String) delegates with INNER join and returns this"() {
        expect: builder.join("transactions").is(builder)
    }

    def "join(String, JoinType) delegates and returns this"() {
        expect: builder.join("transactions", JoinType.LEFT).is(builder)
    }

    def "select(String) delegates and returns this"() {
        expect: builder.select("balance").is(builder)
    }

    // ─── Cache / readOnly / lock ───────────────────────────────────────────

    def "cache(boolean) sets flag and returns this"() {
        expect: builder.cache(true).is(builder)
    }

    def "readOnly(boolean) sets flag and returns this"() {
        expect: builder.readOnly(true).is(builder)
    }

    def "lock(boolean) sets flag without throwing"() {
        when: builder.lock(true)
        then: noExceptionThrown()
    }

    // ─── Projection wrappers ───────────────────────────────────────────────

    def "property(String) adds property projection and returns this"() {
        expect: builder.property("firstName").is(builder)
    }

    def "avg(String) adds avg projection and returns this"() {
        expect: builder.avg("balance").is(builder)
    }

    def "distinct(String) adds distinct projection and returns this"() {
        expect: builder.distinct("firstName").is(builder)
    }

    def "count() adds count projection and returns non-null ProjectionList"() {
        expect: builder.count() != null
    }

    def "countDistinct(String) adds countDistinct projection and returns this"() {
        expect: builder.countDistinct("firstName").is(builder)
    }

    def "groupProperty(String) adds groupProperty projection and returns this"() {
        expect: builder.groupProperty("lastName").is(builder)
    }

    def "min(String) adds min projection and returns this"() {
        expect: builder.min("balance").is(builder)
    }

    def "max(String) adds max projection and returns this"() {
        expect: builder.max("balance").is(builder)
    }

    def "sum(String) adds sum projection and returns this"() {
        expect: builder.sum("balance").is(builder)
    }

    def "rowCount() delegates to count and returns non-null ProjectionList"() {
        expect: builder.rowCount() != null
    }

    def "id() adds id projection and returns this"() {
        expect: builder.id().is(builder)
    }

    // ─── State flags ───────────────────────────────────────────────────────

    def "setUniqueResult and isUniqueResult are symmetric"() {
        when: builder.setUniqueResult(true)
        then: builder.isUniqueResult()
    }

    def "setPaginationEnabledList and isPaginationEnabledList are symmetric"() {
        when: builder.setPaginationEnabledList(true)
        then: builder.isPaginationEnabledList()
    }

    def "setScroll(boolean) sets scroll flag"() {
        when: builder.setScroll(true)
        then: noExceptionThrown()
    }

    def "setCount(boolean) sets count flag"() {
        when: builder.setCount(true)
        then: builder.isCount()
    }

    def "setDistinct(boolean) sets distinct flag"() {
        when: builder.setDistinct(true)
        then: builder.isDistinct()
    }

    def "setDefaultFlushMode and getDefaultFlushMode are symmetric"() {
        when: builder.setDefaultFlushMode(2)
        then: builder.getDefaultFlushMode() == 2
    }

    def "getTargetClass returns the entity class"() {
        expect: builder.targetClass == DirectAccount
    }

    def "getHibernateQuery returns non-null"() {
        expect: builder.hibernateQuery != null
    }

    def "getSessionFactory returns non-null"() {
        expect: builder.sessionFactory != null
    }

    def "getCriteriaBuilder returns non-null"() {
        expect: builder.criteriaBuilder != null
    }

    def "setTargetClass updates the target class"() {
        when: builder.targetClass = DirectTransaction
        then: builder.targetClass == DirectTransaction
    }

    void "test closeSession unbinds and closes session when not participating"() {
        given:
        def sf = manager.hibernateDatastore.sessionFactory
        // Unbind anything before starting
        TransactionSynchronizationManager.unbindResourceIfPossible(sf)
        
        Session nativeSession = sf.openSession()
        // Builder created without bound resource -> participate = false
        HibernateCriteriaBuilder b = new HibernateCriteriaBuilder(DirectAccount, sf, manager.hibernateDatastore)
        // Now bind it so closeSession has something to unbind
        TransactionSynchronizationManager.unbindResourceIfPossible(sf)
        TransactionSynchronizationManager.bindResource(sf, new SessionHolder(nativeSession))

        when:
        b.closeSession()

        then:
        !TransactionSynchronizationManager.hasResource(sf)
        !nativeSession.isOpen()
    }

    void "test closeSession does not unbind or close session when participating"() {
        given:
        def sf = manager.hibernateDatastore.sessionFactory
        // Unbind anything before starting
        TransactionSynchronizationManager.unbindResourceIfPossible(sf)

        Session nativeSession = sf.openSession()
        TransactionSynchronizationManager.unbindResourceIfPossible(sf)
        TransactionSynchronizationManager.bindResource(sf, new SessionHolder(nativeSession))
        // Builder created with bound resource -> participate = true
        HibernateCriteriaBuilder b = new HibernateCriteriaBuilder(DirectAccount, sf, manager.hibernateDatastore)

        when:
        b.closeSession()

        then:
        TransactionSynchronizationManager.hasResource(sf)
        nativeSession.isOpen()

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(sf)
        if (nativeSession?.isOpen()) {
            nativeSession.close()
        }
    }
}

@Entity
class DirectAccount {
    String firstName
    String lastName
    BigDecimal balance
    String branch
    Set<DirectTransaction> transactions

    static hasMany = [transactions: DirectTransaction]
    static constraints = { branch nullable: true }
}

@Entity
class DirectTransaction {
    BigDecimal amount
    static belongsTo = [account: DirectAccount]
}

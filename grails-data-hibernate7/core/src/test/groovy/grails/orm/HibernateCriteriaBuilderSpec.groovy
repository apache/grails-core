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
import org.grails.datastore.mapping.query.api.BuildableCriteria

/**
 * Living documentation for the {@link HibernateCriteriaBuilder} DSL.
 * <p>
 * Every feature method below demonstrates one DSL idiom that a developer can copy into
 * application code. Tests are backed by a real in-memory datastore so they also verify
 * that each DSL call produces the correct SQL and returns the expected results.
 * <p>
 * For low-level method coverage (JaCoCo line hits) see
 * {@link HibernateCriteriaBuilderDirectSpec}.
 *
 * <h2>DSL entry points</h2>
 * <pre>
 *   // via domain class
 *   def c = Account.createCriteria()
 *   def results = c.list { eq("firstName", "Fred") }
 *
 *   // shorthand
 *   def results = Account.withCriteria { eq("firstName", "Fred") }
 * </pre>
 */
class HibernateCriteriaBuilderSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([CriteriaAccount, CriteriaTransaction])
    }

    BuildableCriteria c

    def setup() {
        c = new HibernateCriteriaBuilder(CriteriaAccount, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)

        def fred   = new CriteriaAccount(balance: 250,  firstName: "Fred",    lastName: "Flintstone", branch: "Bedrock").save(failOnError: true)
        def barney = new CriteriaAccount(balance: 500,  firstName: "Barney",  lastName: "Rubble",     branch: "Bedrock").save(failOnError: true)
        new CriteriaAccount(balance: 100,  firstName: "Wilma",   lastName: "Flintstone", branch: "Bedrock").save(failOnError: true)
        new CriteriaAccount(balance: 1000, firstName: "Pebbles", lastName: "Flintstone", branch: "Slate Rock and Gravel").save(failOnError: true)
        new CriteriaAccount(balance: 50,   firstName: "Bam-Bam", lastName: "Rubble",     branch: null).save(failOnError: true)

        fred.addToTransactions(new CriteriaTransaction(amount: 10))
        fred.addToTransactions(new CriteriaTransaction(amount: 20))
        fred.save()
        barney.addToTransactions(new CriteriaTransaction(amount: 50))
        barney.save(flush: true, failOnError: true)
    }

    // ─── Equality ──────────────────────────────────────────────────────────

    /**
     * {@code eq} — exact equality.
     * <pre>
     *   Account.withCriteria { eq("firstName", "Fred") }
     * </pre>
     */
    void "eq matches exact property value"() {
        when:
        def result = c.get { eq("firstName", "Fred") }
        then:
        result.firstName == "Fred"
    }

    /**
     * {@code idEq} — shorthand for equality on the identity property.
     * <pre>
     *   Account.withCriteria { idEq(fred.id) }
     * </pre>
     */
    void "idEq matches by primary key"() {
        given:
        def fred = CriteriaAccount.findByFirstName("Fred")
        when:
        def result = c.get { idEq(fred.id) }
        then:
        result.id == fred.id
    }

    /**
     * {@code ne} — not-equal.
     * <pre>
     *   Account.withCriteria { ne("firstName", "Fred") }
     * </pre>
     */
    void "ne excludes the named value"() {
        when:
        def results = c.list { ne("firstName", "Fred") }
        then:
        !results*.firstName.contains("Fred")
        results.size() == 4
    }

    // ─── Range / comparison ────────────────────────────────────────────────

    /**
     * {@code between} — inclusive range on a single property.
     * <pre>
     *   Account.withCriteria { between("balance", 100, 300) }
     * </pre>
     */
    void "between selects values within the inclusive range"() {
        when:
        def results = c.list { between("balance", BigDecimal.valueOf(100), BigDecimal.valueOf(300)) }
        then:
        results*.firstName.sort() == ["Fred", "Wilma"]
    }

    /**
     * {@code gt} / {@code ge} / {@code lt} / {@code le} — numeric comparisons.
     * <pre>
     *   Account.withCriteria {
     *       ge("balance", 60)
     *       le("balance", 1000)
     *   }
     * </pre>
     */
    void "gt ge lt le filter by comparison"() {
        when:
        def results = c.list {
            ge("balance", BigDecimal.valueOf(100))
            lt("balance", BigDecimal.valueOf(600))
        }
        then:
        results*.firstName.sort() == ["Barney", "Fred", "Wilma"]
    }

    // ─── String matching ───────────────────────────────────────────────────

    /**
     * {@code like} — SQL LIKE pattern (case-sensitive, {@code %} and {@code _} wildcards).
     * <pre>
     *   Account.withCriteria { like("firstName", "Fr%") }
     * </pre>
     */
    void "like matches SQL LIKE pattern"() {
        when:
        def results = c.list { like("firstName", "Fr%") }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    /**
     * {@code ilike} — case-insensitive LIKE.
     * <pre>
     *   Account.withCriteria { ilike("firstName", "fr%") }
     * </pre>
     */
    void "ilike matches case-insensitively"() {
        when:
        def results = c.list { ilike("firstName", "FR%") }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    /**
     * {@code rlike} — regular-expression match (dialect-dependent).
     * <pre>
     *   Account.withCriteria { rlike("firstName", "^F.*") }
     * </pre>
     */
    void "rlike matches by regular expression"() {
        when:
        def results = c.list { rlike("firstName", "^F.*") }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    // ─── Null / empty ──────────────────────────────────────────────────────

    /**
     * {@code isNull} / {@code isNotNull} — null-check predicates.
     * <pre>
     *   Account.withCriteria { isNull("branch") }
     * </pre>
     */
    void "isNull and isNotNull split on null property"() {
        when:
        def nullBranch    = c.list { isNull("branch") }
        def nonNullBranch = c.list { isNotNull("branch") }
        then:
        nullBranch.size() == 1
        nullBranch[0].firstName == "Bam-Bam"
        nonNullBranch.size() == 4
    }

    /**
     * {@code isEmpty} / {@code isNotEmpty} — empty-collection predicates.
     * <pre>
     *   Account.withCriteria { isEmpty("transactions") }
     * </pre>
     */
    void "isEmpty and isNotEmpty split on collection emptiness"() {
        when:
        def empty    = c.list { isEmpty("transactions") }
        def nonEmpty = c.list { isNotEmpty("transactions") }
        then:
        empty.size() == 3
        nonEmpty.size() == 2
    }

    // ─── In / allEq ────────────────────────────────────────────────────────

    /**
     * {@code in} / {@code inList} — membership in a collection or array.
     * <pre>
     *   Account.withCriteria { 'in'("firstName", ["Fred", "Barney"]) }
     *   Account.withCriteria { inList("firstName", ["Fred", "Barney"]) }
     * </pre>
     */
    void "in and inList filter to members of the supplied set"() {
        when:
        def results = c.list { 'in'("firstName", ["Fred", "Barney"]) }
        then:
        results*.firstName.sort() == ["Barney", "Fred"]
    }

    /**
     * {@code allEq} — all properties must equal the supplied values (AND shorthand).
     * <pre>
     *   Account.withCriteria { allEq(firstName: "Fred", lastName: "Flintstone") }
     * </pre>
     */
    void "allEq matches all supplied key-value pairs"() {
        when:
        def results = c.list { allEq(firstName: "Fred", lastName: "Flintstone") }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    // ─── Logical combinators ───────────────────────────────────────────────

    /**
     * {@code and} / {@code or} / {@code not} — logical grouping of predicates.
     * <pre>
     *   Account.withCriteria {
     *       or {
     *           eq("lastName", "Flintstone")
     *           like("branch", "Bedrock")
     *       }
     *   }
     * </pre>
     */
    void "or combinator unions two predicates"() {
        when:
        def results = c.list {
            gt("balance", BigDecimal.valueOf(200))
            or {
                eq("lastName", "Flintstone")
                like("branch", "Bedrock")
            }
            'in'("firstName", ["Fred", "Barney", "Pebbles"])
        }
        then:
        results*.firstName.sort() == ["Barney", "Fred", "Pebbles"]
    }

    // ─── Association traversal ─────────────────────────────────────────────

    /**
     * Closure named after an association property traverses the join.
     * <pre>
     *   Account.withCriteria {
     *       transactions { gt("amount", 40) }
     *   }
     * </pre>
     */
    void "association closure navigates into joined entity"() {
        when:
        def results = c.list {
            transactions { gt("amount", 40) }
        }
        then:
        results.size() == 1
        results[0].firstName == "Barney"
    }

    /**
     * Multiple predicates inside an association closure are ANDed.
     * <pre>
     *   Account.withCriteria {
     *       transactions { between("amount", 15, 25) }
     *   }
     * </pre>
     */
    void "association closure with between narrows the join correctly"() {
        when:
        def results = c.list {
            transactions { between("amount", BigDecimal.valueOf(15), BigDecimal.valueOf(25)) }
        }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    // ─── Collection-size constraints ───────────────────────────────────────

    /**
     * {@code sizeEq} / {@code sizeGt} / {@code sizeGe} / etc.
     * <pre>
     *   Account.withCriteria { sizeEq("transactions", 2) }
     * </pre>
     */
    void "sizeEq filters by exact collection size"() {
        when:
        def results = c.list { sizeEq("transactions", 2) }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "sizeGe filters by minimum collection size"() {
        when:
        def results = c.list {
            isNotNull("branch")
            sizeGe("transactions", 1)
        }
        then:
        results*.firstName.toSet() == ["Fred", "Barney"] as Set
    }

    // ─── Property-to-property comparisons ─────────────────────────────────

    /**
     * {@code eqProperty} / {@code neProperty} / {@code gtProperty} etc. compare two
     * properties of the same entity.
     * <pre>
     *   Account.withCriteria { neProperty("firstName", "lastName") }
     * </pre>
     */
    void "neProperty excludes rows where two properties are equal"() {
        when:
        // All 5 accounts have different firstName and lastName, so neProperty returns all
        def results = c.list { neProperty("firstName", "lastName") }
        then:
        results.size() == 5
    }

    // ─── Projections ───────────────────────────────────────────────────────

    /**
     * {@code projections} block selects scalar aggregates instead of entity rows.
     *
     * <h3>count</h3>
     * <pre>
     *   Account.withCriteria {
     *       projections { count() }
     *       eq("lastName", "Flintstone")
     *   }
     * </pre>
     */
    void "count projection returns the number of matching rows"() {
        when:
        def count = c.get {
            projections { count() }
            eq("lastName", "Flintstone")
        }
        then:
        count == 3
    }

    /**
     * <h3>sum / avg</h3>
     * <pre>
     *   Account.withCriteria {
     *       projections {
     *           sum('balance')
     *           avg('balance')
     *       }
     *       eq("branch", "Bedrock")
     *   }
     * </pre>
     */
    void "sum and avg projections aggregate numeric properties"() {
        when:
        def row = c.get {
            projections {
                sum('balance')
                avg('balance')
            }
            eq("branch", "Bedrock")
        }
        then:
        row[0] == 850
        new BigDecimal(row[1]).setScale(2, java.math.RoundingMode.HALF_UP) == 283.33
    }

    /**
     * <h3>groupProperty / countDistinct / min / max</h3>
     * <pre>
     *   Account.withCriteria {
     *       projections {
     *           groupProperty("lastName")
     *           countDistinct("firstName")
     *           min("balance")
     *           max("balance")
     *       }
     *   }
     * </pre>
     */
    void "groupProperty countDistinct min max projections aggregate per group"() {
        when:
        def results = c.list {
            projections {
                groupProperty("lastName")
                countDistinct("firstName")
                min("balance")
                max("balance")
            }
        }
        then:
        results.size() == 2   // Flintstone and Rubble
    }

    // ─── Ordering ──────────────────────────────────────────────────────────

    /**
     * {@code order} — sorts results.
     * <pre>
     *   Account.withCriteria { order("firstName", "asc") }
     *   Account.withCriteria { order("balance", "desc") }
     * </pre>
     */
    void "order sorts results ascending or descending"() {
        when:
        def asc  = c.list { order("firstName", "asc") }
        def desc = c.list { order("balance", "desc") }
        then:
        asc.first().firstName  == "Bam-Bam"
        desc.first().firstName == "Pebbles"
    }

    // ─── Pagination ────────────────────────────────────────────────────────

    /**
     * {@code maxResults} / {@code firstResult} and the map-argument variants limit and
     * offset the result set.
     * <pre>
     *   Account.withCriteria(max: 2, offset: 1) { order("firstName", "asc") }
     * </pre>
     */
    void "max and offset paginate the result set"() {
        when:
        def results = c.list(max: 2, offset: 1) { order("firstName", "asc") }
        then:
        results.size() == 2
        results*.firstName == ["Barney", "Fred"]
    }

    void "firstResult and maxResults inside the closure paginate independently"() {
        when:
        def results = c.list(max: 1) {
            order("firstName", "asc")
            firstResult(2)
        }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "scroll returns a ScrollableResults cursor over matching rows"() {
        when:
        def results = c.scroll {
            eq("lastName", "Flintstone")
            order("firstName", "asc")
        }

        then:
        results instanceof org.hibernate.ScrollableResults
        results.next()
        results.get().firstName == "Fred"
        results.next()
        results.get().firstName == "Pebbles"
        results.next()
        results.get().firstName == "Wilma"
        !results.next()

        cleanup:
        results?.close()
    }

    void "fetchMode applies joining or selection strategy"() {
        when:
        def results = c.list {
            fetchMode("transactions", org.hibernate.FetchMode.JOIN)
            eq("firstName", "Fred")
        }
        then:
        results.size() == 1
        results[0].firstName == "Fred"

        when:
        results = c.list {
            fetchMode("transactions", org.hibernate.FetchMode.SELECT)
            eq("firstName", "Fred")
        }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "singleResult returns exactly one row"() {
        when:
        c.eq("firstName", "Fred")
        def result = c.singleResult()

        then:
        result != null
        result.firstName == "Fred"
    }
}

@Entity
class CriteriaAccount {
    String firstName
    String lastName
    BigDecimal balance
    String branch
    Set<CriteriaTransaction> transactions

    static hasMany = [transactions: CriteriaTransaction]
    static constraints = {
        branch nullable: true
    }
}

@Entity
class CriteriaTransaction {
    BigDecimal amount
    Date dateCreated

    static belongsTo = [account: CriteriaAccount]
}

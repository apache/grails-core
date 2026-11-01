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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.orm.hibernate.query.PredicateGenerator
import org.hibernate.SessionFactory
import org.grails.orm.hibernate.HibernateDatastore

class HibernateCriteriaBuilderDirectSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(CriteriaTestEntity, CriteriaTestChild)
    }

    HibernateCriteriaBuilder c

    def setup() {
        c = new HibernateCriteriaBuilder(CriteriaTestEntity, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        new CriteriaTestEntity(name: "A", amount: 10, category: "X").save()
        new CriteriaTestEntity(name: "B", amount: 20, category: "X").save()
        new CriteriaTestEntity(name: "C", amount: 30, category: "Y").save()
        new CriteriaTestEntity(name: "D", amount: 40, category: "Y").save(flush: true)
    }

    void "test distinct projection"() {
        when:
        def results = c.list {
            projections {
                distinct("category")
            }
        }
        then:
        results.sort() == ["X", "Y"]
    }

    void "test id projection"() {
        when:
        def results = c.list {
            projections {
                id()
            }
        }
        then:
        results.size() == 4
        results.every { it instanceof Long }
    }

    void "test groupProperty with alias"() {
        when:
        def results = c.list {
            projections {
                groupProperty("category", "cat")
                sum("amount", "total")
            }
            order("cat")
        }
        then:
        results.size() == 2
        results[0] == ["X", 30L]
        results[1] == ["Y", 70L]
    }

    void "test min and max with alias"() {
        when:
        def result = c.get {
            projections {
                min("amount", "min_amt")
                max("amount", "max_amt")
            }
            eq("category", "X")
        }
        then:
        result[0] == 10
        result[1] == 20
    }

    void "test count with alias"() {
        when:
        def result = c.get {
            projections {
                count("name", "cnt")
            }
            eq("category", "X")
        }
        then:
        result == 2L
    }

    void "test gtProperty and colleagues"() {
        given:
        new CriteriaTestEntity(name: "E", amount: 10, otherAmount: 5, category: "Z").save(flush: true)
        
        expect:
        c.list { gtProperty("amount", "otherAmount") }.size() == 5
        c.list { geProperty("amount", "otherAmount") }.size() == 5
        c.list { ltProperty("otherAmount", "amount") }.size() == 5
        c.list { leProperty("otherAmount", "amount") }.size() == 5
    }

    void "test gtAll subquery"() {
        when:
        def results = c.list {
            gtAll("amount", {
                projections { property("amount") }
                eq("category", "X")
            })
        }
        then: "Returns entities with amount > max(X amounts) = 20"
        results*.name.sort() == ["C", "D"]
    }

    void "test geAll subquery"() {
        when:
        def results = c.list {
            geAll("amount", {
                projections { property("amount") }
                eq("category", "X")
            })
        }
        then: "Returns entities with amount >= 20"
        results*.name.sort() == ["B", "C", "D"]
    }

    void "test ltAll subquery"() {
        when:
        def results = c.list {
            ltAll("amount", {
                projections { property("amount") }
                eq("category", "Y")
            })
        }
        then: "Returns entities with amount < min(Y amounts) = 30"
        results*.name.sort() == ["A", "B"]
    }

    void "test leAll subquery"() {
        when:
        def results = c.list {
            leAll("amount", {
                projections { property("amount") }
                eq("category", "Y")
            })
        }
        then: "Returns entities with amount <= 30"
        results*.name.sort() == ["A", "B", "C"]
    }

    void "test exists subquery"() {
        given:
        def e = CriteriaTestEntity.findByName("A")
        new CriteriaTestChild(name: "child1", parent: e).save(flush: true)
        def subquery = new DetachedCriteria(CriteriaTestChild).build {
            projections { id() }
            eq("name", "child1")
            eqProperty("parent.id", "{alias}.id")
        }

        when:
        def results = c.list {
            exists(subquery)
        }
        then:
        results.size() == 1
        results[0].name == "A"
    }

    void "test notExists subquery"() {
        given:
        def e = CriteriaTestEntity.findByName("A")
        new CriteriaTestChild(name: "child1", parent: e).save(flush: true)
        def subquery = new DetachedCriteria(CriteriaTestChild).build {
            projections { id() }
            eqProperty("parent.id", "{alias}.id")
        }

        when:
        def results = c.list {
            notExists(subquery)
        }
        then:
        results*.name.sort() == ["B", "C", "D"]
    }

    void "test size constraints"() {
        given:
        def e = CriteriaTestEntity.findByName("A")
        e.addToChildren(new CriteriaTestChild(name: "c1"))
        e.addToChildren(new CriteriaTestChild(name: "c2"))
        e.save(flush: true)

        expect:
        c.list { sizeLt("children", 1) }.size() == 3
        c.list { sizeLe("children", 0) }.size() == 3
        c.list { sizeNe("children", 0) }.size() == 1
        c.list { sizeGt("children", 1) }.size() == 1
    }

    void "test listDistinct"() {
        given:
        def builder = new HibernateCriteriaBuilder(CriteriaTestEntity, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        when:
        def results = builder.listDistinct {
            projections { property("category") }
        }
        
        then:
        results.sort() == ["X", "Y"]
    }

    void "test idEquals and lte/gte"() {
        given:
        def e = CriteriaTestEntity.findByName("A")

        expect:
        c.list { idEquals(e.id) }.size() == 1
        c.list { lte("amount", 10) }.size() == 1
        c.list { gte("amount", 40) }.size() == 1
    }

    void "test exists and notExists with an inline closure subquery"() {
        expect: "exists/notExists build their subquery against the outer target class when given a closure"
        c.list { exists { eq("category", "X") } }.size() == 4
        c.list { notExists { eq("category", "does-not-exist") } }.size() == 4
    }

    void "test scalar subqueries in comparison criteria (gt, ge, lt, le)"() {
        given:
        def subquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { avg("amount") }
            eq("name", "C")
        }

        expect:
        c.list { gt("amount", subquery) }*.name.sort() == ["D"]
        c.list { ge("amount", subquery) }*.name.sort() == ["C", "D"]
        c.list { lt("amount", subquery) }*.name.sort() == ["A", "B"]
        c.list { le("amount", subquery) }*.name.sort() == ["A", "B", "C"]
    }

    void "test scalar subqueries with count, countDistinct, id, and property projections in comparison criteria"() {
        given:
        CriteriaTestEntity.findByName("A").tap { otherAmount = 1; save() }
        CriteriaTestEntity.findByName("B").tap { otherAmount = 2; save() }
        CriteriaTestEntity.findByName("C").tap { otherAmount = 4; save() }
        CriteriaTestEntity.findByName("D").tap { otherAmount = 5; save(flush: true) }

        def countSubquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { count() }
        }
        def countDistinctSubquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { countDistinct("category") }
        }
        def idSubquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { id() }
            eq("name", "B")
        }
        def propertySubquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { property("amount") }
            eq("name", "B")
        }

        expect: "Long count projection compared with Integer property"
        c.list { eq("otherAmount", countSubquery) }*.name == ["C"]
        c.list { gt("otherAmount", countSubquery) }*.name == ["D"]
        c.list { ge("otherAmount", countSubquery) }*.name.sort() == ["C", "D"]
        c.list { lt("otherAmount", countSubquery) }*.name.sort() == ["A", "B"]
        c.list { le("otherAmount", countSubquery) }*.name.sort() == ["A", "B", "C"]

        and: "Long countDistinct projection compared with Integer property"
        c.list { eq("otherAmount", countDistinctSubquery) }*.name == ["B"]
        c.list { gt("otherAmount", countDistinctSubquery) }*.name.sort() == ["C", "D"]
        c.list { ge("otherAmount", countDistinctSubquery) }*.name.sort() == ["B", "C", "D"]
        c.list { lt("otherAmount", countDistinctSubquery) }*.name == ["A"]
        c.list { le("otherAmount", countDistinctSubquery) }*.name.sort() == ["A", "B"]

        and: "IdProjection compared with identifier property"
        c.list { eq("id", idSubquery) }*.name == ["B"]
        c.list { gt("id", idSubquery) }*.name.sort() == ["C", "D"]
        c.list { ge("id", idSubquery) }*.name.sort() == ["B", "C", "D"]
        c.list { lt("id", idSubquery) }*.name == ["A"]
        c.list { le("id", idSubquery) }*.name.sort() == ["A", "B"]

        and: "PropertyProjection compared with property"
        c.list { eq("amount", propertySubquery) }*.name == ["B"]
        c.list { gt("amount", propertySubquery) }*.name.sort() == ["C", "D"]
        c.list { ge("amount", propertySubquery) }*.name.sort() == ["B", "C", "D"]
        c.list { lt("amount", propertySubquery) }*.name == ["A"]
        c.list { le("amount", propertySubquery) }*.name.sort() == ["A", "B"]
    }

    void "test where query with scalar aggregate subqueries"() {
        when:
        def gtAvg = CriteriaTestEntity.where { amount > avg(amount) }.list()*.name.sort()
        def ltAvg = CriteriaTestEntity.where { amount < avg(amount) }.list()*.name.sort()
        def gtFilteredAvg = CriteriaTestEntity.where { amount > avg(amount).of { category == "Y" } }.list()*.name.sort()

        then:
        gtAvg == ["C", "D"]
        ltAvg == ["A", "B"]
        gtFilteredAvg == ["D"]
    }

    void "test createAlias joins an association with the default and explicit join types"() {
        given:
        def a = CriteriaTestEntity.findByName("A")
        a.addToChildren(new CriteriaTestChild(name: "c1"))
        a.save(flush: true)
        def b = CriteriaTestEntity.findByName("B")
        b.addToChildren(new CriteriaTestChild(name: "c2"))
        b.save(flush: true)

        expect:
        c.list { createAlias("children", "ch"); eq("ch.name", "c1") }*.name == ["A"]
        c.list { createAlias("children", "chLeft", 1); eq("chLeft.name", "c2") }*.name == ["B"]
        c.list { createAlias("children", "chRight", 2); eq("chRight.name", "c2") }*.name == ["B"]
    }

    void "test join lock cache and readOnly hints do not affect filtering"() {
        given:
        def a = CriteriaTestEntity.findByName("A")
        a.addToChildren(new CriteriaTestChild(name: "c1"))
        a.save(flush: true)

        when:
        def results = c.list {
            join("children")
            lock(false)
            cache(true)
            readOnly(true)
            eq("name", "A")
        }

        then:
        noExceptionThrown()
        results*.name.unique() == ["A"]
    }

    void "test select projects onto a scalar property"() {
        when:
        def results = c.list {
            select("name")
        }

        then:
        results.sort() == ["A", "B", "C", "D"]
    }

    void "test count without an alias"() {
        expect:
        c.list { projections { count("name") } }.first() == 4L
    }

    void "test eqAll with a closure subquery"() {
        expect: "only 'A' has amount equal to every value returned by the (single-row) subquery"
        c.list {
            eqAll("amount", {
                projections { property("amount") }
                eq("name", "A")
            })
        }*.name == ["A"]
    }

    void "test gtSome geSome ltSome and leSome with queryable criteria and closures"() {
        given: "a subquery over the X category amounts [10, 20]"
        def subquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { property("amount") }
            eq("category", "X")
        }

        expect:
        c.list { gtSome("amount", subquery) }*.name.sort() == ["B", "C", "D"]
        c.list { geSome("amount", subquery) }*.name.sort() == ["A", "B", "C", "D"]
        c.list { ltSome("amount", subquery) }*.name.sort() == ["A"]
        c.list { leSome("amount", subquery) }*.name.sort() == ["A", "B"]
        c.list { gtSome("amount", { projections { property("amount") }; eq("category", "X") }) }*.name.sort() == ["B", "C", "D"]
        c.list { geSome("amount", { projections { property("amount") }; eq("category", "X") }) }*.name.sort() == ["A", "B", "C", "D"]
        c.list { ltSome("amount", { projections { property("amount") }; eq("category", "X") }) }*.name.sort() == ["A"]
        c.list { leSome("amount", { projections { property("amount") }; eq("category", "X") }) }*.name.sort() == ["A", "B"]
    }

    void "test notIn with queryable criteria and a closure"() {
        given:
        def subquery = new DetachedCriteria(CriteriaTestEntity).build {
            projections { property("category") }
            eq("category", "X")
        }

        expect:
        c.list { notIn("category", subquery) }*.name.sort() == ["C", "D"]
        c.list { notIn("category", { projections { property("category") }; eq("category", "X") }) }*.name.sort() == ["C", "D"]
    }

    void "test inList with a collection and with varargs"() {
        expect:
        c.list { inList("category", ["X", "Y"]) }.size() == 4
        c.list { inList("category", "X", "Y") }.size() == 4
        c.list { inList("category", "Y") }*.name.sort() == ["C", "D"]
        c.list { inList("category", ["Y", null]) }*.name.sort() == ["C", "D"]
        c.list { inList("category", [null]) }.empty
        c.list { inList("category", []) }.empty
    }

    void "test shared subquery without explicit projections is not mutated across queries"() {
        given:
        def sharedSubquery = new DetachedCriteria(CriteriaTestEntity).build {
            eq("category", "Y")
        }

        when:
        def byAmount = c.list { inList("amount", sharedSubquery) }*.name.sort()
        def byCategory = c.list { inList("category", sharedSubquery) }*.name.sort()

        then:
        sharedSubquery.projections.isEmpty()
        byAmount == ["C", "D"]
        byCategory == ["C", "D"]
    }

    void "test invalid property paths in property comparison, unary property name, and subquery criteria throw ConfigurationException"() {
        when:
        c.list(criteriaClosure)

        then:
        def e = thrown(org.grails.datastore.mapping.core.exceptions.ConfigurationException)
        e.message.contains(expectedProperty)

        where:
        expectedProperty     | criteriaClosure
        "nonExistentLeft"    | { eqProperty("nonExistentLeft", "name") }
        "nonExistentRight"   | { eqProperty("name", "nonExistentRight") }
        "nonExistentUnary"   | { isNull("nonExistentUnary") }
        "nonExistentSubquery"| { eqAll("nonExistentSubquery", new DetachedCriteria(CriteriaTestEntity).build { projections { property("amount") } }) }
    }

    void "test eq with ignoreCase param matches the whole value in any case"() {
        given: 'names that a substring or LIKE pattern match would also find'
        new CriteriaTestEntity(name: 'Ann', amount: 50, category: 'Z').save()
        new CriteriaTestEntity(name: 'a%', amount: 60, category: 'Z').save(flush: true)

        expect:
        c.list { eq('name', 'a', [ignoreCase: true]) }*.name == ['A']
        c.list { eq('name', 'ANN', [ignoreCase: true]) }*.name == ['Ann']
        c.list { eq('name', 'A%', [ignoreCase: true]) }*.name == ['a%']
        c.list { eq([ignoreCase: true], 'name', 'ann') }*.name == ['Ann']
    }

    void "test eq with ignoreCase false, a non-String value or a non-String property compares with plain equality"() {
        expect:
        c.list { eq('name', 'a', [ignoreCase: false]) }.empty
        c.list { eq('name', 'A', [ignoreCase: false]) }*.name == ['A']
        c.list { eq('amount', 20, [ignoreCase: true]) }*.name == ['B']
        c.list { eq('amount', '20', [ignoreCase: true]) }*.name == ['B']
    }

    void "test eq with ignoreCase param inside an association"() {
        given:
        var parent = CriteriaTestEntity.findByName('A')
        parent.addToChildren(new CriteriaTestChild(name: 'Kid')).save(flush: true)

        expect:
        c.list { children { eq('name', 'KID', [ignoreCase: true]) } }*.name == ['A']
    }

    void "test registerCriterionHandler overrides built-in criterion handling"() {
        given: "a custom handler registered for the built-in Like criterion that always matches"
        PredicateGenerator.registerCriterionHandler(Query.Like) { query, root, cb, criterion ->
            cb.conjunction()
        }

        when: "a like criterion that would normally match nothing is used"
        def results = c.list {
            like("name", "does-not-exist%")
        }

        then: "the custom handler is invoked instead of the built-in like handling, matching everything"
        results.size() == 4

        cleanup:
        PredicateGenerator.clearCustomCriterionHandlers()
    }

    void "a count query leaves the criteria's orders out"() {
        expect: "a count with an order counts every row instead of failing on the order by"
        new DetachedCriteria(CriteriaTestEntity).build { order('amount', 'desc') }.count() == 4L
        new DetachedCriteria(CriteriaTestEntity).build {
            gt('amount', 15)
            order('name')
        }.count() == 3L

        and: "a row count projection with an order returns the count"
        c.list {
            projections { rowCount() }
            order('amount')
        } == [4L]

        and: "the builder's count with an order counts every row"
        new HibernateCriteriaBuilder(CriteriaTestEntity, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
                .count { order('amount', 'desc') } == 4L

        and: "the order still applies to the list"
        new DetachedCriteria(CriteriaTestEntity).build { order('amount', 'desc') }.list()*.name == ['D', 'C', 'B', 'A']
    }
}

@Entity
class CriteriaTestEntity {
    Long id
    String name
    Integer amount
    Integer otherAmount = 0
    String category
    Set children
    static hasMany = [children: CriteriaTestChild]
}

@Entity
class CriteriaTestChild {
    Long id
    String name
    static belongsTo = [parent: CriteriaTestEntity]
}

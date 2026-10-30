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
import jakarta.persistence.criteria.Expression
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

    void "a contextual criterion handler resolves an association path to the join GORM created for it"() {
        given: "a parent with two children, and a handler that resolves its criterion's path through the context"
        var parent = CriteriaTestEntity.findByName('A')
        parent.addToChildren(new CriteriaTestChild(name: 'Kid'))
        parent.addToChildren(new CriteriaTestChild(name: 'Other')).save(flush: true)
        PredicateGenerator.registerCriterionHandler(CriteriaTestPathLike, { query, root, cb, criterion, context ->
            def c = criterion as CriteriaTestPathLike
            cb.like(context.getFullyQualifiedPath(c.property) as Expression<String>, c.value as String)
        } as PredicateGenerator.ContextualCriterionHandler)

        when: "a GORM criterion and the custom criterion restrict the same association"
        List<CriteriaTestEntity> both = new DetachedCriteria(CriteriaTestEntity).build {
            children { eq('name', 'Kid') }
            add(new CriteriaTestPathLike('children.name', 'K%'))
        }.list()
        List<CriteriaTestEntity> different = new DetachedCriteria(CriteriaTestEntity).build {
            children { eq('name', 'Kid') }
            add(new CriteriaTestPathLike('children.name', 'O%'))
        }.list()

        then: "a child matching both criteria is found"
        both*.name == ['A']

        and: "two children matching one criterion each are not: the handler used the join GORM created, not a second one"
        different.empty

        when: "the custom criterion comes before the association block"
        List<CriteriaTestEntity> bothReversed = new DetachedCriteria(CriteriaTestEntity).build {
            add(new CriteriaTestPathLike('children.name', 'K%'))
            children { eq('name', 'Kid') }
        }.list()
        List<CriteriaTestEntity> differentReversed = new DetachedCriteria(CriteriaTestEntity).build {
            add(new CriteriaTestPathLike('children.name', 'O%'))
            children { eq('name', 'Kid') }
        }.list()

        then: "the handler creates the join from the discovered alias and the association block reuses it"
        bothReversed*.name == ['A']
        differentReversed.empty

        cleanup:
        PredicateGenerator.clearCustomCriterionHandlers()
    }

    void "a contextual criterion handler resolves the alias of createAlias to the join GORM created for it"() {
        given: "a parent with two children, and the same handler as above"
        var parent = CriteriaTestEntity.findByName('A')
        parent.addToChildren(new CriteriaTestChild(name: 'Kid'))
        parent.addToChildren(new CriteriaTestChild(name: 'Other')).save(flush: true)
        PredicateGenerator.registerCriterionHandler(CriteriaTestPathLike) { query, root, cb, criterion, context ->
            def c = criterion as CriteriaTestPathLike
            cb.like(context.getFullyQualifiedPath(c.property) as Expression<String>, c.value as String)
        }

        when: "a createAlias alias is restricted by a GORM criterion and by the custom criterion"
        List<CriteriaTestEntity> both = new DetachedCriteria(CriteriaTestEntity).build {
            createAlias('children', 'ch')
            eq('ch.name', 'Kid')
            add(new CriteriaTestPathLike('ch.name', 'K%'))
        }.list()
        List<CriteriaTestEntity> different = new DetachedCriteria(CriteriaTestEntity).build {
            createAlias('children', 'ch')
            eq('ch.name', 'Kid')
            add(new CriteriaTestPathLike('ch.name', 'O%'))
        }.list()
        List<CriteriaTestEntity> inBuilder = c.list {
            createAlias('children', 'ch')
            eq('ch.name', 'Kid')
            hibernateQuery.add(new CriteriaTestPathLike('ch.name', 'O%'))
        }

        then: "the alias shares the join, in a detached criteria and in the builder"
        both*.name == ['A']
        different.empty
        inBuilder.empty

        cleanup:
        PredicateGenerator.clearCustomCriterionHandlers()
    }

    void "a closure registered as a criterion handler is a contextual handler with five parameters and a plain one otherwise"() {
        given: "one criterion handled by a four parameter closure and one by a five parameter closure, with no as"
        PredicateGenerator.registerCriterionHandler(CriteriaTestPathLike) { query, root, cb, criterion ->
            def c = criterion as CriteriaTestPathLike
            cb.like(root.get(c.property) as Expression<String>, c.value as String)
        }
        PredicateGenerator.registerCriterionHandler(CriteriaTestContextLike) { query, root, cb, criterion, context ->
            def c = criterion as CriteriaTestContextLike
            cb.like(context.getFullyQualifiedPath(c.property) as Expression<String>, c.value as String)
        }

        expect:
        new DetachedCriteria(CriteriaTestEntity).build { add(new CriteriaTestPathLike('name', 'A%')) }.list()*.name == ['A']
        new DetachedCriteria(CriteriaTestEntity).build { add(new CriteriaTestContextLike('name', 'B%')) }.list()*.name == ['B']

        cleanup:
        PredicateGenerator.clearCustomCriterionHandlers()
    }

    void "an order handler converts a Query.Order subclass in its place among the orders"() {
        given: "a handler that orders by the remainder of the amount modulo 15, descending"
        PredicateGenerator.registerOrderHandler(CriteriaTestRemainderOrder) { query, root, cb, order, context ->
            cb.desc(cb.mod(root.get('amount') as Expression<Integer>, 15))
        }

        when: "the custom order comes before an order by name"
        List<String> names = c.list {
            order(new CriteriaTestRemainderOrder())
            order('name', 'asc')
        }*.name

        then: "amounts 10, 20, 30 and 40 give remainders 10, 5, 0 and 10, and the name breaks the tie"
        names == ['A', 'D', 'B', 'C']

        when: "the same orders go through a detached criteria"
        List<String> detached = new DetachedCriteria(CriteriaTestEntity).build {
            order(new CriteriaTestRemainderOrder())
            order('name', 'asc')
        }.list()*.name

        then: "the handler converts the order there too"
        detached == ['A', 'D', 'B', 'C']

        and: "a count with the handled order counts every row, because a count query leaves its orders out"
        new DetachedCriteria(CriteriaTestEntity).build { order(new CriteriaTestRemainderOrder()) }.count() == 4L

        when: "the handler returns null"
        PredicateGenerator.registerOrderHandler(CriteriaTestRemainderOrder) { query, root, cb, order, context -> null }
        names = c.list {
            order(new CriteriaTestRemainderOrder())
            order('name', 'desc')
        }*.name

        then: "its order is left out and the others still apply"
        names == ['D', 'C', 'B', 'A']

        cleanup:
        PredicateGenerator.clearCustomOrderHandlers()
    }
}

/** A criterion whose handler resolves the property through the query context. */
class CriteriaTestPathLike extends Query.PropertyCriterion {

    CriteriaTestPathLike(String property, String value) {
        super(property, value)
    }
}

/** A criterion that a five parameter closure converts. */
class CriteriaTestContextLike extends Query.PropertyCriterion {

    CriteriaTestContextLike(String property, String value) {
        super(property, value)
    }
}

/** An order that a registered handler converts. */
class CriteriaTestRemainderOrder extends Query.Order {

    CriteriaTestRemainderOrder() {
        super('amount')
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

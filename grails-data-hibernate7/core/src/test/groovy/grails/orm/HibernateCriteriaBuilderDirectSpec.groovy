package grails.orm

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.hibernate.SessionFactory
import org.grails.orm.hibernate.HibernateDatastore

class HibernateCriteriaBuilderDirectSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([CriteriaTestEntity, CriteriaTestChild])
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

        when:
        def results = c.list {
            exists {
                projections { id() }
                eq("name", "child1")
                eqProperty("parent.id", "{alias}.id")
            }
        }
        then:
        results.size() == 1
        results[0].name == "A"
    }

    void "test notExists subquery"() {
        given:
        def e = CriteriaTestEntity.findByName("A")
        new CriteriaTestChild(name: "child1", parent: e).save(flush: true)

        when:
        def results = c.list {
            notExists {
                projections { id() }
                eqProperty("parent.id", "{alias}.id")
            }
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

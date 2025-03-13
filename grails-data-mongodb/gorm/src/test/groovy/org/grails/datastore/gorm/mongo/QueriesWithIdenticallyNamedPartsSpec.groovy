package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Test cases for GPMONGODB-296 (and GPMONGODB-302).
 */
@Issue('GPMONGODB-296')
class QueriesWithIdenticallyNamedPartsSpec extends GormDatastoreSpec {
    @Override
    List getDomainClasses() {
        return [Foo]
    }

    void "Ors and ands work together"() {
        given:
        def foos = [
                new Foo(1, 1, 1, 1).save(),
                new Foo(1, 2, 1, 2).save(),
                new Foo(2, 1, 2, 1).save(),
                new Foo(2, 2, 2, 2).save(),
        ]
        session.flush()

        when: "ors combined in implicit conjuction"
        def results = Foo.createCriteria().list {
            or {
                eq 'a', 1
                eq 'b', 1
            }
            or {
                eq 'c', 2
                eq 'd', 2
            }
        }

        then:
        results.size() == 2
        results.contains(foos[1])
        results.contains(foos[2])

        when: "ors combined in explicit conjunction"
        results = Foo.createCriteria().list {
            and {
                or {
                    eq 'a', 1
                    eq 'b', 1
                }
                or {
                    eq 'c', 2
                    eq 'd', 2
                }
            }
        }

        then:
        results.size() == 2
        results.contains(foos[1])
        results.contains(foos[2])
    }

    void "Multiple queries on same property work"() {
        given:
        def foos = [
                new Foo(1, 2, 3, 4).save(),
                new Foo(2, 2, 3, 4).save(),
                new Foo(3, 2, 4, 3).save(),
                new Foo(4, 2, 4, 3).save(),
        ]
        session.flush()
        def results

        when: "Multiple inList queries are combined"
        results = Foo.createCriteria().list {
            inList 'a', [1, 2, 3]
            inList 'a', [2, 4]
        }

        then:
        results.size() == 1
        results.contains(foos[1])

        when: "Multiple inList queries with immutable list parameters are combined"
        results = Foo.createCriteria().list {
            inList 'a', [1, 2, 3].asImmutable()
            inList 'a', [2, 4].asImmutable()
        }

        then:
        results.size() == 1
        results.contains(foos[1])

        when: "Eq and in queries are combined"
        results = Foo.createCriteria().list {
            eq 'a', 2
            inList 'a', [2, 4]
        }

        then:
        results.size() == 1
        results.contains(foos[1])

        when: "Multiple property queries are combined"
        results = Foo.createCriteria().list {
            geProperty 'a', 'd'
            ltProperty 'a', 'c'
        }

        then:
        results.size() == 1
        results.contains(foos[2])
    }
}

@Entity
class Foo {
    ObjectId id

    Foo(Integer a = null, Integer b = null, Integer c = null, Integer d = null) {
        this.a = a
        this.b = b
        this.c = c
        this.d = d
    }

    Integer a
    Integer b
    Integer c
    Integer d

    static constraints = {
        a()
        b nullable: true
        c nullable: true
        d nullable: true
    }
}

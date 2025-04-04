package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Person

/**
 * Tests related to caching of entities.
 */
class SessionCachingSpec extends GormDatastoreSpec {
    void "test cache used for get"() {
        given:
        def a = new Person(firstName:"Bob", lastName:"Builder").save()
        session.flush()

        when:
        def aa = Person.get(a.id)

        then:
        a.attached
        aa != null
        aa.is(a)
    }

    void "test cache used for getAll"() {
        given:
        def a = new Person(firstName:"Bob", lastName:"Builder").save()
        def b = new Person(firstName:"Another", lastName:"Builder").save()
        session.flush()
        session.clear()

        when:
        a = Person.get(a.id)
        def list = Person.getAll(b.id, a.id)

        then:
        a != null
        list.size() == 2
        list.every { it != null }
        list.every { it.attached }
        list[0].id == b.id
        list[1].is(a)
    }

    void "test unique queried elements are from cache"() {
        given:
        def p = new Person(firstName:"Bob", lastName:"Builder").save()
        session.flush()

        when:
        def pp = Person.findByFirstName("Bob")

        then:
        p.attached
        pp != null
        pp.attached
        p.is(pp)
    }

    void "test multi-queried elements are in cache"() {
        given:
        def p = new Person(firstName:"Bob", lastName:"Builder").save()
        session.flush()

        when:
        def test = Person.findAllByFirstName("Bob")

        then:
        test.size() == 1
        session.contains(test[0])
        test[0].attached
        p.is(test[0])
    }
}

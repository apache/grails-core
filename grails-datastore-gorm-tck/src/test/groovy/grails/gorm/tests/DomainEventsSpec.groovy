package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.persistence.Entity;
import spock.lang.Issue

import spock.lang.PendingFeature

/**
 * @author graemerocher
 */
class DomainEventsSpec extends GormDatastoreSpec {

    def setup() {
        PersonEvent.resetStore()
    }
    @Issue('GPMONGODB-262')
    void "Test that returning false from beforeUpdate evicts the event"() {
        when:"An entity is saved"
            def p = new PersonEvent(name: "Fred")
            p.save(flush: true)
            session.clear()
            p = PersonEvent.get(p.id)
        then:"The person is saved"
            p != null

        when:"The beforeUpdate event returns false"
            p.name = "Bad"
            p.save(flush: true)
            session.clear()

        then:"The person is never updated"
            PersonEvent.get(p.id).name == "Fred"
    }

    @Issue('GPMONGODB-262')
    void "Test that returning false from beforeInsert evicts the event"() {
        when:"false is returned from a beforeInsert event"
            def p = new PersonEvent(name: "Bad")
            try {
                p.save()
                session.flush()
            } catch (e) {
                // ignore hibernate related flush errors
            }
            session.clear()

        then:"The person is never saved"
            !PersonEvent.get(p.id)
    }

    @Issue('GPMONGODB-262')
    void "Test that returning false from beforeDelete evicts the event"() {
        when:"a new person is saved"
            def p = new PersonEvent(name: "DontDelete")
            p.save(flush: true)
            session.clear()
            p = PersonEvent.get(p.id)


        then:"The person exists"
            p != null

        when:"The beforeDelete event returns false"
            p.delete(flush: true)
            session.clear()

        then:"The event was cancelled"
            PersonEvent.get(p.id)
    }

    void "Test modify property before save"() {
        given:
            session.datastore.mappingContext.addPersistentEntity(ModifyPerson)
            def p = new ModifyPerson(name:"Bob").save(flush:true)
            session.clear()

        when:"An object is queried by id"
            p = ModifyPerson.get(p.id)

        then: "the correct object is returned"
            p.name == "Fred"

        when:"An object is queried by the updated value"
            p = ModifyPerson.findByName("Fred")

        then:"The correct person is returned"
            p.name == "Fred"
    }

    void "Test auto time stamping working"() {

        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            sleep(2000)

            p.dateCreated == p.lastUpdated

        when:
            p.name = "Wilma"
            p.save(flush:true)

        then:
            p.dateCreated.before(p.lastUpdated)
    }

    void "Test delete events"() {
        given:
            def p = new PersonEvent()
            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            0 == PersonEvent.STORE.beforeDelete
            0 == PersonEvent.STORE.afterDelete

        when:
            p.delete(flush:true)

        then:
            1 == PersonEvent.STORE.beforeDelete
            1 == PersonEvent.STORE.afterDelete
    }

    void "Test multi-delete events"() {
        given:
            def freds = (1..3).collect {
                new PersonEvent(name: "Fred$it").save(flush:true)
            }
            session.clear()

        when:
            freds = PersonEvent.findAllByIdInList(freds*.id)

        then:
            3 == freds.size()
            0 == PersonEvent.STORE.beforeDelete
            0 == PersonEvent.STORE.afterDelete

        when:
            new DetachedCriteria(PersonEvent).build {
                'in'('id', freds*.id)
            }.deleteAll()
            session.flush()

        then:
            0 == PersonEvent.count()
            0 == PersonEvent.list().size()

        // removed the below assertions because in the case of batch DML statements neither Hibernate nor JPA triggers delete events for individual entities
//            3 == PersonEvent.STORE.beforeDelete
//            3 == PersonEvent.STORE.afterDelete
    }

    void "Test before update event"() {
        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            0 == PersonEvent.STORE.beforeUpdate
            0 == PersonEvent.STORE.afterUpdate

        when:
            p.name = "Bob"
            p.save(flush:true)
            session.clear()
            p = PersonEvent.get(p.id)

        then:
            "Bob" == p.name
            1 == PersonEvent.STORE.beforeUpdate
            1 == PersonEvent.STORE.afterUpdate
    }

    void "Test insert events"() {
        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            0 == PersonEvent.STORE.beforeUpdate
            1 == PersonEvent.STORE.beforeInsert
            0 == PersonEvent.STORE.afterUpdate
            1 == PersonEvent.STORE.afterInsert

        when:
            p.name = "Bob"
            p.save(flush:true)
            session.clear()
            p = PersonEvent.get(p.id)

        then:
            "Bob" == p.name
            1 == PersonEvent.STORE.beforeUpdate
            1 == PersonEvent.STORE.beforeInsert
            1 == PersonEvent.STORE.afterUpdate
            1 == PersonEvent.STORE.afterInsert
    }

    void "Test load events"() {
        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            if (!'JpaSession'.equals(session.getClass().simpleName)) {
                // JPA doesn't seem to support a pre-load event
                1 == PersonEvent.STORE.beforeLoad
            }
            1 == PersonEvent.STORE.afterLoad
    }

    void "Test multi-load events"() {
        given:
            def freds = (1..3).collect {
                new PersonEvent(name: "Fred$it").save(flush:true)
            }
            session.clear()

        when:
            freds = PersonEvent.findAllByIdInList(freds*.id)
            for(f in freds) {} // just to trigger load

        then:
            3 == freds.size()
            if (!'JpaSession'.equals(session.getClass().simpleName)) {
                // JPA doesn't seem to support a pre-load event
                3 == PersonEvent.STORE.beforeLoad
            }
            3 == PersonEvent.STORE.afterLoad
    }

    @PendingFeature(reason = 'Was previously @Ignore')
    void "Test bean autowiring"() {
        given:
            def personService = new Object()
            session.datastore.applicationContext.beanFactory.registerSingleton 'personService', personService

            def p = new PersonEvent()
            def saved = p
            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            personService.is saved.personService // test Groovy constructor
            if (!session.datastore.getClass().name.contains('Hibernate')) {
                // autowiring is added to the real constructor by an AST, so can't test this for Hibernate
                personService.is p.personService // test constructor called by the datastore
            }
    }


    def cleanup() {
        session.datastore.applicationContext?.beanFactory?.destroySingleton 'personService'
    }
}

@Entity
class PersonEvent implements Serializable {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    def personService

    static STORE_INITIAL = [
        beforeDelete: 0, afterDelete: 0,
        beforeUpdate: 0, afterUpdate: 0,
        beforeInsert: 0, afterInsert: 0,
        beforeLoad:   0, afterLoad:   0]

    static STORE = [:] + STORE_INITIAL

    static void resetStore() {
        STORE = [:] + STORE_INITIAL
    }

    def beforeDelete() {
        if (name == "DontDelete") {
            return false
        }
        STORE.beforeDelete++
    }

    void afterDelete() {
        STORE.afterDelete++
    }

    def beforeUpdate() {
        if (name == "Bad") {
            return false
        }
        STORE.beforeUpdate++
    }

    void afterUpdate() {
        STORE.afterUpdate++
    }

    def beforeInsert() {
        if (name == "Bad") {
            return false
        }
        STORE.beforeInsert++
    }

    void afterInsert() {
        STORE.afterInsert++
    }

    void beforeLoad() {
        STORE.beforeLoad++
    }

    void afterLoad() {
        STORE.afterLoad++
    }
}

@Entity
class ModifyPerson implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        name index:true
    }

    def beforeInsert() {
        name = "Fred"
    }
}

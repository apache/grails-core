package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/**
 * Created by graemerocher on 21/04/16.
 */
class EventsWithAbstractInheritanceSpec extends GormDatastoreSpec{

    @Issue('https://github.com/grails/grails-data-mapping/issues/701')
    def 'Test that events work with abstract inheritance'() {
        when:"An entity is saved"
        ConcreteEventDomain ced = new ConcreteEventDomain(name: "Bob").save(flush:true)

        then:"An event listener inherited from the base class is fired"
        ced.eventCount('beforeInsert') == 1

        when:"An an instance is updated"
        ced.name = "Fred"
        ced.save(flush:true)

        then:"The beforeUpdate event listener is fired"
        ced.eventCount('beforeInsert') == 1
        ced.eventCount('beforeUpdate') == 1

        when:"An an instance is updated again"
        ced.name = "Joe"
        ced.save(flush:true)

        then:"The beforeUpdate event listener is fired"
        ced.eventCount('beforeInsert') == 1
        ced.eventCount('beforeUpdate') == 2
    }

    @Override
    List getDomainClasses() {
        [ConcreteEventDomain]
    }
}

@Entity
class ConcreteEventDomain extends AbstractEventDomain{
    String name
}

abstract class AbstractEventDomain {
    private Map<String, Integer> eventCount = [:].withDefault {
        0
    }

    int eventCount(String name) {
        eventCount[name]
    }

    boolean beforeInsert() {
        eventCount['beforeInsert']++
        return true
    }

    boolean beforeUpdate() {
        eventCount['beforeUpdate']++
        return true
    }
}

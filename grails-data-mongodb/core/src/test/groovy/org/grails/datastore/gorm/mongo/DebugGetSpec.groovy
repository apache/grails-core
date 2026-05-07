package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.persistence.Entity

class DebugGetSpec extends MongoDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([TestPlace])
    }

    void "test Place.get() returns entity"() {
        when: "A simple Place is saved"
        def p = new TestPlace(name: "Test")
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved TestPlace with id: ${savedId}"
        
        and: "The session is cleared"
        manager.session.clear()
        
        and: "TestPlace.get() is called"
        println "Calling TestPlace.get(${savedId})..."
        def retrieved = TestPlace.get(savedId)
        println "Retrieved: ${retrieved}"
        
        then: "The TestPlace should be retrieved"
        retrieved != null
        retrieved.id == savedId
        retrieved.name == "Test"
    }
}

@Entity
class TestPlace {
    Long id
    String name
}

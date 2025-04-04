package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import com.mongodb.WriteConcern
import spock.lang.Issue
import static grails.mongodb.mapping.MappingBuilder.*
/**
 * Tests usage of WriteConcern
 */
class WriteConcernSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [SafeWrite, UnacknowledgedWrite]
    }

    void "Test that the correct WriteConcern is used to save entities"() {
        when:"An object is saved"
            def sw = new SafeWrite(name:"Bob")
            sw.save(flush:true)

        then:"The correct write concern is used"
            sw != null
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/600')
    void "Test unacknowledged write concern"() {
        when:"An object is saved"
        def sw = new UnacknowledgedWrite(name:"Bob")
        sw.save(flush:true)

        then:"The correct write concern is used"
        sw != null

        when:"The object is updated"
        session.clear()
        sw.name = "Fred"
        sw.save(flush:true)
        session.clear()

        then:"The update worked"
        UnacknowledgedWrite.findByName "Fred"

    }
}

@Entity
class SafeWrite {
    String id
    String name
    static mapping = document {
        writeConcern WriteConcern.JOURNALED
    }
}

@Entity
class UnacknowledgedWrite {
    String id
    String name
    static mapping = document {
        writeConcern WriteConcern.UNACKNOWLEDGED
    }
}

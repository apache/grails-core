package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import groovy.transform.CompileStatic

/**
 * Created by graemerocher on 20/04/16.
 */
class LastUpdatedSpec extends GormDatastoreSpec {

    void "Test lastUpdated and dateCreated"() {
        when:"An object is saved"
        def lum = new LastUpdateMe(name: "Fred")
        lum.save(flush:true)
        session.clear()
        lum = LastUpdateMe.get(lum.id)

        then:"The dateCreated and lastUpdated properties are populated"
        lum.dateCreated != null
        lum.lastUpdated != null

        when:"The object is updated"
        sleep 1000
        def previousLastUpdated = lum.lastUpdated
        def previousDateCreated = lum.dateCreated
        lum.name = "Bob"
        lum.save(flush:true)
        session.clear()

        lum = LastUpdateMe.get(lum.id)

        then:"lastUpdated is updated but date created is the same"
        lum.lastUpdated != previousLastUpdated
        lum.lastUpdated > lum.dateCreated
        lum.dateCreated == previousDateCreated
    }

    @Override
    List getDomainClasses() {
        [LastUpdateMe]
    }
}

@Entity
class LastUpdateMe {

    String name
    Date dateCreated
    Date lastUpdated
}

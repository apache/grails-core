package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class DisableVersionSpec extends GormDatastoreSpec {

    void "Test that disabling the version does not persist the version field"() {
        when:"An object with a disabled version is persisted"
            def nv = new NoVersion(name: "Bob").save(flush:true)
            session.clear()
            nv = NoVersion.findByName("Bob")

        then:"The version field is not persisted"
            nv.name == "Bob"
            nv.version == null
            nv.dbo.version == null
            !nv.dbo.containsKey("version")
    }

    @Override
    List getDomainClasses() {
       [NoVersion]
    }
}

@Entity
class NoVersion {

    Long id
    Long version
    String name

    static mapping = {
        version false
    }
}

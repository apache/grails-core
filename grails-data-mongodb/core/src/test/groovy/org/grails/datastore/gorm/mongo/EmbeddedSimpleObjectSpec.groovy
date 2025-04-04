package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class EmbeddedSimpleObjectSpec extends GormDatastoreSpec {

    void "Test embedded non-domain object"() {
        when:"An entity with a simple non-domain embedded object is persisted"
            def s = new Space(displayName: "foo", db: new DatabaseConfig(name: "test"))
            s.save(flush:true)
            session.clear()
            s = Space.get(s.id)
        then:"The embedded association is persisted correctly"
            s.db.name == 'test'
    }

    @Override
    List getDomainClasses() {
        [Space]
    }
}

@Entity
class Space {

    String id
    String displayName

    DatabaseConfig db

    static embedded = [ 'db' ]
}

class DatabaseConfig {
    String name
}

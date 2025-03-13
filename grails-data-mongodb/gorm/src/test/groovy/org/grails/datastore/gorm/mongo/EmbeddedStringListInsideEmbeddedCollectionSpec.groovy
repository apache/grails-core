package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class EmbeddedStringListInsideEmbeddedCollectionSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [ESLIECPerson]
    }

    void "Test that an embedded primitive string can be used inside an embedded collection"() {
        when:"A embedded collection is persisted which has an embedded string"
            def p = new ESLIECPerson(name:"Bob")
            p.cameras << new Camera(name:"Canon 50D", lenses:["Wide", "Long"])
            p.save(flush:true)
            session.clear()

            p = ESLIECPerson.get(p.id)

        then:"The embedded collection and strings can be read back correctly"
            p.name == "Bob"
            p.cameras.size() == 1
            p.cameras[0].name == "Canon 50D"
            p.cameras[0].lenses == ["Wide", "Long"]

        when:"An embedded collection is updated "
            p.cameras[0].lenses << "Other"
            p.save(flush:true)
            p = ESLIECPerson.get(p.id)

        then:"The embedded collection is updated appropriately"
            p.name == "Bob"
            p.cameras.size() == 1
            p.cameras[0].name == "Canon 50D"
            p.cameras[0].lenses == ["Wide", "Long", "Other"]
    }
}

@Entity
class ESLIECPerson {

    String id
    String name
    List<Camera> cameras   = []

    static embedded = ['cameras']
}

class Camera {

    String name
    List<String> lenses = []

    static embedded = ['lenses']
}

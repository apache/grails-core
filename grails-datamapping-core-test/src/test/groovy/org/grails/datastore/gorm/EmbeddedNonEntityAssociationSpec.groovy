package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class EmbeddedNonEntityAssociationSpec extends GormDatastoreSpec {

    void "Test persistence of embedded entities"() {
        given:
            def i = new Being(name:"Bob", address: new ResidentialAddress(postCode:"30483"))

            i.save(flush:true)
            session.clear()

        when:
            i = Being.findByName("Bob")

        then:
            i != null
            i.name == 'Bob'
            i.address != null
            i.address.postCode == '30483'
    }

    @Override
    List getDomainClasses() {
        [Being]
    }
}

@Entity
class Being {
    Long id
    String name
    ResidentialAddress address
    static embedded = ['address']

    static mapping = {
        name index:true
    }
}

class ResidentialAddress {
    String postCode
}

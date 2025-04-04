package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class QueryNonIndexedPropertySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Company, CompanyAddress]
    }

    def "Test that we can query a property that has no indices specified"() {

        given:"A valid set of persisted domain instances"
            def address = new CompanyAddress(postCode:"30483").save()
            def person = new Company(name:"Bob", address: address)
            person.save(flush:true)

        when: "An indexed property is queried"
            def found = Company.findByName("Bob")

        then: "A result is returned"
            found != null
            found.name == "Bob"

        when: "A non-indexed property is queried"
            found = Company.findByAddress(address)

        then: "A result is returned"
            found != null
            found.name == "Bob"
    }
}

@Entity
class Company {
    Long id
    String name
    CompanyAddress address
}

@Entity
class CompanyAddress {
    Long id
    String postCode
}

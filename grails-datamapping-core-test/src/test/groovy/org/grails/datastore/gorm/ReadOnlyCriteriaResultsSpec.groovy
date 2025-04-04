package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

class ReadOnlyCriteriaResultsSpec extends GormDatastoreSpec {

    @Issue('GRAILS-11670')
    void 'Test that readOnly does not cause a problem in a criteria query'() {
        given:
        new FamilyMember(name:"Jeff").save(flush:true)
        new FamilyMember(name:"Betsy").save(flush:true)
        new FamilyMember(name:"Jake").save(flush:true)
        new FamilyMember(name:"Zack").save(flush:true)

        when:
        def results = FamilyMember.withCriteria {
            readOnly true
            like 'name', 'J%'
        }

        then:
        results.size() == 2
    }

    @Override
    List getDomainClasses() {
        [FamilyMember]
    }
}

@Entity
class FamilyMember {
    Long id
    String name
}

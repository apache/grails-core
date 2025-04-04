package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class GreaterThanAndLessThanCriteriaSpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-180')
    void "Test that gt and lt criterion work together"() {
        given:"some books with publication dates in the last 2 days"
            new GTBook(title:'The Cross and the Switchblade', published:new Date() - 7).save(flush:true)
            new GTBook(title:'The Firm', published:new Date() + 1).save(flush:true)

        when:"lt and gt are used in the same query"
            def books = GTBook.createCriteria().list {
                gt('published', new Date())
                lt('published', new Date() + 5)
            }

        then:"The correct results are returned"
            1 == books.size()
    }

    @Override
    List getDomainClasses() {
        [GTBook]
    }
}

@Entity
class GTBook {
    Long id
    String title
    Date published
}

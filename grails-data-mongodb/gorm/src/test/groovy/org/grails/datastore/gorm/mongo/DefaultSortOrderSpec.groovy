package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class DefaultSortOrderSpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-181')
    void 'Test that default sort order works correctly'() {
         given:"A domain model with default sort order"
            (2..10).each {
                new SOBook(title:'The History of the English Speaking People volume ' + it, published:new Date(), isbn: it).save(flush:true)
            }
            new SOBook(title:'The History of the English Speaking People volume ' + 1, published:new Date(), isbn: 1).save(flush:true)

        when:"The model is queried"
            def books = SOBook.list()

        then:"The sort order is correct"
            books[0].isbn == 1
            books[1].isbn == 2
    }

    @Override
    List getDomainClasses() {
        [SOBook]
    }
}

@Entity
class SOBook {
    Long id
    String title
    Date published
    Integer isbn

    static mapping = {
        sort isbn:'asc'
    }
}

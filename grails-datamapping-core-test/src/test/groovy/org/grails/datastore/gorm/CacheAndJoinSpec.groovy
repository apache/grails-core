package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class CacheAndJoinSpec extends GormDatastoreSpec{

    @Issue('GRAILS-8758')
    void "Test that the cache and join methods can be used in a test"() {
        given:"Some test data"
            new Author(name: "Bob").save flush:true
            session.clear()
        when:"The cache and join methods are used in criteria"
            def a = Author.createCriteria().get {
                eq 'name', "Bob"
                join 'books'
                maxResults 1
                cache true
            }

        then:"Results are returned"
            a != null
    }

    @Override
    List getDomainClasses() {
        [Author, Book]
    }
}

@Entity
class Author {
    Long id
    String name

    static hasMany = [books: Book]

    static constraints = {
        name blank: false
    }
}

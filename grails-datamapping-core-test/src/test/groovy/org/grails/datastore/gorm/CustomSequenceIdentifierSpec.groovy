package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class CustomSequenceIdentifierSpec extends GormDatastoreSpec {

    void "Test sequence identifiers"() {
        when:"when a book with a sequence id is saved"
            new Book(title:"Blah").save(flush:true)
            session.clear()
            def b = Book.findByTitle("Blah")
        then:"It can be retrieved"
            b != null
            b.id != null
    }

    @Override
    List getDomainClasses() {
        [Book]
    }
}

@Entity
class Book {

    Long id
    String title

    static mapping = {
        id generator:'sequence', params:[sequence:'book_seq']
    }
}

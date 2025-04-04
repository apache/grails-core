package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class AddToMethodWithBasicCollectionSpec extends GormDatastoreSpec{

    @Issue('GRAILS-8779')
    void "Test that the addTo* method works with basic collections"() {
         when:"A book is saved with a basic collection"
            def book = new BasicBook(title: "DGG")
            book.addToAuthors("Graeme")
                .addToAuthors("Jeff")
                .save(flush:true)

            session.clear()

            book = BasicBook.get(book.id)
        then:"The model is saved correctly"
            book.title == "DGG"
            book.authors.size() == 2
            book.authors.contains "Graeme"
            book.authors.contains "Jeff"
    }

    @Override
    List getDomainClasses() {
        [BasicBook]
    }
}

@Entity
class BasicBook {

    Long id
    static hasMany = [authors:String]

    Set<String> authors
    String title
}

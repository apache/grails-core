package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class DomainWithPrimitiveGetterSpec extends GormDatastoreSpec{

    @Issue('GRAILS-8788')
    void "Test that a domain that contains a primitive getter maps correctly"() {
        when:"The domain model is saved"
            def author = new DomainWithPrimitiveGetterAuthor(name: "Stephen King")
            author.save()
            def book = new DomainWithPrimitiveGetterBook(title: "The Stand", author: author)
            book.save flush:true
        then:"The save executes correctly"
            DomainWithPrimitiveGetterBook.count() == 1
            DomainWithPrimitiveGetterAuthor.count() == 1
    }

    @Override
    List getDomainClasses() {
        [DomainWithPrimitiveGetterAuthor, DomainWithPrimitiveGetterBook]
    }
}

@Entity
class DomainWithPrimitiveGetterBook {
    Long id
    String title
    DomainWithPrimitiveGetterAuthor author
    int getValue(int param) {
        return 0
    }
}
@Entity
class DomainWithPrimitiveGetterAuthor {
    Long id
    String name
    static hasMany = [books: DomainWithPrimitiveGetterBook]
}

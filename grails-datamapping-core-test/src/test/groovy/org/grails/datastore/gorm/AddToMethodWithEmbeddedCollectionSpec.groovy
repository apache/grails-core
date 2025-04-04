package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class AddToMethodWithEmbeddedCollectionSpec extends GormDatastoreSpec {

    private service = new LibraryService()

    void testAddBooks() {
        when:
        LibraryBook book = new LibraryBook(title:"title", author:"me")
        def library = service.addBook(book)

        then:
        library
        library.books.size()==1
    }

    void testAddBooksInTest() {
        when:
        LibraryBook book = new LibraryBook(title:"title", author:"me")
        def library = new Library()
        library.addToBooks(book)

        then:
        library
        library.books.size()==1
    }

    @Override
    List getDomainClasses() {
        [Library, LibraryBook]
    }
}

@Entity
class Library {

    static embedded = [
        'books'
    ]

    Set books
    Long id
    static hasMany = [books:LibraryBook]
}

@Entity
class LibraryBook {
    Long id
    String title
    String author
}

class LibraryService {

    Library addBook(LibraryBook book) {
        def library = new Library()
        library.addToBooks(book)
        library.save(failOnError:true)
        return library
    }
}

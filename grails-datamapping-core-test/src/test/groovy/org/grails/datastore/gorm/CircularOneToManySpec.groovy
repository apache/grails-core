package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class CircularOneToManySpec extends GormDatastoreSpec {

    // GRAILS-10984
    void "Test that a circular one-to-many with two entities persists correctly"() {
        given: "an author and a book"
        def author = new CircularAuthor(name: 'John Doe')
        def book = new CircularBook(name: 'Divergent')

        when: "the book is added to the author"
        author.addToBooks(book)
        author.save(flush: true, failOnError: true)

        and: "the author is added to the book"
        book.addToFavoriteAuthors(author)
        book.save(flush: true, failOnError: true)

        then: "everything saves correctly"
        author.id
        book.id
        author.books.size() == 1
        book.favoriteAuthors.size() == 1
        author.favoriteBook == book
        book.author == author
    }

    @Override
    List getDomainClasses() {
        [CircularAuthor, CircularBook]
    }
}

@Entity
class CircularAuthor {
    Long id
    String name
    CircularBook favoriteBook

    static hasMany = [ books: CircularBook ]
    static mappedBy = [ books: 'author' ]
    static constraints = { favoriteBook nullable: true }
}

@Entity
class CircularBook {
    Long id
    String name

    static belongsTo = [author: CircularAuthor]
    static hasMany = [favoriteAuthors: CircularAuthor]
    static mappedBy = [favoriteAuthors: 'favoriteBook']
}

package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 25/03/14.
 */
class SimpleHasManySpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-337')
    void "Test save and retrieve one-to-many"() {

        when:"A domain model is persisted"
            def c1 = new Chapter(title: "first")
            def c2 = new Chapter(title: "second")
            c1.save()
            c2.save()

            def book = new Book(name: "mybook")
            book.save(flush:true)
            if(!book.chapters) {
                book.chapters = []
            }
            book.chapters.add(c1)
            book.chapters.add(c2)
            book.save(flush:true)
            session.clear()


            book = Book.get(book.id)
            def chapters = [] as List
            book.chapters.each { it ->
                def chapter = [:] as Map
                chapter.title = it.title
                chapters << chapter
            }

            then:"The retrieved data is correct"
                chapters.find { it.title == 'first'}
                chapters.find { it.title == 'second'}

    }

    void "test changes to items in hasMany collection not persisted"() {
        given: "create a book with some chapters"
        Book book = new Book(name: "Walking the Himalayas")
        book.addToChapters(title: "Fourteen Year Later")
        book.addToChapters(title: "We Are the Pilgrims, Master")
        book.addToChapters(title: "Kabul")
        book.save(flush: true)
        session.clear()

        when: "Update the title of second chapter"
        Book book1 = Book.findByName("Walking the Himalayas")
        book1.chapters.first().title = "Fourteen Years Later"
        book1.save(flush: true)
        session.clear()

        then:
        Book.findByName("Walking the Himalayas").chapters.first().title == "Fourteen Years Later"
    }

    @Override
    List getDomainClasses() {
        [Book, Chapter]
    }
}

@Entity
class Book implements Serializable {

    ObjectId id
    Long version

    String name
    List<Chapter> chapters

    static hasMany = [chapters: Chapter]
}

@Entity
class Chapter implements Serializable {

    ObjectId id
    Long version

    String title
}


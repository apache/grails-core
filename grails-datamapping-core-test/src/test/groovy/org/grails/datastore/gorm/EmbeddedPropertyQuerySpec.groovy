package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class EmbeddedPropertyQuerySpec extends GormDatastoreSpec {

    static {
        GormDatastoreSpec.TEST_CLASSES << Book2 << Author2
    }

    void "Test eq query of embedded properties"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            book.save(flush: true, failOnError: true)
            session.clear()
        when:
            book = Book2.createCriteria().get { eq 'publishPeriod.startDate', new Date(2012, 1, 1) }
        then:
            book != null
    }

    void "Test gt query of embedded properties"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            book.save(flush: true, failOnError: true)
            session.clear()
        when:
            book = Book2.createCriteria().get { gt 'publishPeriod.startDate', new Date(2011, 1, 1) }
        then:
            book != null
    }

    void "Test ge query of embedded properties"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            book.save(flush: true, failOnError: true)
            session.clear()
        when:
            book = Book2.createCriteria().get { ge 'publishPeriod.startDate', new Date(2012, 1, 1) }
        then:
            book != null
    }

    void "Test lt query of embedded properties"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            book.save(flush: true, failOnError: true)
            session.clear()
        when:
            book = Book2.createCriteria().get { lt 'publishPeriod.startDate', new Date(2014, 1, 1) }
        then:
            book != null
    }

    void "Test le query of embedded properties"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            book.save(flush: true, failOnError: true)
            session.clear()
        when:
            book = Book2.createCriteria().get { le 'publishPeriod.endDate', new Date(2013, 1, 1) }
        then:
            book != null
    }

    void "Test isNotNull query of embedded properties"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            book.save(flush: true, failOnError: true)
            session.clear()
        when:
            book = Book2.createCriteria().get {
                isNotNull 'publishPeriod.endDate'
            }
        then:
            book != null
    }

    void "Test associated query of embedded property"() {
        given:
            def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
            def author = new Author2(name: 'George', books: [book])
            author.save(flush: true, failOnError: true)
            session.clear()
        when:
            author = Author2.createCriteria().get {
                books {
                    eq 'publishPeriod.startDate', new Date(2012, 1, 1)
                }
            }
        then:
            author != null
    }

}

@Entity
class Author2 {
    String name
    static hasMany = [books: Book2]
}

@Entity
class Book2 {
    String name
    Period publishPeriod

    static embedded = ['publishPeriod']
}

class Period {
    Date startDate
    Date endDate
}

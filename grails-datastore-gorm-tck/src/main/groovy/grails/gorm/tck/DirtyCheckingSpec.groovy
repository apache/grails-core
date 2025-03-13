package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.mapping.proxy.ProxyHandler
import spock.lang.IgnoreIf
import spock.lang.PendingFeature

/**
 * @author Graeme Rocher
 */
class DirtyCheckingSpec extends GormDatastoreSpec {

    ProxyHandler proxyHandler

    @Override
    List getDomainClasses() {
        [Person, TestBook, TestAuthor, Card, CardProfile]
    }

    def setup() {
        proxyHandler = session.getMappingContext().proxyHandler
    }

    void "Test that dirty checking methods work when changing entities"() {

        when: "A new instance is created"
        def p = new Person(firstName: "Homer", lastName: "Simpson")
        p.save(flush: true)

        then: "The instance is not dirty"
        !p.isDirty()
        !p.isDirty("firstName")

        when: "The instance is changed"
        p.firstName = "Bart"

        then: "The instance is now dirty"
        p.isDirty()
        p.isDirty("firstName")
        p.dirtyPropertyNames == ['firstName']
        p.getPersistentValue('firstName') == "Homer"

        when: "The instance is loaded from the db"
        p.save(flush: true)
        session.clear()
        p = Person.get(p.id)

        then: "The instance is not dirty"
        !p.isDirty()
        !p.isDirty('firstName')

        when: "The instance is changed"
        p.firstName = "Lisa"

        then: "The instance is dirty"
        p.isDirty()
        p.isDirty("firstName")


    }

    void "test relationships not marked dirty when proxies are used"() {

        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true)
                .id
        session.flush()
        session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = book.author

        then:
        proxyHandler.isProxy(book.author)
        !book.isDirty('author')
        !book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    void "test relationships not marked dirty when domain objects are used"() {

        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true, failOnError: true)
                .id
        session.flush()
        session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = TestAuthor.get(book.author.id)

        then:
        !proxyHandler.isProxy(book.author)
        !book.isDirty('author')
        !book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    void "test relationships are marked dirty when proxies are used but different"() {
        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true, failOnError: true)
                .id
        Long otherAuthorId = new TestAuthor(name: "JD").save(flush: true, failOnError: true).id
        session.flush()
        session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = TestAuthor.load(otherAuthorId)

        then:
        proxyHandler.isProxy(book.author)
        book.isDirty('author')
        book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    void "test relationships marked dirty when domain objects are used and changed"() {

        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true, failOnError: true)
                .id
        Long otherAuthorId = new TestAuthor(name: "JD").save(flush: true, failOnError: true).id
        session.flush()
        session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = TestAuthor.get(otherAuthorId)

        then:
        !proxyHandler.isProxy(book.author)
        book.isDirty('author')
        book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    @PendingFeature // because one-to-one association loads eagerly in the Hibernate
    void "test initialized proxy is not marked as dirty"() {

        given:
        Card card = new Card(cardNumber: "1111-2222-3333-4444")
        card.cardProfile = new CardProfile(fullName: "JD")
        card.save(flush: true, failOnError: true)
        session.flush()
        session.clear()

        when:
        card = Card.get(card.id)

        then:
        proxyHandler.isProxy(card.cardProfile)

        when:
        card.cardProfile.hashCode()

        then:
        proxyHandler.isInitialized(card.cardProfile)
        !card.isDirty()

        cleanup:
        Card.deleteAll()
        CardProfile.deleteAll()

    }

}

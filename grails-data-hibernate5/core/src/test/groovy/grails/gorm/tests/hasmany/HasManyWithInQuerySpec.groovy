package grails.gorm.tests.hasmany

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue('https://github.com/grails/gorm-hibernate5/issues/78')
@Rollback
class HasManyWithInQuerySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())

    @Shared PublicationService publicationService = datastore.getService(PublicationService)
    @Shared BookService bookService = datastore.getService(BookService)


    @Ignore
    void "test 'in' criteria"() {
        setupData()

        when:
        Book book = Book.get(1)

        then:
        publicationService.findAllByBook(book)
    }

    private Long setupData() {
        Publication publication = new Publication(name: "OCI").save(flush: true, failOnError: true)
        publication = addBooks(publication)
        publication.id
    }

    private List<Book> createBooks() {
        List<Book> books = []
        ["Grails Goodness Notebook",
         "Falando de Grails",
         "The Definitive Guide to Grails 2",
         "Grails 3 - Step by Step",
         "Making Java Groovy",
         "Grails in Action", "Practical Grails 3"
        ].each { String title ->
            books << bookService.save(title)
        }
        books
    }

    private Publication addBooks(Publication publication) {
        ["Grails Goodness Notebook",
         "Falando de Grails",
         "The Definitive Guide to Grails 2",
         "Grails 3 - Step by Step",
         "Making Java Groovy",
         "Grails in Action", "Practical Grails 3"
        ].each { String title ->
            publicationService.addToBook(publication, title)
        }
        publication.save(flush: true)
    }

}

@Entity
class Publication {

    String name

    static hasMany = [books: Book]
}

@Entity
class Book {

    String title
}

@Service
abstract class PublicationService {
    List<Publication> findAllByBook(Book book) {
        def criteria = new DetachedCriteria(Publication).build {
            inList("books", [book])
        }
        criteria.list()
    }

    Publication addToBook(Publication publication, String title) {
        publication.addToBooks(new Book(title: title))
    }
}

@Service(Book)
interface BookService {

    Book save(String title)

}

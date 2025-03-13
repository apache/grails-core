package grails.gorm.tests.perf

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import groovy.sql.Sql
import groovy.transform.EqualsAndHashCode
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.AccessType

/**
 * Created by graemerocher on 08/12/16.
 */
@Rollback
class JoinPerfSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Author, Book, BookAuthor)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    void setup() {
        for(i in 0..500) {
            Author a = new Author(name: "Author $i").save()

            for(j in 0..3) {
                new Book(title: "Book $i - $j").save()
            }
            datastore.sessionFactory.currentSession.flush()
            datastore.sessionFactory.currentSession.clear()
        }

        for(i in 0..7000) {
            Author a = Author.get(Math.abs(new Random().nextInt() % 500) + 1)
            Book b = Book.get(Math.abs(new Random().nextInt() % 1500) + 1)
            if(a && b) {
                new BookAuthor(book: b, author: a).save()
            }
            datastore.sessionFactory.currentSession.flush()
            datastore.sessionFactory.currentSession.clear()
        }
    }

    void 'test read performance with join query'() {
        when:
        def authors = Author.findAll().groupBy { it.id }
        def books = Book.findAll().groupBy { it.id }
        datastore.sessionFactory.currentSession.clear()
        long time = System.nanoTime();

        BookAuthor.findAll().size()
        long domainsLoadedAt = System.nanoTime()
        long timeOfDomainClassLoad = domainsLoadedAt - time;

        int itemsLoaded = 0
        new Sql(datastore.connectionSources.defaultConnectionSource.dataSource).eachRow("select author_id, book_id from book_author") { row ->
            assert authors.get(row.author_id)
            assert books.get(row.book_id)
            itemsLoaded++
        }
        long timeOfPlainQuery = System.nanoTime() - domainsLoadedAt;

        println "Loaded BookAuthor domains in ${timeOfDomainClassLoad / 1000000.0}ms while query took ${timeOfPlainQuery / 1000000.0}ms"

        then:"the assertion here doesn't matter much, we're testing perf not logic"
        BookAuthor.count() > 6000
    }
}

@Entity
class Author {
    String name
}
@Entity
class Book {
    String title
}

@Entity
@EqualsAndHashCode(includes = ['book', 'author'])
class BookAuthor implements Serializable{
    Book book
    Author author

    static mapping = {
        id composite:['book', 'author']
        version false
        book accessType: AccessType.FIELD
        author accessType: AccessType.FIELD
    }
}
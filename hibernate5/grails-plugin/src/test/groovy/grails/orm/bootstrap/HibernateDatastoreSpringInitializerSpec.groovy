package grails.orm.bootstrap

import grails.gorm.annotation.Entity
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

/**
 * Created by graemerocher on 29/01/14.
 */
class HibernateDatastoreSpringInitializerSpec extends Specification{

    void "Test configure multiple data sources"() {
        given:"An initializer instance"
        Map config = [
                'dataSource.url':"jdbc:h2:mem:people;LOCK_TIMEOUT=10000",
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.books.url':"jdbc:h2:mem:books;LOCK_TIMEOUT=10000",
                'dataSources.moreBooks.url':"jdbc:h2:mem:moreBooks;LOCK_TIMEOUT=10000"
        ]
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(config, Person, Book, Author)

        when:"the application is configured"
        def applicationContext = datastoreInitializer.configure()
        println applicationContext.getBeanDefinitionNames()

        then:"Each session factory has the correct number of persistent entities"
        applicationContext.getBeansOfType(PlatformTransactionManager).size() == 3
        applicationContext.getBean("sessionFactory", SessionFactory).metamodel.entities.size() == 2
        applicationContext.getBean("sessionFactory", SessionFactory).metamodel.entity(Person.name)
        applicationContext.getBean("sessionFactory", SessionFactory).metamodel.entity(Author.name)
        applicationContext.getBean("sessionFactory_books", SessionFactory).metamodel.entities.size() == 2
        applicationContext.getBean("sessionFactory_books", SessionFactory).metamodel.entity(Book.name)
        applicationContext.getBean("sessionFactory_books", SessionFactory).metamodel.entity(Author.name)
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).metamodel.entities.size() == 2
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).metamodel.entity(Book.name)
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).metamodel.entity(Author.name)

        and:"Each domain has the correct data source(s)"
        Person.withNewSession { Person.count() == 0 }
        Person.withNewSession {  Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:people"
            return true
        }
        Book.withNewSession { Book.count() == 0 }
        Book.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            return true
        }
        Book.moreBooks.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }
        Author.withNewSession { Author.count() == 0 }
        Author.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:people"
            return true
        }
        Author.books.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            return true
        }
        Author.moreBooks.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }

    }
}
@Entity
class Person {
    Long id
    Long version
    String name

    static constraints = {
        name blank:false
    }
}

@Entity
class Book {
    Long id
    Long version
    String name

    static mapping = {
        datasources( ['books', 'moreBooks'] )
    }
    static constraints = {
        name blank:false
    }
}

@Entity
class Author {
    Long id
    Long version
    String name

    static mapping = {
        datasource 'ALL'
    }
    static constraints = {
        name blank:false
    }
}

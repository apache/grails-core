package org.grails.orm.hibernate.connections

import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 06/07/2016.
 */
class MultipleDataSourceConnectionsSpec extends Specification {
    @Shared  Map config = [
            'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.books':[url:"jdbc:h2:mem:books;LOCK_TIMEOUT=10000"],
            'dataSources.moreBooks.url':"jdbc:h2:mem:moreBooks;LOCK_TIMEOUT=10000",
            'dataSources.moreBooks.hibernate.default_schema':"schema2"
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),Book, Author )

    void "Test map to multiple data sources"() {

        when: "The default data source is used"
        int result = Author.withTransaction {
            new Author(name: 'Fred').save(flush:true)
            Author.count()
        }



        then:"The default data source is bound"
        result ==1
        Book.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            return true
        }
        Book.moreBooks.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }
        Author.withNewSession { Author.count() == 1 }
        Author.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:grailsDB"
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

        when:"A book is saved"
        Book b =  Book.withTransaction {
            new Book(name: "The Stand").save(flush:true)
            Book.first()
        }



        then:"The data was saved correctly"
        b.name == 'The Stand'
        b.dateCreated
        b.lastUpdated


        when:"A new data source is added at runtime"
        datastore.connectionSources.addConnectionSource("yetAnother", [pooled         : true,
                                                                       dbCreate       : "create-drop",
                                                                       logSql         : false,
                                                                       formatSql      : true,
                                                                       url            : "jdbc:h2:mem:yetAnotherDB;LOCK_TIMEOUT=10000"])

        then:"The other data sources have not been touched"
        Author.withTransaction { Author.count() } == 1
        Book.withTransaction { Book.count() } == 1
        Author.yetAnother.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:yetAnotherDB"
            return true
        }
    }

    void "test @Transactional with connection property to non-default database"() {

        when:
        TestService testService = datastore.getDatastoreForConnection("books").getService(TestService)
        testService.doSomething()

        then:
        noExceptionThrown()
    }
}

@Entity
class Book {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

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

@Service
@Transactional(connection = "books")
class TestService {

    def doSomething() {}
}




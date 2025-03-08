package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by graemerocher on 20/02/2017.
 */
class MultipleDataSourcesWithEventsSpec extends Specification {

    @Issue('https://github.com/grails/grails-core/issues/10451')
    void "Test multiple data sources register the correct events"() {
        given:"A configuration for multiple data sources"
        Map config = [
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.cache':['use_second_level_cache':true,'region.factory_class':'org.hibernate.cache.ehcache.EhCacheRegionFactory'],
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.books':[url:"jdbc:h2:mem:books;LOCK_TIMEOUT=10000"]
        ]

        when:"A entity is saved with the default connection"
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),EventsBook, SecondaryBook )
        EventsBook book = new EventsBook(name:"test")
        EventsBook.withTransaction {
            book.save(flush:true)
            book.discard()
            book = EventsBook.get(book.id)
        }



        then:"The events were triggered"
        book != null
        book.name == 'TEST'
        book.time.startsWith("Time: ")


        when:"A entity is saved with a secondary connection connection"
        EventsBook book2 = new EventsBook(name:"test2")
        EventsBook.books.withTransaction {
            book2.books.save(flush:true)
            book2.books.discard()
            book2 = EventsBook.books.get(book2.id)
        }



        then:"The events were triggered"
        book2 != null
        book2.name == 'TEST2'
        book2.time.startsWith("Time: ")

        when:"An entity is saved that uses only a secondary datasource"
        SecondaryBook book3 = new SecondaryBook(name:"test3")
        SecondaryBook.withTransaction {
            book3.save(flush:true)
            book3.discard()
            book3 = SecondaryBook.get(book3.id)
        }



        then:"The events were triggered"
        book3 != null
        book3.name == 'TEST3'
        book3.time.startsWith("Time: ")
    }
}

@Entity
class SecondaryBook {
    String time
    String name
    def beforeValidate() {
        time = "Time: ${System.currentTimeMillis()}"
    }

    def beforeInsert() {
        name = name.toUpperCase()
    }

    static mapping = {
        datasource "books"
    }
}

@Entity
class EventsBook {
    String time
    String name
    def beforeValidate() {
        time = "Time: ${System.currentTimeMillis()}"
    }

    def beforeInsert() {
        name = name.toUpperCase()
    }

    static mapping = {
        datasource ConnectionSource.ALL
    }
}

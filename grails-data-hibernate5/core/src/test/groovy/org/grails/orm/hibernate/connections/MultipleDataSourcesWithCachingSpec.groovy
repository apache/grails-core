package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification

/**
 * Created by graemerocher on 15/07/2016.
 */
class MultipleDataSourcesWithCachingSpec extends Specification {

    void "Test map to multiple data sources"() {
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
                'dataSources.books':[url:"jdbc:h2:mem:books;LOCK_TIMEOUT=10000"],
                'dataSources.moreBooks':[url:"jdbc:h2:mem:moreBooks;LOCK_TIMEOUT=10000"]
        ]

        when:
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),CachingBook )
        CachingBook book = CachingBook.withTransaction {
            new CachingBook(name:"The Stand").save(flush:true)
            CachingBook.get( CachingBook.first().id )

        }

        then:
        book != null

    }
}
@Entity
class CachingBook {
    Long id
    Long version
    String name

    static mapping = {
        cache true
        datasources( ['books', 'moreBooks'] )
    }
    static constraints = {
        name blank:false
    }
}



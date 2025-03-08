package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 17/02/2017.
 */
class UniqueWithMultipleDataSourcesSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'update',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.cache':['use_second_level_cache':true,'region.factory_class':'org.hibernate.cache.ehcache.EhCacheRegionFactory'],
            'hibernate.hbm2ddl.auto': 'create',
            'dataSources.second':[url:"jdbc:h2:mem:second;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),Abc)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    @Rollback
    @Ignore
    @Issue('https://github.com/grails/grails-core/issues/10481')
    void "test multiple data sources and unique constraint"() {
        when:
        Abc abc = new Abc(temp: "testing")
        abc.save()

        Abc abc1 = new Abc(temp: "testing")
        Abc.second.withNewSession{
            abc1.second.save()
        }

        then:
        !abc1.hasErrors()
    }
}

@Entity
class Abc {

    String temp

    static constraints = {
        temp unique: true
    }

    static mapping = {
        datasource(ConnectionSource.ALL)
    }
}


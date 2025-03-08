package org.grails.orm.hibernate.connections

import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import org.hibernate.resource.jdbc.spi.JdbcSessionOwner
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

/**
 * Created by graemerocher on 20/07/2016.
 */
class SchemaMultiTenantSpec extends Specification {
    void "Test a database per tenant multi tenancy"() {
        given:"A configuration for multiple data sources"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        Map config = [
                "grails.gorm.multiTenancy.mode":"SCHEMA",
                "grails.gorm.multiTenancy.tenantResolverClass":MyResolver,
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
        ]

        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), SingleTenantAuthor )
        HibernateConnectionSource connectionSource = datastore.getConnectionSources().defaultConnectionSource
        def connection = connectionSource.dataSource.getConnection()
        connection.close()
        when:"no tenant id is present"
        SingleTenantAuthor.list()

        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"no tenant id is present"
        new SingleTenantAuthor().save()

        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        then:"the correct tenant is used"
        SingleTenantAuthor.withTransaction { SingleTenantAuthor.count() == 0 }
        SingleTenantAuthor.withTransaction {
            SingleTenantAuthor.withSession { Session s ->
                def conn = ((JdbcSessionOwner) s).getJdbcConnectionAccess().obtainConnection()
                assert conn.metaData.getURL() == "jdbc:h2:mem:grailsDB"
                return true
            }
        }

        when:"An object is saved"
        SingleTenantAuthor.withTransaction {
            SingleTenantAuthor.withSession{ Session s ->
                def conn = ((JdbcSessionOwner) s).getJdbcConnectionAccess().obtainConnection()
                assert conn.metaData.getURL() == "jdbc:h2:mem:grailsDB"
                new SingleTenantAuthor(name: "Stephen King").save(flush:true)
            }
        }

        then:"The results are correct"
        SingleTenantAuthor.withTransaction { SingleTenantAuthor.count() == 1 }

        when:
        def author =  SingleTenantAuthor.withNewSession {
            SingleTenantAuthor.find { name == "Stephen King"}
        }

        then:
        author != null

        when:"An a transaction is used"
        SingleTenantAuthor.withTransaction{
            new SingleTenantAuthor(name: "James Patterson").save(flush:true)
        }

        then:"The results are correct"
        SingleTenantAuthor.withTransaction { SingleTenantAuthor.count() == 2 }

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")


        then:"the correct tenant is used"
        SingleTenantAuthor.withTransaction { SingleTenantAuthor.count() == 0 }
        SingleTenantAuthor.withTransaction {

            SingleTenantAuthor.withSession { Session s ->
                def conn = ((JdbcSessionOwner) s).getJdbcConnectionAccess().obtainConnection()
                assert conn.metaData.getURL() == "jdbc:h2:mem:grailsDB"
                SingleTenantAuthor.count() == 0
            }
        }
        SingleTenantAuthor.withTenant("moreBooks") { String tenantId, Session s ->
            assert s != null
            SingleTenantAuthor.count() == 2
        }
        Tenants.withId("books") {
            SingleTenantAuthor.count() == 0
        }
        Tenants.withId("moreBooks") {
            SingleTenantAuthor.count() == 2
        }
        Tenants.withCurrent {
            SingleTenantAuthor.count() == 0
        }

        SingleTenantAuthor.withTransaction{
            new SingleTenantAuthor(name: "JRR Tolkien").save(flush:true)
        }

        when:"A new tenant is added at runtime"
        MyResolver.tenantIds.add("evenMoreBooks")
        datastore.addTenantForSchema("evenMoreBooks")

        then:
        SingleTenantAuthor.withTenant("evenMoreBooks") { String tenantId, Session s ->
            assert s != null
            def count = SingleTenantAuthor.count()
            count == 0
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        SingleTenantAuthor.eachTenant { String tenantId ->
            tenantIds.put(tenantId, SingleTenantAuthor.count())
        }

        then:"The result is correct"
        tenantIds == [moreBooks:2, books:1, evenMoreBooks:0]


    }

    static class MyResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {
        static List<Serializable> tenantIds = ["moreBooks", "books"]
        @Override
        Iterable<Serializable> resolveTenantIds() {

            return tenantIds
        }
    }

}


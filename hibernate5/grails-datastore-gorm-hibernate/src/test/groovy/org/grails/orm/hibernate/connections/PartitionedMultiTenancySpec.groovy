package org.grails.orm.hibernate.connections

import grails.gorm.DetachedCriteria
import grails.gorm.MultiTenant
import grails.gorm.hibernate.mapping.MappingBuilder
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Rollback
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 11/07/2016.
 */
@Rollback
class PartitionedMultiTenancySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore
    void setupSpec() {
        Map config = [
                "grails.gorm.multiTenancy.mode":MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                "grails.gorm.multiTenancy.tenantResolverClass":MyTenantResolver,
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'dataSource.logSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
        ]

        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), MultiTenantAuthor, MultiTenantBook, MultiTenantPublisher )
    }

    Session getSession() { datastore.sessionFactory.currentSession }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }


    void "Test partitioned multi tenancy"() {
        when:"no tenant id is present"
        MultiTenantAuthor.list()


        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"no tenant id is present"
        def author = new MultiTenantAuthor(name: "Stephen King")
        author.save(flush:true)

        then:"An exception is thrown"
        !author.errors.hasErrors()
        thrown(TenantNotFoundException)

        when:"A tenant id is present"
        datastore.sessionFactory.currentSession.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        then:"the correct tenant is used"
        MultiTenantAuthor.count() == 0

        when:"An object is saved"
        author = new MultiTenantAuthor(name: "Stephen King")
        author.save(flush: true)

        then:"The results are correct"
        author.tmp != null // the beforeInsert event was triggered
        MultiTenantAuthor.findByName("Stephen King")
        MultiTenantAuthor.findAll("from MultiTenantAuthor a").size() == 1
        MultiTenantAuthor.count() == 1

        when:"An a transaction is used"
        MultiTenantAuthor.withTransaction{
            new MultiTenantAuthor(name: "JRR Tolkien").save(flush:true)
        }

        then:"The results are correct"
        MultiTenantAuthor.count() == 2

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")

        then:"the correct tenant is used"
        MultiTenantAuthor.count() == 0
        !MultiTenantAuthor.findByName("Stephen King")
        MultiTenantAuthor.findAll("from MultiTenantAuthor a").size() == 0
        MultiTenantAuthor.withTenant("moreBooks").count() == 2
        MultiTenantAuthor.withTenant("moreBooks") { String tenantId, Session s ->
            assert s != null
            MultiTenantAuthor.count() == 2
        }
        Tenants.withId("books") {
            MultiTenantAuthor.count() == 0
            new MultiTenantAuthor(name: "James Patterson").save(flush:true)
        }
        Tenants.withId("moreBooks") {
            MultiTenantAuthor.count() == 2
        }
        Tenants.withId("moreBooks") {
            MultiTenantAuthor.withCriteria {
                eq 'name', 'James Patterson'
            }.size() == 0
        }


        Tenants.withCurrent {
            def results = MultiTenantAuthor.withCriteria {
                eq 'name', 'James Patterson'
            }
            results.size() == 1
        }
        Tenants.withCurrent {
            MultiTenantAuthor.findByName('James Patterson') != null
        }
        Tenants.withCurrent {
            MultiTenantAuthor.count() == 1
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        MultiTenantAuthor.eachTenant { String tenantId ->
            tenantIds.put(tenantId, MultiTenantAuthor.count())
        }

        then:"The result is correct"
        tenantIds == [moreBooks:2, books:1]

        when:"A tenant service is used"
        MultiTenantAuthorService authorService = new MultiTenantAuthorService()

        then:"The service works correctly"
        authorService.countAuthors() == 1
        authorService.countMoreAuthors() == 2

    }

    void "test multi tenancy and associations"() {
        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")

        MultiTenantAuthor.withTransaction {
            new MultiTenantAuthor(name: "Stephen King")
                .addTo("books", [title:"The Stand"])
                .addTo("books", [title:"The Shining"])
                .save()

            new MultiTenantPublisher(name: "Fluff").save()
        }

        session.clear()
        MultiTenantAuthor author = MultiTenantAuthor.findByName("Stephen King")
        MultiTenantPublisher publisher = MultiTenantPublisher.first()

        then:"The association ids are loaded with the tenant id"
        author.name == "Stephen King"
        author.books.size() == 2
        author.books.every() { MultiTenantBook book -> book.tenantCode == 'books'}
        publisher.tenantCode == 'books'

    }

    void "Test first "() {
        given: "Create two Authors with tenant T0"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A")])
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
            datastore.sessionFactory.currentSession.clear()
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
            MultiTenantAuthor.first()
        then: "An exception is thrown"
            thrown(TenantNotFoundException)

        when: "Query with a TENANT"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
            MultiTenantAuthor.first().name == 'A'

        when: "Query with OTHER TENANT"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        then:
            MultiTenantAuthor.first().name == 'B'
    }


    void "Test last "() {
        given: "Create two Authors with tenant T0"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A")])
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
            datastore.sessionFactory.currentSession.clear()
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
            MultiTenantAuthor.last()
        then: "An exception is thrown"
            thrown(TenantNotFoundException)

        when: "Query with a TENANT"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
            MultiTenantAuthor.last().name == 'A'

        when: "Query with OTHER TENANT"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        then:
            MultiTenantAuthor.last().name == 'B'
    }

    void "Test findAll with max params"() {
        given: "Create two Authors with tenant T0"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A")])
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
            datastore.sessionFactory.currentSession.clear()
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
            MultiTenantAuthor.findAll([max:2])
        then: "An exception is thrown"
            thrown(TenantNotFoundException)

        when: "Query with a TENANT"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
            MultiTenantAuthor.findAll([max:2]).name == ['A']

        when: "Query with OTHER TENANT"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        then:
            MultiTenantAuthor.findAll([max:2]).name == ['B']
    }

    void "Test list without 'max' parameter"() {
        given: "Create two Authors with tenant T0"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A"), new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
            datastore.sessionFactory.currentSession.clear()
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
            MultiTenantAuthor.list()
        then: "An exception is thrown"
            thrown(TenantNotFoundException)

        when: "Query with the same tenant as saved, should obtain 2 entities"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
            MultiTenantAuthor.list().size() == 2
    }

    void "Test list with 'max' parameter"() {
        given: "Create two Authors with tenant T0"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
            MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A"), new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
            datastore.sessionFactory.currentSession.clear()
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
            MultiTenantAuthor.list([max: 2])
        then: "An exception is thrown"
            thrown(TenantNotFoundException)

        when: "Query with the same tenant as saved, should obtain 2 entities"
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
            MultiTenantAuthor.list().size() == 2

        when: "Check the paged results"
            def sameTenantList = MultiTenantAuthor.list([max:1])
        then:
            sameTenantList.size() == 1
            sameTenantList.getTotalCount() == 2

        when: "Query by another tenant, should obtain no entities"
            datastore.sessionFactory.currentSession.clear()
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
            def list = MultiTenantAuthor.list([max: 2])
        then:
            list.size() == 0
            list.getTotalCount() == 0
    }
}

class MyTenantResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {

    Iterable<Serializable> resolveTenantIds() {
        Tenants.withoutId {
            def tenantIds = new DetachedCriteria<MultiTenantAuthor>(MultiTenantAuthor)
                    .distinct('tenantId')
                    .list()
            return tenantIds
        }
    }

}
@Entity
class MultiTenantAuthor implements GormEntity<MultiTenantAuthor>,MultiTenant<MultiTenantAuthor> {
    Long id
    Long version
    String tenantId
    String name
    transient String tmp

    def beforeInsert() {
        tmp = "foo"
    }
    static hasMany = [books:MultiTenantBook]
    static constraints = {
        name blank:false
    }
}

@CurrentTenant
class MultiTenantAuthorService {
    int countAuthors() {
        MultiTenantAuthor.count()
    }

    @Tenant({ "moreBooks" })
    int countMoreAuthors() {
        MultiTenantAuthor.count()
    }
}

@Entity
class MultiTenantBook implements GormEntity<MultiTenantBook>,MultiTenant<MultiTenantBook> {
    Long id
    Long version
    String tenantCode
    String title



    static belongsTo = [author:MultiTenantAuthor]
    static constraints = {
        title blank:false
    }

    static mapping = {
        tenantId name:"tenantCode"
    }
}


@Entity
class MultiTenantPublisher implements GormEntity<MultiTenantPublisher>,MultiTenant<MultiTenantPublisher> {
    String tenantCode
    String name

    static mapping = MappingBuilder.orm {
        tenantId "tenantCode"
    }
}


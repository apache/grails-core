package org.grails.datastore.gorm.mongo.connections

import com.mongodb.client.MongoClient
import org.bson.Document
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSources
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 15/07/2016.
 */
class MongoConnectionSourcesSpec extends Specification {

    @Shared @AutoCleanup MongoDatastore datastore

    void setupSpec() {
        Map config = [
                "grails.gorm.connectionSourcesClass"          : MongoConnectionSources,
                "grails.gorm.multiTenancy.mode"               :"DATABASE",
                "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver,
                (MongoSettings.SETTING_URL)                   : "mongodb://localhost/defaultDb",
                (MongoSettings.SETTING_CONNECTIONS): [
                        test1: [
                                url: "mongodb://localhost/test1Db"
                        ],
                        test2: [
                                url: "mongodb://localhost/test2Db"
                        ]
                ]
        ]
        this.datastore = new MongoDatastore(config, CompanyB)
    }

    void "Test persist and retrieve entities with multi tenancy"() {
        setup:
        CompanyB.eachTenant {
            CompanyB.DB.drop()
        }

        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")

        then:"the correct tenant is used"
        CompanyB.count() == 0
        CompanyB.DB.name == 'test1Db'

        when:"An object is saved"
        new CompanyB(name: "Foo").save(flush:true)

        then:"The results are correct"
        CompanyB.count() == 1

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test2")

        then:"the correct tenant is used"
        CompanyB.DB.name == 'test2Db'
        CompanyB.count() == 0
        CompanyB.withTenant("test1") { Serializable tenantId, Session s ->
            assert tenantId
            assert s
            CompanyB.count() == 1
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        CompanyB.eachTenant { String tenantId ->
            tenantIds.put(tenantId, CompanyB.count())
        }

        then:"The result is correct"
        tenantIds == [test1:1, test2:0]

        when:"A data source is added and switched to at runtime"
        datastore.connectionSources.addConnectionSource("test3",[url:"mongodb://localhost/test3Db"])
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test3")

        then:"The database is usable"
        CompanyB.DB.name == 'test3Db'
        CompanyB.count() == 0

    }
}

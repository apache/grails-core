package org.grails.datastore.gorm.mongo.connections

import org.grails.datastore.gorm.mongo.City
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 14/07/2016.
 */
class SchemaBasedMultiTenancySpec extends Specification {

    @Shared @AutoCleanup MongoDatastore datastore

    void setupSpec() {
        Map config = [
                (MongoSettings.SETTING_URL): "mongodb://localhost/defaultDb",
                "grails.gorm.multiTenancy.mode"               :"SCHEMA",
                "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver
        ]
        this.datastore = new MongoDatastore(config, getDomainClasses() as Class[])
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void "Test no tenant id"() {
        when:
        CompanyB.DB

        then:
        thrown(TenantNotFoundException)
    }

    void "Test multi tenancy state"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")
        expect:
        City.DB.name == "defaultDb"
        CompanyB.DB.name == 'test1'
    }

    void "Test persist and retrieve entities with multi tenancy"() {
        setup:
        CompanyB.eachTenant {
            try {
                CompanyB.DB.drop()    
            } catch(e) {
                // continue
            }
            
        }

        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")

        then:"the correct tenant is used"
        CompanyB.count() == 0
        CompanyB.DB.name == 'test1'

        when:"An object is saved"
        new CompanyB(name: "Foo").save(flush:true)

        then:"The results are correct"
        CompanyB.count() == 1

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test2")

        then:"the correct tenant is used"
        CompanyB.DB.name == 'test2'
        CompanyB.count() == 0
        new CompanyB(name: "Bar").save(flush:true)
        CompanyB.withTenant("test1") { Serializable tenantId, Session s ->
            assert tenantId
            assert s
            new CompanyB(name: "Baz").save(flush:true)
            CompanyB.count() == 2
        }

        when:"each tenant is iterated over"
        final Map<String, Integer> companyCount = [:]
        CompanyB.eachTenant { String tenantId ->
            companyCount.put(tenantId, CompanyB.count())
        }

        then:"The result is correct"
        companyCount['admin'] == 0
        companyCount['test1'] == 2
        companyCount['test2'] == 1
    }

    List getDomainClasses() {
        [City, CompanyB]
    }

}

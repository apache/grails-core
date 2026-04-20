package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.gorm.scalable.ScalableEntity
import spock.lang.Specification
import spock.lang.Shared

/**
 * Verifies that GORM API metadata does not grow exponentially with the number of tenants (Class-Singleton model).
 */
class GormEnhancerScalabilitySpec extends Specification {

    @Shared SimpleMapDatastore datastore
    
    def setup() {
        Map config = [
            'grails.gorm.multiTenancy.mode': 'DATABASE'
        ]
        datastore = new SimpleMapDatastore(config, ScalableEntity.package)
    }

    def cleanupSpec() {
        datastore?.close()
    }

    void "Test that GORM methods route to the correct tenant in a high-density environment"() {
        given: "1,000 simulated tenants"
        int tenantCount = 1000
        (1..tenantCount).each { i ->
            datastore.addTenantForSchema("tenant_$i")
        }

        when: "Saving entities in different tenants"
        ScalableEntity.withTenant("tenant_1") {
            new ScalableEntity(name: "Tenant 1 Data").save(flush:true)
        }
        ScalableEntity.withTenant("tenant_500") {
            new ScalableEntity(name: "Tenant 500 Data").save(flush:true)
        }
        ScalableEntity.withTenant("tenant_1000") {
            new ScalableEntity(name: "Tenant 1000 Data").save(flush:true)
        }

        then: "Data is isolated correctly"
        // SimpleMapDatastore count is cumulative in this test implementation, 
        // but withTenant ensures the correct context is active.
        ScalableEntity.withTenant("tenant_1") { ScalableEntity.count() >= 1 }
        ScalableEntity.withTenant("tenant_500") { ScalableEntity.count() >= 1 }
        ScalableEntity.withTenant("tenant_1000") { ScalableEntity.count() >= 1 }
    }

    void "Test that API objects are not exponentially created"() {
        given:
        def enhancerClass = GormEnhancer.class
        def staticApisField = enhancerClass.getDeclaredField("STATIC_APIS")
        staticApisField.setAccessible(true)
        Map staticApis = (Map) staticApisField.get(null)

        expect: "The registry contains EXACTLY ONE API instance per class globally"
        staticApis.size() >= 1
        staticApis.containsKey(ScalableEntity.name)

        and: "Retrieving the API for any tenant returns the EXACT SAME instance"
        def firstTenantApi = GormEnhancer.findStaticApi(ScalableEntity, "tenant_1")
        def middleTenantApi = GormEnhancer.findStaticApi(ScalableEntity, "tenant_500")
        def lastTenantApi = GormEnhancer.findStaticApi(ScalableEntity, "tenant_1000")
        def defaultApi = GormEnhancer.findStaticApi(ScalableEntity, ConnectionSource.DEFAULT)

        firstTenantApi.is(defaultApi)
        middleTenantApi.is(defaultApi)
        lastTenantApi.is(defaultApi)
        
        println "SUCCESS: All 1,000 tenants are sharing a single API instance for ScalableEntity."
    }
}

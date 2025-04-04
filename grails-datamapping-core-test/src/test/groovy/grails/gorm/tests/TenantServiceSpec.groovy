package grails.gorm.tests

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.TenantService
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 11/01/2017.
 */
class TenantServiceSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DATABASE,
                    (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
            ),
            [ConnectionSource.DEFAULT, 'two'],
            Team
    )
    def setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }
    
    void "test multi tenancy with in-memory datastore"() {
        when:
        Team.count()

        then:
        thrown TenantNotFoundException

        when:
        TenantService tenantService = datastore.getService(TenantService)
        def twoCount = tenantService.withId("two") {
            new Team(name: "Arsenal").save(flush:true)
            Team.count()
        }
        def defaultCount = tenantService.withId(ConnectionSource.DEFAULT) { Team.count() }
        Team.count()

        then:
        twoCount == 1
        defaultCount == 0
        thrown TenantNotFoundException

        when:"The current tenant is set"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "two")
        new Team(name: "Chelsea").save(flush:true)
        twoCount == Team.count()
        defaultCount = tenantService.withoutId {
            Team.count()
        }

        then:
        tenantService.currentId() == "two"
        Team.findByName("Chelsea") != null
        Team.findByName("Arsenal") != null
        defaultCount == 0
        Team.count() == 2


        when:"The current tenant is set"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)
        new Team(name: "Manchester United").save(flush:true)


        then:
        tenantService.currentId() == ConnectionSource.DEFAULT
        Team.findByName("Chelsea") == null
        Team.findByName("Arsenal") == null
        Team.count() == 1

    }
}

@Entity
class Team implements MultiTenant<Team> {
    String name
}



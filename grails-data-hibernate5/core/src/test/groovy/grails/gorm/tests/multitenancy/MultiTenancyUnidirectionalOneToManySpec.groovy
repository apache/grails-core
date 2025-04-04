package grails.gorm.tests.multitenancy

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.GormSpec
import grails.gorm.MultiTenant
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.connections.SingleTenantAuthor
import org.hibernate.dialect.H2Dialect
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by graemerocher on 16/06/2017.
 */
class MultiTenancyUnidirectionalOneToManySpec extends Specification {

    @Issue('https://github.com/grails/grails-data-mapping/issues/954')
    void "test multi-tenancy with unidirectional one-to-many"() {
        given:"A configuration for schema based multi-tenancy"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        Map config = [
                "grails.gorm.multiTenancy.mode":MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver.name,
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
        ]

        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), getClass().getPackage() )

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "ford")
        Vehicle.withTransaction {
            new Vehicle(model: "A5", year: 2017, manufacturer: "Audi")
                    .addToEngines(cylinders: 6, manufacturer: "VW")
                    .addToWheels(spokes: 5)
                    .save(flush:true)
        }

        then:
        Vehicle.withTransaction { Vehicle.count() } == 1
        Vehicle.withTransaction {
            Vehicle.first().engines.size()
        } == 1
        Vehicle.withTransaction {
            Vehicle.where { year == 2017 }.list(fetch: [engines: "join", wheels: "join"]).size()
        } == 1

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "tesla")

        then:
        Vehicle.withTransaction { Vehicle.count() } == 0

        cleanup:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }
}


@Entity
class Engine implements MultiTenant<Engine> {
    Integer cylinders
    String manufacturer
//    static belongsTo = [vehicle: Vehicle] // If you remove this, it fails

    static constraints = {
        cylinders nullable: false
    }

    static mapping = {
        tenantId name:'manufacturer'
    }
}

@Entity
class Wheel implements MultiTenant<Wheel> {
    Integer spokes
    String manufacturer
//    static belongsTo = [vehicle: Vehicle] // If you remove this, it fails

    static constraints = {
        spokes nullable: false
    }

    static mapping = {
        tenantId name:'manufacturer'
    }
}

@Entity
class Vehicle implements MultiTenant<Vehicle> {
    String model
    Integer year
    String manufacturer

    static hasMany = [engines: Engine, wheels: Wheel]
    static constraints = {
        model blank:false
        year min:1980
    }

    static mapping = {
        tenantId name:'manufacturer'
        year column: 'vehicleYear'
    }
}

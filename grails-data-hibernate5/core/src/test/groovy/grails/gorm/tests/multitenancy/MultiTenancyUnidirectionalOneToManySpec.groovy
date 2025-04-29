/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.tests.multitenancy

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import grails.gorm.MultiTenant
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by graemerocher on 16/06/2017.
 */
class MultiTenancyUnidirectionalOneToManySpec extends Specification {

    @Issue('https://github.com/grails/grails-data-mapping/issues/954')
    void "test multi-tenancy with unidirectional one-to-many"() {
        given: "A configuration for schema based multi-tenancy"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        Map config = [
                "grails.gorm.multiTenancy.mode"               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                "grails.gorm.multiTenancy.tenantResolverClass": SystemPropertyTenantResolver.name,
                'dataSource.url'                              : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dialect'                          : H2Dialect.name,
                'dataSource.formatSql'                        : 'true',
                'hibernate.flush.mode'                        : 'COMMIT',
                'hibernate.cache.queries'                     : 'true',
                'hibernate.hbm2ddl.auto'                      : 'create',
        ]

        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), getClass().getPackage())

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "ford")
        Vehicle.withTransaction {
            new Vehicle(model: "A5", year: 2017, manufacturer: "Audi")
                    .addToEngines(cylinders: 6, manufacturer: "VW")
                    .addToWheels(spokes: 5)
                    .save(flush: true)
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
        tenantId name: 'manufacturer'
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
        tenantId name: 'manufacturer'
    }
}

@Entity
class Vehicle implements MultiTenant<Vehicle> {
    String model
    Integer year
    String manufacturer

    static hasMany = [engines: Engine, wheels: Wheel]
    static constraints = {
        model blank: false
        year min: 1980
    }

    static mapping = {
        tenantId name: 'manufacturer'
        year column: 'vehicleYear'
    }
}

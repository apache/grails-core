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
package grails.gorm.multitenancy.tenantid

import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * The constraints declared on a tenant id are checked once multi-tenancy has assigned the current tenant id.
 */
@Issue('https://github.com/apache/grails-core/issues/14365')
@RestoreSystemProperties
class TenantIdConstraintsSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()],
            getClass().getPackage()
    )

    void setupSpec() {
        datastore.mappingContext.validatorRegistry = new DefaultValidatorRegistry(datastore.mappingContext,
                datastore.connectionSources.defaultConnectionSource.settings)
    }

    void setup() {
        datastore.clearData()
    }

    void 'the constraints of a property other than the tenant id are checked'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def customer = new Customer()
        boolean valid = Customer.withNewSession { customer.validate() }

        then:
        !valid
        customer.errors.getFieldError('name').code == 'nullable'
    }

    void 'a tenant id the resolver assigns passes the constraints declared on it'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def customer = new Customer(name: 'Dave')
        def saved = Customer.withNewSession { customer.save(flush: true) }

        then:
        saved
        customer.firmId == 'acme'
        Customer.withNewSession { Customer.count() } == 1
    }

    void 'a tenant id that breaks a constraint declared on it fails validation'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')

        when:
        def customer = new Customer(name: 'Dave')
        def saved = Customer.withNewSession { customer.save(flush: true) }

        then:
        !saved
        customer.id == null
        customer.firmId == 'a-tenant-too-long'
        customer.errors.getFieldError('firmId').code == 'maxSize.exceeded'
    }

    void 'a new associated object is saved with its owner, though its tenant id is assigned only as it is inserted'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: 'Rex'))
        def saved = Owner.withNewSession { owner.save(flush: true) }

        then:
        saved
        !owner.hasErrors()
        owner.pets*.firmId == ['acme']
    }

    void 'the other properties of a new associated object are still checked as it is saved with its owner'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: null))
        def saved = Owner.withNewSession { owner.save(flush: true) }

        then:
        !saved
        owner.id == null
        owner.errors.fieldErrors.find { it.field.endsWith('name') }?.code == 'nullable'
        !owner.errors.fieldErrors.any { it.field.endsWith('firmId') }
    }
}

@Entity
class Customer implements MultiTenant<Customer> {

    String name
    String firmId

    static constraints = {
        name nullable: false
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class Owner implements MultiTenant<Owner> {

    String name
    String tenantId

    static hasMany = [pets: Pet]
}

@Entity
class Pet implements MultiTenant<Pet> {

    String name
    String firmId

    static belongsTo = [owner: Owner]

    static constraints = {
        name nullable: false
        firmId nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

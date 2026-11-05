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
package grails.gorm.tests.tenantid

import org.hibernate.Hibernate
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The constraints declared on the tenant id of a multi-tenant entity are checked when it is validated through
 * Hibernate, after the multi-tenancy event listener has assigned the current tenant id.
 */
@Issue('https://github.com/apache/grails-core/issues/14365')
@Rollback
@RestoreSystemProperties
class TenantIdConstraintsSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore

    void setupSpec() {
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:tenantIdConstraintsDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'create-drop',
                'dataSource.dialect'                          : H2Dialect.name,
        ]), TenantCustomer, TenantAccount, TenantOwner, TenantPet)
    }

    void 'a tenant id the listener assigns passes the constraints declared on it'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def customer = new TenantCustomer(name: 'Dave')
        boolean valid = customer.validate()

        then: 'validate() is given the current tenant id first'
        valid
        customer.firmId == 'acme'

        when:
        customer.save(flush: true)

        then:
        !customer.hasErrors()
        TenantCustomer.count() == 1
    }

    void 'a tenant id the listener assigns that breaks a constraint declared on it fails validation'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')

        when:
        def customer = new TenantCustomer(name: 'Dave')
        def saved = customer.save(flush: true)

        then:
        !saved
        customer.id == null
        customer.firmId == 'a-tenant-too-long'
        customer.errors.getFieldError('firmId').code == 'maxSize.exceeded'
    }

    void 'without a current tenant, a tenant id declared nullable: false is required'() {
        when:
        def customer = new TenantCustomer(name: 'Dave')
        def saved = Tenants.withoutId(datastore) { customer.save(flush: true) }

        then:
        !saved
        customer.id == null
        customer.errors.getFieldError('firmId').code == 'nullable'
    }

    void 'without a current tenant, a tenant id given a value is checked against the constraints declared on it'() {
        when:
        def valid = new TenantCustomer(name: 'Dave', firmId: 'abc')
        def tooLong = new TenantCustomer(name: 'Dave', firmId: 'a-tenant-too-long')
        Tenants.withoutId(datastore) {
            valid.save(flush: true)
            tooLong.save(flush: true)
        }

        then:
        !valid.hasErrors()
        valid.id != null
        valid.firmId == 'abc'
        tooLong.id == null
        tooLong.errors.getFieldError('firmId').code == 'maxSize.exceeded'
    }

    void 'the constraints declared on a tenant id named tenantId are checked'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def account = new TenantAccount(name: 'Main')
        account.save(flush: true)

        then:
        !account.hasErrors()
        account.tenantId == 'acme'
        TenantAccount.count() == 1

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        account = new TenantAccount(name: 'Second')
        def saved = account.save(flush: true)

        then:
        !saved
        account.id == null
        account.errors.getFieldError('tenantId').code == 'maxSize.exceeded'
    }

    void 'a new associated object is given the current tenant id before it is validated and saved with its owner'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex'))
        boolean valid = owner.validate()

        then: 'validate() is given the current tenant id for the pet as well'
        valid
        owner.pets*.firmId == ['acme']

        when:
        owner.save(flush: true)

        then:
        !owner.hasErrors()
        owner.id != null
        owner.pets*.firmId == ['acme']
        TenantPet.count() == 1
    }

    void 'saving an owner fails validation when the current tenant id breaks a constraint declared on the tenant id of a new associated object'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')

        when:
        def owner = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex'))
        def saved = owner.save(flush: true)

        then: 'the owner, which declares nothing on its own tenant id, reports the tenant id of the pet'
        !saved
        owner.id == null
        owner.errors.getFieldError('pets[0].firmId').code == 'maxSize.exceeded'
    }

    void 'without a current tenant, saving an owner fails validation when a new associated object has no tenant id'() {
        when: 'nothing assigns the tenant id of the pet, which is declared nullable: false'
        def owner = new TenantOwner(name: 'Dave', tenantId: 'abc').addToPets(new TenantPet(name: 'Rex'))
        def saved = Tenants.withoutId(datastore) { owner.save(flush: true) }

        then:
        !saved
        owner.id == null
        owner.errors.getFieldError('pets[0].firmId').code == 'nullable'
    }

    void 'validating an owner does not load the associated objects it has not loaded yet'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        Long id = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex')).save(flush: true).id
        datastore.sessionFactory.currentSession.clear()

        when:
        def owner = TenantOwner.get(id)
        boolean valid = owner.validate()

        then:
        valid
        !Hibernate.isInitialized(owner.pets)
    }

    void 'validating an owner without cascading leaves a new associated object alone'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex'))
        boolean valid = owner.validate(deepValidate: false)

        then: 'only the owner is given the current tenant id'
        valid
        owner.tenantId == 'acme'
        owner.pets*.firmId == [null]

        when: 'the pet is not validated with the owner as it is saved either'
        owner.save(deepValidate: false, flush: true)

        then: 'it is given the current tenant id as it is inserted'
        !owner.hasErrors()
        owner.pets*.firmId == ['acme']
        TenantPet.count() == 1
    }

    void 'validating or saving an owner without cascading leaves a changed associated object of another tenant alone'() {
        given: 'an owner and a pet of one tenant, loaded inside withoutId'
        TenantOwner owner = loadWithoutTenant(savedForTenant('beta', 'Rex'))

        when: 'the pet is changed, and the owner is validated while another tenant is current'
        owner.pets.first().name = 'Max'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = owner.validate(deepValidate: false)

        then: 'only the owner is given the current tenant id'
        valid
        owner.tenantId == 'acme'
        owner.pets*.firmId == ['beta']

        when: 'the owner is saved, which does not update the pet before the session is flushed'
        owner.save(deepValidate: false)

        then:
        !owner.hasErrors()
        owner.pets*.firmId == ['beta']
    }

    void 'an associated object that has a tenant id and no changes keeps it as its owner is validated'() {
        given: 'an owner and two pets of one tenant, loaded inside withoutId'
        TenantOwner owner = loadWithoutTenant(savedForTenant('beta', 'Rex', 'Tom'))

        when: 'one pet is changed, and the owner is validated while another tenant is current'
        owner.pets.find { it.name == 'Tom' }.name = 'Tim'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = owner.validate()

        then: 'the changed pet is given the tenant id its update gives it, and the pet that is not updated is left alone'
        valid
        owner.pets.collectEntries { [it.name, it.firmId] } == [Rex: 'beta', Tim: 'acme']

        when:
        owner.save(flush: true)

        then:
        !owner.hasErrors()
        storedTenantIds() == [Rex: 'beta', Tim: 'acme']
    }

    void 'a tenant id an associated object already has is checked as it is saved with its owner'() {
        when:
        def owner = new TenantOwner(name: 'Dave', tenantId: 'abc').addToPets(new TenantPet(name: 'Rex', firmId: 'a-tenant-too-long'))
        def saved = Tenants.withoutId(datastore) { owner.save(flush: true) }

        then:
        !saved
        owner.id == null
        owner.errors.fieldErrors.find { it.field.endsWith('firmId') }?.code == 'maxSize.exceeded'
    }

    private Long savedForTenant(String tenantId, String... petNames) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
        def owner = new TenantOwner(name: 'Dave')
        petNames.each { owner.addToPets(new TenantPet(name: it)) }
        Long id = owner.save(flush: true).id
        datastore.sessionFactory.currentSession.clear()
        id
    }

    private TenantOwner loadWithoutTenant(Long id) {
        Tenants.withoutId(datastore) {
            def owner = TenantOwner.get(id)
            owner.pets.size()
            owner
        }
    }

    private Map<String, String> storedTenantIds() {
        datastore.sessionFactory.currentSession.clear()
        Tenants.withoutId(datastore) { TenantPet.list().collectEntries { [it.name, it.firmId] } }
    }
}

@Entity
class TenantCustomer implements MultiTenant<TenantCustomer> {

    String name
    String firmId

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantAccount implements MultiTenant<TenantAccount> {

    String name
    String tenantId

    static constraints = {
        tenantId nullable: false, maxSize: 5
    }
}

@Entity
class TenantOwner implements MultiTenant<TenantOwner> {

    String name
    String tenantId

    static hasMany = [pets: TenantPet]
}

@Entity
class TenantPet implements MultiTenant<TenantPet> {

    String name
    String firmId

    static belongsTo = [owner: TenantOwner]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

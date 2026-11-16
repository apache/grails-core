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
package grails.gorm.multitenancy.notenantid

import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * Only discriminator mode assigns the tenant id of a multi-tenant entity. Without a multi-tenancy mode, as in
 * {@code DATABASE} and {@code SCHEMA} mode and in unit tests that don't configure discriminator mode, nothing assigns
 * it, so the constraints declared on it decide whether it may be left unset.
 */
@Issue('https://github.com/apache/grails-core/issues/14365')
class UnassignedTenantIdConstraintsSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(Customer, Invoice, Receipt, Voucher, Owner, Pet)

    void setupSpec() {
        datastore.mappingContext.validatorRegistry = new DefaultValidatorRegistry(datastore.mappingContext,
                datastore.connectionSources.defaultConnectionSource.settings)
    }

    void setup() {
        datastore.clearData()
    }

    void 'a tenant id declared nullable: false is required'() {
        when:
        def customer = new Customer(name: 'Dave')
        boolean valid = Customer.withNewSession { customer.validate() }

        then:
        !valid
        customer.errors.getFieldError('firmId').code == 'nullable'

        when:
        customer = new Customer(name: 'Dave')
        def saved = Customer.withNewSession { customer.save(flush: true) }

        then:
        !saved
        customer.id == null
        customer.errors.getFieldError('firmId').code == 'nullable'
    }

    void 'a tenant id given a value passes the constraints declared on it'() {
        when:
        def customer = new Customer(name: 'Dave', firmId: 'acme')
        def saved = Customer.withNewSession { customer.save(flush: true) }

        then:
        saved
        customer.id != null
    }

    void 'an associated object saved with its owner is required to have a tenant id declared nullable: false'() {
        when: 'nothing assigns the tenant id of the new associated object as it is inserted'
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: 'Rex'))
        def saved = Owner.withNewSession { owner.save(flush: true) }

        then:
        !saved
        owner.id == null
        owner.errors.fieldErrors.find { it.field.endsWith('firmId') }?.code == 'nullable'
    }

    void 'a tenant id declared with only maxSize may be left unset, and its maxSize is checked'() {
        when:
        def receipt = new Receipt(number: 'R-1')
        def saved = Receipt.withNewSession { receipt.save(flush: true) }

        then:
        saved
        receipt.id != null
        receipt.firmId == null

        when:
        receipt = new Receipt(number: 'R-2', firmId: 'a-tenant-too-long')
        saved = Receipt.withNewSession { receipt.save(flush: true) }

        then:
        !saved
        receipt.id == null
        receipt.errors.getFieldError('firmId').code == 'maxSize.exceeded'
    }

    void 'a tenant id named tenantId is constrained as one named in the mapping is'() {
        when: 'only maxSize is declared on it'
        def voucher = new Voucher(number: 'V-1')
        def saved = Voucher.withNewSession { voucher.save(flush: true) }

        then: 'it may be left unset'
        saved
        voucher.id != null
        voucher.tenantId == null

        when:
        voucher = new Voucher(number: 'V-2', tenantId: 'a-tenant-too-long')
        saved = Voucher.withNewSession { voucher.save(flush: true) }

        then:
        !saved
        voucher.id == null
        voucher.errors.getFieldError('tenantId').code == 'maxSize.exceeded'
    }

    void 'a tenant id without declared constraints may be left unset'() {
        when:
        def invoice = new Invoice(number: 'INV-1')
        def saved = Invoice.withNewSession { invoice.save(flush: true) }

        then:
        saved
        invoice.id != null
        invoice.tenantId == null
    }
}

@Entity
class Customer implements MultiTenant<Customer> {

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
class Invoice implements MultiTenant<Invoice> {

    String number
    String tenantId
}

@Entity
class Receipt implements MultiTenant<Receipt> {

    String number
    String firmId

    static constraints = {
        firmId maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class Voucher implements MultiTenant<Voucher> {

    String number
    String tenantId

    static constraints = {
        tenantId maxSize: 5
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
        firmId nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

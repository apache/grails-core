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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.hibernate5.HibernateSystemException
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
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
        ]), TenantCustomer, TenantAccount, TenantOwner, TenantPet, TenantShelter, TenantCat, TenantChip, TenantSignup, TenantClub,
                TenantMember, TenantDen, TenantCub, TenantLedger, TenantJournal, TenantArchive, TenantBurrow, TenantFerret, TenantNest, TenantChick, TenantLog,
                TenantEntry, TenantRack, TenantTag, TenantLabel, TenantYard, TenantCrate, TenantBox, TenantBin, TenantPail, TenantFarm,
                TenantCow, TenantGoat, TenantSheep, TenantHen, TenantDuck, TenantHouse, TenantMat)
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

    void 'an associated object that a beforeValidate callback creates as its owner is validated is given the current tenant id'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when: 'the beforeValidate callback of the cat gives it a chip, whose tenant id is declared nullable: false'
        def cat = new TenantCat(name: 'Tom')
        def shelter = new TenantShelter(name: 'North').addToCats(cat)
        shelter.save(flush: true)

        then:
        !shelter.hasErrors()
        cat.chip.firmId == 'acme'
        TenantChip.count() == 1

        when: 'the beforeValidate callback of the shelter gives it a cat'
        shelter = new TenantShelter(name: 'South')
        shelter.save(flush: true)

        then:
        !shelter.hasErrors()
        shelter.cats*.firmId == ['acme']
        shelter.cats*.chip*.firmId == ['acme']
    }

    void 'an associated object whose only change is inside an embedded object is given the current tenant id'() {
        given: 'a shelter and a cat of one tenant, loaded inside withoutId'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta')
        def cat = shelter.cats.first()

        when: 'only its embedded home is changed, and the shelter is validated while a tenant that breaks the maxSize of the cat is current'
        cat.home.city = 'Paris'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = shelter.validate()

        then: 'the cat, which is updated with the current tenant id, is validated with it'
        !cat.hasChanged()
        !valid
        cat.firmId == 'a-tenant-too-long'
        shelter.errors.getFieldError('cats[0].firmId').code == 'maxSize.exceeded'

        and: 'its chip, which is not updated, is left alone'
        cat.chip.firmId == 'beta'
    }

    void 'an associated object that the beforeValidate callback of its embedded object changes is given the current tenant id'() {
        given: 'a shelter and a cat of one tenant, loaded inside withoutId, whose home changes itself when it is validated in Oslo'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta', 'Oslo')
        def cat = shelter.cats.first()

        when: 'the shelter is validated while a tenant that breaks the maxSize of the cat is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = shelter.validate()

        then: 'the cat, which the callback has run after, is updated with the current tenant id, and is validated with it'
        cat.home.city == 'Bergen'
        !cat.hasChanged()
        !valid
        cat.firmId == 'a-tenant-too-long'
        shelter.errors.getFieldError('cats[0].firmId').code == 'maxSize.exceeded'

        and: 'its chip, which is not updated, is left alone'
        cat.chip.firmId == 'beta'
    }

    void 'an associated object that keeps a tenant id that breaks a constraint declared on it fails the validation of its owner'() {
        given: 'a shelter and a cat, loaded inside withoutId, whose stored tenant id breaks the minSize of the cat'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta', null, 'x')
        def cat = shelter.cats.first()

        when: 'the shelter is validated while a tenant that fits is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = shelter.validate()

        then: 'the cat, which is not updated, is validated with the tenant id it has'
        !valid
        cat.firmId == 'x'
        shelter.errors.getFieldError('cats[0].firmId').code == 'minSize.notmet'
    }

    void 'an associated object that a later callback changes is validated with the current tenant id alone'() {
        given: 'the same shelter and cat, whose home changes itself when it is validated in Oslo'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta', 'Oslo', 'x')
        def cat = shelter.cats.first()

        when: 'the shelter is validated while a tenant that fits is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = shelter.validate()

        then: 'the cat, which the callback has run after, is updated with the current tenant id'
        cat.home.city == 'Bergen'
        cat.firmId == 'acme'

        and: 'the tenant id it had, which its constraints were checked with first, is not reported'
        valid
        !shelter.hasErrors()

        when:
        shelter.save(flush: true)

        then:
        !shelter.hasErrors()
        storedTenantIds(TenantCat) == [Tom: 'acme']
    }

    void 'an owner is saved when a later callback changes an associated object whose tenant id it had broke a constraint'() {
        given: 'the shelter and the cat of the previous case'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta', 'Oslo', 'x')
        def cat = shelter.cats.first()

        when: 'the shelter is saved while a tenant that fits is current, without being validated first'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def saved = shelter.save(flush: true)

        then: 'the error of the tenant id the cat had is not reported, and the cat is stored with the current one'
        saved
        !shelter.hasErrors()
        cat.firmId == 'acme'
        storedTenantIds(TenantCat) == [Tom: 'acme']
    }

    void 'a read-only associated object keeps its tenant id as its owner is validated'() {
        given: 'an owner and a pet of one tenant, loaded inside withoutId, the pet with read(id)'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        def stored = new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex')).save(flush: true)
        Long ownerId = stored.id
        Long petId = stored.pets.first().id
        datastore.sessionFactory.currentSession.clear()
        TenantPet pet = null
        TenantOwner owner = Tenants.withoutId(datastore) {
            pet = TenantPet.read(petId)
            def loaded = TenantOwner.get(ownerId)
            loaded.pets.size()
            loaded
        }

        when: 'the pet is changed, and the owner is validated while a tenant that breaks the maxSize of the pet is current'
        pet.name = 'Max'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = owner.validate()

        then: 'the pet, which Hibernate does not update although it has changed, is left alone'
        owner.pets.first().is(pet)
        pet.hasChanged()
        valid
        pet.firmId == 'beta'

        when:
        owner.save(flush: true)

        then: 'and is stored as it was'
        !owner.hasErrors()
        storedTenantIds() == [Rex: 'beta']
    }

    void 'an associated object without a tenant id that Hibernate does not update keeps none'() {
        given: 'a log and an entry stored without tenant ids, which the entry allows, loaded inside withoutId'
        def stored = new TenantLog(name: 'Main').addToEntries(new TenantEntry(text: 'first'))
        Tenants.withoutId(datastore) { stored.save(flush: true) }
        datastore.sessionFactory.currentSession.clear()
        TenantEntry entry = null
        TenantLog log = Tenants.withoutId(datastore) {
            if (readOnly) {
                entry = TenantEntry.read(stored.entries.first().id)
            }
            def loaded = TenantLog.get(stored.id)
            loaded.entries.size()
            loaded
        }
        entry = entry ?: log.entries.first()

        when: 'the log is saved while a tenant that the entry does not allow is current'
        if (readOnly) {
            entry.text = 'changed'
        }
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        def saved = log.save(flush: true)

        then: 'the entry, which is not updated, is validated without a tenant id, and is stored as it was'
        log.entries.first().is(entry)
        saved
        entry.firmId == null
        storedTenantIds(TenantEntry, 'text') == [first: null]

        where:
        readOnly << [true, false]
    }

    void 'a read-only associated object keeps its tenant id when one of its collections has changed'() {
        given: 'a shelter and a cat of one tenant, loaded inside withoutId, the cat made read-only'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta')
        def cat = shelter.cats.first()
        datastore.sessionFactory.currentSession.setReadOnly(cat, true)

        when: 'a nickname is added, and the shelter is validated while a tenant that breaks the maxSize of the cat is current'
        cat.nicknames << 'Tommy'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = shelter.validate()

        then: 'the cat, whose version alone Hibernate updates, is left alone'
        valid
        cat.firmId == 'beta'

        when:
        shelter.save(flush: true)

        then: 'and its tenant id is stored as it was'
        !shelter.hasErrors()
        storedTenantIds(TenantCat) == [Tom: 'beta']
    }

    void 'an associated object whose only change is in a collection is given the current tenant id'() {
        given: 'a shelter and a cat of one tenant, loaded inside withoutId'
        TenantShelter shelter = shelterLoadedWithoutTenant('beta')
        def cat = shelter.cats.first()

        when: 'a nickname is added, and the shelter is validated while a tenant that breaks the maxSize of the cat is current'
        cat.nicknames << 'Tommy'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = shelter.validate()

        then: 'the cat, which Hibernate updates to increment its version, is validated with the current tenant id'
        !cat.hasChanged()
        !valid
        cat.firmId == 'a-tenant-too-long'
        shelter.errors.getFieldError('cats[0].firmId').code == 'maxSize.exceeded'
    }

    void 'a constraint that reads the errors of the instance sees the errors recorded before it'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def validated = new TenantSignup(email: 'not-an-address', emailConfirm: 'other')
        validated.validate()
        def saved = new TenantSignup(email: 'not-an-address', emailConfirm: 'other')
        saved.save()

        then: 'the confirmation is not checked against an address that is invalid itself'
        validated.errors.fieldErrors*.field == ['email']
        saved.errors.fieldErrors*.field == ['email']
    }

    void 'validating an owner that is not multi-tenant without a current tenant leaves its associated objects alone'() {
        given: 'no current tenant'
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)

        when:
        def club = new TenantClub(name: 'North').addToMembers(new TenantMember(name: 'Rex'))
        boolean valid = club.validate()

        then: 'the member gets no tenant id, and its tenant id, which is declared nullable: false, is reported'
        !valid
        club.members*.firmId == [null]
        club.errors.getFieldError('members[0].firmId').code == 'nullable'
    }

    void 'an associated object whose only change is not stored keeps its tenant id'() {
        given: 'an owner and a pet of one tenant, loaded inside withoutId'
        TenantOwner owner = loadWithoutTenant(savedForTenant('beta', 'Rex'))

        when: 'only a property that is not stored is changed, and the owner is validated while another tenant is current'
        def pet = owner.pets.first()
        pet.note = 'seen'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = owner.validate()

        then: 'the pet, which Hibernate does not update for that change, is left alone'
        pet.hasChanged()
        valid
        pet.firmId == 'beta'

        when:
        owner.save(flush: true)

        then: 'and it is stored as it was'
        !owner.hasErrors()
        storedTenantIds() == [Rex: 'beta']
    }

    void 'an associated object with a lastUpdated property is given the current tenant id when only a property that is not stored has changed'() {
        given: 'a nest and a chick of one tenant, loaded inside withoutId'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        Long id = new TenantNest(name: 'North').addToChicks(new TenantChick(name: 'Chirp')).save(flush: true).id
        datastore.sessionFactory.currentSession.clear()
        TenantNest nest = Tenants.withoutId(datastore) {
            def loaded = TenantNest.get(id)
            loaded.chicks.size()
            loaded
        }
        def chick = nest.chicks.first()

        when: 'only a property that is not stored is changed, and the nest is validated while another tenant is current'
        chick.note = 'seen'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = nest.validate()

        then: 'the chick, which Hibernate updates to set its lastUpdated, is given the tenant id its update gives it'
        valid
        chick.firmId == 'acme'

        when:
        nest.save(flush: true)

        then:
        !nest.hasErrors()
        storedTenantIds(TenantChick) == [Chirp: 'acme']
    }

    void 'a new associated object whose insert is pending is given the current tenant id when its owner is validated again'() {
        given: 'a den saved with a new cub, whose insert waits for the flush, as its identifier comes from a sequence'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cub = new TenantCub(name: 'Tom')
        def den = new TenantDen(name: 'North').addToCubs(cub)
        den.save()

        when: 'the den is saved again while a tenant that the cub does not allow is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'gamma')
        def saved = den.save(flush: true)

        then: 'the cub, which is inserted with the current tenant id, is validated with it'
        !saved
        cub.firmId == 'gamma'
        den.errors.getFieldError('cubs[0].firmId').code == 'not.inList'
    }

    void 'a new associated object whose tracked changes were reset is given the current tenant id as it is saved with its owner'() {
        given: 'a new pet that is not in the session and has no tracked changes'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def pet = new TenantPet(name: 'Rex')
        def owner = new TenantOwner(name: 'Dave').addToPets(pet)
        pet.trackChanges()

        when:
        def saved = owner.save(flush: true)

        then: 'the pet, which Hibernate inserts, is validated and stored with the current tenant id'
        saved
        !owner.hasErrors()
        pet.firmId == 'acme'
        storedTenantIds() == [Rex: 'acme']
    }

    void 'a new associated object whose tracked changes were reset is validated with the current tenant id rather than the one it has'() {
        given: 'a new pet that has a tenant id, is not in the session and has no tracked changes'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        def pet = new TenantPet(name: 'Rex', firmId: 'beta')
        def owner = new TenantOwner(name: 'Dave').addToPets(pet)
        pet.trackChanges()

        when:
        boolean valid = owner.validate()

        then: 'the pet is validated with the tenant id it is inserted with'
        !valid
        pet.firmId == 'a-tenant-too-long'
        owner.errors.getFieldError('pets[0].firmId').code == 'maxSize.exceeded'
    }

    void 'a new associated object with an assigned identifier whose tracked changes were reset is given the current tenant id'() {
        given: 'a new item that has an identifier, is not in the session and has no tracked changes'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def item = type.newInstance(name: 'North')
        item.id = 'north'
        def rack = new TenantRack(name: 'Rack')
        rack."addTo${collection}"(item)
        item.trackChanges()

        when:
        def saved = rack.save(flush: true)

        then: 'Hibernate finds that it is not stored yet, and inserts it with the current tenant id'
        saved
        !rack.hasErrors()
        item.firmId == 'acme'
        storedTenantIds(type) == [North: 'acme']

        where:
        type        | collection
        TenantTag   | 'Tags'
        TenantLabel | 'Labels'
    }

    void 'a detached associated object without changes keeps its tenant id'() {
        given: 'an owner and a pet of one tenant, loaded inside withoutId, the pet then detached from the session'
        TenantOwner owner = loadWithoutTenant(savedForTenant('beta', 'Rex'))
        def pet = owner.pets.first()
        pet.discard()

        when: 'the owner is saved while a tenant that breaks the maxSize of the pet is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        def saved = owner.save(flush: true)

        then: 'the pet, which Hibernate does not update, is validated with the tenant id it has, and is stored as it was'
        saved
        pet.firmId == 'beta'
        storedTenantIds() == [Rex: 'beta']
    }

    void 'a detached associated object with an assigned identifier and no version keeps its tenant id'() {
        given: 'a rack and a label of one tenant, loaded inside withoutId, the label then detached from the session'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        def stored = new TenantLabel(name: 'North')
        stored.id = 'north'
        Long id = new TenantRack(name: 'Rack').addToLabels(stored).save(flush: true).id
        datastore.sessionFactory.currentSession.clear()
        TenantRack rack = Tenants.withoutId(datastore) {
            def loaded = TenantRack.get(id)
            loaded.labels.size()
            loaded
        }
        def label = rack.labels.first()
        label.discard()

        when: 'the rack is saved while a tenant that breaks the maxSize of the label is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        def saved = rack.save(flush: true)

        then: 'Hibernate finds the label in the database and does not update it, so it is left alone'
        saved
        label.firmId == 'beta'
        storedTenantIds(TenantLabel) == [North: 'beta']
    }

    void 'an unversioned associated object whose only change is in a collection keeps its tenant id'() {
        given: 'a burrow and a ferret of one tenant, loaded inside withoutId'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        Long id = new TenantBurrow(name: 'North').addToFerrets(new TenantFerret(name: 'Fred', nicknames: ['Freddy'] as Set)).save(flush: true).id
        datastore.sessionFactory.currentSession.clear()
        TenantBurrow burrow = Tenants.withoutId(datastore) {
            def loaded = TenantBurrow.get(id)
            loaded.ferrets*.nicknames*.size()
            loaded
        }
        def ferret = burrow.ferrets.first()

        when: 'a nickname is added, and the burrow is validated while a tenant that breaks the maxSize of the ferret is current'
        ferret.nicknames << 'Fido'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = burrow.validate()

        then: 'the ferret, which has no version for Hibernate to increment, is not updated, and is left alone'
        valid
        ferret.firmId == 'beta'
    }

    void 'an associated object is given the current tenant id only when Hibernate writes it: #type.simpleName with only #property changed'() {
        given: 'a yard and an item of one tenant, loaded inside withoutId'
        TenantYard yard = yardLoadedWithoutTenant(type, collection)
        def item = yard."$collection".first()

        when: 'only the property is changed, and the yard is validated while a tenant that breaks the maxSize of the item is current'
        item."$property" = value
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = yard.validate()

        then: 'the item is validated with the tenant id that is written for it'
        valid == !written
        item.firmId == (written ? 'a-tenant-too-long' : 'beta')
        !written || yard.errors.getFieldError("${collection}[0].firmId").code == 'maxSize.exceeded'

        when: 'the yard is saved while a tenant that fits is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def saved = yard.save(flush: true)

        then:
        saved
        storedTenantIds(type, 'code') == [c1: written ? 'acme' : 'beta']

        where:
        type        | collection | property    | value           || written
        TenantCrate | 'crates'   | 'name'      | 'changed'       || true
        TenantCrate | 'crates'   | 'code'      | 'changed'       || true
        TenantCrate | 'crates'   | 'computed'  | 5               || true
        TenantCrate | 'crates'   | 'nicknames' | ['Fido'] as Set || true
        TenantBox   | 'boxes'    | 'name'      | 'changed'       || true
        TenantBox   | 'boxes'    | 'code'      | 'changed'       || false
        TenantBox   | 'boxes'    | 'computed'  | 5               || false
        TenantBox   | 'boxes'    | 'nicknames' | ['Fido'] as Set || false
        TenantBin   | 'bins'     | 'name'      | 'changed'       || false
        TenantBin   | 'bins'     | 'code'      | 'changed'       || false
        TenantBin   | 'bins'     | 'nicknames' | ['Fido'] as Set || false
        TenantPail  | 'pails'    | 'name'      | 'changed'       || false
        TenantPail  | 'pails'    | 'code'      | 'changed'       || false
    }

    void 'a changed associated object outside the session is given the current tenant id only when the save cascades to it: #type.simpleName, cascade #cascade, detached #detached'() {
        given: 'a farm and an animal of one tenant, loaded inside withoutId'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        def stored = new TenantFarm(name: 'Farm').save(flush: true)
        type.newInstance(name: 'Rex', farm: stored).save(flush: true)
        Long id = stored.id
        datastore.sessionFactory.currentSession.clear()
        TenantFarm farm = Tenants.withoutId(datastore) {
            def loaded = TenantFarm.get(id)
            loaded."$collection".size()
            loaded
        }
        def animal = farm."$collection".first()
        if (detached) {
            animal.discard()
        }

        when: 'the animal is changed, and the farm is validated while a tenant that breaks the maxSize of the animal is current'
        animal.name = 'Max'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = farm.validate()

        then: 'the animal is validated with the tenant id that is written for it'
        valid == !written
        animal.firmId == (written ? 'a-tenant-too-long' : 'beta')

        when: 'the farm is saved while a tenant that fits is current'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def saved = farm.save(flush: true)

        then:
        saved
        storedTenantIds(type) == (written ? [Max: 'acme'] : [Rex: 'beta'])

        where:
        type        | collection | cascade       | detached || written
        TenantCow   | 'cows'     | 'none'        | true     || false
        TenantCow   | 'cows'     | 'none'        | false    || true
        TenantGoat  | 'goats'    | 'save-update' | true     || true
        TenantSheep | 'sheep'    | 'merge'       | true     || false
        TenantSheep | 'sheep'    | 'merge'       | false    || true
        TenantHen   | 'hens'     | 'persist'     | true     || false
        TenantDuck  | 'ducks'    | 'default'     | true     || true
    }

    void 'a changed detached object that is reached through an embedded object is given the current tenant id'() {
        given: 'a house and its mat of one tenant, loaded inside withoutId, the mat then detached from the session'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        def storedMat = new TenantMat(name: 'Rex').save(flush: true)
        Long id = new TenantHouse(name: 'North', porch: new TenantPorch(mat: storedMat)).save(flush: true).id
        datastore.sessionFactory.currentSession.clear()
        TenantHouse house = Tenants.withoutId(datastore) {
            def loaded = TenantHouse.get(id)
            loaded.porch.mat.name
            loaded
        }
        def mat = house.porch.mat
        mat.discard()

        when: 'the mat is changed, and the house is validated'
        mat.name = 'Max'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        boolean valid = house.validate()

        then:
        valid
        mat.firmId == 'acme'
    }

    void 'an associated object of a class with dynamicUpdate that is marked as changed as a whole keeps its tenant id'() {
        given: 'a yard and a bin of one tenant, loaded inside withoutId'
        TenantYard yard = yardLoadedWithoutTenant(TenantBin, 'bins')
        def bin = yard.bins.first()

        when: 'the bin is marked as changed as a whole, and the yard is validated while a tenant that breaks its maxSize is current'
        bin.markDirty()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        boolean valid = yard.validate()

        then: 'a tenant id never counts as changed, so Hibernate does not write it, and the bin is validated with the one it has'
        valid
        bin.firmId == 'beta'

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def saved = yard.save(flush: true)

        then:
        saved
        storedTenantIds(TenantBin, 'code') == [c1: 'beta']
    }

    void 'a new associated object of a class with dynamicUpdate is given the current tenant id when no session is bound'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def bin = new TenantBin(name: 'North', code: 'c1')
        def yard = new TenantYard(name: 'Yard').addToBins(bin)
        Throwable failure = null

        when: 'the yard is saved on a thread without a session'
        Thread.start {
            try {
                yard.save()
            }
            catch (Throwable e) {
                failure = e
            }
        }.join()

        then: 'the bin passes validation with the current tenant id, and the save fails only as it writes'
        failure != null
        !yard.hasErrors()
        bin.firmId == 'acme'
    }

    void 'a new associated object of a class with dynamicUpdate is given the current tenant id'() {
        given: 'a new item, which is inserted whole'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def item = new TenantBin(name: 'North', code: 'c1', firmId: 'beta')
        def yard = new TenantYard(name: 'Yard').addToBins(item)
        if (reset) {
            item.trackChanges()
        }

        when:
        def saved = yard.save(flush: true)

        then:
        saved
        item.firmId == 'acme'
        storedTenantIds(TenantBin, 'code') == [c1: 'acme']

        where:
        reset << [true, false]
    }

    void 'saving an owner without cascading fails as a new associated object is inserted with a tenant id that breaks a constraint declared on it'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')

        when: 'the pet is not validated with the owner, so it is validated as it is inserted, with the current tenant id'
        new TenantOwner(name: 'Dave').addToPets(new TenantPet(name: 'Rex')).save(flush: true, deepValidate: false)

        then: 'that validation fails, and Hibernate does not insert the pet'
        HibernateSystemException e = thrown()
        e.message.contains('vetoed')
    }

    void 'a tenant id mapped to a column of its own is filtered by that column'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        type.newInstance(name: 'Main').save(flush: true, failOnError: true)
        datastore.sessionFactory.currentSession.clear()

        expect:
        type.count() == 1

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')

        then:
        type.count() == 0

        where:
        type << [TenantLedger, TenantJournal]
    }

    void 'a tenant id whose column is configured in mapping alone is not validated, and keeps its column'() {
        when: 'inside withoutId, without a tenant id and with one that is longer than its column'
        def unassigned = new TenantArchive(name: 'Old')
        def tooLong = new TenantArchive(name: 'Older', firmId: 'a-tenant-too-long')
        boolean unassignedValid = Tenants.withoutId(datastore) { unassigned.validate() }
        boolean tooLongValid = Tenants.withoutId(datastore) { tooLong.validate() }

        then: 'neither is checked against the column settings, as they are no constraints'
        unassignedValid
        tooLongValid

        when: 'the one without a tenant id is saved'
        Tenants.withoutId(datastore) { unassigned.save(flush: true) }

        then: 'the column is nullable'
        !unassigned.hasErrors()
        unassigned.id != null

        when: 'the one with a tenant id longer than the column is saved'
        Tenants.withoutId(datastore) { tooLong.save(flush: true) }

        then: 'the column has the length that the mapping configures'
        thrown(DataIntegrityViolationException)
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

    private TenantShelter shelterLoadedWithoutTenant(String tenantId, String storedCity = null, String storedTenantId = null) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
        def cat = new TenantCat(name: 'Tom', home: new TenantHome(city: 'Rome'), nicknames: ['Tom'] as Set)
        Long id = new TenantShelter(name: 'North').addToCats(cat).save(flush: true).id
        if (storedCity) {
            // Stored directly, so that the beforeValidate callback of the home has not seen it yet
            TenantCat.executeUpdate('update TenantCat c set c.home.city = :city', [city: storedCity])
        }
        if (storedTenantId) {
            // Stored directly, as validation does not let a tenant id through that breaks a constraint declared on it
            TenantCat.executeUpdate('update TenantCat c set c.firmId = :tenantId', [tenantId: storedTenantId])
        }
        datastore.sessionFactory.currentSession.clear()
        Tenants.withoutId(datastore) {
            def shelter = TenantShelter.get(id)
            shelter.cats*.chip*.code
            shelter.cats*.nicknames*.size()
            shelter
        }
    }

    private TenantYard yardLoadedWithoutTenant(Class<?> type, String collection) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'beta')
        def yard = new TenantYard(name: 'Yard')
        yard."addTo${collection.capitalize()}"(type.newInstance(name: 'North', code: 'c1'))
        Long id = yard.save(flush: true).id
        datastore.sessionFactory.currentSession.clear()
        Tenants.withoutId(datastore) {
            def loaded = TenantYard.get(id)
            loaded."$collection"*.nicknames*.size()
            loaded
        }
    }

    private Map<String, String> storedTenantIds(Class<?> type = TenantPet, String key = 'name') {
        datastore.sessionFactory.currentSession.clear()
        Tenants.withoutId(datastore) { type.list().collectEntries { [it[key], it.firmId] } }
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
    def note

    static belongsTo = [owner: TenantOwner]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantShelter implements MultiTenant<TenantShelter> {

    String name
    String tenantId

    static hasMany = [cats: TenantCat]

    def beforeValidate() {
        if (!cats) {
            addToCats(new TenantCat(name: 'Stray'))
        }
    }
}

@Entity
class TenantCat implements MultiTenant<TenantCat> {

    String name
    String firmId
    TenantChip chip
    TenantHome home

    static belongsTo = [shelter: TenantShelter]

    static hasMany = [nicknames: String]

    static embedded = ['home']

    static constraints = {
        firmId nullable: false, minSize: 2, maxSize: 5
        chip nullable: true
        home nullable: true
    }

    static mapping = {
        tenantId name: 'firmId'
    }

    def beforeValidate() {
        chip = chip ?: new TenantChip(code: "chip of ${name}")
    }
}

@Entity
class TenantChip implements MultiTenant<TenantChip> {

    String code
    String firmId

    static belongsTo = TenantCat

    static constraints = {
        firmId nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@DirtyCheck
class TenantHome {

    String city

    def beforeValidate() {
        if (city == 'Oslo') {
            setCity('Bergen')
        }
    }
}

@Entity
class TenantSignup {

    String email
    String emailConfirm

    static transients = ['emailConfirm']

    static constraints = {
        email email: true
        emailConfirm nullable: true, validator: { val, obj -> obj.errors.hasFieldErrors('email') ? null : val == obj.email }
    }
}

@Entity
class TenantClub {

    String name

    static hasMany = [members: TenantMember]
}

@Entity
class TenantMember implements MultiTenant<TenantMember> {

    String name
    String firmId

    static constraints = {
        firmId nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantDen implements MultiTenant<TenantDen> {

    String name
    String tenantId

    static hasMany = [cubs: TenantCub]
}

@Entity
class TenantCub implements MultiTenant<TenantCub> {

    String name
    String firmId

    static belongsTo = [den: TenantDen]

    static constraints = {
        firmId nullable: false, inList: ['acme', 'beta']
    }

    static mapping = {
        id generator: 'sequence'
        tenantId name: 'firmId'
    }
}

@Entity
class TenantLedger implements MultiTenant<TenantLedger> {

    String name
    String tenantId

    static mapping = {
        tenantId column: 'ledger_tenant'
    }
}

@Entity
class TenantJournal implements MultiTenant<TenantJournal> {

    String name
    String firmId

    static mapping = {
        tenantId name: 'firmId'
        firmId column: 'journal_firm'
    }
}

@Entity
class TenantArchive implements MultiTenant<TenantArchive> {

    String name
    String firmId

    static mapping = {
        tenantId name: 'firmId'
        firmId nullable: true, maxSize: 5
    }
}

@Entity
class TenantBurrow implements MultiTenant<TenantBurrow> {

    String name
    String tenantId

    static hasMany = [ferrets: TenantFerret]
}

@Entity
class TenantFerret implements MultiTenant<TenantFerret> {

    String name
    String firmId

    static belongsTo = [burrow: TenantBurrow]

    static hasMany = [nicknames: String]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        version false
        tenantId name: 'firmId'
    }
}

@Entity
class TenantNest implements MultiTenant<TenantNest> {

    String name
    String tenantId

    static hasMany = [chicks: TenantChick]
}

@Entity
class TenantChick implements MultiTenant<TenantChick> {

    String name
    String firmId
    Date lastUpdated
    def note

    static belongsTo = [nest: TenantNest]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantLog implements MultiTenant<TenantLog> {

    String name
    String tenantId

    static hasMany = [entries: TenantEntry]

    static constraints = {
        tenantId nullable: true
    }
}

@Entity
class TenantEntry implements MultiTenant<TenantEntry> {

    String text
    String firmId

    static belongsTo = [log: TenantLog]

    static constraints = {
        firmId nullable: true, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantRack implements MultiTenant<TenantRack> {

    String name
    String tenantId

    static hasMany = [tags: TenantTag, labels: TenantLabel]
}

@Entity
class TenantTag implements MultiTenant<TenantTag> {

    String id
    String name
    String firmId

    static belongsTo = [rack: TenantRack]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        id generator: 'assigned'
        tenantId name: 'firmId'
    }
}

@Entity
class TenantLabel implements MultiTenant<TenantLabel> {

    String id
    String name
    String firmId

    static belongsTo = [rack: TenantRack]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        id generator: 'assigned'
        version false
        tenantId name: 'firmId'
    }
}

@Entity
class TenantFarm implements MultiTenant<TenantFarm> {

    String name
    String tenantId

    static hasMany = [cows: TenantCow, goats: TenantGoat, sheep: TenantSheep, hens: TenantHen, ducks: TenantDuck]

    static mapping = {
        cows cascade: 'none'
        goats cascade: 'save-update'
        sheep cascade: 'merge'
        hens cascade: 'persist'
    }
}

@Entity
class TenantCow implements MultiTenant<TenantCow> {

    String name
    String firmId

    static belongsTo = [farm: TenantFarm]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantGoat implements MultiTenant<TenantGoat> {

    String name
    String firmId

    static belongsTo = [farm: TenantFarm]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantSheep implements MultiTenant<TenantSheep> {

    String name
    String firmId

    static belongsTo = [farm: TenantFarm]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantHen implements MultiTenant<TenantHen> {

    String name
    String firmId

    static belongsTo = [farm: TenantFarm]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantDuck implements MultiTenant<TenantDuck> {

    String name
    String firmId

    static belongsTo = [farm: TenantFarm]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class TenantHouse implements MultiTenant<TenantHouse> {

    String name
    String tenantId
    TenantPorch porch

    static embedded = ['porch']
}

@DirtyCheck
class TenantPorch {

    TenantMat mat
}

@Entity
class TenantMat implements MultiTenant<TenantMat> {

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
class TenantYard implements MultiTenant<TenantYard> {

    String name
    String tenantId

    static hasMany = [crates: TenantCrate, boxes: TenantBox, bins: TenantBin, pails: TenantPail]
}

@Entity
class TenantCrate implements MultiTenant<TenantCrate> {

    String name
    String code
    Integer computed
    String firmId

    static belongsTo = [yard: TenantYard]

    static hasMany = [nicknames: String]

    static constraints = {
        firmId nullable: false, maxSize: 5
        computed nullable: true
    }

    static mapping = {
        tenantId name: 'firmId'
        code updateable: false
        computed formula: '1 + 1'
    }
}

@Entity
class TenantBox implements MultiTenant<TenantBox> {

    String name
    String code
    Integer computed
    String firmId

    static belongsTo = [yard: TenantYard]

    static hasMany = [nicknames: String]

    static constraints = {
        firmId nullable: false, maxSize: 5
        computed nullable: true
    }

    static mapping = {
        version false
        tenantId name: 'firmId'
        code updateable: false
        computed formula: '1 + 1'
    }
}

@Entity
class TenantBin implements MultiTenant<TenantBin> {

    String name
    String code
    String firmId

    static belongsTo = [yard: TenantYard]

    static hasMany = [nicknames: String]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        dynamicUpdate true
        tenantId name: 'firmId'
        code updateable: false
    }
}

@Entity
class TenantPail implements MultiTenant<TenantPail> {

    String name
    String code
    String firmId

    static belongsTo = [yard: TenantYard]

    static hasMany = [nicknames: String]

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        dynamicUpdate true
        version false
        tenantId name: 'firmId'
        code updateable: false
    }
}

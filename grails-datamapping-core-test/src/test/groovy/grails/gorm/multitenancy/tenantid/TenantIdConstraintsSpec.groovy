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

import groovy.transform.EqualsAndHashCode

import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.validation.ValidationErrors

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

    void 'the constraints declared on a tenant id named tenantId are checked'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def account = new Account(name: 'Main')
        def saved = Account.withNewSession { account.save(flush: true) }

        then:
        saved
        account.tenantId == 'acme'

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        account = new Account(name: 'Second')
        saved = Account.withNewSession { account.save(flush: true) }

        then:
        !saved
        account.id == null
        account.errors.getFieldError('tenantId').code == 'maxSize.exceeded'
    }

    void 'a new associated object is given the current tenant id before it is validated and saved with its owner'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: 'Rex'))
        def saved = Owner.withNewSession { owner.save(flush: true) }

        then:
        saved
        !owner.hasErrors()
        owner.pets*.firmId == ['acme']
        Pet.withNewSession { Pet.count() } == 1
    }

    void 'validating an owner assigns the current tenant id to each associated object that is validated with it'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when: 'a pet and the tag of that pet are validated with the owner'
        def pet = new Pet(name: 'Rex', tag: new Tag(code: 'T-1'))
        def owner = new Owner(name: 'Dave').addToPets(pet)
        boolean valid = Owner.withNewSession { owner.validate() }

        then:
        valid
        owner.tenantId == 'acme'
        pet.firmId == 'acme'
        pet.tag.firmId == 'acme'
    }

    void 'the current tenant id replaces a tenant id an associated object has before it is validated with its owner'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when: 'the pet has a tenant id that breaks its maxSize, as an insert replaces it too'
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: 'Rex', firmId: 'a-tenant-too-long'))
        def saved = Owner.withNewSession { owner.save(flush: true) }

        then:
        saved
        owner.pets*.firmId == ['acme']
    }

    void 'saving an owner fails when the current tenant id breaks a constraint declared on the tenant id of an associated object'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')

        when:
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: 'Rex'))
        def saved = Owner.withNewSession { owner.save(flush: true) }

        then: 'the owner, which declares nothing on its own tenant id, reports the tenant id of the pet'
        !saved
        owner.id == null
        owner.errors.getFieldError('pets[0].firmId').code == 'maxSize.exceeded'
        Owner.withNewSession { Owner.count() } == 0
        Pet.withNewSession { Pet.count() } == 0
    }

    void 'the associated objects of an owner that is not multi-tenant are given the current tenant id before they are validated'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when: 'a guest in a collection and a guest in a map are validated with the kennel'
        def kennel = new Kennel(name: 'North', guestsByName: [Tom: new Guest(name: 'Tom')]).addToGuests(new Guest(name: 'Rex'))
        boolean valid = Kennel.withNewSession { kennel.validate() }

        then:
        valid
        kennel.guests*.firmId == ['acme']
        kennel.guestsByName.Tom.firmId == 'acme'

        when:
        kennel = new Kennel(name: 'South').addToGuests(new Guest(name: 'Rex'))
        def saved = Kennel.withNewSession { kennel.save(flush: true) }

        then:
        saved
        !kennel.hasErrors()
        kennel.guests*.firmId == ['acme']
        Guest.withNewSession { Guest.count() } == 1
    }

    void 'validating an owner that is not multi-tenant without a current tenant leaves its associated objects alone'() {
        given: 'no current tenant'
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)

        when:
        def kennel = new Kennel(name: 'North').addToGuests(new Guest(name: 'Rex'))
        boolean valid = Kennel.withNewSession { kennel.validate() }

        then: 'the guest gets no tenant id, and its tenant id, which is declared nullable: false, is reported'
        !valid
        kennel.guests*.firmId == [null]
        kennel.errors.getFieldError('guests[0].firmId').code == 'nullable'
    }

    void 'an associated object that two associations refer to is validated once, also when its hashCode includes its tenant id'() {
        given: 'a collar without a colour, whose hashCode includes its tenant id, as the collar and the spare of a hound'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def collar = new Collar()
        def hound = new Hound(name: 'Rex', collar: collar, spare: collar)

        when:
        boolean valid = Hound.withNewSession { hound.validate() }

        then: 'its beforeValidate callback runs once, and its colour is reported once'
        !valid
        collar.firmId == 'acme'
        collar.validations == 1
        hound.errors.fieldErrors*.field.findAll { it.endsWith('colour') }.size() == 1
    }

    void 'an associated object is given the current tenant id when an association that does not cascade validation refers to it too'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when: 'whichever of the two associations comes first, one of the walkers reaches the leash first through the one that does not cascade'
        def leash = new Leash(colour: 'red')
        def walker = walkerType.newInstance(name: 'Dave', borrowed: leash, leash: leash)
        boolean valid = walkerType.withNewSession { walker.validate() }

        then:
        valid
        leash.firmId == 'acme'

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'a-tenant-too-long')
        leash = new Leash(colour: 'red')
        walker = walkerType.newInstance(name: 'Dave', borrowed: leash, leash: leash)
        def saved = walkerType.withNewSession { walker.save(flush: true) }

        then: 'the walker reports the tenant id of the leash through the association that cascades'
        !saved
        walker.errors.getFieldError("${cascading}.firmId").code == 'maxSize.exceeded'
        Leash.withNewSession { Leash.count() } == 0

        where:
        walkerType | cascading
        Walker     | 'leash'
        Jogger     | 'borrowed'
    }

    void 'validating an owner without cascading leaves its associated objects alone'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when:
        def owner = new Owner(name: 'Dave').addToPets(new Pet(name: 'Rex', firmId: 'other')).addToPets(new Pet(name: 'Tom'))
        boolean valid = Owner.withNewSession { owner.validate(deepValidate: false) }

        then: 'only the owner is given the current tenant id'
        valid
        owner.tenantId == 'acme'
        owner.pets.collectEntries { [it.name, it.firmId] } == [Rex: 'other', Tom: null]

        when: 'the pets are not validated with the owner as it is saved either'
        def saved = Owner.withNewSession { owner.save(deepValidate: false, flush: true) }

        then: 'each is given the current tenant id as it is inserted'
        saved
        owner.pets.collectEntries { [it.name, it.firmId] } == [Rex: 'acme', Tom: 'acme']
        Pet.withNewSession { Pet.count() } == 2
    }

    void 'an associated object that has a tenant id and no changes keeps it as its owner is validated'() {
        given: 'two pets of another tenant without changes, as the pets a datastore has loaded'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def rex = new Pet(name: 'Rex', firmId: 'other')
        def tom = new Pet(name: 'Tom', firmId: 'other')
        def owner = new Owner(name: 'Dave').addToPets(rex).addToPets(tom)
        [rex, tom]*.trackChanges()

        when: 'one of them is changed, and the owner is validated'
        tom.name = 'Tim'
        boolean valid = Owner.withNewSession { owner.validate() }

        then: 'the changed pet is given the tenant id its update gives it, and the pet that is not updated is left alone'
        valid
        rex.firmId == 'other'
        tom.firmId == 'acme'

        and: 'the tenant id of the changed pet counts as a change, so that a datastore that writes the changes alone stores it'
        tom.listDirtyPropertyNames() as Set == ['firmId', 'name'] as Set
        tom.getOriginalValue('firmId') == 'other'
        !rex.hasChanged()
    }

    void 'an associated object that is marked as changed as a whole stays so when it is given the current tenant id'() {
        given: 'a pet of another tenant that is marked as changed without a changed property, so that all of it is written'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def pet = new Pet(name: 'Rex', firmId: 'other')
        def owner = new Owner(name: 'Dave').addToPets(pet)
        pet.trackChanges()
        pet.markDirty()

        when:
        boolean valid = Owner.withNewSession { owner.validate() }

        then:
        valid
        pet.firmId == 'acme'
        pet.listDirtyPropertyNames().empty
        pet.hasChanged('name')
    }

    void 'an associated object that has no tenant id is given the current one although it has no changes'() {
        given: 'a pet without changes that has no tenant id yet'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def pet = new Pet(name: 'Rex')
        def owner = new Owner(name: 'Dave').addToPets(pet)
        pet.trackChanges()

        when:
        boolean valid = Owner.withNewSession { owner.validate() }

        then: 'the pet, whose tenant id is declared nullable: false, is validated with the current tenant id'
        valid
        pet.firmId == 'acme'

        and: 'it is still without changes, so validating its owner does not make a datastore update it'
        !pet.hasChanged()
    }

    void 'an associated object that a beforeValidate callback creates as its owner is validated is given the current tenant id'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')

        when: 'the beforeValidate callback of the cat gives it a chip, whose tenant id is declared nullable: false'
        def cat = new Cat(name: 'Tom')
        def shelter = new Shelter(name: 'North').addToCats(cat)
        def saved = Shelter.withNewSession { shelter.save(flush: true) }

        then:
        saved
        !shelter.hasErrors()
        cat.chip.firmId == 'acme'
        Chip.withNewSession { Chip.count() } == 1
    }

    void 'an associated object that its own beforeValidate callback changes is given the current tenant id'() {
        given: 'a cat of another tenant without changes and without a chip, as a cat a datastore has loaded'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cat = new Cat(name: 'Tom', firmId: 'other')
        def shelter = new Shelter(name: 'North').addToCats(cat)
        cat.trackChanges()

        when: 'the shelter is validated, and the beforeValidate callback of the cat gives it a chip'
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the cat, which is updated with its new chip, is given the tenant id its update gives it'
        valid
        cat.firmId == 'acme'
        cat.chip.firmId == 'acme'
    }

    void 'an associated object that the beforeValidate callback of its embedded object changes is given the current tenant id'() {
        given: 'a cat of another tenant without changes, whose basket changes itself as it is validated'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cat = new Cat(name: 'Tom', firmId: 'other', chip: new Chip(code: 'C-1', firmId: 'other'), basket: new Basket(colour: 'fading'))
        def shelter = new Shelter(name: 'North').addToCats(cat)
        [cat, cat.chip, cat.basket]*.trackChanges()

        when:
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the cat, which the callback has run after, is updated, and is given the tenant id its update gives it'
        valid
        cat.basket.colour == 'faded'
        cat.firmId == 'acme'

        and: 'that tenant id is the only change of the cat itself'
        cat.listDirtyPropertyNames() == ['firmId']

        and: 'its chip, which is not updated, is left alone'
        cat.chip.firmId == 'other'
    }

    void 'an associated object that keeps a tenant id that breaks a constraint declared on it fails the validation of its owner'() {
        given: 'a cat without changes whose tenant id breaks its maxSize'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cat = new Cat(name: 'Tom', firmId: 'a-tenant-too-long', chip: new Chip(code: 'C-1', firmId: 'other'), basket: new Basket(colour: 'red'))
        def shelter = new Shelter(name: 'North').addToCats(cat)
        [cat, cat.chip, cat.basket]*.trackChanges()

        when:
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the cat, which is not updated, is validated with the tenant id it has'
        !valid
        cat.firmId == 'a-tenant-too-long'
        shelter.errors.getFieldError('cats[0].firmId').rejectedValue == 'a-tenant-too-long'
    }

    void 'an associated object that a later callback changes is validated with the current tenant id alone'() {
        given: 'a cat without changes whose tenant id breaks its maxSize, and whose basket changes itself as it is validated'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cat = new Cat(name: 'Tom', firmId: 'a-tenant-too-long', chip: new Chip(code: 'C-1', firmId: 'other'), basket: new Basket(colour: 'fading'))
        def shelter = new Shelter(name: 'North').addToCats(cat)
        [cat, cat.chip, cat.basket]*.trackChanges()

        when:
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the cat, which the callback has run after, is updated with the current tenant id'
        cat.basket.colour == 'faded'
        cat.firmId == 'acme'

        and: 'the tenant id it had, which its constraints were checked with first, is not reported'
        valid
        !shelter.hasErrors()
    }

    void 'an associated object whose own associated object changes in its beforeValidate callback is given the current tenant id'() {
        given: 'a cat of another tenant without changes, whose chip changes itself as it is validated'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cat = new Cat(name: 'Tom', firmId: 'other', chip: new Chip(code: 'worn', firmId: 'other'))
        def shelter = new Shelter(name: 'North').addToCats(cat)
        [cat, cat.chip]*.trackChanges()

        when:
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the chip and the cat that owns it, which this datastore updates with it, are given the current tenant id'
        valid
        cat.chip.code == 'renewed'
        cat.chip.firmId == 'acme'
        cat.firmId == 'acme'

        and: 'that tenant id is the only change of the cat itself'
        cat.listDirtyPropertyNames() == ['firmId']
    }

    void 'an associated object of a to-one association keeps its tenant id when its only change is inside an embedded object'() {
        given: 'a horse of another tenant without changes, as a horse a datastore has loaded'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def horse = new Horse(name: 'Bolt', firmId: 'other', stable: new Basket(colour: 'red'))
        def rider = new Rider(name: 'Dave', horse: horse)
        [horse, horse.stable]*.trackChanges()

        when: 'only its embedded stable is changed, and the rider is validated'
        horse.stable.colour = 'blue'
        boolean valid = Rider.withNewSession { rider.validate() }

        then: 'the horse, which a datastore saves through a to-one association only when it has changes of its own, is left alone'
        valid
        horse.firmId == 'other'
        !horse.hasChanged()

        when: 'the horse itself is changed'
        horse.name = 'Storm'
        valid = Rider.withNewSession { rider.validate() }

        then: 'it is given the current tenant id, which is recorded as a change'
        valid
        horse.firmId == 'acme'
        horse.listDirtyPropertyNames() as Set == ['firmId', 'name'] as Set
    }

    void 'a validation that a beforeValidate callback runs with a validator assigns no tenant ids for the validation around it'() {
        given: 'a cat whose beforeValidate callback validates another owner with the validator of that owner'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def other = new Owner(name: 'Other').addToPets(new Pet(name: 'Rex'))
        def otherErrors = new ValidationErrors(other)
        def ownerValidator = datastore.mappingContext.getEntityValidator(datastore.mappingContext.getPersistentEntity(Owner.name))
        def cat = new Cat(name: 'Tom', duringValidation: { ownerValidator.validate(other, otherErrors) })
        def shelter = new Shelter(name: 'North').addToCats(cat)

        when:
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the cat is given the current tenant id'
        valid
        cat.firmId == 'acme'

        and: 'the pet of the other owner is not, and its tenant id, which is declared nullable: false, is reported'
        other.pets*.firmId == [null]
        otherErrors.getFieldError('pets[0].firmId').code == 'nullable'
    }

    void 'an associated object whose only change is inside an embedded object is given the current tenant id'() {
        given: 'a cat of another tenant without changes, as a cat a datastore has loaded'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'acme')
        def cat = new Cat(name: 'Tom', firmId: 'other', chip: new Chip(code: 'C-1', firmId: 'other'), basket: new Basket(colour: 'red'))
        def shelter = new Shelter(name: 'North').addToCats(cat)
        [cat, cat.chip, cat.basket]*.trackChanges()

        when: 'only its embedded basket is changed, and the shelter is validated'
        cat.basket.colour = 'blue'
        boolean valid = Shelter.withNewSession { shelter.validate() }

        then: 'the cat, which is updated, is given the tenant id its update gives it'
        valid
        cat.firmId == 'acme'

        and: 'that tenant id is the only change of the cat itself'
        cat.listDirtyPropertyNames() == ['firmId']

        and: 'its chip, which is not, is left alone'
        cat.chip.firmId == 'other'
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
class Account implements MultiTenant<Account> {

    String name
    String tenantId

    static constraints = {
        tenantId nullable: false, maxSize: 5
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
    Tag tag

    static belongsTo = [owner: Owner]

    static constraints = {
        name nullable: false
        firmId nullable: false, maxSize: 5
        tag nullable: true
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class Tag implements MultiTenant<Tag> {

    String code
    String firmId

    static belongsTo = Pet

    static constraints = {
        firmId nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class Walker implements MultiTenant<Walker> {

    String name
    String tenantId
    Leash borrowed
    Leash leash

    static mapping = {
        borrowed cascadeValidate: 'none'
    }
}

@Entity
class Jogger implements MultiTenant<Jogger> {

    String name
    String tenantId
    Leash borrowed
    Leash leash

    static mapping = {
        leash cascadeValidate: 'none'
    }
}

@Entity
class Leash implements MultiTenant<Leash> {

    String colour
    String firmId

    static belongsTo = [Walker, Jogger]

    static constraints = {
        firmId maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

@Entity
class Shelter implements MultiTenant<Shelter> {

    String name
    String tenantId

    static hasMany = [cats: Cat]
}

@Entity
class Cat implements MultiTenant<Cat> {

    String name
    String firmId
    Chip chip
    Basket basket
    Closure duringValidation

    static belongsTo = [shelter: Shelter]

    static embedded = ['basket']

    static transients = ['duringValidation']

    static constraints = {
        firmId nullable: false, maxSize: 5
        chip nullable: true
        basket nullable: true
    }

    static mapping = {
        tenantId name: 'firmId'
    }

    def beforeValidate() {
        duringValidation?.call()
        chip = chip ?: new Chip(code: "chip of ${name}")
    }
}

@Entity
class Chip implements MultiTenant<Chip> {

    String code
    String firmId

    static belongsTo = Cat

    static constraints = {
        firmId nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }

    def beforeValidate() {
        if (code == 'worn') {
            setCode('renewed')
        }
    }
}

@DirtyCheck
class Basket {

    String colour

    def beforeValidate() {
        if (colour == 'fading') {
            setColour('faded')
        }
    }
}

@Entity
class Kennel {

    String name
    Map<String, Guest> guestsByName

    static hasMany = [guests: Guest, guestsByName: Guest]

    static constraints = {
        guestsByName nullable: true
    }
}

@Entity
class Guest implements MultiTenant<Guest> {

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
class Hound implements MultiTenant<Hound> {

    String name
    String tenantId
    Collar collar
    Collar spare
}

@Entity
@EqualsAndHashCode(includes = ['colour', 'firmId'])
class Collar implements MultiTenant<Collar> {

    String colour
    String firmId
    int validations

    static belongsTo = Hound

    static transients = ['validations']

    static constraints = {
        colour nullable: false
    }

    static mapping = {
        tenantId name: 'firmId'
    }

    def beforeValidate() {
        validations++
    }
}

@Entity
class Rider implements MultiTenant<Rider> {

    String name
    String tenantId
    Horse horse
}

@Entity
class Horse implements MultiTenant<Horse> {

    String name
    String firmId
    Basket stable

    static belongsTo = Rider

    static embedded = ['stable']

    static constraints = {
        firmId maxSize: 5
        stable nullable: true
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

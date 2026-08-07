/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm

import jakarta.persistence.FlushModeType
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.Validator
import spock.lang.Specification

/**
 * {@link GormValidationApiSpec} (pre-existing) covers {@code getValidator}'s caching. This spec
 * targets the remaining gaps: the alternate constructors, {@code forQualifier}/
 * {@code executeQualified}, {@code getTransactionManager}, the full {@code validate(...)} flow
 * (via {@code setValidator} to bypass resolution entirely), field-filtered validation, event
 * firing, and the {@code getErrors}/{@code setErrors}/{@code hasErrors}/{@code clearErrors} pair
 * of code paths (a {@link GormValidateable} instance stores errors on itself; anything else goes
 * through the datastore's current session).
 */
class GormValidationApiCoverageSpec extends Specification {

    void "the Datastore-only constructor derives mappingContext/eventPublisher/hasDatastore from the datastore"() {
        given:
        def mappingContext = Stub(MappingContext)
        def eventPublisher = Stub(ApplicationEventPublisher)
        def ds = Stub(Datastore) {
            getMappingContext() >> mappingContext
            getApplicationEventPublisher() >> eventPublisher
        }

        when:
        def api = new GormValidationApi<Thing>(Thing, ds)

        then:
        api.mappingContext == mappingContext
        api.eventPublisher == eventPublisher
        api.hasDatastore
    }

    void "the MappingContext+DatastoreResolver constructor always reports hasDatastore true with a null eventPublisher"() {
        given:
        def mappingContext = Stub(MappingContext)
        def resolver = new DatastoreResolver() {
            @Override Datastore resolve() { null }
        }

        when:
        def api = new GormValidationApi<Thing>(Thing, mappingContext, resolver)

        then:
        api.mappingContext == mappingContext
        api.eventPublisher == null
        api.hasDatastore
    }

    void "the MappingContext+ApplicationEventPublisher constructor reports hasDatastore false"() {
        given:
        def mappingContext = Stub(MappingContext)
        def eventPublisher = Stub(ApplicationEventPublisher)

        when:
        def api = new GormValidationApi<Thing>(Thing, mappingContext, eventPublisher)

        then:
        api.mappingContext == mappingContext
        api.eventPublisher == eventPublisher
        !api.hasDatastore
    }

    void "forQualifier returns this unchanged when the api has no datastore"() {
        given:
        def api = new GormValidationApi<Thing>(Thing, Stub(MappingContext), Stub(ApplicationEventPublisher))

        expect:
        api.forQualifier('secondary').is(api)
    }

    void "forQualifier builds a new api bound to a resolver for the requested qualifier"() {
        given:
        def registry = new GormRegistry()
        def targetDs = Stub(Datastore)
        registry.datastoresByQualifier.put('secondary', targetDs)
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
        }
        def api = new GormValidationApi<Thing>(Thing, ds, registry)

        when:
        def qualified = api.forQualifier('secondary')

        then:
        !qualified.is(api)
        qualified.hasDatastore

        and: "the resolver built into the qualified api resolves through the registry by qualifier"
        qualified.getDatastore() == targetDs
    }

    void "executeQualified runs directly when no distinct qualified api is registered"() {
        given:
        def session = Stub(Session)
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            connect() >> session
        }
        session.getDatastore() >> ds
        def api = new GormValidationApi<Thing>(Thing, ds, new GormRegistry())

        expect:
        api.executeQualified(org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT, { Session s -> 'ran' }) == 'ran'
    }

    void "executeQualified delegates to the distinct api the registry resolves for a different qualifier"() {
        given: "GormValidationApi#executeQualified calls the STATIC GormRegistry.findValidationApi, which always\n" +
                "resolves against GormRegistry.instance - a fresh, unregistered GormRegistry passed to the\n" +
                "constructor is not consulted, so the singleton must be used and reset around this test"
        def registry = GormRegistry.instance
        registry.reset()
        def session = Stub(Session)
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            connect() >> session
        }
        session.getDatastore() >> ds
        def api = new GormValidationApi<Thing>(Thing, ds, registry)
        registry.validationApiRegistry.register(Thing.name, api)
        // getDirect() only calls qualify()/forQualifier() - producing a genuinely distinct api -
        // when the qualifier resolves to a DIFFERENT datastore than the default api's own.
        def secondaryDs = Stub(Datastore) { getMappingContext() >> Stub(MappingContext) }
        registry.registerEntityDatastore(Thing.name, 'secondary', secondaryDs)

        when: "findValidationApi('secondary') routes through forQualifier and returns a distinct instance"
        def result = api.executeQualified('secondary', { Session s -> 'ran-qualified' })

        then:
        result == 'ran-qualified'

        cleanup:
        registry.reset()
    }

    void "getTransactionManager returns the transaction manager for a transaction-capable datastore and null otherwise"() {
        given:
        def txManager = Stub(PlatformTransactionManager)
        def capableDs = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def plainDs = Stub(Datastore)

        expect:
        new GormValidationApi<Thing>(Thing, capableDs).getTransactionManager() == txManager
        new GormValidationApi<Thing>(Thing, plainDs).getTransactionManager() == null
    }

    void "getValidator prefers a ValidatorProvider persistent entity over the mapping context lookup"() {
        given:
        def providedValidator = Stub(Validator)
        def persistentEntity = Mock(additionalInterfaces: [org.grails.datastore.gorm.validation.ValidatorProvider], PersistentEntity) {
            getValidator() >> providedValidator
        }
        def mappingContext = Mock(MappingContext) {
            getPersistentEntity(Thing.name) >> persistentEntity
        }
        def ds = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        def api = new GormValidationApi<Thing>(Thing, ds)

        when:
        def result = api.getValidator()

        then:
        result == providedValidator
        0 * mappingContext.getEntityValidator(_)
    }

    void "setValidator makes getValidator return the set validator directly, bypassing resolution"() {
        given:
        def validator = Stub(Validator)
        def api = new GormValidationApi<Thing>(Thing, Stub(MappingContext), Stub(ApplicationEventPublisher))

        when:
        api.setValidator(validator)

        then:
        api.getValidator() == validator
    }

    void "validate(instance) returns true without calling the validator when none can be resolved"() {
        given: "createValidationEvent needs a non-null getDatastore() - Spring's ApplicationEvent rejects a null source"
        def ds = Stub(Datastore) { getMappingContext() >> Stub(MappingContext) }
        def api = new GormValidationApi<Thing>(Thing, ds)
        def instance = new Thing(name: 'a')

        expect:
        api.validate(instance)
    }

    void "validate(instance) delegates to a plain Validator and reflects whether it added errors"() {
        given:
        def eventPublisher = Mock(ApplicationEventPublisher)
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            getApplicationEventPublisher() >> eventPublisher
        }
        def api = new GormValidationApi<Thing>(Thing, ds)
        def validator = Mock(Validator)
        api.setValidator(validator)
        def instance = new Thing(name: 'a')

        when:
        boolean valid = api.validate(instance)

        then:
        1 * validator.validate(instance, _ as Errors)
        1 * eventPublisher.publishEvent(_)
        valid

        when: "the validator rejects the instance"
        boolean invalid = api.validate(instance)

        then:
        1 * validator.validate(instance, _ as Errors) >> { Object obj, Errors errors -> errors.reject('bad') }
        !invalid
    }

    void "validate(instance) delegates to a CascadingValidator with the resolved deepValidate argument"() {
        given:
        def ds = Stub(Datastore) { getMappingContext() >> Stub(MappingContext) }
        def api = new GormValidationApi<Thing>(Thing, ds)
        def validator = Mock(grails.gorm.validation.CascadingValidator)
        api.setValidator(validator)
        def instance = new Thing(name: 'a')

        when:
        api.validate(instance, [deepValidate: false])

        then:
        1 * validator.validate(instance, _ as Errors, false)
    }

    void "validate(instance, fields) filters the resulting errors to only the validated fields"() {
        given:
        def ds = Stub(Datastore) { getMappingContext() >> Stub(MappingContext) }
        def api = new GormValidationApi<Thing>(Thing, ds)
        def validator = Mock(Validator)
        validator.validate(_, _) >> { Object obj, Errors errors ->
            errors.rejectValue('name', 'bad.name')
            errors.rejectValue('other', 'bad.other')
        }
        api.setValidator(validator)
        def instance = new Thing(name: 'a')

        when:
        api.validate(instance, ['name'])

        then:
        def errors = api.getErrors(instance)
        errors.hasFieldErrors('name')
        !errors.hasFieldErrors('other')
    }

    void "validate temporarily switches an active session to COMMIT flush mode and restores it afterwards"() {
        given:
        def session = Mock(Session) {
            getFlushMode() >> FlushModeType.AUTO
        }
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            hasCurrentSession() >> true
            getCurrentSession() >> session
        }
        def api = new GormValidationApi<Thing>(Thing, ds)
        api.setValidator(Stub(Validator))
        def instance = new Thing(name: 'a')

        when:
        api.validate(instance)

        then:
        1 * session.setFlushMode(FlushModeType.COMMIT)
        1 * session.setFlushMode(FlushModeType.AUTO)
    }

    void "validate swallows an IllegalStateException from a disconnected session while checking the flush mode"() {
        given:
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            hasCurrentSession() >> true
            getCurrentSession() >> { throw new IllegalStateException('disconnected') }
        }
        def api = new GormValidationApi<Thing>(Thing, ds)
        api.setValidator(Stub(Validator))
        def instance = new Thing(name: 'a')

        expect:
        api.validate(instance)
    }

    void "fireEvent falls back to the datastore's application event publisher when none was set at construction"() {
        given: "the DatastoreResolver constructor leaves the eventPublisher field null (unlike the Datastore-only constructor)"
        def eventPublisher = Mock(ApplicationEventPublisher)
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            getApplicationEventPublisher() >> eventPublisher
        }
        def resolver = new DatastoreResolver() {
            @Override Datastore resolve() { ds }
        }
        def api = new GormValidationApi<Thing>(Thing, Stub(MappingContext), resolver)
        api.setValidator(Stub(Validator))
        def instance = new Thing(name: 'a')

        expect:
        api.eventPublisher == null

        when:
        api.validate(instance)

        then:
        1 * eventPublisher.publishEvent(_)
    }

    void "getErrors/setErrors/hasErrors/clearErrors operate directly on a GormValidateable instance"() {
        given:
        def api = new GormValidationApi<Thing>(Thing, Stub(MappingContext), Stub(ApplicationEventPublisher))
        def instance = new Thing(name: 'a')

        expect: "a fresh instance has no errors and getErrors() lazily initializes them"
        !api.hasErrors(instance)
        api.getErrors(instance) != null

        when:
        Errors errors = api.getErrors(instance)
        errors.reject('bad')
        api.setErrors(instance, errors)

        then:
        api.hasErrors(instance)

        when:
        api.clearErrors(instance)

        then:
        !api.hasErrors(instance)
    }

    void "getErrors/setErrors/hasErrors store on the current session's attributes for a non-GormValidateable instance with an active session"() {
        given:
        def attributes = [:]
        def session = Stub(Session) {
            getAttribute(_, _) >> { Object target, String name -> attributes[name] }
            setAttribute(_, _, _) >> { Object target, String name, Object value -> attributes[name] = value }
        }
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            hasCurrentSession() >> true
            getCurrentSession() >> session
        }
        def api = new GormValidationApi<PlainThing>(PlainThing, ds)
        def instance = new PlainThing(name: 'a')

        expect:
        !api.hasErrors(instance)

        when:
        Errors errors = api.getErrors(instance)
        errors.reject('bad')
        api.setErrors(instance, errors)

        then:
        api.hasErrors(instance)
    }

    void "getErrors/hasErrors return fresh/empty results for a non-GormValidateable instance with no active session"() {
        given:
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            hasCurrentSession() >> false
        }
        def api = new GormValidationApi<PlainThing>(PlainThing, ds)
        def instance = new PlainThing(name: 'a')

        expect:
        !api.hasErrors(instance)
        api.getErrors(instance) != null
    }

    void "getErrors/hasErrors swallow an IllegalStateException from a disconnected session"() {
        given:
        def ds = Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext)
            hasCurrentSession() >> true
            getCurrentSession() >> { throw new IllegalStateException('disconnected') }
        }
        def api = new GormValidationApi<PlainThing>(PlainThing, ds)
        def instance = new PlainThing(name: 'a')

        expect:
        api.getErrors(instance) != null
        !api.hasErrors(instance)
    }

    static class Thing implements GormValidateable {
        String name
        String other
    }

    static class PlainThing {
        String name
    }
}

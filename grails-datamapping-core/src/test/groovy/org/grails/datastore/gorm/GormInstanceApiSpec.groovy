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

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.gorm.schemaless.DynamicAttributes
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.AutoCleanup
import spock.lang.Specification

class GormInstanceApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    void setup() {
        GormRegistry.instance.reset()
        datastore = new SimpleMapDatastore(GormInstanceApiThing, GormInstanceApiInvalidThing)
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "the Datastore-only and Datastore+registry constructors default failOnError false and markDirty true"() {
        when:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)

        then:
        !api.failOnError
        api.markDirty
        api.registry != null

        when:
        def registry = new GormRegistry()
        def apiWithRegistry = new GormInstanceApi(GormInstanceApiThing, datastore, registry)

        then:
        apiWithRegistry.registry.is(registry)
    }

    void "the MappingContext+DatastoreResolver constructors default failOnError false and markDirty true"() {
        given:
        def resolver = new DatastoreResolver() {
            @Override Datastore resolve() { datastore }
        }

        when:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore.mappingContext, resolver)

        then:
        !api.failOnError
        api.markDirty
        api.registry != null

        when:
        def registry = new GormRegistry()
        def apiWithRegistry = new GormInstanceApi(GormInstanceApiThing, datastore.mappingContext, resolver, registry)

        then:
        apiWithRegistry.registry.is(registry)
    }

    void "getTransactionManager returns the transaction manager for a transaction-capable datastore and null otherwise"() {
        given:
        def txManager = Stub(org.springframework.transaction.PlatformTransactionManager)
        def capableDs = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def plainDs = Stub(Datastore)

        expect:
        new GormInstanceApi(GormInstanceApiThing, capableDs).getTransactionManager() == txManager
        new GormInstanceApi(GormInstanceApiThing, plainDs).getTransactionManager() == null
    }

    void "executeQualified runs directly when no distinct qualified api is registered"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)

        expect:
        api.executeQualified(ConnectionSource.DEFAULT, { session -> 'ran' }) == 'ran'
    }

    void "forQualifier builds a new api for the requested qualifier, preserving failOnError and markDirty"() {
        given:
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormInstanceApiThing.name, ConnectionSource.DEFAULT, datastore)
        def api = new GormInstanceApi(GormInstanceApiThing, datastore, registry)
        api.failOnError = true
        api.markDirty = false

        when:
        def qualified = api.forQualifier(ConnectionSource.DEFAULT)

        then:
        !qualified.is(api)
        qualified.failOnError
        !qualified.markDirty
    }

    void "propertyMissing delegates to a connection-source-specific instance api when the datastore exposes that qualifier"() {
        given:
        def registry = new GormRegistry()
        def connectionSources = Stub(ConnectionSources) {
            getConnectionSource('secondary') >> Stub(ConnectionSource)
        }
        def ds = Stub(MultipleConnectionSourceDatastoreForTest) {
            getMappingContext() >> datastore.mappingContext
            getConnectionSources() >> connectionSources
        }
        registry.registerEntityDatastore(GormInstanceApiThing.name, ConnectionSource.DEFAULT, ds)
        def api = new GormInstanceApi(GormInstanceApiThing, ds, registry)
        def instance = new GormInstanceApiThing(name: 'a')

        when:
        def result = api.propertyMissing(instance, 'secondary')

        then:
        result instanceof DelegatingGormEntityApi
    }

    void "propertyMissing falls back to DynamicAttributes.getAt when the datastore does not expose the qualifier"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def instance = new DynamicAttributesThing()
        instance['foo'] = 'bar'

        expect:
        api.propertyMissing(instance, 'foo') == 'bar'
    }

    void "propertyMissing throws MissingPropertyException when nothing resolves the name"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def instance = new GormInstanceApiThing(name: 'a')

        when:
        api.propertyMissing(instance, 'doesNotExist')

        then:
        thrown(MissingPropertyException)
    }

    void "instanceOf returns false for a null instance, unwraps an EntityProxy target, and checks a plain instance directly"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def target = new GormInstanceApiThing(name: 'a')
        def proxy = Stub(EntityProxy) {
            getTarget() >> target
        }

        expect:
        !api.instanceOf(null, GormInstanceApiThing)
        api.instanceOf(proxy, GormInstanceApiThing)
        api.instanceOf(target, GormInstanceApiThing)
        !api.instanceOf(target, String)
    }

    void "refresh re-reads the instance's persisted state via the session"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def saved = new GormInstanceApiThing(name: 'a').save(flush: true)

        expect:
        api.refresh(saved).is(saved)
    }

    void "read resolves a persisted instance by id"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def saved = new GormInstanceApiThing(name: 'a').save(flush: true)

        expect:
        api.read(saved.id) != null
    }

    void "merge delegates to save with and without arguments"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def instance = new GormInstanceApiThing(name: 'a')

        expect:
        api.merge(instance) != null
        api.merge(instance, [flush: true]) != null
    }

    void "save(instance) and save(instance, boolean) delegate to the Map overload"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)

        expect:
        api.save(new GormInstanceApiThing(name: 'a')) != null
        api.save(new GormInstanceApiThing(name: 'b'), true) != null
    }

    void "save validates by default and returns null without throwing when validation fails and failOnError is unset"() {
        given: "static constraints DSL blocks aren't evaluated into a real Validator outside a Grails app, so a rejecting Validator is injected directly at the resolution point save() uses"
        rejectAllValidationFor(GormInstanceApiInvalidThing)
        def api = new GormInstanceApi(GormInstanceApiInvalidThing, datastore)
        def invalid = new GormInstanceApiInvalidThing()

        expect:
        api.save(invalid) == null
    }

    void "save throws when validation fails and failOnError is requested"() {
        // ValidationException.newInstance(...) is itself a static factory method that always
        // constructs its own dynamically-resolved VALIDATION_EXCEPTION_TYPE, ignoring whatever Class
        // GormInstanceApi.validationException holds as its receiver - so the type actually thrown
        // here is grails.validation.ValidationException, unrelated to the core base class.
        given:
        rejectAllValidationFor(GormInstanceApiInvalidThing)
        def api = new GormInstanceApi(GormInstanceApiInvalidThing, datastore)
        api.failOnError = true
        def invalid = new GormInstanceApiInvalidThing()

        when:
        api.save(invalid)

        then:
        thrown(grails.validation.ValidationException)
    }

    void "save(validate: false) skips validation and temporarily marks the instance to skip its own validation"() {
        given:
        rejectAllValidationFor(GormInstanceApiInvalidThing)
        def api = new GormInstanceApi(GormInstanceApiInvalidThing, datastore)
        def invalid = new GormInstanceApiInvalidThing()

        expect: "an otherwise-invalid instance still saves because validation was explicitly skipped"
        api.save(invalid, [validate: false]) != null
        !invalid.shouldSkipValidation()
    }

    private void rejectAllValidationFor(Class entityClass) {
        GormRegistry.instance.getValidationApi(entityClass).setValidator(Stub(Validator) {
            validate(_, _) >> { Object obj, Errors errors -> errors.reject('always.invalid') }
        })
    }

    void "save marks a DirtyCheckable instance dirty before persisting when markDirty is enabled"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def saved = new GormInstanceApiThing(name: 'a').save(flush: true)
        saved.trackChanges()

        expect: "no tracked property changed, yet the explicit save still persists because markDirty forces it"
        !saved.hasChanged()
        api.save(saved, [flush: true]) != null
    }

    void "save(flush: true) flushes the session"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def instance = new GormInstanceApiThing(name: 'a')

        when:
        api.save(instance, [flush: true])

        then:
        api.read(instance.id) != null
    }

    void "insert persists a new instance and insert(Map) honors the flush argument"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)

        expect:
        api.insert(new GormInstanceApiThing(name: 'a')) != null
        api.insert(new GormInstanceApiThing(name: 'b'), [flush: true]) != null
    }

    void "delete removes the instance and delete(Map) honors the flush argument"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def saved = new GormInstanceApiThing(name: 'a').save(flush: true)

        when:
        api.delete(saved, [flush: true])

        then:
        api.read(saved.id) == null

        when:
        def another = new GormInstanceApiThing(name: 'b').save(flush: true)
        api.delete(another)

        then:
        notThrown(Exception)
    }

    void "ident reads the instance's id property"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def saved = new GormInstanceApiThing(name: 'a').save(flush: true)

        expect:
        api.ident(saved) == saved.id
    }

    void "isAttached reports true for an instance persisted within the current session"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)

        expect:
        datastore.withSession { session ->
            def instance = new GormInstanceApiThing(name: 'a')
            session.persist(instance)
            api.isAttached(instance) && !api.isAttached(new GormInstanceApiThing(name: 'never-persisted'))
        }
    }

    void "discard detaches a previously-attached instance from the current session"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)

        expect:
        datastore.withSession { session ->
            def instance = new GormInstanceApiThing(name: 'a')
            session.persist(instance)
            boolean attachedBeforeDiscard = api.isAttached(instance)
            api.discard(instance)
            attachedBeforeDiscard && !api.isAttached(instance)
        }
    }

    void "attach re-associates a detached instance with the current session"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def saved = new GormInstanceApiThing(name: 'a').save(flush: true)

        expect:
        datastore.withSession { session ->
            api.attach(saved).is(saved) && api.isAttached(saved)
        }
    }

    void "isDirty/getDirtyPropertyNames/getPersistentValue report false/empty/null for a non-DirtyCheckable instance"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def instance = new NonDirtyCheckableThing()

        expect:
        !api.isDirty(instance)
        !api.isDirty(instance, 'name')
        api.getDirtyPropertyNames(instance).isEmpty()
        api.getPersistentValue(instance, 'name') == null
    }

    void "isDirty/getDirtyPropertyNames/getPersistentValue reflect real change tracking for a DirtyCheckable instance"() {
        given:
        def api = new GormInstanceApi(GormInstanceApiThing, datastore)
        def instance = new GormInstanceApiThing(name: 'original')
        instance.trackChanges()
        instance.name = 'changed'

        expect:
        api.isDirty(instance)
        api.isDirty(instance, 'name')
        !api.isDirty(instance, 'unrelatedField')
        api.getDirtyPropertyNames(instance) == ['name']
        api.getPersistentValue(instance, 'name') == 'original'
    }

    interface MultipleConnectionSourceDatastoreForTest extends Datastore, ConnectionSourcesProvider {}
}

@Entity
class GormInstanceApiThing implements GormValidateable, DirtyCheckable {
    Long id
    String name
}

@Entity
class GormInstanceApiInvalidThing implements GormValidateable {
    Long id
}

class DynamicAttributesThing implements DynamicAttributes {
}

class NonDirtyCheckableThing {
    String name
}

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

import jakarta.persistence.LockModeType

import grails.gorm.annotation.Entity
import grails.gorm.api.GormInstanceOperations
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.gorm.internal.RefreshLockArguments
import org.grails.datastore.gorm.schemaless.DynamicAttributes
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class GormInstanceApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    @AutoCleanup
    SimpleMapDatastore connectionDatastore

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

    void "refresh(lock: true) rejects an unsupported datastore without changing the entity"() {
        given:
        def saved = new GormInstanceApiThing(name: 'persisted').save(flush: true)
        saved.name = 'local change'

        when:
        saved.refresh(lock: true)

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == 'Datastore implementation does not support refreshing under a lock'
        saved.name == 'local change'
    }

    void "named connection refresh(lock: true) rejects an unsupported datastore"() {
        given:
        connectionDatastore = new SimpleMapDatastore(['secondary'], GormLockConnectionThing)
        def instance = new GormLockConnectionThing(name: 'local change')

        when:
        instance.secondary.refresh(lock: true)

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == 'Datastore implementation does not support refreshing under a lock'
        instance.name == 'local change'
    }

    void "refresh(lock: #description) rejects an unsupported datastore like refresh(lock: true)"() {
        given:
        def saved = new GormInstanceApiThing(name: 'persisted').save(flush: true)
        saved.name = 'local change'

        when:
        saved.refresh(lock: lock)

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == 'Datastore implementation does not support refreshing under a lock'
        saved.name == 'local change'

        where:
        description               | lock
        'PESSIMISTIC_READ'        | LockModeType.PESSIMISTIC_READ
        'OPTIMISTIC'              | LockModeType.OPTIMISTIC
        "'PESSIMISTIC_WRITE'"     | 'PESSIMISTIC_WRITE'
        "'true'"                  | 'true'
    }

    void "refresh rejects a lock argument that is neither a boolean nor a lock mode (#description)"() {
        given:
        def saved = new GormInstanceApiThing(name: 'persisted').save(flush: true)
        saved.name = 'local change'

        when:
        saved.refresh(lock: lock)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "The 'lock' argument must be a boolean or a jakarta.persistence.LockModeType but was ${actual}"
        saved.name == 'local change'

        where:
        description      | lock          | actual
        'unknown name'   | 'SHARED'      | "'SHARED'"
        'number'         | 1             | 'an instance of java.lang.Integer'
    }

    void "a direct implementation of the entity api inherits refresh(Map) (#description)"() {
        given:
        def api = new DirectEntityApi()

        when:
        def result = api.refresh(args)

        then:
        result.is(api)
        api.refreshInvocations == 1

        when:
        api.refresh(lock: true)

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == 'Datastore implementation does not support refreshing under a lock'
        api.refreshInvocations == 1

        where:
        description   | args
        'empty map'   | [:]
        'null map'    | null
        'lock: false' | [lock: false]
        'lock: NONE'  | [lock: LockModeType.NONE]
    }

    void "refresh with arguments that do not request a lock performs a plain refresh (#description)"() {
        given:
        def recording = new RecordingGormInstanceApi<GormInstanceApiThing>(GormInstanceApiThing, datastore)
        registerInstanceApi(recording)
        def instance = new GormInstanceApiThing(name: 'local change')

        when:
        def result = instance.refresh(args)

        then:
        recording.refreshInvocations == 1
        recording.refreshedInstance.is(instance)
        result.is(instance)

        where:
        description       | args
        'empty map'       | [:]
        'null map'        | null
        'lock: false'     | [lock: false]
        "lock: 'false'"   | [lock: 'false']
        'lock: NONE'      | [lock: LockModeType.NONE]
        'other arguments' | [flush: true]
    }

    void "refresh(lock: true) passes the entity and arguments to the instance api and returns its result"() {
        given:
        def locking = new LockingGormInstanceApi<GormInstanceApiThing>(GormInstanceApiThing, datastore)
        def reloaded = new GormInstanceApiThing(name: 'reloaded')
        locking.refreshResult = reloaded
        registerInstanceApi(locking)
        def instance = new GormInstanceApiThing(name: 'local change')

        when:
        def result = instance.refresh(lock: true)

        then:
        locking.refreshInvocations == 1
        locking.refreshedInstance.is(instance)
        locking.refreshArguments == [lock: true]
        result.is(reloaded)
    }

    void "mutex reloads under the lock when the datastore supports it, and runs the closure after"() {
        given:
        def locking = new LockingGormInstanceApi<GormInstanceApiThing>(GormInstanceApiThing, datastore)
        locking.refreshResult = new GormInstanceApiThing(name: 'reloaded')
        registerInstanceApi(locking)
        def instance = new GormInstanceApiThing(name: 'local change')
        def ran = []

        when:
        def result = instance.mutex { ran << 'closure'; 'outcome' }

        then: 'the row is reloaded under an exclusive lock rather than version-checked as loaded'
        locking.refreshInvocations == 1
        locking.refreshedInstance.is(instance)
        locking.refreshArguments == [lock: true]

        and: 'the closure runs while the lock is held, and its result is returned'
        ran == ['closure']
        result == 'outcome'
    }

    void "mutex still takes the datastore's own lock when it cannot reload under one"() {
        given: 'the stock instance api, which keeps the datastore-neutral default of refresh(D, Map)'
        def api = new GormInstanceApi<GormInstanceApiThing>(GormInstanceApiThing, datastore)
        registerInstanceApi(api)
        def instance = new GormInstanceApiThing(name: 'unsupported').save(flush: true)

        expect:
        !api.supportsLockedRefresh()

        when:
        instance.mutex { 'outcome' }

        then: 'the failure is the one this datastore has always reported from session.lock, not a new one about reloading'
        def exception = thrown(UnsupportedOperationException)
        exception.message.contains('does not support locking')
        exception.message != RefreshLockArguments.UNSUPPORTED
    }

    void "the delegating entity api passes its target and arguments to the instance api and returns its result"() {
        given:
        def locking = new LockingGormInstanceApi<GormInstanceApiThing>(GormInstanceApiThing, datastore)
        def reloaded = new GormInstanceApiThing(name: 'reloaded')
        locking.refreshResult = reloaded
        def target = new GormInstanceApiThing(name: 'local change')
        def delegating = new DelegatingGormEntityApi<GormInstanceApiThing>(locking, target)

        when:
        def result = delegating.refresh([lock: true])

        then:
        locking.refreshInvocations == 1
        locking.refreshedInstance.is(target)
        locking.refreshArguments == [lock: true]
        result.is(reloaded)
    }

    void "the default refresh(instance, args) of the instance operations contract splits on the lock argument"() {
        given: 'an implementation that provides nothing beyond the interface defaults and records what they call'
        def calls = []
        GormInstanceOperations<Object> operations = (GormInstanceOperations<Object>) Proxy.newProxyInstance(
                GormInstanceOperations.classLoader, [GormInstanceOperations] as Class[],
                { Object proxy, Method method, Object[] methodArgs ->
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, methodArgs)
                    }
                    calls << [method.name, methodArgs as List]
                    return methodArgs ? methodArgs[0] : null
                } as InvocationHandler)
        def instance = new Object()

        when:
        def result = operations.refresh(instance, [flush: true])

        then:
        calls == [['refresh', [instance]]]
        result.is(instance)

        when:
        operations.refresh(instance, [lock: true])

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == 'Datastore implementation does not support refreshing under a lock'
        calls.size() == 1
    }

    private void registerInstanceApi(GormInstanceApi<GormInstanceApiThing> instanceApi) {
        GormRegistry.instance.registerEntityApis(GormInstanceApiThing,
                new GormStaticApi<GormInstanceApiThing>(GormInstanceApiThing, datastore, []),
                instanceApi,
                new GormValidationApi<GormInstanceApiThing>(GormInstanceApiThing, datastore))
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

@Entity
class GormLockConnectionThing {
    String name

    static mapping = {
        datasource 'ALL'
    }
}

/**
 * An instance api for a datastore without locked-refresh support. Only the plain {@code refresh(instance)}
 * is recorded, so the inherited default {@code refresh(instance, args)} can be observed delegating to it.
 */
class RecordingGormInstanceApi<D> extends GormInstanceApi<D> {

    int refreshInvocations
    D refreshedInstance

    RecordingGormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
    }

    @Override
    D refresh(D instance) {
        refreshInvocations++
        refreshedInstance = instance
        return instance
    }
}

/**
 * An instance api for a datastore that supports refreshing under a lock. It records the instance and
 * arguments passed to {@code refresh(instance, args)}, so that delegation from the entity trait, the
 * delegating entity api and the static api can be verified.
 */
class LockingGormInstanceApi<D> extends GormInstanceApi<D> {

    int refreshInvocations
    D refreshedInstance
    Map refreshArguments
    D refreshResult

    LockingGormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
    }

    @Override
    boolean supportsLockedRefresh() {
        true
    }

    @Override
    D refresh(D instance, Map args) {
        refreshInvocations++
        refreshedInstance = instance
        refreshArguments = args
        return refreshResult
    }
}

/**
 * Implements the entity api directly rather than through GormEntity, as a third-party trait consumer might.
 * Only refresh() is meaningful; the remaining abstract methods are stubs the test never calls.
 */
class DirectEntityApi implements GormEntityApi<DirectEntityApi> {
    int refreshInvocations

    @Override
    DirectEntityApi refresh() {
        refreshInvocations++
        return this
    }

    @Override
    boolean instanceOf(Class cls) { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi lock() { throw new UnsupportedOperationException() }

    @Override
    def mutex(Closure callable) { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi save() { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi insert() { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi insert(Map params) { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi merge() { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi merge(Map params) { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi save(boolean validate) { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi save(Map params) { throw new UnsupportedOperationException() }

    @Override
    Serializable ident() { throw new UnsupportedOperationException() }

    @Override
    DirectEntityApi attach() { throw new UnsupportedOperationException() }

    @Override
    boolean isAttached() { throw new UnsupportedOperationException() }

    @Override
    void discard() { throw new UnsupportedOperationException() }

    @Override
    void delete() { throw new UnsupportedOperationException() }

    @Override
    void delete(Map params) { throw new UnsupportedOperationException() }

    @Override
    boolean isDirty(String fieldName) { throw new UnsupportedOperationException() }

    @Override
    boolean isDirty() { throw new UnsupportedOperationException() }
}

/**
 * Overrides refresh(D, Map) for arguments of its own without supporting a lock, as a datastore that
 * honours something like refresh(flush: true) would.
 */
class UnlockableRefreshingGormInstanceApi<D> extends GormInstanceApi<D> {

    int refreshInvocations

    UnlockableRefreshingGormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
    }

    @Override
    D refresh(D instance, Map args) {
        refreshInvocations++
        return instance
    }
}

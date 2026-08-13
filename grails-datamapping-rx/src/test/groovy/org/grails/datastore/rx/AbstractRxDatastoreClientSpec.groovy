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
package org.grails.datastore.rx

import grails.gorm.rx.proxy.ObservableProxy
import groovy.lang.Delegate
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.IdentityGenerationException
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.batch.BatchOperation
import org.grails.datastore.rx.query.QueryState
import org.grails.datastore.rx.query.RxQuery
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import org.grails.gorm.rx.api.RxGormValidationApi
import rx.Observable
import spock.lang.Specification

class AbstractRxDatastoreClientSpec extends Specification {

    MappingContext mappingContext
    ProxyHandler proxyHandler
    ConnectionSourceSettings connectionSourceSettings
    ConnectionSources connectionSources
    RecordingRxDatastoreClient client

    void setup() {
        proxyHandler = Mock(ProxyHandler)
        mappingContext = Mock(MappingContext) {
            getProxyHandler() >> proxyHandler
            getPersistentEntities() >> []
        }
        connectionSourceSettings = new ConnectionSourceSettings()
        ConnectionSource connectionSource = Stub(ConnectionSource) {
            getName() >> ConnectionSource.DEFAULT
            getSettings() >> connectionSourceSettings
        }
        connectionSources = Stub(ConnectionSources) {
            getDefaultConnectionSource() >> connectionSource
            getAllConnectionSources() >> [connectionSource]
        }
        client = new RecordingRxDatastoreClient(connectionSources, mappingContext)
    }

    void cleanup() {
        RxGormEnhancer.close()
    }

    private PersistentEntity stubEntity(String name, Class javaClass, ValueGenerator generator = ValueGenerator.NATIVE, List associations = []) {
        IdentityMapping identityMapping = Stub(IdentityMapping) {
            getGenerator() >> generator
        }
        ClassMapping classMapping = Stub(ClassMapping) {
            getIdentifier() >> identityMapping
            getMappedForm() >> null
        }
        Stub(PersistentEntity) {
            getName() >> name
            getJavaClass() >> javaClass
            getMapping() >> classMapping
            getAssociations() >> associations
        }
    }

    private ToOne toOneAssociation(String name, PersistentEntity associatedEntity, boolean cascades = true) {
        Stub(ToOne) {
            getName() >> name
            getAssociatedEntity() >> associatedEntity
            doesCascade(_) >> cascades
            isEmbedded() >> false
            isBasic() >> false
        }
    }

    private ToMany toManyAssociation(String name, PersistentEntity associatedEntity, boolean cascades = true) {
        Stub(ToMany) {
            getName() >> name
            getAssociatedEntity() >> associatedEntity
            doesCascade(_) >> cascades
            isEmbedded() >> false
            isBasic() >> false
        }
    }

    void "the constructor registers itself as the default named datastore client"() {
        expect:
        client.getDatastoreClient(ConnectionSource.DEFAULT).is(client)
    }

    void "the constructor derives the multi tenancy mode and tenant resolver from the default connection source settings"() {
        given:
        TenantResolver resolver = Stub(TenantResolver)
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        connectionSourceSettings.multiTenancy.tenantResolver = resolver

        when:
        RecordingRxDatastoreClient created = new RecordingRxDatastoreClient(connectionSources, mappingContext)

        then:
        created.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE
        created.getTenantResolver().is(resolver)
    }

    void "the constructor wires itself into a tenant resolver that is aware of the datastore client"() {
        given:
        AwareTenantResolver resolver = new AwareTenantResolver()
        connectionSourceSettings.multiTenancy.tenantResolver = resolver

        when:
        RecordingRxDatastoreClient created = new RecordingRxDatastoreClient(connectionSources, mappingContext)

        then:
        resolver.datastoreClient.is(created)
    }

    void "isSchemaless is always false"() {
        expect:
        !client.isSchemaless()
    }

    void "getMappingContext returns the mapping context supplied to the constructor"() {
        expect:
        client.getMappingContext().is(mappingContext)
    }

    void "getDatastoreClientForTenantId returns this client outside DATABASE mode"() {
        given:
        connectionSourceSettings.multiTenancy.mode = mode
        RecordingRxDatastoreClient created = new RecordingRxDatastoreClient(connectionSources, mappingContext)

        expect:
        created.getDatastoreClientForTenantId('someTenant').is(created)

        where:
        mode << [MultiTenancySettings.MultiTenancyMode.NONE, MultiTenancySettings.MultiTenancyMode.SCHEMA, MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR]
    }

    void "getDatastoreClientForTenantId resolves a named datastore client in DATABASE mode"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        RecordingRxDatastoreClient created = new RecordingRxDatastoreClient(connectionSources, mappingContext)
        RxDatastoreClient other = Stub(RxDatastoreClient)
        created.datastoreClients.put('tenantA', other)

        expect:
        created.getDatastoreClientForTenantId('tenantA').is(other)
    }

    void "getDatastoreClient throws a ConfigurationException for an unregistered connection source name"() {
        when:
        client.getDatastoreClient('unknown')

        then:
        ConfigurationException e = thrown(ConfigurationException)
        e.message.contains('unknown')
    }

    void "setEventPublisher stores the publisher and always registers the auto timestamp and domain event listeners"() {
        given:
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)

        when:
        client.eventPublisher = publisher

        then:
        client.getEventPublisher().is(publisher)
        1 * publisher.addApplicationListener({ it.class.simpleName == 'AutoTimestampEventListener' })
        1 * publisher.addApplicationListener({ it.class.simpleName == 'DomainEventListener' })
        0 * publisher.addApplicationListener({ it.class.simpleName == 'MultiTenantEventListener' })
    }

    void "setEventPublisher additionally registers the multi tenant event listener in DISCRIMINATOR mode"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        RecordingRxDatastoreClient created = new RecordingRxDatastoreClient(connectionSources, mappingContext)
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)

        when:
        created.eventPublisher = publisher

        then:
        1 * publisher.addApplicationListener({ it.class.simpleName == 'MultiTenantEventListener' })
    }

    void "get(type, id) builds a query, restricts it by id, limits to a single result and delegates to singleResult"() {
        given:
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        Query query = Mock(Query)
        client.queryFactory = { PersistentEntity e, QueryState qs, Map args -> query }
        Observable<TestEntity> observable = Observable.just(new TestEntity())

        when:
        Observable<TestEntity> result = client.get(TestEntity, '1')

        then:
        1 * query.idEq('1') >> query
        1 * query.max(1) >> query
        1 * query.singleResult() >> observable
        result.is(observable)
    }

    void "createQuery(type) resolves the persistent entity and delegates to createEntityQuery"() {
        given:
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        Query expectedQuery = Stub(Query)
        Map capturedArguments = null
        client.queryFactory = { PersistentEntity e, QueryState qs, Map args -> capturedArguments = args; expectedQuery }

        expect:
        client.createQuery(TestEntity).is(expectedQuery)
        capturedArguments == [:]
    }

    void "createQuery(type, arguments) forwards the arguments to createEntityQuery"() {
        given:
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        Map capturedArguments = null
        client.queryFactory = { PersistentEntity e, QueryState qs, Map args -> capturedArguments = args; Stub(Query) }

        when:
        client.createQuery(TestEntity, [max: 10])

        then:
        capturedArguments == [:]
    }

    void "createQuery throws an IllegalArgumentException when the type is not persistent"() {
        given:
        mappingContext.getPersistentEntity(TestEntity.name) >> null

        when:
        client.createQuery(TestEntity)

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message.contains(TestEntity.name)
    }

    void "createEntityQuery(entity, queryState) delegates to the three argument overload with an empty argument map"() {
        given:
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        Query expectedQuery = Stub(Query)
        Map capturedArguments = null
        client.queryFactory = { PersistentEntity e, QueryState qs, Map args -> capturedArguments = args; expectedQuery }

        expect:
        client.createEntityQuery(entity, new QueryState()).is(expectedQuery)
        capturedArguments == [:]
    }

    void "setMessageSource rebuilds the validator registry using the given message source"() {
        when:
        client.setMessageSource(new org.springframework.context.support.StaticMessageSource())

        then:
        noExceptionThrown()
    }

    void "proxy(type, id) creates a proxy of the requested type using the real proxy factory"() {
        given: 'a proxy defers resolution, but the id query is still built eagerly when the proxy handler is created'
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        Query query = Stub(Query)
        query.idEq(_) >> query
        query.max(_) >> query
        query.singleResult() >> Observable.empty()
        client.queryFactory = { PersistentEntity e, QueryState qs, Map args -> query }

        expect:
        ObservableProxy proxy = client.proxy(TestEntity, '42')
        proxy instanceof TestEntity
        proxy.proxyKey == '42'
        !proxy.initialized
    }

    void "proxy(query) creates a proxy for the query's entity using the real proxy factory"() {
        given:
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        query.getEntity() >> entity

        when:
        ObservableProxy proxy = client.proxy(query)

        then:
        1 * query.projections() >> new Query.ProjectionList()
        1 * query.singleResult() >> Observable.empty()
        proxy instanceof TestEntity
    }

    void "delete(instance) delegates to deleteAll and converts a positive delete count to true"() {
        given:
        TestEntity instance = new TestEntity(id: '1')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(instance) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(instance) >> '1' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.batchDeleteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        expect:
        client.delete(instance).toBlocking().single()
    }

    void "delete(instance) converts a zero delete count to false"() {
        given:
        TestEntity instance = new TestEntity(id: '1')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(instance) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(instance) >> '1' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.batchDeleteHandler = { BatchOperation op -> Observable.just((Number) 0) }

        expect:
        !client.delete(instance).toBlocking().single()
    }

    void "deleteAll(null) returns an observable of zero without touching the batch delete implementation"() {
        expect:
        client.deleteAll(null).toBlocking().single() == 0
        client.batchDeleteOperations.isEmpty()
    }

    void "deleteAll publishes pre and post delete events and adds each instance to the batch operation"() {
        given:
        TestEntity first = new TestEntity(id: '1')
        TestEntity second = new TestEntity(id: '2')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) {
            getIdentifier(_) >> { args -> args[0].id }
        }
        mappingContext.createEntityAccess(entity, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)
        client.eventPublisher = publisher
        client.batchDeleteHandler = { BatchOperation op -> Observable.just((Number) 2) }

        when:
        Number count = client.deleteAll([first, second]).toBlocking().single()

        then:
        count == 2
        client.batchDeleteOperations.size() == 1
        client.batchDeleteOperations[0].deletes[entity].keySet() == ['1', '2'] as Set
        1 * publisher.publishEvent(_ as PreDeleteEvent)
        1 * publisher.publishEvent({ it instanceof PreDeleteEvent && it.entityObject.is(second) })
        1 * publisher.publishEvent(_ as PostDeleteEvent)
        1 * publisher.publishEvent({ it instanceof PostDeleteEvent && it.entityObject.is(second) })
    }

    void "deleteAll excludes instances whose pre delete event is cancelled from the batch and does not publish their post delete event"() {
        given:
        TestEntity instance = new TestEntity(id: '1')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> '1' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)
        client.eventPublisher = publisher
        client.batchDeleteHandler = { BatchOperation op -> Observable.just((Number) 0) }

        when:
        Number count = client.deleteAll([instance]).toBlocking().single()

        then:
        1 * publisher.publishEvent(_ as PreDeleteEvent) >> { PreDeleteEvent event -> event.cancel() }
        0 * publisher.publishEvent(_ as PostDeleteEvent)
        client.batchDeleteOperations[0].deletes.isEmpty()
        count == 0
    }

    void "deleteAll skips instances that have no identifier because they were never persisted"() {
        given:
        TestEntity transientInstance = new TestEntity(id: null)
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        proxyHandler.getIdentifier(_) >> null
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        client.batchDeleteHandler = { BatchOperation op -> Observable.just((Number) 0) }

        when:
        Number count = client.deleteAll([transientInstance]).toBlocking().single()

        then:
        count == 0
        client.batchDeleteOperations[0].deletes.isEmpty()
    }

    void "deleteAll throws an IllegalArgumentException when an instance's type is not persistent"() {
        given:
        TestEntity instance = new TestEntity(id: '1')
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> null

        when:
        client.deleteAll([instance]).toBlocking().single()

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message.contains(TestEntity.name)
    }

    void "persist(instance) inserts a new instance, publishes pre and post insert events, and resolves to the instance itself"() {
        given:
        TestEntity instance = new TestEntity()
        wireSimplePersist(instance)
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)
        client.eventPublisher = publisher

        when:
        TestEntity result = client.persist(instance).toBlocking().single()

        then:
        result.is(instance)
        1 * publisher.publishEvent(_ as PreInsertEvent)
        1 * publisher.publishEvent(_ as PostInsertEvent)
    }

    void "persist(instance) still resolves to the instance even when its pre insert event is cancelled"() {
        given:
        TestEntity instance = new TestEntity()
        wireSimplePersist(instance)
        client.eventPublisher = Mock(ConfigurableApplicationEventPublisher) {
            publishEvent(_) >> { AbstractPersistenceEvent event -> event.cancel() }
        }

        expect:
        client.persist(instance).toBlocking().single().is(instance)
    }

    void "insert(instance) forces an insert even when the instance already has an identifier"() {
        given:
        TestEntity instance = new TestEntity(id: 'already-assigned')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> 'already-assigned' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        TestEntity result = client.insert(instance, [:]).toBlocking().single()

        then:
        result.is(instance)
        client.batchWriteOperations[0].inserts[entity].containsKey('already-assigned')
        client.batchWriteOperations[0].updates.isEmpty()
    }

    void "insertAll(iterable) without an arguments map forces an insert of every instance"() {
        given:
        TestEntity instance = new TestEntity(id: 'already-assigned')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> 'already-assigned' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        List<Serializable> identifiers = client.insertAll([instance]).toBlocking().single()

        then:
        identifiers == ['already-assigned']
        client.batchWriteOperations[0].inserts[entity].containsKey('already-assigned')
        client.batchWriteOperations[0].updates.isEmpty()
    }

    void "persistAll with an existing identifier publishes a pre and post update event and adds an update to the batch"() {
        given:
        TestEntity instance = new TestEntity(id: 'existing')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> 'existing' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess) { getEntity() >> instance }
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)
        client.eventPublisher = publisher
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        List<Serializable> identifiers = client.persistAll([instance]).toBlocking().single()

        then:
        identifiers == ['existing']
        client.batchWriteOperations[0].updates[entity].containsKey('existing')
        1 * publisher.publishEvent(_ as PreUpdateEvent)
        1 * publisher.publishEvent(_ as PostUpdateEvent)
        0 * publisher.publishEvent(_ as PreInsertEvent)
    }

    void "persistAll excludes an update whose pre update event is cancelled, but still reports the identifier"() {
        given:
        TestEntity instance = new TestEntity(id: 'existing')
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> 'existing' }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        ConfigurableApplicationEventPublisher publisher = Mock(ConfigurableApplicationEventPublisher)
        client.eventPublisher = publisher

        when:
        List<Serializable> identifiers = client.persistAll([instance]).toBlocking().single()

        then:
        1 * publisher.publishEvent(_ as PreUpdateEvent) >> { PreUpdateEvent event -> event.cancel() }
        identifiers == ['existing']
        client.batchWriteOperations.isEmpty()
    }

    void "persistAllInternal assigns a hash code based identifier for natively generated identifiers"() {
        given:
        TestEntity instance = new TestEntity()
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity, ValueGenerator.NATIVE)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        List<Serializable> identifiers = client.persistAll([instance]).toBlocking().single()

        then:
        identifiers == [instance.hashCode()]
        client.batchWriteOperations[0].inserts[entity].containsKey(instance.hashCode())
    }

    void "persistAllInternal delegates to generateIdentifier for a non native, non assigned generator"() {
        given:
        TestEntity instance = new TestEntity()
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity, ValueGenerator.UUID)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.identifierGenerator = { PersistentEntity e, Object o, EntityReflector r -> 'generated-id' }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        List<Serializable> identifiers = client.persistAll([instance]).toBlocking().single()

        then:
        identifiers == ['generated-id']
    }

    void "persistAllInternal throws an IdentityGenerationException when the generator is assigned but no identifier was supplied"() {
        given:
        TestEntity instance = new TestEntity()
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity, ValueGenerator.ASSIGNED)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> null }

        when:
        client.persistAll([instance]).toBlocking().single()

        then:
        thrown(IdentityGenerationException)
    }

    void "persistAllInternal throws an IllegalArgumentException when the type is not persistent"() {
        given:
        TestEntity instance = new TestEntity()
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> null

        when:
        client.persistAll([instance]).toBlocking().single()

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message.contains(TestEntity.name)
    }

    void "persistAllInternal(null) resolves to an empty identifier list without invoking batchWrite"() {
        expect:
        client.persistAll(null).toBlocking().single() == []
        client.batchWriteOperations.isEmpty()
    }

    void "persistAllInternal skips the batch write call entirely when every operation was cancelled"() {
        given:
        TestEntity instance = new TestEntity()
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.eventPublisher = Mock(ConfigurableApplicationEventPublisher) {
            publishEvent(_) >> { AbstractPersistenceEvent event -> event.cancel() }
        }

        when:
        client.persistAll([instance]).toBlocking().single()

        then:
        client.batchWriteOperations.isEmpty()
    }

    void "processAssociations cascades an insert of a dirty, unsaved to-one association and publishes its events"() {
        given:
        Child child = new Child(name: 'child')
        child.trackChanges()
        child.markDirty('name')
        Parent parent = new Parent(child: child)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toOneAssociation('child', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'child') >> child
        }
        mappingContext.getEntityReflector(childEntity) >> Stub(EntityReflector) { getIdentifier(child) >> null }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 2) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts[parentEntity].size() == 1
        client.batchWriteOperations[0].inserts[childEntity].containsKey(child.hashCode())
    }

    void "processAssociations delegates to generateIdentifier for a cascaded insert when the associated entity uses a non native generator"() {
        given:
        Child child = new Child(name: 'child')
        child.trackChanges()
        child.markDirty('name')
        Parent parent = new Parent(child: child)
        PersistentEntity childEntity = stubEntity(Child.name, Child, ValueGenerator.UUID)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toOneAssociation('child', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'child') >> child
        }
        mappingContext.getEntityReflector(childEntity) >> Stub(EntityReflector) { getIdentifier(child) >> null }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.identifierGenerator = { PersistentEntity e, Object o, EntityReflector r -> e.is(childEntity) ? 'generated-child-id' : UUID.randomUUID().toString() }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 2) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts[childEntity].containsKey('generated-child-id')
    }

    void "processAssociations schedules an update for a to-one association that already has an identifier"() {
        given:
        Child child = new Child(id: 'child-1', name: 'child')
        Parent parent = new Parent(child: child)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toOneAssociation('child', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'child') >> child
        }
        mappingContext.getEntityReflector(childEntity) >> Stub(EntityReflector) { getIdentifier(child) >> 'child-1' }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 2) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].updates[childEntity].containsKey('child-1')
    }

    void "processAssociations does not cascade a to-one association that has not changed"() {
        given:
        Child child = new Child(name: 'child')
        child.trackChanges()
        Parent parent = new Parent(child: child)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toOneAssociation('child', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'child') >> child
        }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts.containsKey(childEntity) == false
    }

    void "processAssociations does not cascade an association that is not configured to cascade PERSIST"() {
        given:
        Child child = new Child(name: 'child')
        Parent parent = new Parent(child: child)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toOneAssociation('child', childEntity, false)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'child') >> child
        }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts.containsKey(childEntity) == false
    }

    void "processAssociations cascades an insert for every dirty item in a to-many association"() {
        given:
        Child firstChild = new Child(name: 'first')
        firstChild.trackChanges()
        firstChild.markDirty('name')
        Child secondChild = new Child(name: 'second')
        secondChild.trackChanges()
        secondChild.markDirty('name')
        Parent parent = new Parent(children: [firstChild, secondChild])
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toManyAssociation('children', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'children') >> parent.children
        }
        mappingContext.getEntityReflector(childEntity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 3) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts[childEntity].size() == 2
    }

    void "processAssociations skips a to-many association backed by an uninitialized persistent collection"() {
        given:
        FakePersistentCollection children = new FakePersistentCollection(initialized: false)
        Parent parent = new Parent(children: children)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toManyAssociation('children', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'children') >> children
        }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts.containsKey(childEntity) == false
    }

    void "processAssociations skips a to-many association whose dirty checkable collection has not changed"() {
        given:
        FakeDirtyCheckableList children = new FakeDirtyCheckableList(changed: false)
        children.add(new Child(name: 'untouched'))
        Parent parent = new Parent(children: children)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [toManyAssociation('children', childEntity)])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) {
            getIdentifier(_) >> null
            getProperty(parent, 'children') >> children
        }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts.containsKey(childEntity) == false
    }

    void "processAssociations ignores an embedded or basic association even when it cascades PERSIST"() {
        given:
        Child child = new Child(name: 'child')
        Parent parent = new Parent(child: child)
        PersistentEntity childEntity = stubEntity(Child.name, Child)
        ToOne embeddedAssociation = Stub(ToOne) {
            getName() >> 'child'
            getAssociatedEntity() >> childEntity
            doesCascade(_) >> true
            isEmbedded() >> true
            isBasic() >> false
        }
        PersistentEntity parentEntity = stubEntity(Parent.name, Parent, ValueGenerator.NATIVE, [embeddedAssociation])
        proxyHandler.getProxiedClass(_) >> Parent
        mappingContext.getPersistentEntity(Parent.name) >> parentEntity
        mappingContext.getEntityReflector(parentEntity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        mappingContext.createEntityAccess(_, _) >> { PersistentEntity e, Object o -> Stub(EntityAccess) { getEntity() >> o } }
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }

        when:
        client.persistAll([parent]).toBlocking().single()

        then:
        client.batchWriteOperations[0].inserts.containsKey(childEntity) == false
    }

    void "close delegates to doClose even when there are no persistent entities to clear metaclasses for"() {
        given:
        mappingContext.getPersistentEntities() >> []

        when:
        client.close()

        then:
        client.closed
    }

    void "createStaticApi builds an RxGormStaticApi bound to this client for the default connection source"() {
        given:
        PersistentEntity entity = entityWithoutDatasources(TestEntity)

        when:
        RxGormStaticApi api = client.createStaticApi(entity)

        then:
        api.entity.is(entity)
        api.datastoreClient.is(client)
    }

    void "createInstanceApi builds an RxGormInstanceApi bound to this client for the default connection source"() {
        given:
        PersistentEntity entity = entityWithoutDatasources(TestEntity)

        when:
        RxGormInstanceApi api = client.createInstanceApi(entity)

        then:
        api.entity.is(entity)
        api.datastoreClient.is(client)
    }

    void "createValidationApi builds an RxGormValidationApi bound to this client for the default connection source"() {
        given:
        PersistentEntity entity = entityWithoutDatasources(TestEntity)

        when:
        RxGormValidationApi api = client.createValidationApi(entity)

        then:
        api.datastoreClient.is(client)
    }

    void "isIndexed is true only when the property mapping is present and marked as an index"() {
        given:
        PersistentProperty indexed = Stub(PersistentProperty) {
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> new Property(index: true) }
        }
        PersistentProperty notIndexed = Stub(PersistentProperty) {
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> new Property(index: false) }
        }
        PersistentProperty unmapped = Stub(PersistentProperty) {
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> null }
        }

        expect:
        client.isIndexedProperty(indexed)
        !client.isIndexedProperty(notIndexed)
        !client.isIndexedProperty(unmapped)
    }

    void "activeDirtyChecking starts change tracking for dirty checkable objects and does nothing otherwise"() {
        given:
        Child dirtyCheckable = new Child(name: 'child')

        expect: 'a fresh dirty checkable instance is considered changed until change tracking begins'
        dirtyCheckable.hasChanged()

        when:
        client.triggerActiveDirtyChecking(dirtyCheckable)
        client.triggerActiveDirtyChecking('not dirty checkable')

        then:
        !dirtyCheckable.hasChanged()
    }

    private void wireSimplePersist(TestEntity instance, ValueGenerator generator = ValueGenerator.NATIVE) {
        PersistentEntity entity = stubEntity(TestEntity.name, TestEntity, generator)
        proxyHandler.getProxiedClass(_) >> TestEntity
        mappingContext.getPersistentEntity(TestEntity.name) >> entity
        mappingContext.getEntityReflector(entity) >> Stub(EntityReflector) { getIdentifier(_) >> null }
        mappingContext.createEntityAccess(entity, instance) >> Stub(EntityAccess)
        client.batchWriteHandler = { BatchOperation op -> Observable.just((Number) 1) }
    }

    private PersistentEntity entityWithoutDatasources(Class javaClass) {
        Stub(PersistentEntity) {
            getJavaClass() >> javaClass
            getName() >> javaClass.name
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
        }
    }

    private static class TestEntity {
        Serializable id
    }

    private static class Parent implements DirtyCheckable {
        Serializable id
        Child child
        Collection<Child> children = []
    }

    private static class Child implements DirtyCheckable {
        Serializable id
        String name
    }

    private static class FakePersistentCollection implements PersistentCollection {
        @Delegate(interfaces = false)
        List items = []
        boolean initialized = true
        boolean changed = true

        boolean isInitialized() { initialized }
        void initialize() { }
        boolean isDirty() { changed }
        void resetDirty() { }
        void markDirty() { }
        boolean hasChanged() { changed }
        int getOriginalSize() { 0 }
        boolean hasGrown() { false }
        boolean hasShrunk() { false }
        boolean hasChangedSize() { false }
    }

    private static class FakeDirtyCheckableList extends ArrayList implements DirtyCheckableCollection {
        boolean changed = true

        boolean hasChanged() { changed }
        int getOriginalSize() { 0 }
        boolean hasGrown() { false }
        boolean hasShrunk() { false }
        boolean hasChangedSize() { false }
    }

    private static class AwareTenantResolver implements TenantResolver, RxDatastoreClientAware {
        RxDatastoreClient datastoreClient

        @Override
        void setRxDatastoreClient(RxDatastoreClient datastoreClient) {
            this.datastoreClient = datastoreClient
        }

        @Override
        Serializable resolveTenantIdentifier() {
            return 'tenant'
        }
    }
}

class RecordingRxDatastoreClient<T> extends AbstractRxDatastoreClient<T> {

    List<BatchOperation> batchWriteOperations = []
    List<BatchOperation> batchDeleteOperations = []
    Closure<Observable<Number>> batchWriteHandler = { BatchOperation op -> Observable.just((Number) 0) }
    Closure<Observable<Number>> batchDeleteHandler = { BatchOperation op -> Observable.just((Number) 0) }
    Closure<Serializable> identifierGenerator = { entity, instance, reflector -> UUID.randomUUID().toString() }
    Closure<Query> queryFactory = { entity, queryState, arguments -> null }
    boolean allowBlockingOperations = true
    boolean closed = false

    RecordingRxDatastoreClient(ConnectionSources connectionSources, MappingContext mappingContext) {
        super(connectionSources, mappingContext)
    }

    @Override
    void doClose() {
        closed = true
    }

    @Override
    Observable<Number> batchWrite(BatchOperation operation) {
        batchWriteOperations << operation
        batchWriteHandler.call(operation)
    }

    @Override
    Observable<Number> batchDelete(BatchOperation operation) {
        batchDeleteOperations << operation
        batchDeleteHandler.call(operation)
    }

    @Override
    Serializable generateIdentifier(PersistentEntity entity, Object instance, EntityReflector reflector) {
        identifierGenerator.call(entity, instance, reflector)
    }

    @Override
    Query createEntityQuery(PersistentEntity entity, QueryState queryState, Map arguments) {
        queryFactory.call(entity, queryState, arguments)
    }

    @Override
    Object getNativeInterface() {
        null
    }

    @Override
    boolean isAllowBlockingOperations() {
        allowBlockingOperations
    }

    boolean isIndexedProperty(PersistentProperty property) {
        isIndexed(property)
    }

    void triggerActiveDirtyChecking(object) {
        activeDirtyChecking(object)
    }
}

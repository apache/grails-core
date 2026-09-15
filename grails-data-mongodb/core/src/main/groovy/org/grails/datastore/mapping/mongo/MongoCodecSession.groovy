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
package org.grails.datastore.mapping.mongo

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import jakarta.persistence.FlushModeType

import com.mongodb.DBRef
import com.mongodb.WriteConcern
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.DeleteManyModel
import com.mongodb.client.model.DeleteOneModel
import com.mongodb.client.model.InsertOneModel
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.WriteModel
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWrapper
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.conversions.Bson

import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException

import org.grails.datastore.bson.query.BsonQuery
import org.grails.datastore.mapping.core.OptimisticLockingException
import org.grails.datastore.mapping.core.impl.PendingDelete
import org.grails.datastore.mapping.core.impl.PendingInsert
import org.grails.datastore.mapping.core.impl.PendingOperation
import org.grails.datastore.mapping.core.impl.PendingUpdate
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.document.config.DocumentMappingContext
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.mongo.engine.MongoCodecEntityPersister
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister
import org.grails.datastore.mapping.mongo.config.MongoAttribute
import org.grails.datastore.mapping.mongo.engine.MongoIdCoercion
import org.grails.datastore.mapping.mongo.engine.codecs.PersistentEntityCodec
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria

/**
 * A MongoDB session for codec mapping style
 *
 * @author Graeme Rocher
 * @since 4.1
 */
@CompileStatic
class MongoCodecSession extends AbstractMongoSession {

    protected Map<Class, MongoCodecEntityPersister> mongoCodecEntityPersisterMap = new ConcurrentHashMap<Class, MongoCodecEntityPersister>().withDefault { Class type ->
        def context = getDocumentMappingContext()
        def entity = context.getPersistentEntity(type.name)
        if (entity) {
            return new MongoCodecEntityPersister(context, entity, this, publisher, cacheAdapterRepository)
        }
        throw new IllegalArgumentException("Type [$type] is not an entity")
    }

    MongoCodecSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher) {
        this(datastore, mappingContext, publisher, false)
    }
    MongoCodecSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher, boolean stateless) {
        super(datastore, mappingContext, publisher, stateless)

        FlushModeType defaultFlushMode = datastore.getDefaultFlushMode()
        setFlushMode(defaultFlushMode)
    }

    @Override
    MongoDatastore getDatastore() {
        return (MongoDatastore) super.getDatastore()
    }

    @Override
    void flush(WriteConcern writeConcern) {
        WriteConcern currentWriteConcern = this.getWriteConcern()
        try {
            this.writeConcern = writeConcern
            final Map<PersistentEntity, Collection<PendingUpdate>> pendingUpdates = getPendingUpdates()
            final Map<PersistentEntity, Collection<PendingInsert>> pendingInserts = getPendingInserts()
            final Map<PersistentEntity, Collection<PendingDelete>> pendingDeletes = getPendingDeletes()

            if (pendingUpdates.isEmpty() && pendingInserts.isEmpty() && pendingDeletes.isEmpty()) {
                return
            }

            Map<String,Integer> numberOfOptimisticUpdates = [:].withDefault { 0 }
            Map<String,Integer> numberOfPessimisticUpdates = [:].withDefault { 0 }

            Map<PersistentEntity, List<WriteModel<Object>>> writeModels = [:]
            for (PersistentEntity persistentEntity in pendingInserts.keySet()) {
                final Collection<PendingInsert> inserts = pendingInserts[persistentEntity]
                if (inserts) {
                    def entityWrites = getCodecWriteModelsForEntity(persistentEntity, writeModels)
                    for (PendingInsert insert in inserts) {
                        insert.run()

                        if (insert.vetoed) continue

                        def object = insert.nativeEntry
                        entityWrites << new InsertOneModel<Object>(object)

                        final List<PendingOperation> cascadeOperations = insert.cascadeOperations
                        addPostFlushOperations(cascadeOperations)
                    }
                }
            }

            for (PersistentEntity persistentEntity in pendingUpdates.keySet()) {

                final String name = persistentEntity.isRoot() ? persistentEntity.name : persistentEntity.rootEntity.name

                final Collection<PendingUpdate> updates = pendingUpdates[persistentEntity]
                if (updates) {
                    def entityWrites = getCodecWriteModelsForEntity(persistentEntity, writeModels)
                    for (PendingUpdate update in updates) {
                        update.run()

                        if (update.vetoed) continue

                        def changedObject = (DirtyCheckable) update.nativeEntry
                        def codec = (PersistentEntityCodec) datastore.codecRegistry.get((Class) changedObject.getClass())

                        final Object nativeKey = coerceIdToStoredType(update.nativeKey, persistentEntity)
                        final Document id = new Document(MongoEntityPersister.MONGO_ID_FIELD, nativeKey)

                        EntityAccess entityAccess = update.entityAccess
                        boolean isVersioned = persistentEntity.isVersioned()
                        def currentVersion = null
                        if (isVersioned) {
                            currentVersion = entityAccess.getProperty(persistentEntity.version.name)
                        }
                        def updateDoc = codec.encodeUpdate(changedObject, entityAccess)

                        if (updateDoc) {

                            if (isVersioned) {
                                // if the entity is versioned we add to the query the current version
                                // if the query doesn't match a result this means the document has been updated by
                                // another thread and an optimistic locking exception should be thrown
                                if (currentVersion == null) {
                                    currentVersion = entityAccess.getProperty(persistentEntity.version.name)
                                }
                                id[GormProperties.VERSION] = currentVersion
                                numberOfOptimisticUpdates[name]++
                            }
                            else {
                                numberOfPessimisticUpdates[name]++
                            }
                            final options = new UpdateOptions()

                            entityWrites << new UpdateOneModel<Object>(id, updateDoc, options.upsert(false))

                            final List cascadeOperations = update.cascadeOperations
                            addPostFlushOperations(cascadeOperations)
                        }

                    }
                }
            }

            for (PersistentEntity persistentEntity in pendingDeletes.keySet()) {
                final Collection<PendingDelete> deletes = pendingDeletes[persistentEntity]
                if (deletes) {
                    def entityWrites = getCodecWriteModelsForEntity(persistentEntity, writeModels)
                    List<Object> nativeKeys = []
                    for (PendingDelete delete in deletes) {
                        delete.run()

                        if (delete.vetoed) continue

                        final Object k = coerceIdToStoredType(delete.nativeKey, persistentEntity)
                        // Groovy truthiness would skip an empty assigned String id, which is a
                        // valid BSON _id -- the document would silently survive the delete.
                        if (k != null) {
                            nativeKeys << k
                            final List cascadeOperations = delete.cascadeOperations
                            addPostFlushOperations(cascadeOperations)
                        }

                    }
                    if (nativeKeys.size() == 1) {
                        entityWrites << new DeleteOneModel<Object>(new Document(MongoEntityPersister.MONGO_ID_FIELD, nativeKeys.get(0)))
                    }
                    else {
                        entityWrites << new DeleteManyModel<Object>(new Document(MongoEntityPersister.MONGO_ID_FIELD, new Document(BsonQuery.IN_OPERATOR, nativeKeys)))
                    }
                }
            }

            for (PersistentEntity persistentEntity : writeModels.keySet()) {
                MongoCollection<Object> collection = getCollection(persistentEntity)
                                                .withDocumentClass((Class<Object>) persistentEntity.javaClass)

                WriteConcern wc = writeConcern
                if (wc == null) {
                    org.grails.datastore.mapping.mongo.config.MongoCollection mapping = (org.grails.datastore.mapping.mongo.config.MongoCollection) persistentEntity.mapping.mappedForm
                    wc = mapping.writeConcern
                }
                if (wc != null) {
                    collection = collection.withWriteConcern(wc)
                }
                else {
                    wc = collection.writeConcern
                }
                def writes = writeModels[persistentEntity]
                if (writes) {

                    final BulkWriteResult bulkWriteResult = bulkWrite(collection, writes)

                    final boolean isAcknowledged = wc.isAcknowledged()
                    if (!bulkWriteResult.wasAcknowledged() && isAcknowledged) {
                        errorOccured = true
                        throw new DataIntegrityViolationException('Write operation was not acknowledged')
                    }
                    else if (isAcknowledged) {
                        final int matchedCount = bulkWriteResult.matchedCount
                        final String name = persistentEntity.name
                        final Integer numOptimistic = numberOfOptimisticUpdates[name]
                        final Integer numPessimistic = numberOfPessimisticUpdates[name]
                        if ((matchedCount - numPessimistic) != numOptimistic) {
                            setFlushMode(FlushModeType.COMMIT)
                            throw new OptimisticLockingException(persistentEntity, null)
                        }
                    }
                }
            }

            for (Runnable postFlushOperation : postFlushOperations) {
                postFlushOperation.run()
            }
        } finally {
            clearPendingOperations()
            postFlushOperations.clear()
            firstLevelCollectionCache.clear()
            this.writeConcern = currentWriteConcern
        }
    }

    protected List<WriteModel<Object>> getCodecWriteModelsForEntity(PersistentEntity persistentEntity, Map<PersistentEntity, List<WriteModel<Object>>> writeModels) {
        PersistentEntity key = persistentEntity.isRoot() ? persistentEntity : persistentEntity.getRootEntity()
        List<WriteModel<Object>> entityWrites = writeModels[key]
        if (entityWrites == null) {
            entityWrites = []
            writeModels[key] = entityWrites
        }
        return entityWrites
    }

    @Override
    MongoClient getNativeInterface() {
        return mongoDatastore.mongoClient
    }

    DocumentMappingContext getDocumentMappingContext() {
        return (DocumentMappingContext) getMappingContext()
    }

    protected List<WriteModel<Document>> getWriteModelsForEntity(
            PersistentEntity persistentEntity,
            Map<PersistentEntity,
            List<WriteModel<Document>>> writeModels
    ) {
        def key = persistentEntity.root ? persistentEntity : persistentEntity.rootEntity
        def entityWrites = writeModels[key]
        if (entityWrites == null) {
            entityWrites = new ArrayList<WriteModel<Document>>()
            writeModels[key] = entityWrites
        }
        return entityWrites
    }

    /**
     * If the entity's id mapping declares {@code storedAs} and it differs from the in-memory
     * native key type, coerce the key so that update/delete filters target BSON values of
     * the correct type (otherwise {@code {_id: "<hex>"}} sent as a BSON String would never
     * match an {@code _id: ObjectId(...)} document on disk, and the write would silently miss,
     * surfacing as a misleading {@link OptimisticLockingException}).
     *
     * <p>Exercised end-to-end by {@code StringIdWithObjectIdStorageSpec}:
     * <ul>
     *   <li>"with storedAs ObjectId, updates persist (no phantom OptimisticLockingException)" — happy path on update filter</li>
     *   <li>"with storedAs ObjectId, update of a non-hex id document lands on the right row" — null-return fallback on update filter</li>
     *   <li>"with storedAs ObjectId, delete of a non-hex id document removes the row" — null-return fallback on delete filter</li>
     *   <li>"with storedAs ObjectId, legacy documents written directly as BSON ObjectId are fully accessible" — update path against legacy BSON ObjectId _id</li>
     * </ul>
     */
    protected Object coerceIdToStoredType(Object nativeKey, PersistentEntity entity) {
        MongoIdCoercion.coerceIdToStoredType(nativeKey, entity)
    }

    @Override
    protected MongoCodecEntityPersister createPersister(Class cls, MappingContext mappingContext) {
        return mongoCodecEntityPersisterMap[cls]
    }

    @Override
    long deleteAll(QueryableCriteria criteria) {
        final PersistentEntity entity = criteria.getPersistentEntity()
        final Document nativeQuery = buildNativeDocumentQueryFromCriteria(criteria, entity)

        final MongoCollection collection = getCollection(entity)
        final DeleteResult deleteResult = deleteMany(collection, (Bson) nativeQuery)
        if (deleteResult.wasAcknowledged()) {
            return deleteResult.deletedCount
        }
        else {
            return 0
        }
    }

    /**
     * The identifier an updateAll association value stands for. A lazy proxy keeps its id in the
     * proxy handler, so -- like ToOneEncoder -- the proxy factory is asked first. An instance of
     * the associated class is reflected. Anything else is taken to be the identifier itself, so
     * updateAll(project: project) and updateAll(project: project.id) write the same value.
     */
    private Object identifierOf(Object value, PersistentEntity associatedEntity) {
        def proxyFactory = mappingContext.proxyFactory
        if (proxyFactory.isProxy(value)) {
            return proxyFactory.getIdentifier(value)
        }
        if (associatedEntity.javaClass.isInstance(value)) {
            return associatedEntity.reflector.getIdentifier(value)
        }
        return value
    }

    @Override
    long updateAll(QueryableCriteria criteria, Map<String, Object> properties) {
        final PersistentEntity entity = criteria.persistentEntity
        final Document nativeQuery = buildNativeDocumentQueryFromCriteria(criteria, entity)
        final MongoCollection collection = getCollection(entity)
        final updateOptions = new UpdateOptions()
        updateOptions.upsert(false)
        // Normalise into a copy: the caller's map is theirs, and may be immutable. Writing the
        // encoded reference back into it replaced their domain object with an ObjectId or
        // DBRef, and threw UnsupportedOperationException for a Map.of/singletonMap argument.
        Map<String, Object> updateProperties = new LinkedHashMap<String, Object>(properties)
        for (Association association in entity.associations) {
            String associationName = association.name
            // Embedded extends ToOne, but an embedded value is a subdocument with no
            // identity of its own -- normal persistence encodes it through the embedded
            // path, not ToOneEncoder. Reflecting an id from one yields null.
            // hasOne keeps the foreign key on the child, so ToOneEncoder writes nothing on the
            // owner for it -- see its !isForeignKeyInChild() guard. Normalizing it here would
            // put an id field on a document that never carries one.
            if (association instanceof ToOne && !(association instanceof Embedded)
                    && ((ToOne) association).isForeignKeyInChild()
                    && updateProperties.containsKey(associationName)) {
                throw new UnsupportedOperationException(
                        "Cannot updateAll the hasOne association [${entity.name}.${associationName}]: " +
                        "its foreign key is held by [${association.associatedEntity?.name}], " +
                        'so update the inverse side instead')
            }
            if (association instanceof ToOne && !(association instanceof Embedded)
                    && updateProperties.containsKey(associationName)) {
                def value = updateProperties.get(associationName)
                if (value != null) {
                    // Write the reference exactly as ToOneEncoder does on the normal
                    // persistence path: in the target's stored _id type, as a DBRef where the
                    // mapping asks for one. Otherwise a bulk update leaves a reference that
                    // association queries and external clients cannot match.
                    def associatedEntity = association.associatedEntity
                    def associationId = MongoIdCoercion.coerceIdToStoredType(
                            identifierOf(value, associatedEntity), associatedEntity)
                    MongoAttribute attr = (MongoAttribute) association.mapping.mappedForm
                    if (attr?.isReference()) {
                        updateProperties.put(associationName,
                                new DBRef(getCollectionName(associatedEntity), associationId))
                    }
                    else {
                        updateProperties.put(associationName, associationId)
                    }
                }
            }
            // A bidirectional one-to-many keeps its foreign key on the inverse side, so there
            // is no field on this document to update -- OneToManyEncoder's shouldEncodeIds
            // skips it for the same reason, and the decoder never reads one. Left in the $set
            // the value is written as raw subdocuments and the owner stops decoding, so this
            // says so rather than corrupting the document or silently doing nothing.
            // ManyToMany extends ToMany, not OneToMany, so it never reaches this branch.
            else if (association instanceof OneToMany && association.isBidirectional()
                    && updateProperties.containsKey(associationName)) {
                throw new UnsupportedOperationException(
                        "Cannot updateAll the bidirectional one-to-many [${entity.name}.${associationName}]: " +
                        "its foreign key is held by [${association.associatedEntity?.name}], " +
                        'so update the inverse side instead')
            }
            // Unidirectional OneToMany and ManyToMany carry a collection of associated instances.
            // Normal persistence stores their ids -- DBRefs where the mapping asks for it -- so
            // the bulk path does the same rather than sending domain objects through $set.
            // Only those two kinds: Basic also extends ToMany but is a collection of simple
            // values with no associated entity, and must pass through untouched.
            else if ((association instanceof OneToMany || association instanceof ManyToMany)
                    && association.associatedEntity != null
                    && updateProperties.containsKey(associationName)) {
                def value = updateProperties.get(associationName)
                if (value instanceof Collection) {
                    def associatedEntity = association.associatedEntity
                    MongoAttribute attr = (MongoAttribute) association.mapping.mappedForm
                    def ids = value.collect { element ->
                        element == null ? null :
                                MongoIdCoercion.coerceIdToStoredType(identifierOf(element, associatedEntity), associatedEntity)
                    }
                    // Exactly OneToManyEncoder's shape: nulls are dropped before wrapping,
                    // never turned into DBRef(collection, null), and a non-reference list
                    // keeps whatever the caller passed.
                    def encoded = attr?.isReference()
                            ? ids.findAll { it != null }.collect { new DBRef(getCollectionName(associatedEntity), it) }
                            : ids
                    updateProperties.put(associationName, encoded)
                }
            }
        }
        final UpdateResult updateResult = updateMany(collection, nativeQuery, new Document(MONGO_SET_OPERATOR, updateProperties), updateOptions)
        if (updateResult.wasAcknowledged()) {
            try {
                return updateResult.modifiedCount
            } catch (UnsupportedOperationException e) {
                // not supported on versions of MongoDB earlier than 2.6
                return -1
            }
        }
        else {
            return 0
        }
    }

    @Override
    Object decode(Class type, Object nativeObject) {
        if (nativeObject instanceof FindIterable) {
            return decode(type, ((FindIterable) nativeObject).first())
        }
        else if (nativeObject instanceof Document) {

            def registry = datastore.getCodecRegistry()
            def codec = registry.get(type)

            def reader = new BsonDocumentReader(new BsonDocumentWrapper(nativeObject, registry.get(Document)))
            return codec.decode(reader, DecoderContext.builder().build())
        }
        return null
    }

    private Document buildNativeDocumentQueryFromCriteria(QueryableCriteria criteria, PersistentEntity entity) {
        def mongoQuery = new MongoQuery(this, entity)
        for (Query.Criterion c in criteria.criteria) {
            mongoQuery.add(c)
        }

        return mongoQuery.mongoQuery
    }
}

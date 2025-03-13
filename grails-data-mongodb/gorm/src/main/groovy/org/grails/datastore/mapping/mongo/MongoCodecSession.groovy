/* Copyright (C) 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.mongo
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
import groovy.transform.CompileStatic
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWrapper
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.conversions.Bson
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
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.mongo.engine.MongoCodecEntityPersister
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister
import org.grails.datastore.mapping.mongo.engine.codecs.PersistentEntityCodec
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.transactions.SessionOnlyTransaction
import org.grails.datastore.mapping.transactions.Transaction
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException

import jakarta.persistence.FlushModeType
import java.util.concurrent.ConcurrentHashMap
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
        if(entity) {
            return new MongoCodecEntityPersister(context, entity, this, publisher, cacheAdapterRepository )
        }
        throw new IllegalArgumentException("Type [$type] is not an entity")
    }

    public MongoCodecSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher) {
        this(datastore, mappingContext, publisher, false);
    }
    public MongoCodecSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher, boolean stateless) {
        super(datastore, mappingContext, publisher, stateless);


        FlushModeType defaultFlushMode = datastore.getDefaultFlushMode()
        setFlushMode(defaultFlushMode);
    }

    @Override
    MongoDatastore getDatastore() {
        return (MongoDatastore)super.getDatastore()
    }


    @Override
    void flush(WriteConcern writeConcern) {
        WriteConcern currentWriteConcern = this.getWriteConcern();
        try {
            this.writeConcern = writeConcern;
            final Map<PersistentEntity, Collection<PendingUpdate>> pendingUpdates = getPendingUpdates();
            final Map<PersistentEntity, Collection<PendingInsert>> pendingInserts = getPendingInserts();
            final Map<PersistentEntity, Collection<PendingDelete>> pendingDeletes = getPendingDeletes();

            if(pendingUpdates.isEmpty() && pendingInserts.isEmpty() && pendingDeletes.isEmpty()) {
                return;
            }


            Map<String,Integer> numberOfOptimisticUpdates = [:].withDefault { 0 }
            Map<String,Integer> numberOfPessimisticUpdates = [:].withDefault { 0 }

            Map<PersistentEntity, List<WriteModel<Document>>> writeModels = [:]
            for (PersistentEntity persistentEntity in pendingInserts.keySet()) {
                final Collection<PendingInsert> inserts = pendingInserts[persistentEntity]
                if(inserts) {
                    List<WriteModel<?>> entityWrites = getWriteModelsForEntity(persistentEntity, writeModels)
                    for (PendingInsert insert in inserts) {
                        insert.run()

                        if(insert.vetoed) continue


                        def object = insert.nativeEntry
                        entityWrites << new InsertOneModel<?>(object)

                        final List<PendingOperation> cascadeOperations = insert.cascadeOperations
                        addPostFlushOperations cascadeOperations
                    }
                }
            }


            for (PersistentEntity persistentEntity in pendingUpdates.keySet()) {

                final String name = persistentEntity.isRoot() ? persistentEntity.name : persistentEntity.rootEntity.name

                final Collection<PendingUpdate> updates = pendingUpdates[persistentEntity]
                if(updates) {
                    List<WriteModel<?>> entityWrites = getWriteModelsForEntity(persistentEntity, writeModels);
                    for (PendingUpdate update in updates) {
                        update.run()

                        if(update.vetoed) continue

                        DirtyCheckable changedObject = (DirtyCheckable) update.getNativeEntry()
                        PersistentEntityCodec codec = (PersistentEntityCodec)datastore.codecRegistry.get(changedObject.getClass())

                        final Object nativeKey = update.nativeKey
                        final Document id = new Document(MongoEntityPersister.MONGO_ID_FIELD, nativeKey)

                        EntityAccess entityAccess = update.entityAccess
                        boolean isVersioned = persistentEntity.isVersioned()
                        def currentVersion = null
                        if(isVersioned) {
                            currentVersion = entityAccess.getProperty( persistentEntity.version.name )
                        }
                        def updateDoc = codec.encodeUpdate(changedObject, entityAccess)

                        if(updateDoc) {

                            if(isVersioned) {
                                // if the entity is versioned we add to the query the current version
                                // if the query doesn't match a result this means the document has been updated by
                                // another thread and an optimistic locking exception should be thrown
                                if(currentVersion == null) {
                                    currentVersion = entityAccess.getProperty( persistentEntity.version.name )
                                }
                                id[GormProperties.VERSION] = currentVersion
                                numberOfOptimisticUpdates[name]++
                            }
                            else {
                                numberOfPessimisticUpdates[name]++
                            }
                            final options = new UpdateOptions()

                            entityWrites << new UpdateOneModel<Document>(id, updateDoc, options.upsert(false))

                            final List cascadeOperations = update.cascadeOperations
                            addPostFlushOperations cascadeOperations
                        }

                    }
                }
            }


            for (PersistentEntity persistentEntity in pendingDeletes.keySet()) {
                final Collection<PendingDelete> deletes = pendingDeletes[persistentEntity]
                if(deletes) {
                    List<WriteModel<?>> entityWrites = getWriteModelsForEntity(persistentEntity, writeModels)
                    List<Object> nativeKeys = []
                    for (PendingDelete delete in deletes) {
                        delete.run()

                        if(delete.vetoed) continue

                        final Object k = delete.nativeKey
                        if(k) {
                            nativeKeys << k
                            final List cascadeOperations = delete.cascadeOperations
                            addPostFlushOperations cascadeOperations
                        }

                    }
                    if(nativeKeys.size() == 1) {
                        entityWrites << new DeleteOneModel<Document>(new Document( MongoEntityPersister.MONGO_ID_FIELD, nativeKeys.get(0)))
                    }
                    else {
                        entityWrites << new DeleteManyModel<Document>(new Document( MongoEntityPersister.MONGO_ID_FIELD, new Document(BsonQuery.IN_OPERATOR, nativeKeys)))
                    }
                }
            }


            for (PersistentEntity persistentEntity : writeModels.keySet()) {
                MongoCollection collection = getCollection(persistentEntity)
                                                .withDocumentClass(persistentEntity.javaClass)

                WriteConcern wc = writeConcern
                if(wc == null) {
                    org.grails.datastore.mapping.mongo.config.MongoCollection mapping = (org.grails.datastore.mapping.mongo.config.MongoCollection)persistentEntity.mapping.mappedForm
                    wc = mapping.writeConcern
                }
                if(wc != null) {
                    collection = collection.withWriteConcern(wc)
                }
                else {
                    wc = collection.writeConcern
                }
                final List<WriteModel<?>> writes = writeModels[persistentEntity]
                if(writes) {

                    final BulkWriteResult bulkWriteResult = collection
                                                                .bulkWrite(writes)

                    final boolean isAcknowledged = wc.isAcknowledged()
                    if( !bulkWriteResult.wasAcknowledged() && isAcknowledged) {
                        errorOccured = true;
                        throw new DataIntegrityViolationException("Write operation was not acknowledged");
                    }
                    else if(isAcknowledged) {
                        final int matchedCount = bulkWriteResult.matchedCount
                        final String name = persistentEntity.name
                        final Integer numOptimistic = numberOfOptimisticUpdates[name]
                        final Integer numPessimistic = numberOfPessimisticUpdates[name]
                        if((matchedCount - numPessimistic) != numOptimistic) {
                            setFlushMode(FlushModeType.COMMIT)
                            throw new OptimisticLockingException(persistentEntity, null)
                        }
                    }
                }
            }

            for (Runnable postFlushOperation : postFlushOperations) {
                postFlushOperation.run();
            }
        } finally {
            clearPendingOperations();
            postFlushOperations.clear();
            firstLevelCollectionCache.clear();
            this.writeConcern = currentWriteConcern;
        }
    }

    @Override
    MongoClient getNativeInterface() {
        return mongoDatastore.mongoClient
    }

    public DocumentMappingContext getDocumentMappingContext() {
        return (DocumentMappingContext) getMappingContext()
    }

    protected List<WriteModel<?>> getWriteModelsForEntity(PersistentEntity persistentEntity, Map<PersistentEntity, List<WriteModel<?>>> writeModels) {
        PersistentEntity key = persistentEntity.root ? persistentEntity : persistentEntity.rootEntity
        List<WriteModel<?>> entityWrites = writeModels[key]
        if(entityWrites == null) {
            entityWrites = new ArrayList<WriteModel<?>>()
            writeModels[key] = entityWrites
        }
        return entityWrites
    }

    @Override
    protected Transaction beginTransactionInternal() {
        return new SessionOnlyTransaction<MongoClient>(getNativeInterface(), this);
    }

    @Override
    protected MongoCodecEntityPersister createPersister(Class cls, MappingContext mappingContext) {
        return mongoCodecEntityPersisterMap[cls]
    }

    @Override
    long deleteAll(QueryableCriteria criteria) {
        final PersistentEntity entity = criteria.getPersistentEntity();
        final Document nativeQuery = buildNativeDocumentQueryFromCriteria(criteria, entity);

        final MongoCollection collection = getCollection(entity)
        final DeleteResult deleteResult = collection.deleteMany((Bson)nativeQuery)
        if( deleteResult.wasAcknowledged() ) {
            return deleteResult.deletedCount
        }
        else {
            return 0
        }
    }

    @Override
    long updateAll(QueryableCriteria criteria, Map<String, Object> properties) {
        final PersistentEntity entity = criteria.persistentEntity
        final Document nativeQuery = buildNativeDocumentQueryFromCriteria(criteria, entity)
        final MongoCollection collection = getCollection(entity)
        final updateOptions = new UpdateOptions()
        updateOptions.upsert(false)
        for (Association association in entity.associations) {
            String associationName = association.name
            if(association instanceof ToOne && properties.containsKey(associationName)) {
                def value = properties.get(associationName)
                if(value != null) {
                    properties.put(associationName, association.associatedEntity.reflector.getIdentifier(value))
                }
            }
        }
        final UpdateResult updateResult = collection.updateMany(nativeQuery, new Document(MONGO_SET_OPERATOR, properties), updateOptions)
        if(updateResult.wasAcknowledged()) {
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
        if(nativeObject instanceof FindIterable) {
            return decode(type, ((FindIterable) nativeObject).first())
        }
        else if( nativeObject instanceof Document ) {

            def registry = datastore.getCodecRegistry()
            def codec = registry.get(type)

            def reader = new BsonDocumentReader(new BsonDocumentWrapper(nativeObject, registry.get(Document)))
            return codec.decode(reader, DecoderContext.builder().build())
        }
        return null
    }

    private Document buildNativeDocumentQueryFromCriteria(QueryableCriteria criteria, PersistentEntity entity) {
        def mongoQuery = new MongoQuery(this, entity)
        for(Query.Criterion c in criteria.criteria) {
            mongoQuery.add(c)
        }

        return mongoQuery.mongoQuery
    }
}

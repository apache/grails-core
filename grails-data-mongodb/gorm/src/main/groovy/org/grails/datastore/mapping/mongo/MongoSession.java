/* Copyright (C) 2010 SpringSource
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

package org.grails.datastore.mapping.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.grails.datastore.bson.query.BsonQuery;
import org.grails.datastore.mapping.core.OptimisticLockingException;
import org.grails.datastore.mapping.core.impl.*;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.EntityPersister;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.mongo.engine.AbstractMongoObectEntityPersister;
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.transactions.SessionOnlyTransaction;
import org.grails.datastore.mapping.transactions.Transaction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.FlushModeType;
import java.io.Serializable;
import java.util.*;

/**
 * A {@link org.grails.datastore.mapping.core.Session} implementation for the Mongo document store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MongoSession extends AbstractMongoSession {


    public MongoSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher) {
        this(datastore, mappingContext, publisher, false);
    }
    public MongoSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher, boolean stateless) {
        super(datastore, mappingContext, publisher, stateless);
    }


    @Override
    public Query createQuery(@SuppressWarnings("rawtypes") Class type) {
        return super.createQuery(type);
    }


    @Override
    protected void cacheEntry(Serializable key, Object entry, Map<Serializable, Object> entryCache, boolean forDirtyCheck) {
        entryCache.put(key, entry);
    }

    public void flush(WriteConcern writeConcern) {
        WriteConcern currentWriteConcern = this.getWriteConcern();
        try {
            this.writeConcern = writeConcern;
            final Map<PersistentEntity, Collection<PendingUpdate>> pendingUpdates = getPendingUpdates();
            final Map<PersistentEntity, Collection<PendingInsert>> pendingInserts = getPendingInserts();
            final Map<PersistentEntity, Collection<PendingDelete>> pendingDeletes = getPendingDeletes();

            if(pendingUpdates.isEmpty() && pendingInserts.isEmpty() && pendingDeletes.isEmpty()) {
                return;
            }


            Map<String,Integer> numberOfOptimisticUpdates = new LinkedHashMap<String, Integer>();
            Map<String,Integer> numberOfPessimisticUpdates = new LinkedHashMap<String, Integer>();

            Map<PersistentEntity, List<WriteModel<Document>>> writeModels = new LinkedHashMap<PersistentEntity, List<WriteModel<Document>>>();
            for (PersistentEntity persistentEntity : pendingInserts.keySet()) {
                final Collection<PendingInsert> inserts = pendingInserts.get(persistentEntity);
                if(inserts != null && !inserts.isEmpty()) {
                    List<WriteModel<Document>> entityWrites = getWriteModelsForEntity(persistentEntity, writeModels);
                    for (PendingInsert insert : inserts) {
                        insert.run();

                        if(insert.isVetoed()) continue;

                        entityWrites.add(new InsertOneModel<Document>((Document) insert.getNativeEntry()));

                        final List<PendingOperation> cascadeOperations = insert.getCascadeOperations();
                        addPostFlushOperations(cascadeOperations);
                    }
                }
            }


            for (PersistentEntity persistentEntity : pendingUpdates.keySet()) {

                final String name = persistentEntity.isRoot() ? persistentEntity.getName() : persistentEntity.getRootEntity().getName();
                int numberOfOptimistic = numberOfOptimisticUpdates.containsKey(name) ? numberOfOptimisticUpdates.get(name) : 0;
                int numberOfPessimistic = numberOfPessimisticUpdates.containsKey(name) ? numberOfPessimisticUpdates.get(name) : 0;

                final Collection<PendingUpdate> updates = pendingUpdates.get(persistentEntity);
                if(updates != null && !updates.isEmpty()) {
                    List<WriteModel<Document>> entityWrites = getWriteModelsForEntity(persistentEntity, writeModels);
                    for (PendingUpdate update : updates) {
                        update.run();

                        if(update.isVetoed()) continue;

                        Document updateDoc = (Document) update.getNativeEntry();
                        updateDoc.remove(MongoConstants.MONGO_ID_FIELD);
                        updateDoc = createSetAndUnsetDoc(updateDoc);
                        final Object nativeKey = update.getNativeKey();
                        final Document id = new Document(MongoConstants.MONGO_ID_FIELD, nativeKey);
                        MongoEntityPersister documentEntityPersister = (MongoEntityPersister) getPersister(persistentEntity);
                        final EntityAccess entityAccess = update.getEntityAccess();
                        if(documentEntityPersister.isVersioned(entityAccess)) {
                            Object currentVersion = documentEntityPersister.getCurrentVersion(entityAccess);
                            documentEntityPersister.incrementVersion(entityAccess);

                            // if the entity is versioned we add to the query the current version
                            // if the query doesn't match a result this means the document has been updated by
                            // another thread an an optimistic locking exception should be thrown
                            id.put(GormProperties.VERSION, currentVersion);
                            numberOfOptimistic++;
                        }
                        else {
                            numberOfPessimistic++;
                        }
                        final UpdateOptions options = new UpdateOptions();

                        entityWrites.add(new UpdateOneModel<Document>(id, updateDoc, options.upsert(false)));

                        final List cascadeOperations = update.getCascadeOperations();
                        addPostFlushOperations(cascadeOperations);
                    }
                }
                numberOfOptimisticUpdates.put(name, numberOfOptimistic);
                numberOfPessimisticUpdates.put(name, numberOfPessimistic);

            }


            for (PersistentEntity persistentEntity : pendingDeletes.keySet()) {
                final Collection<PendingDelete> deletes = pendingDeletes.get(persistentEntity);
                if(deletes != null && !deletes.isEmpty()) {
                    List<WriteModel<Document>> entityWrites = getWriteModelsForEntity(persistentEntity, writeModels);
                    List<Object> nativeKeys = new ArrayList<Object>();
                    for (PendingDelete delete : deletes) {
                        delete.run();

                        if(delete.isVetoed()) continue;

                        final Object k = delete.getNativeKey();
                        if(k != null) {
                            if(k instanceof Document) {
                                entityWrites.add(new DeleteManyModel<Document>((Document)k));
                            }
                            else {
                                nativeKeys.add(k);
                            }
                        }

                        final List cascadeOperations = delete.getCascadeOperations();
                        addPostFlushOperations(cascadeOperations);
                    }
                    entityWrites.add(new DeleteManyModel<Document>(new Document( MongoConstants.MONGO_ID_FIELD, new Document(BsonQuery.IN_OPERATOR, nativeKeys))));
                }
            }


            for (PersistentEntity persistentEntity : writeModels.keySet()) {
                com.mongodb.client.MongoCollection collection = getCollection(persistentEntity);
                final WriteConcern wc = getWriteConcern();
                if(wc != null) {
                    collection = collection.withWriteConcern(wc);
                }
                final List<WriteModel<Document>> writes = writeModels.get(persistentEntity);
                if(!writes.isEmpty()) {

                    final com.mongodb.bulk.BulkWriteResult bulkWriteResult = collection
                            .bulkWrite(writes);

                    if( !bulkWriteResult.wasAcknowledged() ) {
                        errorOccured = true;
                        throw new DataIntegrityViolationException("Write operation was not acknowledged");
                    }
                    else {
                        final int matchedCount = bulkWriteResult.getMatchedCount();
                        final String name = persistentEntity.getName();
                        final Integer numOptimistic = numberOfOptimisticUpdates.get(name);
                        final Integer numPessimistic = numberOfPessimisticUpdates.get(name);
                        final int no = numOptimistic != null ? numOptimistic : 0;
                        final int pe = numPessimistic != null ? numPessimistic : 0;
                        if((matchedCount - pe) != no) {
                            setFlushMode(FlushModeType.COMMIT);
                            throw new OptimisticLockingException(persistentEntity, null);
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

    protected Document createSetAndUnsetDoc(Document updateDoc) {
        final Set<String> keys = updateDoc.keySet();
        final Document unsets = new Document();
        for (String key : keys) {
            final Object v = updateDoc.get(key);
            if(v == null) {
                unsets.put(key, "");
            }
        }
        for (String key : unsets.keySet()) {
            updateDoc.remove(key);
        }
        updateDoc = new Document(MONGO_SET_OPERATOR, updateDoc);
        if(!unsets.isEmpty()) {
            updateDoc.put(MONGO_UNSET_OPERATOR, unsets);
        }
        return updateDoc;
    }

    protected List<WriteModel<Document>> getWriteModelsForEntity(PersistentEntity persistentEntity, Map<PersistentEntity, List<WriteModel<Document>>> writeModels) {
        PersistentEntity key = persistentEntity.isRoot() ? persistentEntity : persistentEntity.getRootEntity();
        List<WriteModel<Document>> entityWrites = writeModels.get(key);
        if(entityWrites == null) {
            entityWrites = new ArrayList<WriteModel<Document>>();
            writeModels.put(key, entityWrites);
        }
        return entityWrites;
    }

    @Override
    protected void flushPendingUpdates(Map<PersistentEntity, Collection<PendingUpdate>> updates) {
        // noop, ignore
    }


    @Override
    public void disconnect() {
        super.disconnect();
    }


    @Override
    protected Persister createPersister(@SuppressWarnings("rawtypes") Class cls, MappingContext mappingContext) {
        final PersistentEntity entity = mappingContext.getPersistentEntity(cls.getName());
        return entity == null ? null : new MongoEntityPersister(mappingContext, entity, this, publisher);
    }

    @Override
    protected Transaction<MongoClient> beginTransactionInternal() {
        return new SessionOnlyTransaction<MongoClient>(getNativeInterface(), this);
    }


    @Override
    public void delete(Iterable objects) {
        final Map<PersistentEntity, List> toDelete = getDeleteMap(objects);

        for (final PersistentEntity persistentEntity : toDelete.keySet()) {
            final MongoQuery query = new MongoQuery(this, persistentEntity);
            query.in(MongoEntityPersister.MONGO_ID_FIELD, toDelete.get(persistentEntity));
            final Document mongoQuery = query.getMongoQuery();
            final EntityPersister persister = (EntityPersister)getPersister(persistentEntity);
            addPendingDelete(new PendingDeleteAdapter<Object, Object>(persistentEntity, mongoQuery, null) {
                @Override
                public void run() {
                    for (Object o : toDelete.get(persistentEntity)) {
                        if( !persister.cancelDelete(persistentEntity, createEntityAccess(persistentEntity, o)) ) {
                            clear(o);
                        }
                    }
                }
            });
        }

    }


    protected Map<PersistentEntity, List> getDeleteMap(Iterable objects) {
        // sort the objects into sets by Persister, in case the objects are of different types.
        Map<PersistentEntity, List> toDelete = new HashMap<PersistentEntity, List>();
        for (Object object : objects) {
            if (object == null) {
                continue;
            }
            final PersistentEntity p = getMappingContext().getPersistentEntity(object.getClass().getName());
            if (p == null) {
                continue;
            }
            List listForPersister = toDelete.get(p);
            if (listForPersister == null) {
                toDelete.put(p, listForPersister = new ArrayList());
            }
            Serializable id = getObjectIdentifier(object);
            if(id != null) {
                listForPersister.add(id);
            }
        }
        return toDelete;
    }


    @Override
    public long deleteAll(QueryableCriteria criteria) {
        final PersistentEntity entity = criteria.getPersistentEntity();
        final Document nativeQuery = buildNativeDocumentQueryFromCriteria(criteria, entity);

        final com.mongodb.client.MongoCollection collection = getCollection(entity);
        final DeleteResult deleteResult = collection.deleteMany(nativeQuery);
        if( deleteResult.wasAcknowledged() ) {
            return deleteResult.getDeletedCount();
        }
        else {
            return 0;
        }
    }



    @Override
    public long updateAll(QueryableCriteria criteria, Map<String, Object> properties) {
        final PersistentEntity entity = criteria.getPersistentEntity();
        final Document nativeQuery = buildNativeDocumentQueryFromCriteria(criteria, entity);
        final com.mongodb.client.MongoCollection collection = getCollection(entity);
        final UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);
        final UpdateResult updateResult = collection.updateMany(nativeQuery, new Document("$set", properties), updateOptions);
        if(updateResult.wasAcknowledged()) {
            try {
                return updateResult.getModifiedCount();
            } catch (UnsupportedOperationException e) {
                // not supported on versions of MongoDB earlier than 2.6
                return -1;
            }
        }
        else {
            return 0;
        }
    }

    @Override
    public Object decode(Class type, Object nativeObject) {
        if(nativeObject instanceof FindIterable) {
            return decode(type, ((FindIterable) nativeObject).first());
        }
        else if( nativeObject instanceof Document ){
            Document dbo = (Document) nativeObject;
            Serializable key = (Serializable)dbo.get(AbstractMongoObectEntityPersister.MONGO_ID_FIELD);
            final Persister persister = getPersister(type);
            final MongoEntityPersister mongoEntityPersister = (MongoEntityPersister) persister;
            return mongoEntityPersister.createObjectFromNativeEntry(mongoEntityPersister.getPersistentEntity(), key, dbo);
        }
        return null;
    }

    private Document buildNativeDocumentQueryFromCriteria(QueryableCriteria criteria, PersistentEntity entity) {
        MongoQuery mongoQuery = new MongoQuery(this, entity);
        List<Query.Criterion> criteriaList = criteria.getCriteria();

        for(Query.Criterion c : criteriaList) {
            mongoQuery.add(c);
        }

        return mongoQuery.getMongoQuery();
    }
}

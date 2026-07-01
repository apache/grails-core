/* Copyright (C) 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.mongo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import org.springframework.context.ApplicationEventPublisher;

import org.grails.datastore.mapping.core.AbstractSession;
import org.grails.datastore.mapping.core.impl.PendingOperation;
import org.grails.datastore.mapping.document.config.DocumentMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.mongo.config.MongoCollection;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.grails.datastore.mapping.transactions.SessionOnlyTransaction;
import org.grails.datastore.mapping.transactions.Transaction;

/**
 * Abstract implementation on the {@link org.grails.datastore.mapping.core.Session} interface for MongoDB
 *
 * @author Graeme Rocher
 * @since 4.1
 */
public abstract class AbstractMongoSession extends AbstractSession<MongoClient> {
    public static final String MONGO_SET_OPERATOR = "$set";
    public static final String MONGO_UNSET_OPERATOR = "$unset";
    protected static final Map<PersistentEntity, WriteConcern> declaredWriteConcerns = new ConcurrentHashMap<>();

    protected final String defaultDatabase;
    protected MongoDatastore mongoDatastore;
    protected WriteConcern writeConcern = null;
    protected boolean errorOccured = false;
    // Confined to the owning session's single thread (per the AbstractSession contract), so it needs
    // no synchronization; it is null unless a server-side transaction is active on this session.
    protected ClientSession clientSession;
    protected Map<PersistentEntity, String> mongoCollections = new ConcurrentHashMap<>();
    protected Map<PersistentEntity, String> mongoDatabases = new ConcurrentHashMap<>();

    public AbstractMongoSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher) {
        this(datastore, mappingContext, publisher, false);
    }

    public AbstractMongoSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher, boolean stateless) {
        super(datastore, mappingContext, publisher, stateless);
        mongoDatastore = datastore;
        this.defaultDatabase = getDocumentMappingContext().getDefaultDatabaseName();
    }

    @Override
    public boolean hasTransaction() {
        // the session is the transaction, since MongoDB doesn't support them directly
        return true;
    }

    @Override
    public MongoDatastore getDatastore() {
        return (MongoDatastore) super.getDatastore();
    }

    @Override
    public void flush() {
        flush(this.getWriteConcern());
    }

    public abstract void flush(WriteConcern writeConcern);

    /**
     * @return The name of the default database
     */
    public String getDefaultDatabase() {
        return defaultDatabase;
    }

    /**
     * @return The name of the default database
     */
    public String getDatabase(PersistentEntity entity) {

        final String name = mongoDatabases.get(entity);
        if (name != null) {
            return name;
        }
        return getDatastore().getDatabaseName(entity);
    }

    /**
     * Sets the WriteConcern to use for the session
     *
     * @param writeConcern The WriteConcern to use
     */
    public void setWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    /**
     * Obtains the WriteConcern to use for the session
     * @return the WriteConcern
     */
    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    public WriteConcern getDeclaredWriteConcern(PersistentEntity entity) {
        return getDeclaredWriteConcern(this.writeConcern, entity);
    }

    private WriteConcern getDeclaredWriteConcern(WriteConcern defaultConcern, PersistentEntity entity) {
        WriteConcern writeConcern = declaredWriteConcerns.get(entity);
        if (writeConcern == null) {
            Object mappedForm = entity.getMapping().getMappedForm();
            if (mappedForm instanceof MongoCollection) {
                MongoCollection mc = (MongoCollection) mappedForm;
                writeConcern = mc.getWriteConcern();
                if (writeConcern == null) {
                    writeConcern = defaultConcern;
                }
            }

            if (writeConcern != null) {
                declaredWriteConcerns.put(entity, writeConcern);
            }
        }
        return writeConcern;
    }

    public MongoClient getNativeInterface() {
        return ((MongoDatastore) getDatastore()).getMongoClient();
    }

    public DocumentMappingContext getDocumentMappingContext() {
        return (DocumentMappingContext) getMappingContext();
    }

    public String getCollectionName(PersistentEntity entity) {
        entity = entity.isRoot() ? entity : entity.getRootEntity();
        return mongoCollections.containsKey(entity) ? mongoCollections.get(entity) : mongoDatastore.getCollectionName(entity);
    }

    /**
     * Use the given collection for the given entity
     *
     * @param entity The entity
     * @param collectionName The collection
     * @return The previous collection that was used
     */
    public String useCollection(PersistentEntity entity, String collectionName) {
        entity = entity.isRoot() ? entity : entity.getRootEntity();
        String current = mongoCollections.containsKey(entity) ? mongoCollections.get(entity) : mongoDatastore.getCollectionName(entity);
        mongoCollections.put(entity, collectionName);
        return current;
    }

    /**
     * Use the given database name for the given entity
     *
     * @param entity The entity name
     * @param databaseName The database name
     * @return The name of the previous database
     */
    public String useDatabase(PersistentEntity entity, String databaseName) {
        if (databaseName == null) {
            return mongoDatabases.put(entity, getDefaultDatabase());
        }
        else {
            return mongoDatabases.put(entity, databaseName);
        }
    }

    public com.mongodb.client.MongoCollection<Document> getCollection(PersistentEntity entity) {
        if (entity.isRoot()) {
            final String database = getDatabase(entity);
            final String collectionName = getCollectionName(entity);
            return getNativeInterface()
                    .getDatabase(database)
                    .getCollection(collectionName)
                    .withCodecRegistry(getDatastore().getCodecRegistry());
        }
        else {
            final PersistentEntity root = entity.getRootEntity();
            return getCollection(root);
        }
    }

    @Override
    public MongoMappingContext getMappingContext() {
        return (MongoMappingContext) super.getMappingContext();
    }

    /**
     * @return the active {@link ClientSession} for the current MongoDB transaction, or {@code null}
     * if no server-side transaction is in progress
     */
    public ClientSession getClientSession() {
        return clientSession;
    }

    /**
     * @return {@code true} if a server-side MongoDB transaction is currently active on this session
     */
    public boolean hasActiveTransaction() {
        return clientSession != null && clientSession.hasActiveTransaction();
    }

    /**
     * Detaches the {@link ClientSession} from this session once its transaction has completed.
     * Called by {@link MongoTransaction} after commit or rollback closes the session.
     */
    void clearClientSession() {
        this.clientSession = null;
    }

    /**
     * Closes and detaches the {@link ClientSession} if one is still attached. Used defensively when a
     * transaction did not complete through {@link MongoTransaction}, so a session is never leaked.
     */
    protected void closeClientSessionQuietly() {
        if (clientSession != null) {
            try {
                clientSession.close();
            }
            catch (RuntimeException ignored) {
                // best effort
            }
            finally {
                clientSession = null;
            }
        }
    }

    @Override
    public void disconnect() {
        try {
            closeClientSessionQuietly();
        }
        finally {
            super.disconnect();
        }
    }

    @Override
    protected Transaction beginTransactionInternal() {
        if (getDatastore().isTransactionsEnabled()) {
            // Defensive: if a previous transaction did not complete cleanly, close its orphaned
            // session before starting a new one so it cannot leak.
            closeClientSessionQuietly();
            ClientSession session = getNativeInterface().startSession();
            try {
                session.startTransaction();
            }
            catch (RuntimeException e) {
                session.close();
                throw e;
            }
            this.clientSession = session;
            return new MongoTransaction(this, session);
        }
        return new SessionOnlyTransaction<>(getNativeInterface(), this);
    }

    // The driver exposes a session-less and a ClientSession overload for every operation, and the
    // session argument cannot be null. These helpers pass the ClientSession only while a transaction
    // is active on this session (see hasActiveTransaction()), and use the session-less overload
    // otherwise, so call sites stay readable.

    @SuppressWarnings({"rawtypes", "unchecked"})
    public BulkWriteResult bulkWrite(com.mongodb.client.MongoCollection collection, List<? extends WriteModel> writes) {
        return hasActiveTransaction() ? collection.bulkWrite(clientSession, writes) : collection.bulkWrite(writes);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public DeleteResult deleteMany(com.mongodb.client.MongoCollection collection, Bson filter) {
        return hasActiveTransaction() ? collection.deleteMany(clientSession, filter) : collection.deleteMany(filter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public UpdateResult updateMany(com.mongodb.client.MongoCollection collection, Bson filter, Bson update, UpdateOptions options) {
        return hasActiveTransaction() ? collection.updateMany(clientSession, filter, update, options) : collection.updateMany(filter, update, options);
    }

    public <T> FindIterable<T> find(com.mongodb.client.MongoCollection<T> collection, Bson filter) {
        return hasActiveTransaction() ? collection.find(clientSession, filter) : collection.find(filter);
    }

    public <R> FindIterable<R> find(com.mongodb.client.MongoCollection<?> collection, Bson filter, Class<R> resultClass) {
        return hasActiveTransaction() ? collection.find(clientSession, filter, resultClass) : collection.find(filter, resultClass);
    }

    public <T> AggregateIterable<T> aggregate(com.mongodb.client.MongoCollection<T> collection, List<? extends Bson> pipeline) {
        return hasActiveTransaction() ? collection.aggregate(clientSession, pipeline) : collection.aggregate(pipeline);
    }

    public <T> T findOneAndDelete(com.mongodb.client.MongoCollection<T> collection, Bson filter) {
        return hasActiveTransaction() ? collection.findOneAndDelete(clientSession, filter) : collection.findOneAndDelete(filter);
    }

    public <T> T findOneAndDelete(com.mongodb.client.MongoCollection<T> collection, Bson filter, FindOneAndDeleteOptions options) {
        return hasActiveTransaction() ? collection.findOneAndDelete(clientSession, filter, options) : collection.findOneAndDelete(filter, options);
    }

    public long countDocuments(com.mongodb.client.MongoCollection<?> collection, Bson filter) {
        return hasActiveTransaction() ? collection.countDocuments(clientSession, filter) : collection.countDocuments(filter);
    }

    /**
     * Decodes the given entity type from the given native object type
     *
     * @param type A GORM entity type
     * @param nativeObject A native MongoDB object type (Document, FinderIterable etc.)
     * @param <T> The concrete type of the entity
     * @return An instanceof the type or null if it doesn't exist
     */
    public abstract <T> T decode(Class<T> type, Object nativeObject);

    protected void addPostFlushOperations(List<PendingOperation> cascadeOperations) {
        for (PendingOperation cascadeOperation : cascadeOperations) {
            addPostFlushOperation(cascadeOperation);
        }
    }
}

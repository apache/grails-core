/* Copyright (C) 2015-2025 the original author or authors.
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

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.grails.datastore.mapping.core.AbstractSession;
import org.grails.datastore.mapping.core.impl.PendingOperation;
import org.grails.datastore.mapping.document.config.DocumentMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.mongo.config.MongoCollection;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation on the {@link org.grails.datastore.mapping.core.Session} interface for MongoDB
 *
 * @author Graeme Rocher
 * @since 4.1
 */
public abstract class AbstractMongoSession extends AbstractSession<MongoClient> {
    public static final String MONGO_SET_OPERATOR = "$set";
    public static final String MONGO_UNSET_OPERATOR = "$unset";
    protected static final Map<PersistentEntity, WriteConcern> declaredWriteConcerns = new ConcurrentHashMap<PersistentEntity, WriteConcern>();

    protected final String defaultDatabase;
    protected MongoDatastore mongoDatastore;
    protected WriteConcern writeConcern = null;
    protected boolean errorOccured = false;
    protected Map<PersistentEntity, String> mongoCollections = new ConcurrentHashMap<PersistentEntity, String>();
    protected Map<PersistentEntity, String> mongoDatabases = new ConcurrentHashMap<PersistentEntity, String>();

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
        if(name != null) {
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
        return ((MongoDatastore)getDatastore()).getMongoClient();
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
        if(databaseName == null) {
            return mongoDatabases.put(entity, getDefaultDatabase());
        }
        else {
            return mongoDatabases.put(entity, databaseName);
        }
    }

    public com.mongodb.client.MongoCollection<Document> getCollection(PersistentEntity entity) {
        if(entity.isRoot()) {
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

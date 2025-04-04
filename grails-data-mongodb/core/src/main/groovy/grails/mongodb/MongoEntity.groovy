/*
 * Copyright 2015 original authors
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
package grails.mongodb

import com.mongodb.ReadPreference
import com.mongodb.client.AggregateIterable
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.FindOneAndDeleteOptions
import grails.mongodb.api.MongoAllOperations
import groovy.transform.CompileStatic
import org.bson.Document
import org.bson.conversions.Bson
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.mongo.MongoCriteriaBuilder
import org.grails.datastore.gorm.mongo.api.MongoStaticApi
import org.grails.datastore.gorm.schemaless.DynamicAttributes
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.SessionImplementor
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister

import java.util.function.Function

/**
 * Enhances the default {@link GormEntity} class with MongoDB specific methods
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
trait MongoEntity<D> implements GormEntity<D>, DynamicAttributes {


    /**
     * Allows accessing to dynamic properties with the dot operator
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    @Override
    def propertyMissing(String name) {
        DynamicAttributes.super.getAt(name)
    }

    /**
     * Allows setting a dynamic property via the dot operator
     * @param instance The instance
     * @param name The property name
     * @param val The value
     */
    def propertyMissing(String name, val) {
        DynamicAttributes.super.putAt(name, val)
    }


    /**
     * Return the DBObject instance for the entity
     *
     * @deprecated use dynamic properties instead
     * @param instance The instance
     * @return The DBObject instance
     */
    @Deprecated
    Document getDbo() {
        AbstractMongoSession session = (AbstractMongoSession)AbstractDatastore.retrieveSession(MongoDatastore)
        // check first for embedded cached entries
        SessionImplementor<Document> si = (SessionImplementor<Document>) session;
        def persistentEntity = session.mappingContext.getPersistentEntity(getClass().name)
        Document dbo = (Document)si.getCachedEntry(persistentEntity, MongoEntityPersister.createEmbeddedCacheEntryKey(this))
        if(dbo != null) return dbo
        // otherwise check if instance is contained within session
        if (!session.contains(this)) {
            dbo = new Document()
            si.cacheEntry(persistentEntity, MongoEntityPersister.createInstanceCacheEntryKey(this), dbo)
            return dbo
        }

        EntityPersister persister = (EntityPersister)session.getPersister(this)
        def id = persister.getObjectIdentifier(this)
        dbo = (Document)((SessionImplementor)session).getCachedEntry(persister.getPersistentEntity(), id)
        if (dbo == null) {
            MongoCollection<Document> coll = session.getCollection(persistentEntity)
            dbo = coll.find((Bson)new Document(MongoEntityPersister.MONGO_ID_FIELD, id))
                    .limit(1)
                    .first()

        }
        return dbo
    }
    /**
     * Finds all of the entities in the collection.
     *
     * @param filter the query filter
     * @return the find iterable interface
     */
    static FindIterable<D> find(Bson filter) {
        currentMongoStaticApi().find(filter)
    }

    /**
     * Atomically find a document and remove it.
     *
     * @param filter the query filter to find the document with
     * @return the document that was removed.  If no documents matched the query filter, then null will be returned
     */
    static D findOneAndDelete(Bson filter) {
        currentMongoStaticApi().findOneAndDelete(filter)
    }

    /**
     * Atomically find a document and remove it.
     *
     * @param filter the query filter to find the document with
     * @return the document that was removed.  If no documents matched the query filter, then null will be returned
     */
    static D findOneAndDelete(Bson filter, FindOneAndDeleteOptions options) {
        currentMongoStaticApi().findOneAndDelete(filter)
    }

    /**
     * Counts the number of the entities in the collection.
     *
     * @param filter the query filter
     * @return the find iterable interface
     */
    static Number count(Bson filter) {
        currentMongoStaticApi().count(filter)
    }

    /**
     * @return Custom MongoDB criteria builder
     */
    static MongoCriteriaBuilder createCriteria() {
        currentMongoStaticApi().createCriteria()
    }

    /**
     * @return The database for this domain class
     */
    static MongoDatabase getDB() {
        currentMongoStaticApi().getDB()
    }

    /**
     * @return The name of the Mongo collection that entity maps to
     */
    static String getCollectionName() {
        currentMongoStaticApi().getCollectionName()
    }

    /**
     * The actual collection that this entity maps to.
     *
     * @return The actual collection
     */
    static MongoCollection<Document> getCollection() {
        currentMongoStaticApi().getCollection()
    }

    /**
     * Use the given collection for this entity for the scope of the closure call
     * @param collectionName The collection name
     * @param callable The callable
     * @return The result of the closure
     */
    static <T> T withCollection(String collectionName, Closure<T> callable) {
        currentMongoStaticApi().withCollection(collectionName, callable)
    }

    /**
     * Use the given collection for this entity for the scope of the session
     *
     * @param collectionName The collection name
     * @return The previous collection name
     */
    static String useCollection(String collectionName) {
        currentMongoStaticApi().useCollection(collectionName)
    }

    /**
     * Use the given database for this entity for the scope of the closure call
     * @param databaseName The collection name
     * @param callable The callable
     * @return The result of the closure
     */
    static <T> T withDatabase(String databaseName, Closure<T> callable) {
        currentMongoStaticApi().withDatabase(databaseName, callable)
    }

    /**
     * Use the given database for this entity for the scope of the session
     *
     * @param databaseName The collection name
     * @return The previous database name
     */
    static String useDatabase(String databaseName) {
        currentMongoStaticApi().useDatabase(databaseName)
    }

    /**
     * Counts the number of hits
     * @param query The query
     * @return The hit count
     */
    static int countHits(String query) {
        currentMongoStaticApi().countHits(query)
    }

    /**
     * Execute a MongoDB aggregation pipeline. Note that the pipeline should return documents that represent this domain class as each return document will be converted to a domain instance in the result set
     *
     * @param pipeline The pipeline
     * @param doWithAggregate The function to transform the aggregate iterable (optional)
     * @return A mongodb result list
     */
    static List<D> aggregate(List pipeline, Function<AggregateIterable, AggregateIterable> doWithAggregate = Function.identity()) {
        currentMongoStaticApi().aggregate(pipeline, doWithAggregate)
    }

    /**
     * Execute a MongoDB aggregation pipeline. Note that the pipeline should return documents that represent this domain class as each return document will be converted to a domain instance in the result set
     *
     * @param pipeline The pipeline
     * @param doWithAggregate The function to transform the aggregate iterable (optional)
     * @return A mongodb result list
     */
    static List<D> aggregate(List pipeline, Function<AggregateIterable, AggregateIterable> doWithAggregate, ReadPreference readPreference) {
        currentMongoStaticApi().aggregate(pipeline, doWithAggregate, readPreference)
    }

    /**
     * Search for entities using the given query
     *
     * @param query The query
     * @return The results
     */
    static List<D> search(String query, Map options = Collections.emptyMap()) {
        currentMongoStaticApi().search(query, options)
    }

    /**
     * Searches for the top results ordered by the MongoDB score
     *
     * @param query The query
     * @param limit The maximum number of results. Defaults to 5.
     * @return The results
     */
    static List<D> searchTop(String query, int limit = 5, Map options = Collections.emptyMap()) {
        currentMongoStaticApi().searchTop(query, limit, options)
    }

    /**
     * Perform an operation with the given connection
     *
     * @param connectionName The name of the connection
     * @param callable The operation
     * @return The return value of the closure
     */
    static <T> T withConnection(String connectionName, @DelegatesTo(MongoAllOperations)Closure callable) {
        def staticApi = GormEnhancer.findStaticApi(this, connectionName)
        return (T)staticApi.withNewSession {
            callable.setDelegate(staticApi)
            return callable.call()
        }
    }

    private static MongoStaticApi currentMongoStaticApi() {
        (MongoStaticApi)GormEnhancer.findStaticApi(this)
    }

}
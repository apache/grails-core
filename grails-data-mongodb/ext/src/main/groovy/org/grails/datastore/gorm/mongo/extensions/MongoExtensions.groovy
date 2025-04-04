/*
 * Copyright 2015-2025 the original author or authors.
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
package org.grails.datastore.gorm.mongo.extensions

import com.mongodb.BasicDBObject
import com.mongodb.DBObject
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.client.*
import com.mongodb.client.model.*
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.lang.Nullable
import groovy.transform.CompileStatic
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoConstants
import org.grails.datastore.mapping.mongo.engine.AbstractMongoObectEntityPersister
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister
import org.grails.datastore.mapping.mongo.query.MongoQuery

import static java.util.Arrays.asList

/**
 * Extra methods for MongoDB API
 *
 * This extension makes it possible to use Groovy's map syntax instead of having to construct {@link org.bson.Document} instances
 *
 * @author Graeme Rocher
 * @since 4.0.5
 */
@CompileStatic
class MongoExtensions {


    static <T> T asType(Document document, Class<T> cls) {
        if(Document.isAssignableFrom(cls)) {
            return (T)document
        }
        else {
            def datastore = GormEnhancer.findDatastore(cls)
            AbstractMongoSession session = (AbstractMongoSession)datastore.currentSession
            if (session != null) {
                return session.decode(cls, document)
            }
            else if(cls.name == 'grails.converters.JSON') {
                return cls.newInstance( document )
            }
            else {
                throw new IllegalArgumentException("Cannot convert DBOject [$document] to writer type $cls. Type is not a persistent entity")
            }
        }
    }

    static <T> T asType(FindIterable iterable, Class<T> cls) {
        if(FindIterable.isAssignableFrom(cls)) {
            return (T)iterable
        }
        else {
            def datastore = GormEnhancer.findDatastore(cls)
            AbstractMongoSession session = (AbstractMongoSession)datastore.currentSession

            if (session != null) {
                return session.decode(cls, iterable)
            }
            else {
                throw new IllegalArgumentException("Cannot convert DBOject [$iterable] to writer type $cls. Type is not a persistent entity")
            }
        }
    }

    static <T> List<T> toList(FindIterable iterable, Class<T> cls) {
        def datastore = GormEnhancer.findDatastore(cls)
        AbstractMongoSession session = (AbstractMongoSession)datastore.currentSession

        MongoEntityPersister p = (MongoEntityPersister)session.getPersister(cls)
        if (p)
            return new MongoQuery.MongoResultList(((FindIterable<Document>)iterable).iterator(),0,p)
        else {
            throw new IllegalArgumentException("Cannot convert DBCursor [$iterable] to writer type $cls. Type is not a persistent entity")
        }
    }

    @CompileStatic
    static DBObject toDBObject(Document document) {
        def object = new BasicDBObject()
        for(key in document.keySet()) {
            def value = document.get(key)
            if(value instanceof Document) {
                value = toDBObject((Document)value)
            }
            else if(value instanceof Collection) {
                Collection col = (Collection)value
                Collection newCol = []
                for(i in col) {
                    if(i instanceof Document) {
                        newCol << toDBObject((Document)i)
                    }
                    else {
                        newCol << i
                    }
                }
                value = newCol
            }
            object.put(key, value)
        }
        return object
    }

    /**
     * Adds a method to return a collection using the dot syntax
     *
     * @param db The database object
     * @param name The collection name
     * @return A {@link MongoCollection}
     */
    static Object propertyMissing(MongoDatabase db, String name) {
        db.getCollection(name)
    }

    /**
     * Adds a method to return a collection using the dot syntax
     *
     * @param db The database object
     * @param name The collection name
     * @return A {@link MongoCollection}
     */
    static Object getAt(MongoDatabase db, String name) {
        db.getCollection(name)
    }

    private static Bson toBson(Map<String, Object> map) {
        if (map == null) {
            return null
        }
        (Bson) new Document(map)
    }

    private static List<Bson> toBson(List<? extends Map<String, Object>> list) {
        if (list == null) {
            return null
        }
        list.collect { toBson(it) }
    }


    /************** FindIterable Extensions *************/

    static FindIterable<Document> filter(FindIterable<Document> iterable, @Nullable Map<String, Object> filter) {
        iterable.filter(toBson(filter))
    }
    static FindIterable<Document> projection(FindIterable<Document> iterable, @Nullable Map<String, Object> projection) {
        iterable.projection(toBson(projection))
    }
    static FindIterable<Document> sort(FindIterable<Document> iterable, @Nullable Map<String, Object> sort) {
        iterable.sort(toBson(sort))
    }
    static FindIterable<Document> hint(FindIterable<Document> iterable, @Nullable Map<String, Object> hint) {
        iterable.hint(toBson(hint))
    }
    static FindIterable<Document> max(FindIterable<Document> iterable, @Nullable Map<String, Object> max) {
        iterable.max(toBson(max))
    }
    static FindIterable<Document> min(FindIterable<Document> iterable, @Nullable Map<String, Object> min) {
        iterable.min(toBson(min))
    }

    /************** DistinctIterable Extensions *************/

    static DistinctIterable<Document> filter(DistinctIterable<Document> iterable, Map<String, Object> filter) {
        iterable.filter(toBson(filter))
    }

    /************** MongoDatabase Extensions *************/

    static MongoIterable<String> getCollectionNames(MongoDatabase db) {
        db.listCollectionNames()
    }

    static MongoCollection<Document> createAndGetCollection(MongoDatabase db, final String collectionName, final Map<String, Object> options) {
        CreateCollectionOptions createCollectionOptions = MongoConstants.mapToObject(CreateCollectionOptions, options)
        db.createCollection(collectionName, createCollectionOptions)
        db.getCollection(collectionName)
    }

    /************** MongoCollection Extensions *************/

    static long count(MongoCollection<Document> collection) {
        collection.countDocuments()
    }

    static long count(MongoCollection<Document> collection, final Map<String, Object> query) {
        getCount(collection, query)
    }

    static long count(MongoCollection<Document> collection, final Map<String, Object> query, final ReadPreference readPreference) {
        getCount(collection, query, readPreference)
    }

    static long count(MongoCollection<Document> collection, final Map query, final Map<String, Object> options) {
        getCount(collection, query, options)
    }

    static long getCount(MongoCollection<Document> collection, final Map<String, Object> query) {
        collection.countDocuments(toBson(query))
    }

    static long getCount(MongoCollection<Document> collection, final Map<String, Object> query, final ReadPreference readPreference) {
        collection
                .withReadPreference(readPreference)
                .countDocuments(toBson(query))
    }

    static long getCount(MongoCollection<Document> collection, final Map<String, Object> query, final  Map<String, Object> options) {
        collection.countDocuments(toBson(query), MongoConstants.mapToObject(CountOptions, options))
    }

    static String getName(MongoCollection<Document> collection) {
        collection.namespace.collectionName
    }

    static Document findOne(MongoCollection<Document> collection, final Map<String, Object> query) {
        collection.find(toBson(query)).limit(1).first()
    }

    static Document findOne(MongoCollection<Document> collection, ObjectId id) {
        def query = new Document()
        query.put(AbstractMongoObectEntityPersister.MONGO_ID_FIELD, id)
        collection.find((Bson)query)
                .limit(1)
                .first()
    }

    static Document findOne(MongoCollection<Document> collection, CharSequence id) {
        def query = new Document()
        query.put(AbstractMongoObectEntityPersister.MONGO_ID_FIELD, id)
        collection.find((Bson)query)
                .limit(1)
                .first()
    }

    static <T> T findOne(MongoCollection<Document> collection, Serializable id, Class<T> type) {
        def query = new Document()
        query.put(AbstractMongoObectEntityPersister.MONGO_ID_FIELD, id)
        collection
                .find((Bson)query, type)
                .limit(1)
                .first()
    }

    static Document findOne(MongoCollection<Document> collection, final Map<String,Object> query, final Map<String, Object> projection) {
        collection
                .find(toBson(query))
                .projection(toBson(projection))
                .limit(1)
                .first()
    }

    static Document findOne(MongoCollection<Document> collection, final Map<String,Object> query, final Map<String,Object> projection, final Map<String,Object> sort) {
        collection
                .find(toBson(query))
                .projection(toBson(projection))
                .sort(toBson(sort))
                .limit(1)
                .first()
    }

    static Document findOne(MongoCollection<Document> collection) {
        collection
                .find()
                .first()
    }

    static Document findOne(MongoCollection<Document> collection, final Map<String,Object> query, final Map<String,Object> projection, final ReadPreference readPreference) {
        collection
                .withReadPreference(readPreference)
                .find(toBson(query))
                .projection(toBson(projection))
                .limit(1)
                .first()
    }

    static Document findOne(MongoCollection<Document> collection,
                            final Map<String,Object> query,
                            final Map<String,Object> projection,
                            final Map<String,Object> sort,
                            final ReadPreference readPreference) {
        collection
                .withReadPreference(readPreference)
                .find(toBson(query))
                .projection(toBson(projection))
                .sort(toBson(sort))
                .limit(1)
                .first()
    }

    static FindIterable<Document> find(MongoCollection<Document> collection, final Map<String, Object> query) {
        collection.find(toBson(query))
    }

    static <T>  FindIterable<T> find(MongoCollection<T> collection, final Map<String, Object> query, Class<T> type) {
        collection.find(toBson(query), type)
    }

    static FindIterable<Document> find(MongoCollection<Document> collection, final Map<String, Object> query, final Map<String, Object> projection) {
        collection.find(toBson(query))
                .projection(toBson(projection))
    }

    static AggregateIterable<Document> aggregate(final MongoCollection<Document> collection, final List<? extends Map<String, Object>> pipeline) {
        collection.aggregate(toBson(pipeline))
    }

    static <T> AggregateIterable<T> aggregate(final MongoCollection<Document> collection, List<? extends Map<String, Object>> pipeline, Class<T> resultClass) {
        collection.aggregate(toBson(pipeline), resultClass)
    }

    static DistinctIterable<Document> distinct(final MongoCollection<Document> collection, final String fieldName) {
        collection.distinct(fieldName, Document)
    }

    static DistinctIterable<Document> distinct(final MongoCollection<Document> collection, final String fieldName, final ReadPreference readPreference) {
        collection
                .withReadPreference(readPreference)
                .distinct(fieldName, Document)
    }

    static DistinctIterable<Document> distinct(final MongoCollection<Document> collection, final String fieldName, Map<String, Object> query) {
        collection.distinct(fieldName, Document)
                .filter(toBson(query))
    }

    static <T> DistinctIterable<T> distinct(final MongoCollection<Document> collection, final String fieldName, Map<String, Object> query, Class<T> resultClass) {
        collection.distinct(fieldName, resultClass)
                .filter(toBson(query))
    }

    static DistinctIterable<Document> distinct(final MongoCollection<Document> collection, final String fieldName, Map<String, Object> query, final ReadPreference readPreference) {
        collection
                .withReadPreference(readPreference)
                .distinct(fieldName, Document)
                .filter(toBson(query))
    }

    static ChangeStreamIterable<Document> watch(final MongoCollection<Document> collection, List<? extends Map<String, Object>> pipeline) {
        collection.watch(toBson(pipeline))
    }

    static <T> ChangeStreamIterable<T> watch(final MongoCollection<Document> collection, List<? extends Map<String, Object>> pipeline, Class<T> resultClass) {
        collection.watch(toBson(pipeline), resultClass)
    }

    static DeleteResult deleteMany(final MongoCollection<Document> collection, final Map<String,Object> query) {
        collection.deleteMany(toBson(query))
    }

    static DeleteResult remove(final MongoCollection<Document> collection, final Map<String,Object> query) {
        deleteMany collection, query
    }

    static MongoCollection<Document> rightShift(final MongoCollection<Document> collection, final Map<String, Object> query) {
        deleteMany collection, query
        return collection
    }

    static DeleteResult deleteMany(final MongoCollection<Document> collection, final Map<String,Object> query, final WriteConcern writeConcern) {
        collection
                .withWriteConcern(writeConcern)
                .deleteMany(toBson(query))
    }

    static DeleteResult deleteOne(final MongoCollection<Document> collection, final Map<String,Object> query) {
        collection.deleteOne(toBson(query))
    }

    static DeleteResult deleteOne(final MongoCollection<Document> collection, final Map<String,Object> query, final WriteConcern writeConcern) {
        collection
                .withWriteConcern(writeConcern)
                .deleteOne(toBson(query))
    }

    static DeleteResult deleteOne(final MongoCollection<Document> collection, final Map<String,Object> query, final Map<String, Object> options) {
        collection.deleteOne(toBson(query), MongoConstants.mapToObject(DeleteOptions, options))
    }

    static DeleteResult deleteMany(final MongoCollection<Document> collection, final Map<String,Object> query, final Map<String, Object> options) {
        collection.deleteMany(toBson(query), MongoConstants.mapToObject(DeleteOptions, options))
    }

    static UpdateResult updateOne(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update) {
        collection.updateOne(toBson(filter), toBson(update))
    }

    static UpdateResult update(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update) {
        collection.updateOne(toBson(filter), toBson(update))
    }

    static UpdateResult updateOne(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update, Map<String, Object> options) {
        collection.updateOne(toBson(filter), toBson(update), MongoConstants.mapToObject(UpdateOptions, options))
    }

    static UpdateResult update(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update, Map<String, Object> options) {
        collection.updateOne(toBson(filter), toBson(update), MongoConstants.mapToObject(UpdateOptions, options))
    }

    static UpdateResult updateOne(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update, UpdateOptions updateOptions) {
        collection.updateOne(toBson(filter), toBson(update), updateOptions)
    }

    static UpdateResult update(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update, UpdateOptions updateOptions) {
        collection.updateOne(toBson(filter), toBson(update), updateOptions)
    }

    static UpdateResult updateMany(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update) {
        collection.updateMany(toBson(filter), toBson(update))
    }

    static UpdateResult updateMany(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update, Map<String, Object> options) {
        collection.updateMany(toBson(filter), toBson(update), MongoConstants.mapToObject(UpdateOptions, options))
    }

    static UpdateResult updateMany(final MongoCollection<Document> collection, Map<String,Object> filter, Map<String, Object> update, UpdateOptions updateOptions) {
        collection.updateMany(toBson(filter), toBson(update), updateOptions)
    }

    static UpdateResult updateOne(final MongoCollection<Document> collection,
                                  final Map<String, Object> filter,
                                  final List<? extends Map<String, Object>> update) {
        collection.updateOne(toBson(filter), toBson(update))
    }

    static UpdateResult updateOne(final MongoCollection<Document> collection,
                                  final Map<String, Object> filter,
                                  final List<? extends Map<String, Object>> update,
                                  final Map<String, Object> options) {
        collection.updateOne(toBson(filter), toBson(update), MongoConstants.mapToObject(UpdateOptions, options))
    }

    static UpdateResult updateOne(final MongoCollection<Document> collection,
                                  final Map<String, Object> filter,
                                  final List<? extends Map<String, Object>> update,
                                  final UpdateOptions updateOptions) {
        collection.updateOne(toBson(filter), toBson(update), updateOptions)
    }

    static UpdateResult updateMany(final MongoCollection<Document> collection,
                                   final Map<String, Object> filter,
                                   final List<? extends Map<String, Object>> update) {
        collection.updateMany(toBson(filter), toBson(update))
    }

    static UpdateResult updateMany(final MongoCollection<Document> collection,
                                   final Map<String, Object> filter,
                                   final List<? extends Map<String, Object>> update,
                                   final Map<String, Object> options) {
        collection.updateMany(toBson(filter), toBson(update), MongoConstants.mapToObject(UpdateOptions, options))
    }

    static UpdateResult updateMany(final MongoCollection<Document> collection,
                                   final Map<String, Object> filter,
                                   final List<? extends Map<String, Object>> update,
                                   UpdateOptions updateOptions) {
        collection.updateMany(toBson(filter), toBson(update), updateOptions)
    }

    static void createIndex(MongoCollection<Document> collection, final Map<String, Object> keys, final String name) {
        collection.createIndex(toBson(keys), new IndexOptions().name(name))
    }

    static void createIndex(MongoCollection<Document> collection, final Map<String, Object> keys, final String name, final boolean unique) {
        collection.createIndex(toBson(keys), new IndexOptions().name(name).unique(unique))
    }

    static void createIndex(final MongoCollection<Document> collection, final Map<String, Object> keys) {
        collection.createIndex(toBson(keys))
    }

    static void createIndex(final MongoCollection<Document> collection, final Map<String, Object> keys, final IndexOptions options) {
        collection.createIndex(toBson(keys), options)
    }

    static void createIndex(final MongoCollection<Document> collection, final Map<String, Object> keys, final Map<String, Object> options) {
        collection.createIndex(toBson(keys), MongoConstants.mapToObject(IndexOptions, options))
    }

    static void dropIndex(final MongoCollection<Document> collection, final Map<String, Object> index) {
        collection.dropIndex(toBson(index))
    }

    static void dropIndex(final MongoCollection<Document> collection, final Map<String, Object> index, final Map<String, Object> options) {
        collection.dropIndex(toBson(index), MongoConstants.mapToObject(DropIndexOptions, options))
    }

    static void dropIndex(final MongoCollection<Document> collection, final Map<String, Object> index, DropIndexOptions dropIndexOptions) {
        collection.dropIndex(toBson(index), dropIndexOptions)
    }

    static void insert(final MongoCollection<Document> collection, final Map<String, Object> document) {
        insert(collection, asList(document))
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final Map<String, Object> document, final WriteConcern writeConcern) {
        insert(collection, asList(document), writeConcern);
    }

    static void insert(final MongoCollection<Document> collection, final Map<String, Object>... documents) {
        collection.insertMany documents.collect() { Map m -> new Document(m) } as List<Document>
    }

    static MongoCollection<Document> leftShift(final MongoCollection<Document> collection, final Map<String, Object>... documents) {
        insert(collection, documents)
        return collection
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final WriteConcern writeConcern, final Map<String, Object>... documents) {
        insert(collection, documents, writeConcern);
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final Map<String,Object>[] documents, final WriteConcern writeConcern) {
        insert(collection, asList(documents), writeConcern);
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final List<? extends Map<String, Object>> documents) {
        collection.insertMany documents.collect() { Map m -> new Document(m) } as List<Document>
        return collection
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final List<? extends Map<String, Object>> documents, final WriteConcern aWriteConcern) {
        return insert(collection, documents, aWriteConcern, null);
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final List<? extends Map<String, Object>> documents, final WriteConcern writeConcern, final InsertManyOptions insertOptions) {
        collection
                .withWriteConcern(writeConcern)
                .insertMany documents.collect() { Map m -> new Document(m) } as List<Document>, insertOptions
        return collection
    }

    static MongoCollection<Document> insert(final MongoCollection<Document> collection, final List<? extends Map> documents, final InsertManyOptions insertOptions) {
        collection.insertMany documents.collect() { Map m -> new Document(m) } as List<Document>, insertOptions
        return collection
    }

    static  MongoCollection save(final MongoCollection<Document> collection, final Map<String, Object> document) {
        insert collection, document
    }

    static  MongoCollection save(final MongoCollection<Document> collection, final Map<String, Object> document, final WriteConcern writeConcern) {
        insert collection, document, writeConcern
    }

    static UpdateResult replaceOne(MongoCollection<Document> collection, Map<String, Object> filter, Document replacement) {
        collection.replaceOne(toBson(filter), replacement)
    }

    static UpdateResult replaceOne(MongoCollection<Document> collection, Map<String, Object> filter, Document replacement, Map<String,Object> options) {
        collection.replaceOne(
                toBson(filter),
                replacement,
                MongoConstants.mapToObject(ReplaceOptions, options))
    }

    static Document findOneAndDelete(MongoCollection<Document> collection, Map<String, Object> filter) {
        collection.findOneAndDelete( toBson(filter) )
    }

    static Document findOneAndDelete(MongoCollection<Document> collection, Map<String, Object> filter, Map<String, Object> options) {
        collection.findOneAndDelete( toBson(filter), MongoConstants.mapToObject(FindOneAndDeleteOptions, options) )
    }

    static Document findOneAndReplace(MongoCollection<Document> collection, Map<String, Object> filter, Map<String, Object> replacement) {
        collection.findOneAndReplace( toBson(filter), new Document(replacement) )
    }

    static Document findOneAndReplace(MongoCollection<Document> collection, Map<String, Object> filter, Map<String, Object> replacement, Map<String, Object> options) {
        collection.findOneAndReplace( toBson(filter), new Document(replacement), MongoConstants.mapToObject(FindOneAndReplaceOptions, options) )
    }

    static Document findOneAndUpdate(MongoCollection<Document> collection, Map<String, Object> filter, Map<String, Object> update) {
        collection.findOneAndUpdate( toBson(filter), new Document(update) )
    }

    static Document findOneAndUpdate(MongoCollection<Document> collection, Map<String, Object> filter, Map<String, Object> update, Map<String, Object> options) {
        collection.findOneAndUpdate( toBson(filter), new Document(update), MongoConstants.mapToObject(FindOneAndUpdateOptions, options) )
    }

}


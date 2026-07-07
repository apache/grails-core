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

package org.grails.datastore.gorm.mongo.api

import java.util.function.Function

import groovy.transform.CompileStatic

import com.mongodb.ReadPreference
import com.mongodb.client.AggregateIterable
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndDeleteOptions
import com.mongodb.client.model.Projections
import com.mongodb.client.model.TextSearchOptions
import org.bson.Document
import org.bson.conversions.Bson

import org.springframework.transaction.PlatformTransactionManager

import grails.gorm.multitenancy.Tenants
import grails.mongodb.api.MongoAllOperations
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.mongo.MongoCriteriaBuilder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoCodecSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings

/**
 * MongoDB static API implementation
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class MongoStaticApi<D> extends GormStaticApi<D> implements MongoAllOperations<D> {

    MongoStaticApi(Class<D> persistentClass, Datastore datastore, List<FinderMethod> finders, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, finders, transactionManager)
    }

    FindIterable<D> find(Bson filter) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            filter = wrapFilterWithMultiTenancy(filter)
            MongoCollection<D> collection = mongoSession.getCollection(entity)
                    .withDocumentClass(persistentClass)
            return mongoSession.find(collection, filter)
        }
    }

    @Override
    D findOneAndDelete(Bson filter, FindOneAndDeleteOptions options = null) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            filter = wrapFilterWithMultiTenancy(filter)
            MongoCollection<D> mongoCollection = mongoSession.getCollection(entity)
                                                        .withDocumentClass(persistentClass)
            D result = options ? mongoSession.findOneAndDelete(mongoCollection, filter, options) :
                                 mongoSession.findOneAndDelete(mongoCollection, filter)

            return result
        }
    }

    Number count(Bson filter) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            filter = wrapFilterWithMultiTenancy(filter)
            return mongoSession.countDocuments(mongoSession.getCollection(entity), filter)
        }
    }

    @Override
    MongoCriteriaBuilder createCriteria() {
        (MongoCriteriaBuilder) withSession { Session session ->
            def entity = session.mappingContext.getPersistentEntity(persistentClass.name)
            return new MongoCriteriaBuilder(entity.javaClass, session)
        }
    }

    @Override
    MongoDatabase getDB() {
        (MongoDatabase) withSession({ AbstractMongoSession mongoSession ->
            def databaseName = mongoSession.getDatabase(mongoSession.mappingContext.getPersistentEntity(persistentClass.name))
            mongoSession.getNativeInterface()
                    .getDatabase(databaseName)

        })
    }

    @Override
    String getCollectionName() {
        (String) withSession({ AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            return mongoSession.getCollectionName(entity)
        })
    }

    @Override
    MongoCollection<Document> getCollection() {
        (MongoCollection<Document>) withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            return mongoSession.getCollection(entity)
        }
    }

    @Override
    def <T> T withCollection(String collectionName, Closure<T> callable) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            final previous = mongoSession.useCollection(entity, collectionName)
            try {
                def dbName = mongoSession.getDatabase(entity)
                MongoClient mongoClient = (MongoClient) mongoSession.getNativeInterface()
                MongoDatabase db = mongoClient.getDatabase(dbName)
                def coll = db.getCollection(collectionName)
                return callable.call(coll)
            } finally {
                mongoSession.useCollection(entity, previous)
            }
        }
    }

    @Override
    String useCollection(String collectionName) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            mongoSession.useCollection(entity, collectionName)
        }
    }

    @Override
    def <T> T withDatabase(String databaseName, Closure<T> callable) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            final previous = mongoSession.useDatabase(entity, databaseName)
            try {
                MongoDatabase db = mongoSession.getNativeInterface().getDatabase(databaseName)
                return callable.call(db)
            } finally {
                mongoSession.useDatabase(entity, previous)
            }
        }
    }

    @Override
    String useDatabase(String databaseName) {
        withSession { AbstractMongoSession mongoSession ->
            def entity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            mongoSession.useDatabase(entity, databaseName)
        }
    }

    @Override
    int countHits(String query) {
        search(query).size()
    }

    @Override
    List<D> aggregate(List pipeline, Function<AggregateIterable, AggregateIterable> doWithAggregate = Function.identity()) {
        (List<D>) withSession({ AbstractMongoSession mongoSession ->
            def persistentEntity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            def mongoCollection = mongoSession.getCollection(persistentEntity)
            if (mongoSession instanceof MongoCodecSession) {
                MongoDatastore datastore = (MongoDatastore)mongoSession.getDatastore()
                mongoCollection = mongoCollection
                        .withDocumentClass(persistentEntity.javaClass)
                        .withCodecRegistry(datastore.getCodecRegistry())
            }

            List<? extends Bson> newPipeline = preparePipeline(pipeline)
            AggregateIterable aggregateIterable = mongoSession.aggregate(mongoCollection, newPipeline)
            if (doWithAggregate != null) {
                aggregateIterable = doWithAggregate.apply(aggregateIterable)
            }
            new MongoQuery.MongoResultList(aggregateIterable.iterator(), 0, (EntityPersister)mongoSession.getPersister(persistentEntity) as EntityPersister)
        })
    }

    @Override
    List<D> aggregate(List pipeline, Function<AggregateIterable, AggregateIterable> doWithAggregate, ReadPreference readPreference) {
        (List<D>) withSession({ AbstractMongoSession mongoSession ->
            def persistentEntity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            List<? extends Bson> newPipeline = preparePipeline(pipeline)
            def mongoCollection = mongoSession.getCollection(persistentEntity)
                    .withReadPreference(readPreference)
            def aggregateIterable = mongoSession.aggregate(mongoCollection, newPipeline)
            if (doWithAggregate != null) {
                aggregateIterable = doWithAggregate.apply(aggregateIterable)
            }
            new MongoQuery.MongoResultList(aggregateIterable.iterator(), 0, (EntityPersister)mongoSession.getPersister(persistentEntity))
        })
    }

    @Override
    List<D> search(String query, Map options = Collections.emptyMap()) {
        (List<D>) withSession({ AbstractMongoSession mongoSession ->
            def persistentEntity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)
            def coll = mongoSession.getCollection(persistentEntity)
            if (mongoSession instanceof MongoCodecSession) {
                MongoDatastore datastore = (MongoDatastore)mongoSession.datastore
                coll = coll
                        .withDocumentClass(persistentEntity.javaClass)
                        .withCodecRegistry(datastore.codecRegistry)
            }
            Bson search
            if (options.language) {
                search = Filters.text(query, new TextSearchOptions().language(options.language.toString()))
            }
            else {
                search = Filters.text(query)
            }
            search = wrapFilterWithMultiTenancy(search)
            FindIterable cursor = mongoSession.find(coll, search)

            int offset = options.offset instanceof Number ? ((Number)options.offset).intValue() : 0
            int max = options.max instanceof Number ? ((Number)options.max).intValue() : -1
            if (offset > 0) cursor.skip(offset)
            if (max > -1) cursor.limit(max)
            new MongoQuery.MongoResultList(cursor.iterator(), offset, (EntityPersister)mongoSession.getPersister(persistentEntity))
        })
    }

    @Override
    List<D> searchTop(String query, int limit = 5, Map options = Collections.emptyMap()) {
        (List<D>) withSession({ AbstractMongoSession mongoSession ->
            def persistentEntity = mongoSession.mappingContext.getPersistentEntity(persistentClass.name)

            MongoCollection coll = mongoSession.getCollection(persistentEntity)
            if (mongoSession instanceof MongoCodecSession) {
                MongoDatastore datastore = (MongoDatastore)mongoSession.datastore
                coll = coll
                        .withDocumentClass(persistentEntity.javaClass)
                        .withCodecRegistry(datastore.codecRegistry)
            }
            EntityPersister persister = (EntityPersister)mongoSession.getPersister(persistentEntity)

            Bson search
            if (options.language) {
                search = Filters.text(query, new TextSearchOptions().language(options.language.toString()))
            }
            else {
                search = Filters.text(query)
            }

            def score = Projections.metaTextScore('score')
            search = wrapFilterWithMultiTenancy(search)
            FindIterable cursor = mongoSession.find(coll, search)
                                            .projection(score)
                                            .sort(score)
                                            .limit(limit)

            new MongoQuery.MongoResultList(cursor.iterator(), 0, persister)
        })
    }

    protected Bson wrapFilterWithMultiTenancy(Bson filter) {
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR && persistentEntity.isMultiTenant()) {
            filter = Filters.and(
                    Filters.eq(MappingUtils.getTargetKey(persistentEntity.tenantId), Tenants.currentId((Class<Datastore>) datastore.getClass())),
                    filter
            )
        }
        return filter
    }

    private List<Bson> preparePipeline(List pipeline) {
        List<Bson> newPipeline = new ArrayList<Bson>()
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR && persistentEntity.isMultiTenant()) {
            newPipeline.add(
                    Aggregates.match(Filters.eq(MappingUtils.getTargetKey(persistentEntity.tenantId), Tenants.currentId((Class<Datastore>) datastore.getClass())))
            )
        }
        for (o in pipeline) {
            if (o instanceof Bson) {
                newPipeline << (Bson) o
            } else if (o instanceof Map) {
                newPipeline << new Document((Map) o)
            }
        }
        newPipeline
    }
}

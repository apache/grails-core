/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.mongo.core

import com.mongodb.BasicDBObject
import com.mongodb.client.MongoClient
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.validation.PersistentEntityValidator
import groovy.util.logging.Slf4j
import org.apache.grails.data.testing.tck.base.GrailsDataTckManager
import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.bson.Document
import org.grails.datastore.bson.query.BsonQuery
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.mongo.Birthday
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.query.Query
import org.slf4j.LoggerFactory
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.output.Slf4jLogConsumer

@Slf4j
class GrailsDataMongoTckManager extends GrailsDataTckManager {

    MongoDBContainer mongoDBContainer

    MongoDatastore mongoDatastore
    MongoClient mongoClient
    GrailsApplication grailsApplication
    MappingContext mappingContext

    Map<String, Object> configuration

    @Override
    void setupSpec() {
        super.setupSpec()
        mongoDBContainer = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)
        mongoDBContainer.start()
        mongoDBContainer.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("testcontainers")))

        configuration = [
                (MongoSettings.SETTING_DATABASE_NAME): 'test',
                (MongoSettings.SETTING_HOST)         : mongoDBContainer.host,
                (MongoSettings.SETTING_PORT)         : mongoDBContainer.getMappedPort(AbstractMongoGrailsExtension.DEFAULT_MONGO_PORT) as String,
                //TODO: 'grails.mongodb.url': "mongodb://${host}:${port as String}/myDb" as String
        ]
    }

    @Override
    void cleanupSpec() {
        super.cleanupSpec()
        mongoDBContainer.stop()
    }

    @Override
    Session createSession() {
        def allClasses = getDomainClasses() as Class[]
        def ctx = new GenericApplicationContext()
        ctx.refresh()

        mongoDatastore = new MongoDatastore(configuration)
        mappingContext = mongoDatastore.mappingContext
        mappingContext.mappingFactory.registerCustomType(new AbstractMappingAwareCustomTypeMarshaller<Birthday, Document, Document>(Birthday) {
            @Override
            protected Object writeInternal(PersistentProperty property, String key, Birthday value, Document nativeTarget) {

                final converted = value.date.time
                nativeTarget.put(key, converted)
                return converted
            }

            @Override
            protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion criterion, Document nativeQuery) {
                if (criterion instanceof Query.Between) {
                    def dbo = new BasicDBObject()
                    dbo.put(BsonQuery.GTE_OPERATOR, criterion.getFrom().date.time)
                    dbo.put(BsonQuery.LTE_OPERATOR, criterion.getTo().date.time)
                    nativeQuery.put(key, dbo)
                } else {
                    nativeQuery.put(key, criterion.value.date.time)
                }
            }

            @Override
            protected Birthday readInternal(PersistentProperty property, String key, Document nativeSource) {
                final num = nativeSource.get(key)
                if (num instanceof Long) {
                    return new Birthday(new Date(num))
                }
                return null
            }
        })
        mappingContext.addPersistentEntities(allClasses as Class[])
        mongoClient = mongoDatastore.getMongoClient()

        grailsApplication = new DefaultGrailsApplication(allClasses, getClass().getClassLoader())
        grailsApplication.mainContext = ctx
        grailsApplication.initialise()

        mongoDatastore.connect()
    }

    @Override
    void destroy() {
        mongoDatastore.getMongoClient().listDatabaseNames().findAll {!(it in ['admin', 'config', 'local']) }.each {
            try {
                mongoDatastore.getMongoClient().getDatabase(it).drop()
            }
            catch(e) {
                log.warn("Could not drop ${it}")
            }
        }
        mongoDatastore.buildIndex()
        for (cls in getDomainClasses()) {
            GormEnhancer.findValidationApi(cls).setValidator(null)
        }

        super.destroy()
    }

    void setupValidator(Class entityClass, Validator validator = null) {
        PersistentEntity entity = mappingContext.persistentEntities.find { PersistentEntity e -> e.javaClass == entityClass }
        def messageSource = new StaticMessageSource()
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(messageSource), mappingContext, Collections.emptyMap())
        if (entity) {
            mappingContext.addEntityValidator(entity, validator ?:
                    new PersistentEntityValidator(entity, messageSource, evaluator))
        }
    }
}

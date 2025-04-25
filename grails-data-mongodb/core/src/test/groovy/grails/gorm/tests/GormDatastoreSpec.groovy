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
package grails.gorm.tests

import com.mongodb.BasicDBObject
import com.mongodb.client.MongoClient
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.tck.Book
import grails.gorm.tck.ChildEntity
import grails.gorm.tck.City
import grails.gorm.tck.ClassWithListArgBeforeValidate
import grails.gorm.tck.ClassWithNoArgBeforeValidate
import grails.gorm.tck.ClassWithOverloadedBeforeValidate
import grails.gorm.tck.CommonTypes
import grails.gorm.tck.Country
import grails.gorm.tck.EnumThing
import grails.gorm.tck.Highway
import grails.gorm.tck.Location
import grails.gorm.tck.ModifyPerson
import grails.gorm.tck.OptLockNotVersioned
import grails.gorm.tck.OptLockVersioned
import grails.gorm.tck.PersonEvent
import grails.gorm.tck.PetType
import grails.gorm.tck.PlantCategory
import grails.gorm.tck.Publication
import grails.gorm.tck.Task
import grails.gorm.tck.TestEntity
import grails.gorm.validation.PersistentEntityValidator
import groovy.util.logging.Slf4j
import org.apache.grails.testing.AutoStartedMongoSpec
import org.bson.Document
import org.grails.datastore.bson.query.BsonQuery
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.mongo.Birthday
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.query.Query
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Created by graemerocher on 06/06/16.
 */
@Slf4j
abstract class GormDatastoreSpec extends AutoStartedMongoSpec {

    private static final CURRENT_TEST_NAME = "current.gorm.test"

    List getDomainClasses() {
        [Book, ChildEntity, City, ClassWithListArgBeforeValidate, ClassWithNoArgBeforeValidate,
         ClassWithOverloadedBeforeValidate, CommonTypes, Country, EnumThing, Face, Highway,
         Location, ModifyPerson, Nose, OptLockNotVersioned, OptLockVersioned, Person, PersonEvent,
         Pet, PetType, Plant, PlantCategory, Publication, Task, TestEntity]
    }

    Map getConfiguration() {
        [
            (MongoSettings.SETTING_HOST): mongoHost,
            (MongoSettings.SETTING_PORT): mongoPort,
        ]
    }

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    @Shared
    @AutoCleanup
    MongoDatastore mongoDatastore

    @Shared
    MongoClient mongoClient

    @Shared
    GrailsApplication grailsApplication

    @Shared
    MappingContext mappingContext

    AbstractMongoSession session

    void setupSpec() {
        def allClasses = getDomainClasses() as Class[]
        def ctx = new GenericApplicationContext()
        ctx.refresh()

        String databaseName = System.getProperty(GormDatastoreSpec.CURRENT_TEST_NAME) ?: 'test'
        def config = [
                (MongoSettings.SETTING_DATABASE_NAME): databaseName,
                (MongoSettings.SETTING_HOST)         : mongoHost,
                (MongoSettings.SETTING_PORT)         : mongoPort,
        ]
        mongoDatastore = new MongoDatastore(config << getConfiguration())
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

    void setup() {
        session = mongoDatastore.connect()
        DatastoreUtils.bindSession session
    }

    void cleanup() {
        session.disconnect()
        DatastoreUtils.unbindSession(session)
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
    }
}

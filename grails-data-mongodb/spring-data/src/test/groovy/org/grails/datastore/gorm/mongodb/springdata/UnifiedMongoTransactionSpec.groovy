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
package org.grails.datastore.gorm.mongodb.springdata

import grails.gorm.annotation.Entity

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Proves a single transaction, managed by {@link GormSharedSessionMongoTransactionManager}, spans
 * both a GORM {@code save()} and a Spring Data {@code MongoTemplate} write atomically on one shared
 * {@link com.mongodb.client.ClientSession}.
 */
class UnifiedMongoTransactionSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Shared
    MongoTemplate mongoTemplate

    @Shared
    MongoDatabaseFactory factory

    @Shared
    TransactionTemplate transactionTemplate

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'          : dbContainer.getReplicaSetUrl('myDb'),
                'grails.mongodb.transactional': true
        ]
        datastore = new MongoDatastore(config, GormThing)

        factory = new SimpleMongoClientDatabaseFactory(datastore.mongoClient, datastore.defaultDatabase)
        MongoCustomConversions conversions = new MongoCustomConversions([])
        MongoMappingContext mappingContext = new MongoMappingContext()
        mappingContext.simpleTypeHolder = conversions.simpleTypeHolder
        mappingContext.afterPropertiesSet()
        MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), mappingContext)
        converter.customConversions = conversions
        converter.afterPropertiesSet()
        mongoTemplate = new MongoTemplate(factory, converter)

        transactionTemplate = new TransactionTemplate(new GormSharedSessionMongoTransactionManager(datastore, factory))
    }

    void setup() {
        GormThing.withNewSession {
            GormThing.DB.drop()
        }
        mongoTemplate.dropCollection(SpringDataThing)
    }

    private long gormCount() {
        GormThing.withNewSession { GormThing.count() }
    }

    private long springDataCount() {
        mongoTemplate.count(new Query(), SpringDataThing)
    }

    void "test a GORM save and a Spring Data write commit together in one transaction"() {
        when: "both a GORM entity and a Spring Data document are written in one transaction"
        transactionTemplate.execute {
            new GormThing(name: "gorm").save(flush: true)
            mongoTemplate.insert(new SpringDataThing(name: "springData"))
            return null
        }

        then: "both are persisted"
        gormCount() == 1
        springDataCount() == 1
    }

    void "test a GORM save and a Spring Data write roll back together when the transaction fails"() {
        when: "both are written then the transaction throws"
        transactionTemplate.execute {
            new GormThing(name: "gorm").save(flush: true)
            mongoTemplate.insert(new SpringDataThing(name: "springData"))
            throw new RuntimeException("boom")
        }

        then: "the exception propagates"
        thrown(RuntimeException)

        and: "neither write was persisted - they shared one aborted MongoDB transaction"
        gormCount() == 0
        springDataCount() == 0
    }

    void "test Spring Data reads a document written by GORM on the shared connection"() {
        given: "GORM writes an entity outside any transaction"
        GormThing.withNewSession {
            new GormThing(name: "written-by-gorm").save(flush: true)
        }

        expect: "Spring Data, using the same client and database, sees it in GORM's collection"
        mongoTemplate.count(new Query(), "gormThing") == 1
    }

    void "test sequential unified transactions each commit without leaking the shared session or holder"() {
        when: "two separate transactions each write to both stacks"
        transactionTemplate.execute {
            new GormThing(name: "first").save(flush: true)
            mongoTemplate.insert(new SpringDataThing(name: "firstSD"))
            return null
        }
        transactionTemplate.execute {
            new GormThing(name: "second").save(flush: true)
            mongoTemplate.insert(new SpringDataThing(name: "secondSD"))
            return null
        }

        then: "both committed - the first transaction's session/holder did not leak into the second or get double-closed"
        gormCount() == 2
        springDataCount() == 2

        and: "no Spring Data resource holder remains bound to the thread after completion"
        !TransactionSynchronizationManager.hasResource(factory)
    }

    void "test a Spring Data repository works over the shared connection"() {
        given:
        SpringDataThingRepository repository = new MongoRepositoryFactory(mongoTemplate).getRepository(SpringDataThingRepository)

        when: "a document is saved and read back through the repository"
        repository.save(new SpringDataThing(name: "viaRepository"))

        then:
        repository.count() == 1
        repository.findAll().first().name == "viaRepository"

        and: "GORM sees the same database"
        mongoTemplate.count(new Query(), SpringDataThing) == 1
    }

    void "test the auto-configuration wires a shared factory, a mongoTemplate and a unified transaction manager"() {
        given:
        SpringDataMongoGormAutoConfiguration config = new SpringDataMongoGormAutoConfiguration()
        MongoCustomConversions conversions = config.mongoCustomConversions()
        MongoMappingContext context = config.springDataMongoMappingContext(conversions)

        when:
        MongoDatabaseFactory factory = config.mongoDatabaseFactory(datastore)

        then: "the factory is built over GORM's client and database"
        factory instanceof SimpleMongoClientDatabaseFactory
        factory.mongoDatabase.name == datastore.defaultDatabase

        and: "a MongoTemplate is produced"
        config.mongoTemplate(factory, config.mappingMongoConverter(factory, context, conversions)) != null

        and: "the primary transaction manager shares GORM's session with Spring Data"
        config.transactionManager(datastore, factory) instanceof GormSharedSessionMongoTransactionManager
    }

    void "test the shared database factory does not close GORM's MongoClient"() {
        given:
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(datastore.mongoClient, datastore.defaultDatabase)

        when: "the factory is destroyed"
        ((org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory) factory).destroy()

        then: "GORM's client is still usable"
        gormCount() == 0
    }
}

@Entity
class GormThing {
    String name
}

class SpringDataThing {
    String id
    String name
}

interface SpringDataThingRepository extends MongoRepository<SpringDataThing, String> {
}

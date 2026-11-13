/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.datastore.mapping.mongo

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mongodb.MongoCommandException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.IndexOptions
import grails.persistence.Entity
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

class MongoDatastoreIndexFailureLogSpec extends Specification {

    @Shared
    MongoDBContainer mongoDBContainer

    @Shared
    @AutoCleanup
    MongoClient driverClient

    // MongoDatastore logs through the logger it inherits from AbstractDatastore
    ListAppender<ILoggingEvent> appender
    Logger datastoreLogger
    MongoDatastore datastore

    void setupSpec() {
        mongoDBContainer = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)
        mongoDBContainer.start()
        driverClient = MongoClients.create(mongoDBContainer.connectionString)
    }

    void cleanupSpec() {
        mongoDBContainer?.stop()
    }

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        datastoreLogger = (Logger) LoggerFactory.getLogger(AbstractDatastore)
        datastoreLogger.addAppender(appender)
        datastoreLogger.level = Level.DEBUG
    }

    void cleanup() {
        datastoreLogger.detachAppender(appender)
        datastoreLogger.level = null
        datastore?.close()
    }

    void 'a conflicting index is logged as one ERROR line with the stack trace at DEBUG'() {
        given: 'an index on the same key as the mapped one, created with different options'
        def database = driverClient.getDatabase('indexFailureLog')
        database.drop()
        def collection = database.getCollection('indexConflictItem')
        collection.createIndex(new Document('name', 1), new IndexOptions().name('name_1'))
        String serverMessage
        try {
            collection.createIndex(new Document('name', 1), new IndexOptions().name('name_1').unique(true))
        } catch (MongoCommandException e) {
            serverMessage = e.errorMessage
        }

        when: 'a datastore maps the same key as unique'
        datastore = new MongoDatastore([
                (MongoSettings.SETTING_DATABASE_NAME): 'indexFailureLog',
                (MongoSettings.SETTING_HOST)         : mongoDBContainer.host,
                (MongoSettings.SETTING_PORT)         : mongoDBContainer.getMappedPort(AbstractMongoGrailsExtension.DEFAULT_MONGO_PORT) as String,
        ], IndexConflictItem)
        def errors = appender.list.findAll { it.level == Level.ERROR && it.formattedMessage.contains('Failed to create') }
        def debugs = appender.list.findAll { it.level == Level.DEBUG && it.throwableProxy != null }

        then: 'one ERROR event names the entity and carries the server message but no throwable'
        serverMessage
        errors.size() == 1
        errors[0].formattedMessage.contains(IndexConflictItem.name)
        errors[0].formattedMessage.contains(serverMessage)
        errors[0].throwableProxy == null

        and: 'the stack trace is logged at DEBUG'
        debugs.any { it.throwableProxy.className == MongoCommandException.name }
    }
}

@Entity
class IndexConflictItem {
    ObjectId id
    String name

    static mapping = {
        name index: true, indexAttributes: [unique: true]
    }
}

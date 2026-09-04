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
package org.grails.datastore.gorm.mongo

import java.util.concurrent.ConcurrentLinkedQueue

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * MongoDB answers a {@code createIndexes} command only once the index has been built, so by default
 * whoever creates the datastore - in an application, the startup thread - waits for every declared index.
 * This specification pins both halves of that: the default build runs on the calling thread, and with
 * {@code grails.mongodb.buildIndexesAsync = true} it runs on a background thread instead.
 *
 * <p>Which thread issued the command is observed through a driver {@link CommandListener}, which the
 * synchronous driver invokes on the thread making the call.
 */
class BuildIndexesAsyncSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore blockingDatastore

    @Shared
    @AutoCleanup
    MongoDatastore asyncDatastore

    @Shared
    MongoClient blockingClient

    @Shared
    MongoClient asyncClient

    @Shared
    CreateIndexThreadRecorder blockingRecorder = new CreateIndexThreadRecorder()

    @Shared
    CreateIndexThreadRecorder asyncRecorder = new CreateIndexThreadRecorder()

    @Shared
    String creatingThread

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    private MongoClient clientFor(String database, CommandListener listener) {
        MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(dbContainer.getReplicaSetUrl(database)))
                .addCommandListener(listener)
                .build())
    }

    void setupSpec() {
        creatingThread = Thread.currentThread().name

        blockingClient = clientFor('blockingIndexDb', blockingRecorder)
        blockingDatastore = new MongoDatastore(blockingClient,
                DatastoreUtils.createPropertyResolver(['grails.mongodb.databaseName': 'blockingIndexDb']),
                BlockingIndexThing)

        asyncClient = clientFor('asyncIndexDb', asyncRecorder)
        asyncDatastore = new MongoDatastore(asyncClient,
                DatastoreUtils.createPropertyResolver([
                        'grails.mongodb.databaseName'              : 'asyncIndexDb',
                        (MongoSettings.SETTING_BUILD_INDEXES_ASYNC): true
                ]),
                AsyncIndexThing)
    }

    void cleanupSpec() {
        blockingClient?.close()
        asyncClient?.close()
    }

    void "test the index build blocks the thread creating the datastore by default"() {
        expect: "the setting is off"
        !blockingDatastore.isBuildIndexesAsync()

        and: "the index already exists by the time the constructor has returned"
        [name: 1] in BlockingIndexThing.collection.listIndexes()*.key

        and: "it was built by the thread that created the datastore, which therefore waited for it"
        blockingRecorder.threads == [creatingThread]
    }

    void "test the index build runs on a background thread when enabled"() {
        given:
        def conditions = new PollingConditions(timeout: 30)

        expect: "the setting is on"
        asyncDatastore.isBuildIndexesAsync()

        when: "the background build has had a chance to run"
        conditions.eventually {
            assert [name: 1] in AsyncIndexThing.collection.listIndexes()*.key
        }

        then: "the command was issued by the datastore's own index build thread"
        asyncRecorder.threads.size() == 1
        asyncRecorder.threads.first().startsWith('gorm-mongo-index-build')

        and: "not by the thread that created the datastore, which did not wait for it"
        asyncRecorder.threads.first() != creatingThread
    }
}

/**
 * Records the thread each {@code createIndexes} command was issued from.
 */
class CreateIndexThreadRecorder implements CommandListener {

    private final Queue<String> recorded = new ConcurrentLinkedQueue<>()

    @Override
    void commandStarted(CommandStartedEvent event) {
        if (event.commandName == 'createIndexes') {
            recorded.add(Thread.currentThread().name)
        }
    }

    List<String> getThreads() {
        recorded.toList().unique()
    }
}

@Entity
class BlockingIndexThing {
    String name

    static mapping = {
        version false
        collection 'blockingIndexThing'
        name index: true
    }
}

@Entity
class AsyncIndexThing {
    String name

    static mapping = {
        version false
        collection 'asyncIndexThing'
        name index: true
    }
}

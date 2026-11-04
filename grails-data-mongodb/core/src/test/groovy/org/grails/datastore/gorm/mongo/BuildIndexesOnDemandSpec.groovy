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

import ch.qos.logback.classic.Level
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * {@code grails.mongodb.buildIndexes = false} stops GORM building indexes by itself. It does not stop the
 * application building them when it chooses to, by calling {@link MongoDatastore#buildIndex()} - once a
 * deployment has been verified, say, or from an administrative action.
 */
class BuildIndexesOnDemandSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('onDemandDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    private List indexKeys(String database, String collection) {
        realClient.getDatabase(database).getCollection(collection).listIndexes()*.key
    }

    private Map config(String database, Map extra = [:]) {
        ['grails.mongodb.url'                 : dbContainer.getReplicaSetUrl(database),
         (MongoSettings.SETTING_BUILD_INDEXES): false] + extra
    }

    void "test an explicit build creates the declared indexes while GORM does not build them by itself"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(config('onDemandSyncDb'), OnDemandSyncThing)

        expect: "nothing was built at startup"
        !([name: 1] in indexKeys('onDemandSyncDb', 'onDemandSyncThing'))

        when: "the application asks for the build"
        datastore.buildIndex()

        then: "it runs on the calling thread, creates the declared index and reports it like the startup build"
        [name: 1] in indexKeys('onDemandSyncDb', 'onDemandSyncThing')
        log.events*.formattedMessage.any {
            it.startsWith('Index build for database [onDemandSyncDb] finished') && it.contains('1 created, 0 already present')
        }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test an explicit build runs on the background thread when builds are asynchronous"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(config('onDemandAsyncDb', [(MongoSettings.SETTING_BUILD_INDEXES_ASYNC): true]),
                OnDemandAsyncThing)

        when:
        datastore.buildIndex()

        then: "the declared index is built, and by the connection's own index build thread"
        conditions.eventually {
            assert [name: 1] in indexKeys('onDemandAsyncDb', 'onDemandAsyncThing')
            assert log.events.any {
                it.formattedMessage.startsWith('Index build for database [onDemandAsyncDb] finished') &&
                        it.threadName.startsWith('gorm-mongo-index-build-default-')
            }
        }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a named connection is built through its own datastore"() {
        given:
        def datastore = new MongoDatastore(config('onDemandDefaultDb', [
                'grails.mongodb.connections': [reporting: [url: dbContainer.getReplicaSetUrl('onDemandReportingDb')]]
        ]), OnDemandConnectionThing)

        when:
        (datastore.getDatastoreForConnection('reporting') as MongoDatastore).buildIndex()

        then: "that connection's index is built, and the default connection is left as it was"
        [name: 1] in indexKeys('onDemandReportingDb', 'onDemandConnectionThing')
        !([name: 1] in indexKeys('onDemandDefaultDb', 'onDemandConnectionThing'))

        cleanup:
        datastore?.close()
    }

    void "test a restart does not build what the setting says GORM should not build by itself"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(config('onDemandRestartDb', [(MongoSettings.SETTING_BUILD_INDEXES_ASYNC): true]),
                OnDemandRestartThing)

        when:
        datastore.stop()
        datastore.start()

        then: "start() says so before returning when it resumes a build; it did not"
        !log.events.any { it.formattedMessage.startsWith('Resuming the index build') }
        !([name: 1] in indexKeys('onDemandRestartDb', 'onDemandRestartThing'))

        cleanup:
        datastore?.close()
        log?.close()
    }
}

@Entity
class OnDemandSyncThing {
    String name

    static mapping = {
        version false
        collection 'onDemandSyncThing'
        name index: true
    }
}

@Entity
class OnDemandAsyncThing {
    String name

    static mapping = {
        version false
        collection 'onDemandAsyncThing'
        name index: true
    }
}

@Entity
class OnDemandConnectionThing {
    String name

    static mapping = {
        version false
        collection 'onDemandConnectionThing'
        connection ConnectionSource.ALL
        name index: true
    }
}

@Entity
class OnDemandRestartThing {
    String name

    static mapping = {
        version false
        collection 'onDemandRestartThing'
        name index: true
    }
}

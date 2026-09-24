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
package org.grails.datastore.gorm.mongo.connections

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import ch.qos.logback.classic.Level
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.mongo.CapturedLog
import org.grails.datastore.gorm.mongo.FailingMongoClient
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings

/**
 * Verifies that {@code buildIndexes} is resolved per connection: a connection inherits the top level
 * setting unless it declares its own, so index building can be switched off globally and left on for an
 * individual connection (or the other way round).
 */
class BuildIndexesPerConnectionSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'                 : "mongodb://${mongoHost}:${mongoPort}/skippedDb" as String,
                (MongoSettings.SETTING_BUILD_INDEXES): false,
                'grails.mongodb.connections'         : [
                        'indexed': [
                                'url'         : "mongodb://${mongoHost}:${mongoPort}/indexedDb" as String,
                                'buildIndexes': true
                        ],
                        'inherits': [
                                'url': "mongodb://${mongoHost}:${mongoPort}/inheritsDb" as String
                        ]
                ]
        ]
        datastore = new MongoDatastore(config, PerConnectionThing)
    }

    void "test closing interrupts a child index build added at runtime: #addedAtRuntime"() {
        given:
        def buildReached = new CountDownLatch(1)
        def releaseBuild = new CountDownLatch(1)
        def worker = new AtomicReference<Thread>()
        def log = new CapturedLog('org.grails.datastore.mapping', Level.DEBUG)
        def factory = new MongoConnectionSourceFactory() {
            @Override
            ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
                def source = super.create(name, settings)
                if (name != 'indexedAsync') {
                    return source
                }
                MongoClient blocking = FailingMongoClient.wrap(source.source, 'createIndex') {
                    worker.set(Thread.currentThread())
                    buildReached.countDown()
                    releaseBuild.await()
                }
                new DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings>(name, blocking, settings)
            }
        }
        Map childConfig = [url: dbContainer.getReplicaSetUrl('asyncChildDb'), buildIndexes: true]
        Map config = [
                'grails.mongodb.url': dbContainer.getReplicaSetUrl('asyncParentDb'),
                'grails.mongodb.buildIndexes': false,
                'grails.mongodb.buildIndexesAsync': true,
                'grails.mongodb.connections': addedAtRuntime ? [:] : [indexedAsync: childConfig]
        ]

        when:
        def parent = new MongoDatastore(DatastoreUtils.createPropertyResolver(config), factory,
                new DefaultApplicationEventPublisher(), AsyncPerConnectionThing)
        if (addedAtRuntime) {
            parent.connectionSources.addConnectionSource('indexedAsync', childConfig)
        }

        then:
        parent.getDatastoreForConnection('indexedAsync').isBuildIndexesAsync()
        buildReached.await(30, TimeUnit.SECONDS)
        worker.get().name.startsWith('gorm-mongo-index-build-indexedAsync-')

        when: "the parent closes while its child is still building"
        parent.close()
        worker.get().join(10000)

        then: "the child worker is interrupted and terminates"
        !worker.get().alive

        and: "the abandoned build is classified as shutdown rather than an error"
        log.events.any {
            it.threadName == worker.get().name && it.level == Level.DEBUG &&
                    it.formattedMessage.contains('abandoned because the datastore is shutting down')
        }
        !log.events.any {
            it.threadName == worker.get().name && it.level == Level.ERROR
        }

        cleanup:
        releaseBuild.countDown()
        parent?.close()
        worker.get()?.join(10000)
        log.close()

        where:
        addedAtRuntime << [false, true]
    }

    void "test stop() and start() interrupt and resume a named connection's build"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.DEBUG)
        def buildReached = new CountDownLatch(1)
        def releaseBuild = new CountDownLatch(1)
        def blockOnce = new AtomicBoolean(true)
        def factory = new MongoConnectionSourceFactory() {
            @Override
            ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
                def source = super.create(name, settings)
                if (name != 'checkpointedChild') {
                    return source
                }
                MongoClient blocking = FailingMongoClient.wrap(source.source, 'createIndex') { Closure proceed ->
                    if (blockOnce.compareAndSet(true, false)) {
                        buildReached.countDown()
                        releaseBuild.await()
                    }
                    proceed()
                }
                new DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings>(name, blocking, settings)
            }
        }
        MongoClient inspector = MongoClients.create(dbContainer.getReplicaSetUrl('checkpointedChildDb'))
        def childIndexes = { -> inspector.getDatabase('checkpointedChildDb').getCollection('asyncPerConnectionThing').listIndexes()*.key }

        when: "the named connection's build is under way when the datastore is stopped for a checkpoint"
        def parent = new MongoDatastore(DatastoreUtils.createPropertyResolver([
                'grails.mongodb.url'              : dbContainer.getReplicaSetUrl('checkpointedParentDb'),
                'grails.mongodb.buildIndexes'     : false,
                'grails.mongodb.buildIndexesAsync': true,
                'grails.mongodb.connections'      : [checkpointedChild: [url: dbContainer.getReplicaSetUrl('checkpointedChildDb'), buildIndexes: true]]
        ]), factory, new DefaultApplicationEventPublisher(), AsyncPerConnectionThing)
        buildReached.await(30, TimeUnit.SECONDS)

        then: "the build announces which connection it is for"
        log.events.any {
            it.formattedMessage.startsWith('Building the indexes declared by the domain classes for connection [checkpointedChild]')
        }

        when:
        parent.stop()

        then: "the child's build is abandoned as a shutdown, not reported as a failure"
        conditions.eventually {
            assert log.events.any {
                it.level == Level.DEBUG && it.formattedMessage.contains('database [checkpointedChildDb] did not finish')
            }
        }
        !log.events.any { it.level == Level.ERROR && it.threadName.startsWith('gorm-mongo-index-build-checkpointedChild-') }
        !([name: 1] in childIndexes())

        when: "the datastore is restarted"
        parent.start()

        then: "the child's build is resumed, runs again and completes"
        log.events.any { it.formattedMessage.contains('Resuming the index build for connection [checkpointedChild]') }
        conditions.eventually {
            assert [name: 1] in childIndexes()
        }

        cleanup:
        releaseBuild.countDown()
        parent?.close()
        inspector?.close()
        log?.close()
    }

    void "test start() neither waits on nor re-runs a connection added while the datastore was stopped"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def parent = new MongoDatastore(DatastoreUtils.createPropertyResolver([
                'grails.mongodb.url'              : dbContainer.getReplicaSetUrl('stoppedParentDb'),
                'grails.mongodb.buildIndexes'     : false,
                'grails.mongodb.buildIndexesAsync': true
        ]), AsyncPerConnectionThing)
        parent.stop()
        parent.connectionSources.addConnectionSource('addedWhileStopped',
                [url: dbContainer.getReplicaSetUrl('addedWhileStoppedDb'), buildIndexes: true])
        conditions.eventually {
            assert log.events.any { it.formattedMessage.contains('database [addedWhileStoppedDb] finished') }
        }

        when: "the datastore is restarted"
        parent.start()

        then: "that connection's build was never interrupted, so start() resumes no second one"
        !log.events.any { it.formattedMessage.startsWith('Resuming the index build for connection [addedWhileStopped]') }

        cleanup:
        parent?.close()
        log?.close()
    }

    void "test a connection added after close() starts no index build"() {
        given:
        def parent = new MongoDatastore(DatastoreUtils.createPropertyResolver([
                'grails.mongodb.url'              : dbContainer.getReplicaSetUrl('closedParentDb'),
                'grails.mongodb.buildIndexes'     : false,
                'grails.mongodb.buildIndexesAsync': true
        ]), AsyncPerConnectionThing)
        parent.close()

        when:
        parent.connectionSources.addConnectionSource('addedAfterClose',
                [url: dbContainer.getReplicaSetUrl('addedAfterCloseDb'), buildIndexes: true])

        then: "a build would have started its thread before returning, and kept it for a second after finishing; none did"
        !Thread.getAllStackTraces().keySet().any { it.name.startsWith('gorm-mongo-index-build-addedAfterClose-') }

        cleanup:
        parent?.connectionSources?.getConnectionSource('addedAfterClose')?.close()
    }

    void "test a connection can override the global setting"() {
        expect: "the default connection is disabled by the top level setting"
        !datastore.isBuildIndexes()

        and: "the connection that declares its own setting builds indexes"
        datastore.getDatastoreForConnection('indexed').isBuildIndexes()

        and: "a connection that declares nothing inherits the top level setting"
        !datastore.getDatastoreForConnection('inherits').isBuildIndexes()
    }

    void "test only the connection with index building enabled has the declared index"() {
        when: "a document is written through each connection so every collection exists"
        PerConnectionThing.withNewSession {
            new PerConnectionThing(name: 'Fred').save(flush: true)
        }
        PerConnectionThing.indexed.withNewSession {
            new PerConnectionThing(name: 'Fred').save(flush: true)
        }

        then: "the disabled connection has only the implicit _id index"
        PerConnectionThing.collection.listIndexes()*.key == [[_id: 1]]

        and: "the enabled connection has the index declared in the mapping"
        [name: 1] in PerConnectionThing.indexed.collection.listIndexes()*.key
    }
}

@Entity
class PerConnectionThing {
    String name

    static mapping = {
        version false
        collection 'perConnectionThing'
        connection ConnectionSource.ALL
        name index: true
    }
}

@Entity
class AsyncPerConnectionThing {
    String name

    static mapping = {
        connection ConnectionSource.ALL
        name index: true
    }
}

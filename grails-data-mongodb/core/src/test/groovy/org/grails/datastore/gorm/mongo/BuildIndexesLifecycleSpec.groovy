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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import ch.qos.logback.classic.Level
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * A background build belongs to the datastore that started it. Stopping the datastore for a checkpoint
 * interrupts the build rather than letting it fail against the closed client, and restarting it runs the
 * build again; closing it for good turns a later request for a build into a warning rather than an
 * exception.
 */
class BuildIndexesLifecycleSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('lifecycleDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    private Map asyncConfig(String database) {
        ['grails.mongodb.url'                       : dbContainer.getReplicaSetUrl(database),
         (MongoSettings.SETTING_BUILD_INDEXES_ASYNC): true] as Map
    }

    void "test a build cut short by stop() is not reported as a failure and runs again on start()"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.DEBUG)
        CheckpointedDatastore.REACHED = new CountDownLatch(1)
        CheckpointedDatastore.RELEASE = new CountDownLatch(1)
        CheckpointedDatastore.BLOCK_ONCE.set(true)
        def indexes = { -> realClient.getDatabase('checkpointDb').getCollection('checkpointedThing').listIndexes()*.key }

        when: "the background build is under way"
        def datastore = new CheckpointedDatastore(asyncConfig('checkpointDb'), CheckpointedThing)
        datastore.start()

        then:
        CheckpointedDatastore.REACHED.await(30, TimeUnit.SECONDS)

        when: "the datastore is stopped for a checkpoint while the build is still running"
        datastore.stop()

        then: "the build is abandoned as a shutdown and reports how far it got, without a failure"
        conditions.eventually {
            assert log.events.any {
                it.level == Level.DEBUG && it.formattedMessage.contains('database [checkpointDb] did not finish')
            }
        }
        !log.events.any {
            it.level.isGreaterOrEqual(Level.WARN) && it.formattedMessage.contains('database [checkpointDb]')
        }

        and: "the declared index was not created"
        !([name: 1] in indexes())

        when: "the datastore is restarted"
        datastore.start()

        then: "the build that was cut short is resumed"
        log.events.any {
            it.level == Level.INFO && it.formattedMessage.contains('Resuming the index build for connection [default]')
        }

        and: "runs again and completes"
        conditions.eventually {
            assert [name: 1] in indexes()
        }

        cleanup:
        CheckpointedDatastore.RELEASE?.countDown()
        datastore?.close()
        log?.close()
    }

    void "test a build that finishes despite stop() is not run again on start()"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        SlowToStopDatastore.REACHED = new CountDownLatch(1)
        SlowToStopDatastore.RELEASE = new CountDownLatch(1)

        when: "the build has applied every index and is on its way out when the datastore is stopped"
        def datastore = new SlowToStopDatastore(asyncConfig('slowToStopDb'), SlowToStopThing)
        datastore.start()
        SlowToStopDatastore.REACHED.await(30, TimeUnit.SECONDS)
        datastore.stop()
        SlowToStopDatastore.RELEASE.countDown()

        and: "restarted"
        datastore.start()

        then: "the build finished, so the restart does not run it again"
        log.events.any { it.formattedMessage.contains('Index build for database [slowToStopDb] finished') }
        !log.events.any { it.formattedMessage.startsWith('Resuming the index build') }

        cleanup:
        SlowToStopDatastore.RELEASE?.countDown()
        datastore?.close()
        log?.close()
    }

    void "test a build requested while stopped runs when the datastore is restarted"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def collection = realClient.getDatabase('deferredDb').getCollection('deferredBuildThing')
        def datastore = new MongoDatastore(asyncConfig('deferredDb'), DeferredBuildThing)
        datastore.start()
        conditions.eventually {
            assert [name: 1] in collection.listIndexes()*.key
        }

        when: "the datastore is stopped, and a build is requested while it is"
        datastore.stop()
        datastore.buildIndex()

        then: "the request is deferred, not refused"
        notThrown(Exception)
        log.events.any {
            it.level == Level.INFO && it.formattedMessage.contains('for connection [default] while the datastore is stopped; it will run when the datastore is restarted')
        }

        when: "the index is dropped on the server and the datastore restarted"
        collection.dropIndex('name_1')
        datastore.start()

        then: "the deferred build runs and puts it back"
        log.events.any { it.formattedMessage.contains('Resuming the index build for connection [default]') }
        conditions.eventually {
            assert [name: 1] in collection.listIndexes()*.key
        }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a domain class registered while the datastore is stopped is indexed when it is started (async: #async)"() {
        given: "a datastore stopped for a checkpoint"
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(['grails.mongodb.url'                       : dbContainer.getReplicaSetUrl(database),
                                            (MongoSettings.SETTING_BUILD_INDEXES_ASYNC): async] as Map)
        datastore.start()
        datastore.stop()
        def indexes = { -> realClient.getDatabase(database).getCollection('registeredWhileStoppedThing').listIndexes()*.key }

        when: "a domain class is registered before the restore"
        datastore.mappingContext.addPersistentEntity(RegisteredWhileStoppedThing)

        then: "its index is not built while the clients refuse to be used"
        notThrown(Exception)
        !([name: 1] in indexes())

        when: "the datastore is started after the restore"
        datastore.start()

        then: "it is built, rather than put off as though the datastore were still stopped"
        conditions.eventually {
            assert [name: 1] in indexes()
        }
        !deferred(log)

        when: "a build is requested now that it is running"
        datastore.buildIndex()

        then: "that is not put off either"
        !deferred(log)

        cleanup:
        log?.close()
        datastore?.close()

        where:
        async << [false, true]
        database = async ? 'registeredWhileStoppedAsyncDb' : 'registeredWhileStoppedDb'
    }

    void "test a datastore stopped before it was ever started builds its indexes when it is started (async: #async)"() {
        given: "a datastore stopped before its first start"
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(['grails.mongodb.url'                       : dbContainer.getReplicaSetUrl(database),
                                            (MongoSettings.SETTING_BUILD_INDEXES_ASYNC): async] as Map, StoppedBeforeStartThing)
        def indexes = { -> realClient.getDatabase(database).getCollection('stoppedBeforeStartThing').listIndexes()*.key }
        datastore.stop()

        when:
        datastore.start()

        then: "its indexes are built, rather than put off as though the datastore were still stopped"
        conditions.eventually {
            assert [name: 1] in indexes()
        }
        !deferred(log)

        when: "a build is requested now that it is running"
        datastore.buildIndex()

        then: "that is not put off either"
        !deferred(log)

        cleanup:
        log?.close()
        datastore?.close()

        where:
        async << [false, true]
        database = async ? 'stoppedBeforeStartAsyncDb' : 'stoppedBeforeStartDb'
    }

    void "test a build requested before the first start runs when the datastore starts, with index creation off"() {
        given: "a datastore that builds no index by itself, stopped before its first start"
        def conditions = new PollingConditions(timeout: 30)
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(asyncConfig('requestedBeforeStartDb') + [(MongoSettings.SETTING_BUILD_INDEXES): false],
                RequestedBeforeStartThing)
        def indexes = { -> realClient.getDatabase('requestedBeforeStartDb').getCollection('requestedBeforeStartThing').listIndexes()*.key }
        datastore.stop()

        when: "a build is requested while it is stopped, and it is then started"
        datastore.buildIndex()
        datastore.start()

        then: "the requested build runs, though the startup build does not"
        log.events.any { it.formattedMessage.contains('Index creation on startup is disabled for connection [default]') }
        conditions.eventually {
            assert [name: 1] in indexes()
        }

        cleanup:
        log?.close()
        datastore?.close()
    }

    /**
     * Whether a build was put off until the next start, which is right only while the datastore is stopped.
     */
    private static boolean deferred(CapturedLog log) {
        log.events.any { it.formattedMessage.contains('while the datastore is stopped') }
    }

    void "test a build on the calling thread requested after close() is refused with a warning"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(['grails.mongodb.url': dbContainer.getReplicaSetUrl('closedSyncDb')] as Map,
                ClosedSyncThing)
        datastore.close()

        when: "a synchronous build is requested, which would otherwise run against the closed client"
        datastore.buildIndex()

        then:
        notThrown(Exception)
        log.events.any {
            it.level == Level.WARN && it.formattedMessage.contains('requested for connection [default] after the datastore was closed')
        }

        cleanup:
        log?.close()
    }

    void "test a build requested after close() is refused with a warning rather than an exception"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(asyncConfig('closedDb'), ClosedBuildThing)
        datastore.close()

        when:
        datastore.buildIndex()

        then:
        notThrown(Exception)
        log.events.any {
            it.level == Level.WARN && it.formattedMessage.contains('requested for connection [default] after the datastore was closed')
        }

        cleanup:
        log?.close()
    }
}

/**
 * Blocks the first index build in the protected hook, so that a test can act while the build is under
 * way. The latches are static: with an asynchronous build the hook can run before this class's own
 * constructor has finished, which is exactly what the hook's documentation warns an override about.
 */
class CheckpointedDatastore extends MongoDatastore {

    static final AtomicBoolean BLOCK_ONCE = new AtomicBoolean()

    static volatile CountDownLatch REACHED

    static volatile CountDownLatch RELEASE

    CheckpointedDatastore(Map<String, Object> configuration, Class... classes) {
        super(configuration, classes)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        if (BLOCK_ONCE.compareAndSet(true, false)) {
            REACHED.countDown()
            RELEASE.await()
        }
        super.initializeIndices(entity)
    }
}

/**
 * Holds the build up after it has applied every index, and does not respond to being interrupted: the
 * build then finishes normally even though {@link MongoDatastore#stop()} ran while it was under way.
 */
class SlowToStopDatastore extends MongoDatastore {

    static volatile CountDownLatch REACHED

    static volatile CountDownLatch RELEASE

    SlowToStopDatastore(Map<String, Object> configuration, Class... classes) {
        super(configuration, classes)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        super.initializeIndices(entity)
        REACHED.countDown()
        while (true) {
            try {
                RELEASE.await()
                return
            }
            catch (InterruptedException ignored) {
                // A step that does not respond to interruption, as a blocking call may not
            }
        }
    }
}

@Entity
class ClosedSyncThing {
    String name

    static mapping = {
        version false
        collection 'closedSyncThing'
        name index: true
    }
}

@Entity
class SlowToStopThing {
    String name

    static mapping = {
        version false
        collection 'slowToStopThing'
        name index: true
    }
}

@Entity
class DeferredBuildThing {
    String name

    static mapping = {
        version false
        collection 'deferredBuildThing'
        name index: true
    }
}

@Entity
class CheckpointedThing {
    String name

    static mapping = {
        version false
        collection 'checkpointedThing'
        name index: true
    }
}

@Entity
class ClosedBuildThing {
    String name

    static mapping = {
        version false
        collection 'closedBuildThing'
        name index: true
    }
}

@Entity
class RegisteredWhileStoppedThing {
    String name

    static mapping = {
        collection 'registeredWhileStoppedThing'
        name index: true
    }
}

@Entity
class RequestedBeforeStartThing {
    String name

    static mapping = {
        collection 'requestedBeforeStartThing'
        name index: true
    }
}

@Entity
class StoppedBeforeStartThing {
    String name

    static mapping = {
        collection 'stoppedBeforeStartThing'
        name index: true
    }
}

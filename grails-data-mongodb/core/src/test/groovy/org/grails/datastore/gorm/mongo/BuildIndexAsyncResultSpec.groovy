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

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.Level
import com.mongodb.MongoWriteConcernException
import com.mongodb.ServerAddress
import com.mongodb.bulk.WriteConcernError
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.IndexOptions
import grails.gorm.annotation.Entity
import org.bson.BsonDocument
import org.bson.Document
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * {@link MongoDatastore#buildIndexAsync()} runs the index build on the connection's background thread whatever
 * {@code grails.mongodb.buildIndexesAsync} says, and hands its caller the outcome.
 */
class BuildIndexAsyncResultSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('asyncResultDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    private Map config(String database) {
        ['grails.mongodb.url'                 : dbContainer.getReplicaSetUrl(database),
         (MongoSettings.SETTING_BUILD_INDEXES): false]
    }

    private List indexKeys(String database, String collection) {
        realClient.getDatabase(database).getCollection(collection).listIndexes()*.key
    }

    void "test the build runs on the background thread and reports what it applied"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new MongoDatastore(config('asyncResultCountsDb'), AsyncResultThing)

        when: "builds are synchronous by setting, and nothing was built at startup"
        def result = datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then: "both declarations were created, on the connection's index build thread"
        result.database() == 'asyncResultCountsDb'
        result.domainClasses() == 1
        result.created() == 2
        result.alreadyPresent() == 0
        result.recreated() == 0
        result.unclassified() == 0
        result.failures() == 0
        result.elapsedMillis() >= 0
        [name: 1] in indexKeys('asyncResultCountsDb', 'asyncResultThing')
        [code: 1, name: -1] in indexKeys('asyncResultCountsDb', 'asyncResultThing')
        log.events.any {
            it.formattedMessage.startsWith('Index build for database [asyncResultCountsDb] finished') &&
                    it.threadName.startsWith('gorm-mongo-index-build-default-')
        }

        and: "the build says a caller is waiting on it, not that startup does not wait"
        log.events*.formattedMessage.contains('Building the indexes declared by the domain classes for connection ' +
                '[default] on a background thread, for a caller waiting on the result.')
        !log.events.any { it.formattedMessage.contains('Startup does not wait') }

        when: "it runs again"
        def again = datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then:
        again.created() == 0
        again.alreadyPresent() == 2

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test created and present indexes are told apart even when the summary is not logged"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping.mongo', Level.WARN)
        def datastore = new MongoDatastore(config('asyncResultQuietDb'), AsyncResultThing)

        when:
        def result = datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then:
        result.created() == 2
        result.unclassified() == 0
        !log.events.any { it.formattedMessage.startsWith('Index build for database [asyncResultQuietDb]') }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a declaration that fails is counted and the build still completes"() {
        given: "an index on the declared keys whose options differ, which GORM will not replace undeclared"
        realClient.getDatabase('asyncResultFailureDb').getCollection('asyncResultThing')
                .createIndex(new Document('name', 1), new IndexOptions().unique(true))
        def datastore = new MongoDatastore(config('asyncResultFailureDb'), AsyncResultThing)

        when:
        def result = datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then: "the conflicting declaration failed, and the other was still applied"
        result.failures() == 1
        result.created() == 1
        [code: 1, name: -1] in indexKeys('asyncResultFailureDb', 'asyncResultThing')

        cleanup:
        datastore?.close()
    }

    void "test a unique index over duplicate values is counted as a failure and the build still completes"() {
        given:
        realClient.getDatabase('asyncResultDuplicateDb').getCollection('asyncResultUniqueThing')
                .insertMany([new Document('code', 'same'), new Document('code', 'same')])
        def datastore = new MongoDatastore(config('asyncResultDuplicateDb'), AsyncResultUniqueThing)

        when:
        def result = datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then: "the unique index failed, and the other declaration was still applied"
        result.failures() == 1
        result.created() == 1
        [name: 1] in indexKeys('asyncResultDuplicateDb', 'asyncResultUniqueThing')
        !([code: 1] in indexKeys('asyncResultDuplicateDb', 'asyncResultUniqueThing'))

        cleanup:
        datastore?.close()
    }

    void "test a write concern the server could not satisfy stops the build instead of counting as a failed declaration"() {
        given: "each index is created, and only its acknowledgement fails"
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        MongoClient unacknowledged = FailingMongoClient.wrap(realClient, 'createIndex') { Closure proceed ->
            proceed()
            throw writeConcernFailure()
        }
        def datastore = datastoreOn(unacknowledged, 'asyncResultWriteConcernDb', AsyncResultThing)

        when:
        datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof MongoWriteConcernException
        log.events.any {
            it.formattedMessage.startsWith('Index build for database [asyncResultWriteConcernDb] did not finish')
        }

        when: "the build runs on the caller's thread"
        datastore.buildIndex()

        then: "it reaches the caller"
        thrown(MongoWriteConcernException)

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a write concern failure after recreateOnConflict dropped the old index stops the build"() {
        given: "an index on the declared keys without the unique option declared, which the declaration may replace"
        realClient.getDatabase('asyncResultRecreateConcernDb').getCollection('asyncResultRecreatedThing')
                .createIndex(new Document('code', 1))
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        MongoClient unacknowledged = FailingMongoClient.wrap(realClient, 'createIndex') { Closure proceed ->
            // The first attempt is refused by the server for its options; the one after the drop is applied
            proceed()
            throw writeConcernFailure()
        }
        def datastore = datastoreOn(unacknowledged, 'asyncResultRecreateConcernDb', AsyncResultRecreatedThing)

        when:
        datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof MongoWriteConcernException

        and: "the unique index was built; only its acknowledgement failed"
        realClient.getDatabase('asyncResultRecreateConcernDb').getCollection('asyncResultRecreatedThing')
                .listIndexes().find { it.key == [code: 1] }?.unique == true

        and: "the log says the outcome is not known, not that the collection has no index on those keys"
        log.events.any {
            it.level == Level.WARN && it.formattedMessage.startsWith('Dropped index [code_1] on entity [') &&
                    it.formattedMessage.contains('but the server did not confirm the new one')
        }
        !log.events.any { it.formattedMessage.contains('could not be built again') }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a build on the caller's thread waits for the background build to finish"() {
        given:
        def datastore = new SerializedBuildDatastore(config('asyncResultCallerWaitsDb'), AsyncResultThing)
        SerializedBuildDatastore.reset()

        when: "a background build is under way"
        SerializedBuildDatastore.BLOCK.set(true)
        def background = datastore.buildIndexAsync()
        SerializedBuildDatastore.REACHED.await(30, TimeUnit.SECONDS)

        and: "a build is requested on another thread, with builds synchronous by setting"
        def callerDone = new CountDownLatch(1)
        Thread.start { datastore.buildIndex(); callerDone.countDown() }

        then: "it does not enter index creation while the background build is running"
        !callerDone.await(1, TimeUnit.SECONDS)
        SerializedBuildDatastore.ENTERED.get() == 1

        when:
        SerializedBuildDatastore.RELEASE.countDown()

        then: "both builds ran, one after the other"
        background.get(30, TimeUnit.SECONDS).failures() == 0
        callerDone.await(30, TimeUnit.SECONDS)
        SerializedBuildDatastore.ENTERED.get() == 2
        SerializedBuildDatastore.MOST_AT_ONCE.get() == 1

        cleanup:
        SerializedBuildDatastore.RELEASE?.countDown()
        datastore?.close()
    }

    void "test a background build waits for a build running on a caller's thread"() {
        given:
        def datastore = new SerializedBuildDatastore(config('asyncResultBackgroundWaitsDb'), AsyncResultThing)
        SerializedBuildDatastore.reset()

        when: "a build is under way on another thread, with builds synchronous by setting"
        SerializedBuildDatastore.BLOCK.set(true)
        def callerDone = new CountDownLatch(1)
        Thread.start { datastore.buildIndex(); callerDone.countDown() }
        SerializedBuildDatastore.REACHED.await(30, TimeUnit.SECONDS)

        and:
        def background = datastore.buildIndexAsync()

        then: "the background build does not enter index creation while it is running"
        !background.isDone()
        SerializedBuildDatastore.ENTERED.get() == 1

        when:
        Thread.sleep(500)

        then:
        !background.isDone()
        SerializedBuildDatastore.ENTERED.get() == 1

        when:
        SerializedBuildDatastore.RELEASE.countDown()

        then:
        callerDone.await(30, TimeUnit.SECONDS)
        background.get(30, TimeUnit.SECONDS).failures() == 0
        SerializedBuildDatastore.ENTERED.get() == 2
        SerializedBuildDatastore.MOST_AT_ONCE.get() == 1

        cleanup:
        SerializedBuildDatastore.RELEASE?.countDown()
        datastore?.close()
    }

    void "test a domain class registered while a background build is running is indexed after it"() {
        given: "a datastore that built its indexes at startup"
        def datastore = new SerializedBuildDatastore(['grails.mongodb.url': dbContainer.getReplicaSetUrl('asyncResultRegisterWaitsDb')],
                AsyncResultThing)
        datastore.start()
        SerializedBuildDatastore.reset()

        when: "a background build is under way"
        SerializedBuildDatastore.BLOCK.set(true)
        def background = datastore.buildIndexAsync()
        SerializedBuildDatastore.REACHED.await(30, TimeUnit.SECONDS)

        and: "another domain class is registered on another thread"
        def registered = new CountDownLatch(1)
        Thread.start { datastore.mappingContext.addPersistentEntity(AsyncResultLateThing); registered.countDown() }

        then: "its indexes are not applied while the background build is running"
        !registered.await(1, TimeUnit.SECONDS)
        SerializedBuildDatastore.ENTERED.get() == 1

        when:
        SerializedBuildDatastore.RELEASE.countDown()

        then: "both ran, one after the other"
        background.get(30, TimeUnit.SECONDS).failures() == 0
        registered.await(30, TimeUnit.SECONDS)
        SerializedBuildDatastore.ENTERED.get() == 2
        SerializedBuildDatastore.MOST_AT_ONCE.get() == 1
        [label: 1] in indexKeys('asyncResultRegisterWaitsDb', 'asyncResultLateThing')

        cleanup:
        SerializedBuildDatastore.RELEASE?.countDown()
        datastore?.close()
    }

    private static MongoWriteConcernException writeConcernFailure() {
        new MongoWriteConcernException(new WriteConcernError(64, 'WriteConcernFailed', 'waiting for replication timed out',
                new BsonDocument()), new ServerAddress())
    }

    private MongoDatastore datastoreOn(MongoClient client, String database, Class... classes) {
        new MongoDatastore(client, DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName'        : database,
                (MongoSettings.SETTING_BUILD_INDEXES): false
        ]), classes)
    }

    void "test a build that stops partway completes the future with the exception that stopped it"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)
        def datastore = new FailingAsyncResultDatastore(config('asyncResultThrowDb'), AsyncResultThing)

        when:
        datastore.buildIndexAsync().get(30, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof IllegalStateException
        e.cause.message == 'the build stopped here'

        and: "it is still reported, as any background build that fails is"
        log.events.any {
            it.level == Level.ERROR && it.formattedMessage.startsWith('The background index build failed: the build stopped here')
        }

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a build requested while the datastore is stopped or closed fails at once"() {
        given:
        def datastore = new MongoDatastore(config('asyncResultStoppedDb'), AsyncResultThing)
        datastore.start()

        when:
        datastore.stop()
        def whileStopped = datastore.buildIndexAsync()

        then:
        whileStopped.isCompletedExceptionally()
        causeOf(whileStopped) instanceof IllegalStateException
        causeOf(whileStopped).message == 'The index build for connection [default] was not started: the datastore is stopped.'

        when: "it is restarted"
        datastore.start()

        then: "builds run again"
        datastore.buildIndexAsync().get(30, TimeUnit.SECONDS).created() == 2

        when:
        datastore.close()
        def afterClose = datastore.buildIndexAsync()

        then:
        afterClose.isCompletedExceptionally()
        causeOf(afterClose).message == 'The index build for connection [default] was not started: the datastore is closed.'

        cleanup:
        datastore?.close()
    }

    void "test stopping the datastore completes the futures of the build it cuts short and of one still queued"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        BlockingAsyncResultDatastore.REACHED = new CountDownLatch(1)
        BlockingAsyncResultDatastore.RELEASE = new CountDownLatch(1)
        def datastore = new BlockingAsyncResultDatastore(config('asyncResultQueuedDb'), AsyncResultThing)
        datastore.start()

        when: "one build is under way and another is queued behind it"
        BlockingAsyncResultDatastore.BLOCK.set(true)
        def running = datastore.buildIndexAsync()
        BlockingAsyncResultDatastore.REACHED.await(30, TimeUnit.SECONDS)
        def queued = datastore.buildIndexAsync()

        and: "the datastore is stopped for a checkpoint"
        datastore.stop()

        then: "the running build is interrupted, and the queued one never runs"
        conditions.eventually {
            assert running.isCompletedExceptionally()
        }
        causeOf(running) instanceof InterruptedException
        queued.isCompletedExceptionally()
        causeOf(queued) instanceof CancellationException

        when: "it is restarted"
        datastore.start()

        then: "the build that was cut short runs again in the background"
        conditions.eventually {
            assert [name: 1] in indexKeys('asyncResultQueuedDb', 'asyncResultThing')
        }

        cleanup:
        BlockingAsyncResultDatastore.BLOCK.set(false)
        BlockingAsyncResultDatastore.RELEASE?.countDown()
        datastore?.close()
    }

    private static Throwable causeOf(CompletableFuture future) {
        try {
            future.join()
            return null
        }
        catch (CompletionException e) {
            return e.cause
        }
        catch (CancellationException e) {
            return e
        }
    }
}

@Entity
class AsyncResultThing {

    String name
    String code

    static mapping = {
        version false
        collection 'asyncResultThing'
        name index: true
        compoundIndex([code: 1, name: -1])
    }
}

@Entity
class AsyncResultLateThing {

    String label

    static mapping = {
        version false
        collection 'asyncResultLateThing'
        label index: true
    }
}

@Entity
class AsyncResultRecreatedThing {

    String code

    static mapping = {
        version false
        collection 'asyncResultRecreatedThing'
        code index: true, indexAttributes: [unique: true, recreateOnConflict: true]
    }
}

@Entity
class AsyncResultUniqueThing {

    String name
    String code

    static mapping = {
        version false
        collection 'asyncResultUniqueThing'
        name index: true
        code index: true, indexAttributes: [unique: true]
    }
}

class FailingAsyncResultDatastore extends MongoDatastore {

    FailingAsyncResultDatastore(Map<String, Object> configuration, Class... classes) {
        super(configuration, classes)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        throw new IllegalStateException('the build stopped here')
    }
}

/**
 * Counts the builds inside index creation at once, and can hold the first one there until released.
 */
class SerializedBuildDatastore extends MongoDatastore {

    static final AtomicBoolean BLOCK = new AtomicBoolean()

    static final AtomicInteger ENTERED = new AtomicInteger()

    static final AtomicInteger RUNNING = new AtomicInteger()

    static final AtomicInteger MOST_AT_ONCE = new AtomicInteger()

    static volatile CountDownLatch REACHED

    static volatile CountDownLatch RELEASE

    SerializedBuildDatastore(Map<String, Object> configuration, Class... classes) {
        super(configuration, classes)
    }

    static void reset() {
        BLOCK.set(false)
        ENTERED.set(0)
        RUNNING.set(0)
        MOST_AT_ONCE.set(0)
        REACHED = new CountDownLatch(1)
        RELEASE = new CountDownLatch(1)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        ENTERED.incrementAndGet()
        MOST_AT_ONCE.accumulateAndGet(RUNNING.incrementAndGet(), Math::max)
        try {
            if (BLOCK.compareAndSet(true, false)) {
                REACHED.countDown()
                RELEASE.await()
            }
            super.initializeIndices(entity)
        }
        finally {
            RUNNING.decrementAndGet()
        }
    }
}

class BlockingAsyncResultDatastore extends MongoDatastore {

    static final AtomicBoolean BLOCK = new AtomicBoolean()

    static volatile CountDownLatch REACHED

    static volatile CountDownLatch RELEASE

    BlockingAsyncResultDatastore(Map<String, Object> configuration, Class... classes) {
        super(configuration, classes)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        if (BLOCK.compareAndSet(true, false)) {
            REACHED.countDown()
            RELEASE.await()
        }
        super.initializeIndices(entity)
    }
}

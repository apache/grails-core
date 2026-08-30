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
package org.grails.datastore.gorm.mongodb.embedded

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients

import org.bson.Document

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource

import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import java.nio.file.Path

/**
 * Exercises the stop and restart that a CRaC checkpoint and restore drive.
 *
 * <p>Stopping is what releases the sockets that would otherwise refuse the checkpoint, and
 * starting again has to leave the application looking at the same database on the same
 * port -- a restored process that silently comes back empty is a worse failure than one
 * that could not be checkpointed at all.
 */
class EmbeddedMongoLifecycleSpec extends Specification {

    @TempDir
    Path temp

    PollingConditions conditions = new PollingConditions(timeout: 20, delay: 0.1)

    void 'the lifecycle of the server it started is managed by the application context'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27994',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)
        EmbeddedMongoLifecycle lifecycle = context.beanFactory
                .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)

        then: 'the server is already listening by the time the bean exists'
        lifecycle.running

        and: 'it starts before, and stops after, the datastore that talks to it'
        lifecycle.phase == EmbeddedMongoLifecycle.PHASE
        lifecycle.phase < 0

        cleanup:
        lifecycle?.stop()
    }

    void 'the in-memory backend keeps its data across the stop a checkpoint needs'() {
        given: 'a server holding a document, as an application would before it is checkpointed'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND) : InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27995/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')
        write(url, 'Groovy in Action')

        and:
        EmbeddedMongoLifecycle lifecycle = context.beanFactory
                .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)

        when: 'the checkpoint stops it, because CRaC refuses to snapshot a process holding sockets'
        lifecycle.stop()

        then:
        !lifecycle.running
        conditions.eventually { assert !listening(27995) }

        when: 'the restore starts it again'
        lifecycle.start()

        then: 'it is back on the port the published url already names'
        lifecycle.running
        conditions.eventually { assert listening(27995) }

        and: 'holding what it held, rather than the empty database a cleared backend leaves'
        titles(url) == ['Groovy in Action']

        cleanup:
        lifecycle?.stop()
    }

    void 'stopping and starting more than once does the work only once'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND) : InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27996/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')
        EmbeddedMongoLifecycle lifecycle = context.beanFactory
                .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)

        when: 'a context that is already stopped is stopped again'
        lifecycle.stop()
        lifecycle.stop()

        then:
        !lifecycle.running

        when: 'and a running one is started again, which binding a second server would fail'
        lifecycle.start()
        lifecycle.start()

        then:
        lifecycle.running
        conditions.eventually { assert listening(27996) }

        and: 'the server still answers'
        write(url, 'Making Java Groovy')
        titles(url) == ['Making Java Groovy']

        cleanup:
        lifecycle?.stop()
    }

    void 'a server stopped by the context is stopped again by the shutdown hook'() {
        given: 'a real mongod, whose process directory flapdoodle removes as it tears the server down'
        RunningEmbeddedMongo running = new FlapdoodleMongoBackend()
                .start(new EmbeddedMongoSettings(27979, null, null))

        when: 'the context stops it, and the hook that covers a context that never closes follows'
        running.stop()
        running.stop()

        then: 'the second stop finds the files the first one removed, and says nothing of it'
        noExceptionThrown()
        conditions.eventually { assert !listening(27979) }
    }

    void 'a running server is replaced by a restart rather than fought with for its port'() {
        given: 'a real mongod told to keep its data, so a replacement can be seen holding it'
        String databaseDir = temp.resolve('restartDb').toString()
        RunningEmbeddedMongo running = new FlapdoodleMongoBackend()
                .start(new EmbeddedMongoSettings(27977, null, databaseDir))
        String url = 'mongodb://localhost:27977/bookstore'
        write(url, 'Grails in Action')
        long before = processId(url)

        when: 'a restore starts a server that whatever took the checkpoint never stopped'
        running.restart()

        then: 'the port was released and taken again, rather than bound twice or left alone'
        conditions.eventually { assert processId(url) != before }

        and:
        titles(url) == ['Grails in Action']

        cleanup:
        running?.stop()
    }

    void 'an in-memory server is stopped twice by the same pair'() {
        given:
        RunningEmbeddedMongo running = new InMemoryMongoBackend()
                .start(new EmbeddedMongoSettings(27978, null, null))

        when:
        running.stop()
        running.stop()

        then:
        noExceptionThrown()
        conditions.eventually { assert !listening(27978) }
    }

    void 'the flapdoodle backend keeps a persistent database across the same stop'() {
        given: 'a real mongod told to keep its data, which is how a production application runs'
        String databaseDir = temp.resolve('prodDb').toString()
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)     : FlapdoodleMongoBackend.NAME,
                (EmbeddedMongoInitializer.DATABASE_DIR): databaseDir,
                'grails.mongodb.url'              : 'mongodb://embedded:27997/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')
        write(url, 'Grails in Action')

        and:
        EmbeddedMongoLifecycle lifecycle = context.beanFactory
                .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)

        when:
        lifecycle.stop()

        then: 'the mongod process is gone, so the driver has nothing to reconnect to'
        conditions.eventually { assert !listening(27997) }

        and: 'and what it wrote is on disc rather than in a temp directory it deleted'
        new File(databaseDir).list()

        when:
        lifecycle.start()

        then:
        conditions.eventually { assert listening(27997) }
        titles(url) == ['Grails in Action']

        cleanup:
        lifecycle?.stop()
    }

    void 'a replica set that never forms leaves no server behind'() {
        given: 'a backend whose set does not come up, as it would not if the node never elected itself'
        FlapdoodleMongoBackend backend = new FlapdoodleMongoBackend() {
            @Override
            void initiateReplicaSet(String host, int port, String replicaSet) {
                throw new IllegalStateException('the set never formed')
            }
        }

        when:
        backend.start(new EmbeddedMongoSettings(27976, null, null, 'rs0'))

        then: 'the failure is what the caller sees'
        IllegalStateException failure = thrown()
        failure.message == 'the set never formed'

        and: 'and the mongod it started is not left holding the port, since nothing else can stop it'
        conditions.eventually { assert !listening(27976) }
    }

    void 'a server the context stopped is started again for the context after it'() {
        given: 'a server, and the bean the context that started it manages it with'
        RunningEmbeddedMongo running = new InMemoryMongoBackend()
                .start(new EmbeddedMongoSettings(27975, null, null))
        EmbeddedMongoLifecycle first = new EmbeddedMongoLifecycle(running)

        when: 'that context closes, as devtools closes it before reloading'
        first.stop()

        then:
        !first.running
        conditions.eventually { assert !listening(27975) }

        when: 'the reloaded application manages the same server from a new context'
        EmbeddedMongoLifecycle second = new EmbeddedMongoLifecycle(running)

        then: 'the bean says what the server says, so Spring knows there is something to start'
        !second.running

        when:
        second.start()

        then: 'the application is given a server that is actually listening'
        second.running
        conditions.eventually { assert listening(27975) }

        cleanup:
        second?.stop()
    }

    void 'stopping a server that is already stopped is what a shutdown hook does'() {
        given: 'an in-memory server the context has stopped'
        RunningEmbeddedMongo running = new InMemoryMongoBackend()
                .start(new EmbeddedMongoSettings(27974, null, null))
        new Socket('localhost', 27974).withCloseable { }
        running.stop()

        when: 'the JVM shutdown hook stops it again on the way out'
        running.stop()

        then: 'it says nothing, because a hook has nowhere to report to'
        noExceptionThrown()
        !running.running
    }

    private static void write(String url, String title) {
        try (MongoClient client = MongoClients.create(url)) {
            client.getDatabase('bookstore').getCollection('books').insertOne(new Document('title', title))
        }
    }

    private static long processId(String url) {
        try (MongoClient client = MongoClients.create(url)) {
            client.getDatabase('admin').runCommand(new Document('serverStatus', 1)).get('pid') as long
        }
    }

    private static List<String> titles(String url) {
        try (MongoClient client = MongoClients.create(url)) {
            client.getDatabase('bookstore').getCollection('books').find()*.getString('title')
        }
    }

    private static boolean listening(int port) {
        try {
            new Socket('localhost', port).withCloseable { true }
        }
        catch (IOException ignored) {
            false
        }
    }

    private static GenericApplicationContext contextWith(Map<String, Object> properties) {
        GenericApplicationContext context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        context
    }
}

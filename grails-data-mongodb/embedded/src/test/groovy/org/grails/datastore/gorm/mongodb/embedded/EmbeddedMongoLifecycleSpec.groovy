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

    private static void write(String url, String title) {
        try (MongoClient client = MongoClients.create(url)) {
            client.getDatabase('bookstore').getCollection('books').insertOne(new Document('title', title))
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

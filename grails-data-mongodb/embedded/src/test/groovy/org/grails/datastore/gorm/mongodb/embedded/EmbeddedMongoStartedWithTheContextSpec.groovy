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
import spock.lang.Specification

import org.springframework.context.SmartLifecycle
import org.springframework.context.support.DefaultLifecycleProcessor
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource

/**
 * With {@code spring.context.checkpoint=onRefresh}, Spring checkpoints the process after every bean has been created
 * and before it starts a single lifecycle bean, and with {@code spring.context.exit=onRefresh} it halts the JVM there.
 * Either way the embedded server must not be listening by then: CRaC refuses a process holding its socket, and a halt
 * runs no shutdown hook to stop a {@code mongod}.
 *
 * <p>Both are set the way an application sets them, as system properties. Spring reads them once, when
 * {@link DefaultLifecycleProcessor} is loaded, and acts on them as a context refreshes; that class is loaded here
 * before either is set, so the contexts refreshed by this specification are neither checkpointed nor halted.
 */
class EmbeddedMongoStartedWithTheContextSpec extends Specification {

    private final List<GenericApplicationContext> contexts = []

    private final List<String> properties = []

    void setupSpec() {
        new DefaultLifecycleProcessor()
    }

    void cleanup() {
        properties.each { System.clearProperty(it) }
        contexts.each { GenericApplicationContext context ->
            if (context.beanFactory.containsSingleton(EmbeddedMongoLifecycle.BEAN_NAME)) {
                context.beanFactory.getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle).stop()
            }
        }
    }

    void 'a process checkpointed as the context refreshes has its server started by the context'() {
        given:
        onRefresh(DefaultLifecycleProcessor.CHECKPOINT_PROPERTY_NAME)
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:28031/bookstore',
        ])
        FirstToStart first = new FirstToStart(28031)
        context.beanFactory.registerSingleton('firstToStart', first)

        when:
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')

        then: 'the url is published for the server, which is not listening yet'
        url == 'mongodb://localhost:28031/bookstore'
        !listening(28031)

        when:
        context.refresh()

        then: 'nothing was listening when the first lifecycle bean started, which is where the checkpoint is taken'
        first.listeningWhenStarted == false

        and: 'the context started the server, and it answers at the url'
        roundTrip(url) == 'answered'

        cleanup:
        context.close()
    }

    void 'a process that exits as the context refreshes never starts a server'() {
        given:
        onRefresh(DefaultLifecycleProcessor.EXIT_PROPERTY_NAME)
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:28032/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)
        EmbeddedMongoLifecycle lifecycle = context.beanFactory
                .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)

        then: 'Spring halts the JVM before it starts the lifecycle, so nothing is left for a shutdown hook to stop'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:28032/bookstore'
        !lifecycle.running
        !listening(28032)

        when: 'a context that is started, all the same, starts it'
        lifecycle.start()

        then:
        lifecycle.running
        listening(28032)
    }

    void 'a url that asks for any port names the one the server will bind'() {
        given:
        onRefresh(DefaultLifecycleProcessor.CHECKPOINT_PROPERTY_NAME)
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:0/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')
        int port = (url =~ /localhost:(\d+)\//)[0][1] as int

        then: 'published before the server binds, so it has to be a port rather than 0'
        port > 0
        !listening(port)

        when:
        context.beanFactory.getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle).start()

        then:
        roundTrip(url) == 'answered'
    }

    void 'a port held by something else is reported when the context starts the server'() {
        given: 'an unrelated service holding the port, on the address a backend binds'
        ServerSocket intruder = new ServerSocket(28033, 1, InetAddress.getByName('localhost'))
        onRefresh(DefaultLifecycleProcessor.CHECKPOINT_PROPERTY_NAME)
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:28033/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)

        when:
        context.beanFactory.getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle).start()

        then:
        IllegalStateException e = thrown()
        e.message.contains('something else may already be using')

        cleanup:
        intruder.close()
    }

    void 'a server a reloaded context reuses is started again by that context, not before'() {
        given: 'a server started for one context and stopped as that context closed'
        onRefresh(DefaultLifecycleProcessor.CHECKPOINT_PROPERTY_NAME)
        Map<String, Object> settings = [
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:28034/bookstore',
        ] as Map<String, Object>
        GenericApplicationContext first = contextWith(settings)
        new EmbeddedMongoInitializer().initialize(first)
        EmbeddedMongoLifecycle firstLifecycle = first.beanFactory.getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)
        firstLifecycle.start()
        firstLifecycle.stop()

        when: 'the reloaded application builds another'
        GenericApplicationContext reloaded = contextWith(settings)
        new EmbeddedMongoInitializer().initialize(reloaded)
        EmbeddedMongoLifecycle reused = reloaded.beanFactory.getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle)

        then: 'it is not started again before that context starts it'
        reloaded.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:28034/bookstore'
        !reused.running
        !listening(28034)

        when:
        reused.start()

        then:
        listening(28034)
    }

    private void onRefresh(String property) {
        properties << property
        System.setProperty(property, DefaultLifecycleProcessor.ON_REFRESH_VALUE)
    }

    private GenericApplicationContext contextWith(Map<String, Object> properties) {
        GenericApplicationContext context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        contexts << context
        context
    }

    static boolean listening(int port) {
        try {
            new Socket('localhost', port).withCloseable { true }
        }
        catch (IOException ignored) {
            false
        }
    }

    private static String roundTrip(String url) {
        try (MongoClient client = MongoClients.create(url)) {
            def collection = client.getDatabase('bookstore').getCollection('probe')
            collection.insertOne(new Document('answer', 'answered'))
            collection.find().first().getString('answer')
        }
    }
}

/**
 * The lifecycle bean Spring starts before any other, and so the first thing to run after a checkpoint taken as the
 * context refreshes: whatever it finds is what the checkpoint would have contained.
 */
class FirstToStart implements SmartLifecycle {

    private final int port

    Boolean listeningWhenStarted

    private boolean running

    FirstToStart(int port) {
        this.port = port
    }

    @Override
    void start() {
        listeningWhenStarted = EmbeddedMongoStartedWithTheContextSpec.listening(port)
        running = true
    }

    @Override
    void stop() {
        running = false
    }

    @Override
    boolean isRunning() {
        running
    }

    @Override
    int getPhase() {
        Integer.MIN_VALUE
    }
}

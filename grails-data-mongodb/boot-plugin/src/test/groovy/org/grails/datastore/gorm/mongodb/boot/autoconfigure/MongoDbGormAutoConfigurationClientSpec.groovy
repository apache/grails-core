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
package org.grails.datastore.gorm.mongodb.boot.autoconfigure

import java.util.concurrent.atomic.AtomicInteger

import com.mongodb.MongoTimeoutException
import com.mongodb.client.MongoClient
import com.mongodb.event.ConnectionPoolCreatedEvent
import com.mongodb.event.ConnectionPoolListener
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.MapPropertySource

import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.connections.RestartableMongoClient

/**
 * Spring Boot's {@link MongoAutoConfiguration} declares a {@code MongoClient} unless there is one already. GORM declares
 * one first, built the same way, so the application's {@code MongoClient} is one GORM owns: it connects when the
 * datastore starts, and it is stopped and started again around a CRaC checkpoint. Nothing here talks to a server: the
 * url names a port nothing listens on, and see {@link #closed}.
 */
class MongoDbGormAutoConfigurationClientSpec extends Specification {

    static final String UNREACHABLE = 'mongodb://localhost:1/test?serverSelectionTimeoutMS=50'

    @AutoCleanup
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()

    private void refresh(Map<String, Object> properties, Class... configurations) {
        // A package with no domain classes: the datastore then builds no indexes.
        AutoConfigurationPackages.register(context, 'org.grails.datastore.gorm.mongodb.boot.autoconfigure.noentities')
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        context.register(configurations)
        context.refresh()
    }

    void 'without a MongoClient of its own, the application is given the one GORM builds from Spring Boot settings'() {
        when:
        refresh(['spring.mongodb.uri': UNREACHABLE], BootMongoConfiguration)
        MongoClient mongo = context.getBean(MongoClient)

        then: 'there is one client, and it is the one the datastore uses and owns'
        context.getBeansOfType(MongoClient).size() == 1
        mongo.is(context.getBean(MongoDatastore).mongoClient)
        mongo instanceof RestartableMongoClient

        and: 'built from Spring Boot settings, as Spring Boot would have built its own'
        mongo.clusterDescription.clusterSettings.hosts*.toString() == ['localhost:1']
    }

    void 'the client GORM builds is created when the context starts, and is the same client after a checkpoint'() {
        given:
        AtomicInteger clientsCreated = new AtomicInteger()
        context.beanFactory.registerSingleton('watchClients', { builder ->
            // Counted by the connection pool the driver creates as it builds a client, which it reports on the
            // building thread; it reports cluster events later, on a thread of its own.
            builder.applyToConnectionPoolSettings {
                it.addConnectionPoolListener(new ConnectionPoolListener() {
                    @Override
                    void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
                        clientsCreated.incrementAndGet()
                    }
                })
            }
        } as MongoClientSettingsBuilderCustomizer)
        FirstToStart first = new FirstToStart(clientsCreated)
        context.beanFactory.registerSingleton('firstToStart', first)

        when:
        refresh(['spring.mongodb.uri': UNREACHABLE], BootMongoConfiguration)
        MongoClient mongo = context.getBean(MongoClient)

        then: 'nothing had been created when the first lifecycle bean started, where a checkpoint on refresh is taken'
        first.clientsCreatedWhenStarted == 0

        and: 'the datastore created it when Spring started it'
        clientsCreated.get() == 1
        !closed(mongo)

        when: 'Spring stops the lifecycle beans for a checkpoint'
        context.stop()

        then:
        closed(mongo)

        when: 'and starts them again after the restore'
        context.start()

        then: 'the client everything was given is the one connected again'
        context.getBean(MongoClient).is(mongo)
        !closed(mongo)
        clientsCreated.get() == 2
    }

    void 'a MongoClient the application declares is the one GORM uses, and GORM leaves it to the application'() {
        given:
        MongoClient declared = Mock()
        context.beanFactory.registerSingleton('applicationMongo', declared)

        when:
        refresh(['spring.mongodb.uri': UNREACHABLE], BootMongoConfiguration)

        then: 'neither GORM nor Spring Boot declares another'
        context.getBeansOfType(MongoClient) == [applicationMongo: declared]
        context.getBean(MongoDatastore).mongoClient.is(declared)

        when:
        context.getBean(MongoDatastore).stop()

        then: 'stopping or closing it for a checkpoint is the application\'s business'
        0 * declared.close()
    }

    void 'without Spring Boot MongoDB settings, GORM builds the client from grails.mongodb and still publishes it'() {
        when:
        refresh(['grails.mongodb.url': UNREACHABLE], GormOnlyConfiguration)
        MongoClient mongo = context.getBean(MongoClient)

        then:
        mongo.is(context.getBean(MongoDatastore).mongoClient)
        mongo.clusterDescription.clusterSettings.hosts*.toString() == ['localhost:1']
    }

    /**
     * Whether the driver has been closed, which needs no MongoDB to answer: selecting a server from a closed
     * cluster is rejected outright, while an open client with nothing to connect to waits for the server
     * selection timeout and gives up.
     */
    private static boolean closed(MongoClient client) {
        try {
            client.listDatabaseNames().first()
            false
        }
        catch (IllegalStateException ignored) {
            true
        }
        catch (MongoTimeoutException ignored) {
            false
        }
    }

    /**
     * Both auto-configurations, in the order Spring Boot puts them, which is not the order they are listed in.
     */
    @Configuration
    @ImportAutoConfiguration([MongoDbGormAutoConfiguration, MongoAutoConfiguration])
    static class BootMongoConfiguration {
    }

    @Configuration
    @Import(MongoDbGormAutoConfiguration)
    static class GormOnlyConfiguration {
    }

    static class FirstToStart implements SmartLifecycle {

        private final AtomicInteger clientsCreated

        Integer clientsCreatedWhenStarted

        private boolean running

        FirstToStart(AtomicInteger clientsCreated) {
            this.clientsCreated = clientsCreated
        }

        @Override
        void start() {
            if (clientsCreatedWhenStarted == null) {
                clientsCreatedWhenStarted = clientsCreated.get()
            }
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
}

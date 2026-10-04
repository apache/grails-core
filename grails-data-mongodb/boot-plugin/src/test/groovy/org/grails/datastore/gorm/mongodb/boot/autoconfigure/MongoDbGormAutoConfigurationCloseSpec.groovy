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

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
import com.mongodb.client.MongoClient
import com.mongodb.event.ConnectionPoolCreatedEvent
import com.mongodb.event.ConnectionPoolListener
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.mongodb.autoconfigure.MongoProperties
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * With MongoDB's own properties present but no {@code MongoClient} bean, the auto-configuration builds the client
 * itself, from Spring Boot's settings rather than from {@code grails.mongodb}. That client belongs to GORM: it is
 * closed with the datastore, and stopped and started again around a CRaC checkpoint, so the checkpoint is not left
 * holding its sockets. No MongoDB is needed: see {@link #closed}.
 */
class MongoDbGormAutoConfigurationCloseSpec extends Specification {

    @AutoCleanup
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()

    private MongoDatastore datastoreFromBootSettings() {
        // A package with no domain classes: the datastore then builds no indexes, so no server is needed.
        AutoConfigurationPackages.register(context, 'org.grails.datastore.gorm.mongodb.boot.autoconfigure.noentities')
        context.register(BootSettingsConfiguration)
        context.refresh()
        context.getBean(MongoDatastore)
    }

    void 'test a client the auto-configuration builds is closed with the datastore'() {
        given:
        MongoDatastore datastore = datastoreFromBootSettings()
        MongoClient built = datastore.mongoClient

        when:
        datastore.close()

        then:
        closed(built)
    }

    void 'test a client the auto-configuration builds is stopped for a checkpoint and started again for the restore'() {
        given:
        MongoDatastore datastore = datastoreFromBootSettings()
        MongoClient built = datastore.mongoClient

        when: 'the checkpoint stops it'
        datastore.stop()

        then: 'it holds no sockets for the checkpoint to trip over'
        closed(built)

        when: 'the restore starts it again'
        datastore.start()

        then: 'it is the same client, connected again through a driver client built the same way'
        datastore.mongoClient.is(built)
        !closed(built)

        cleanup:
        datastore.close()
    }

    void 'test the client the auto-configuration builds is created only when the context starts its lifecycle'() {
        given:
        AutoConfigurationPackages.register(context, 'org.grails.datastore.gorm.mongodb.boot.autoconfigure.noentities')
        context.register(WatchedBootSettingsConfiguration)

        when:
        context.refresh()

        then: 'no client existed when the first lifecycle bean started, which is where a checkpoint taken as the ' +
                'context refreshes is taken'
        context.getBean(FirstToStart).clientsCreatedWhenStarted == 0

        and: 'the datastore created it when Spring started it'
        context.getBean(WatchedBootSettingsConfiguration).clientsCreated.get() == 1
        context.getBean(MongoDatastore).running
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
     * As {@link BootSettingsConfiguration}, with the settings telling the driver to report each client it creates,
     * and the lifecycle bean Spring starts first.
     */
    @Configuration
    @Import(MongoDbGormAutoConfiguration)
    static class WatchedBootSettingsConfiguration {

        final AtomicInteger clientsCreated = new AtomicInteger()

        @Bean
        MongoProperties mongoProperties() {
            new MongoProperties()
        }

        @Bean
        MongoClientSettings mongoClientSettings() {
            MongoClientSettings.builder()
                    .applyToClusterSettings { it.serverSelectionTimeout(50, TimeUnit.MILLISECONDS) }
                    // Counted by the connection pool the driver creates as it builds a client, which it reports on
                    // the building thread; it reports cluster events later, on a thread of its own.
                    .applyToConnectionPoolSettings {
                        it.addConnectionPoolListener(new ConnectionPoolListener() {
                            @Override
                            void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
                                clientsCreated.incrementAndGet()
                            }
                        })
                    }
                    .build()
        }

        @Bean
        FirstToStart firstToStart() {
            new FirstToStart(clientsCreated)
        }
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
            clientsCreatedWhenStarted = clientsCreated.get()
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

    /**
     * MongoDB's properties and settings, as Spring Boot contributes them, without a {@code MongoClient} bean.
     */
    @Configuration
    @Import(MongoDbGormAutoConfiguration)
    static class BootSettingsConfiguration {

        @Bean
        MongoProperties mongoProperties() {
            new MongoProperties()
        }

        @Bean
        MongoClientSettings mongoClientSettings() {
            MongoClientSettings.builder()
                    .applyToClusterSettings { it.serverSelectionTimeout(50, TimeUnit.MILLISECONDS) }
                    .build()
        }
    }
}

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
package grails.mongodb.bootstrap

import java.util.concurrent.CopyOnWriteArrayList

import com.mongodb.MongoClientSettings
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.ConnectionPoolCreatedEvent
import com.mongodb.event.ConnectionPoolListener
import grails.mongodb.MongoEntity
import grails.persistence.Entity

import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.AnnotationConfigApplicationContext

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoClientSettingsBuilderCustomizer

/**
 * With {@code spring.context.checkpoint=onRefresh}, Spring checkpoints the process as the context refreshes: after
 * every bean has been created and before it starts a single lifecycle bean. CRaC refuses a process holding an open
 * socket, so what GORM may do before its datastore is started decides whether that checkpoint can be taken at all.
 *
 * <p>The driver is watched rather than GORM: it reports a client the moment one is created, which is when it starts
 * the monitors that connect, and every command it sends.
 */
class ConnectsWhenStartedSpec extends AutoStartedMongoSpec {

    void 'GORM opens no connection to MongoDB until the application context starts its lifecycle'() {
        given: 'a listener on every client GORM creates'
        DriverActivity activity = new DriverActivity()
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton('driverActivity', { MongoClientSettings.Builder builder ->
            builder.applyToConnectionPoolSettings { it.addConnectionPoolListener(activity) }
                    .addCommandListener(activity)
        } as MongoClientSettingsBuilderCustomizer)

        and: 'the first lifecycle bean Spring starts, where a checkpoint taken as the context refreshes is taken'
        FirstToStart first = new FirstToStart(activity)
        context.beanFactory.registerSingleton('firstToStart', first)

        and: 'GORM for MongoDB, registered as a Grails application registers it'
        new MongoDbDataStoreSpringInitializer([
                (MongoSettings.SETTING_DATABASE_NAME): 'connectsWhenStartedDb',
                (MongoSettings.SETTING_HOST)         : mongoHost,
                (MongoSettings.SETTING_PORT)         : mongoPort,
        ], StartupIndexedThing) {
            @Override
            protected Map<String, Class<?>> loadDataServices(String secondaryDatastore = null) {
                [:]
            }
        }.configureForBeanDefinitionRegistry(context)

        when:
        context.refresh()

        then: 'by the time the first lifecycle bean started, no client had been created and no command sent'
        first.activityWhenStarted == []

        and: 'the datastore connected when Spring started it, and built the index the domain class declares'
        context.getBean(MongoDatastore).running
        activity.events.contains('client created')
        activity.events.contains('command createIndexes')
        [name: 1] in StartupIndexedThing.collection.listIndexes()*.key

        cleanup:
        context?.close()
    }
}

/**
 * What the driver reports on the thread doing the work, so nothing it reports can still be on its way when a test
 * looks: the connection pool it creates as it builds a client, and each command it sends. Cluster events are not
 * used, since the driver delivers them on a thread of its own.
 */
class DriverActivity implements ConnectionPoolListener, CommandListener {

    final List<String> events = new CopyOnWriteArrayList<>()

    @Override
    void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        events << 'client created'
    }

    @Override
    void commandStarted(CommandStartedEvent event) {
        events << "command ${event.commandName}".toString()
    }
}

class FirstToStart implements SmartLifecycle {

    private final DriverActivity activity

    List<String> activityWhenStarted

    private boolean running

    FirstToStart(DriverActivity activity) {
        this.activity = activity
    }

    @Override
    void start() {
        activityWhenStarted = new ArrayList<>(activity.events)
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

@Entity
class StartupIndexedThing implements MongoEntity<StartupIndexedThing> {
    Long id
    Long version
    String name

    static mapping = {
        name index: true
    }
}

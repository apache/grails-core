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
package org.grails.datastore.mapping.mongo

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.Level
import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.event.ConnectionPoolClosedEvent
import com.mongodb.event.ConnectionPoolCreatedEvent
import com.mongodb.event.ConnectionPoolListener
import grails.gorm.annotation.Entity

import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.mongo.CapturedLog
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import org.grails.datastore.mapping.mongo.connections.RestartableMongoClient

import spock.lang.Specification

/**
 * Covers the {@code SmartLifecycle} contract the datastore takes part in.
 *
 * <p>CRaC refuses to checkpoint a process holding open sockets, and a connected driver holds
 * one per pooled connection plus its server monitors. Spring stops lifecycle beans before the
 * checkpoint and starts them again after the restore, so closing the driver client on stop is
 * what lets an application using MongoDB be snapshotted at all -- and building a new one on
 * start, behind the same {@code MongoClient}, is what leaves the restored process able to query
 * anything through whatever was holding it.
 *
 * <p>No server is needed to tell an open client from a closed one: see {@link #closed}.
 */
class MongoDatastoreLifecycleSpec extends Specification {

    void 'a datastore is not running until it is started, and holds no connection until then'() {
        given: 'a datastore built as an application context builds it, with a connection of its own besides the default'
        MongoDatastore datastore = ownedClientDatastore(withConnections(), false)
        Map<String, MongoClient> clients = clientsByConnection(datastore)

        expect: 'nothing has connected, which is what a checkpoint taken as the context refreshes needs'
        !datastore.running
        clients.values().every { it instanceof RestartableMongoClient && !((RestartableMongoClient) it).connected }

        and: 'starting after everything that may need an embedded MongoDB, and stopping before it'
        datastore.phase == MongoDatastore.LIFECYCLE_PHASE
        datastore.phase < 0

        when: 'Spring starts it'
        datastore.start()

        then: 'every client is connected'
        datastore.running
        clients.values().every { ((RestartableMongoClient) it).connected }

        cleanup:
        datastore.close()
    }

    void 'a datastore nothing has started is started by the first session opened on it'() {
        given: 'one built outside an application context, which nothing will start'
        MongoDatastore datastore = ownedClientDatastore([:], false)

        when:
        def session = datastore.connect()

        then:
        datastore.running
        ((RestartableMongoClient) datastore.mongoClient).connected

        cleanup:
        session?.disconnect()
        datastore.close()
    }

    void 'a client used before the datastore was started is closed when it is stopped'() {
        given: 'a datastore nothing has started, whose client has been used, which builds a driver client'
        DriverClients drivers = new DriverClients()
        MongoDatastore datastore = ownedClientDatastore([:], false, drivers)
        closed(datastore.mongoClient)

        expect:
        drivers.opened == 1
        drivers.closed == 0
        !datastore.running

        when:
        datastore.stop()

        then: 'the driver client is closed, which releases its sockets'
        drivers.closed == 1

        and: 'the client refuses to be used'
        closed(datastore.mongoClient)

        when: 'a session is opened on the datastore'
        datastore.connect().disconnect()

        then: 'which does not start it'
        !datastore.running
        drivers.opened == 1

        when: 'only start() does'
        datastore.start()

        then:
        datastore.running
        !closed(datastore.mongoClient)
        drivers.opened == 2

        cleanup:
        datastore.close()
    }

    void 'a start that connects and then fails leaves nothing open once the datastore is stopped'() {
        given: 'a datastore whose index build cannot reach a server'
        DriverClients drivers = new DriverClients()
        def factory = new MongoConnectionSourceFactory(clientOptionsBuilder: MongoClientSettings.builder()
                .applyToConnectionPoolSettings { it.addConnectionPoolListener(drivers) })
        MongoDatastore datastore = new MongoDatastore(
                DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): unreachableUrl('test')]),
                factory, new DefaultApplicationEventPublisher(), LifecycleIndexedThing)

        when: 'the first start connects its client and then fails on the build'
        datastore.start()

        then:
        thrown(MongoTimeoutException)
        !datastore.running
        drivers.opened == 1

        when:
        datastore.stop()

        then:
        drivers.closed == 1
        closed(datastore.mongoClient)

        cleanup:
        datastore.close()
    }

    void 'a datastore that was stopped is not started again by being used'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()
        datastore.stop()

        when: 'a session is opened while it is stopped, as a request arriving during a checkpoint might'
        def session = datastore.connect()

        then: 'it stays stopped, so nothing reconnects before the checkpoint is taken'
        !datastore.running
        closed(datastore.mongoClient)

        when: 'whatever stopped it starts it'
        datastore.start()

        then:
        datastore.running
        !closed(datastore.mongoClient)

        cleanup:
        session?.disconnect()
        datastore.close()
    }

    void 'stopping closes the client GORM owns, which is what releases its sockets'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()
        MongoClient client = datastore.mongoClient

        expect: 'the client is usable to begin with'
        !closed(client)

        when: 'the checkpoint stops it'
        datastore.stop()

        then: 'draining the pool would leave the monitors connected, so the client itself is closed'
        !datastore.running
        closed(client)

        cleanup:
        datastore.close()
    }

    void 'starting after a stop brings the same client back, connected again'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()
        MongoClient original = datastore.mongoClient
        datastore.stop()

        when: 'the restore starts it again'
        datastore.start()

        then:
        datastore.running

        and: 'a closed driver client cannot be reopened, so a new one is built behind the client handed out'
        datastore.mongoClient.is(original)
        !closed(original)

        cleanup:
        datastore.close()
    }

    void 'a client obtained before the checkpoint works again after the restore'() {
        given: 'a client held by the application, as the mongo bean is when it is injected'
        MongoDatastore datastore = ownedClientDatastore(withConnections())
        Map<String, MongoClient> held = clientsByConnection(datastore)

        when: 'it is checkpointed and restored'
        datastore.stop()
        datastore.start()

        then: 'what was held is still what the datastore hands out, and it is open'
        held.every { String name, MongoClient client ->
            datastore.getDatastoreForConnection(name).mongoClient.is(client) && !closed(client)
        }

        cleanup:
        datastore.close()
    }

    void 'closing after a restore closes the client that was restarted'() {
        given: 'a datastore that has been through a checkpoint and a restore'
        MongoDatastore datastore = ownedClientDatastore()
        datastore.stop()
        datastore.start()
        MongoClient restored = datastore.mongoClient

        when: 'the application shuts down for real'
        datastore.close()

        then: 'the connection sources only know the client they were built with, so the one ' +
                'actually in use has to be closed as well rather than left holding sockets'
        closed(restored)
    }

    void 'stopping an already stopped datastore leaves it alone'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()

        when:
        datastore.stop()
        datastore.stop()

        then:
        !datastore.running

        when: 'and a running datastore is started again, which would otherwise leak a client'
        datastore.start()
        MongoClient restored = datastore.mongoClient
        datastore.start()

        then:
        datastore.running
        datastore.mongoClient.is(restored)

        cleanup:
        datastore.close()
    }

    void 'a client the application supplied is neither closed nor replaced'() {
        given: 'a datastore built around an externally managed MongoClient'
        MongoClient supplied = Mock(MongoClient)
        MongoDatastore datastore = new MongoDatastore(supplied)
        datastore.start()

        when: 'the checkpoint stops it'
        datastore.stop()

        then: 'whoever created the client owns closing it, checkpoint or not'
        0 * supplied.close()

        and: 'so it stays running, and a later start does not replace something it does not own'
        datastore.running

        when:
        datastore.start()

        then:
        datastore.mongoClient.is(supplied)

        cleanup:
        datastore.close()
    }

    void 'stopping closes the client of every connection, not only the default one'() {
        given: 'a connection that is configured and one added at runtime, each with a client of its own'
        MongoDatastore datastore = ownedClientDatastore(withConnections())
        datastore.connectionSources.addConnectionSource('late', [url: unreachableUrl('late')])
        Map<String, MongoClient> clients = clientsByConnection(datastore)

        expect:
        clients.keySet() == ['default', 'reporting', 'late'] as Set
        clients.values().every { !closed(it) }

        when: 'the checkpoint stops it'
        datastore.stop()

        then: 'a socket left open on any of them would still fail the checkpoint'
        !datastore.running
        clients.values().every { closed(it) }

        cleanup:
        datastore.close()
    }

    void 'starting restarts every client stop stopped, and each connection source still hands out its own'() {
        given:
        MongoDatastore datastore = ownedClientDatastore(withConnections())
        datastore.connectionSources.addConnectionSource('late', [url: unreachableUrl('late')])
        Map<String, MongoClient> originals = clientsByConnection(datastore)
        datastore.stop()

        when: 'the restore starts it again'
        datastore.start()
        Map<String, MongoClient> restored = clientsByConnection(datastore)

        then: 'every connection has its client back, open'
        restored.every { String name, MongoClient client -> client.is(originals[name]) && !closed(client) }

        and: 'which is the one its connection source hands out'
        restored.every { String name, MongoClient client ->
            datastore.connectionSources.getConnectionSource(name).source.is(client)
        }

        cleanup:
        datastore.close()
    }

    void 'closing after a restore closes the client of every connection'() {
        given: 'a datastore with named connections that has been through a checkpoint and a restore'
        MongoDatastore datastore = ownedClientDatastore(withConnections())
        datastore.stop()
        datastore.start()
        Map<String, MongoClient> restored = clientsByConnection(datastore)

        expect:
        restored.values().every { !closed(it) }

        when:
        datastore.close()

        then:
        restored.values().every { closed(it) }
    }

    void 'a client a custom factory builds itself is closed and replaced, and closing after a restore closes the replacement'() {
        given: 'a factory that builds plain driver clients, kept by connection sources that cannot be given a replacement'
        def factory = new MongoConnectionSourceFactory() {
            @Override
            ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
                MongoClient client = MongoClients.create(MongoClientSettings.builder()
                        .applyConnectionString(settings.url)
                        .build())
                new DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings>(name, client, settings)
            }
        }
        MongoDatastore datastore = new MongoDatastore(
                DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): unreachableUrl('test')] + withConnections()),
                factory, new DefaultApplicationEventPublisher())
        datastore.start()
        Map<String, MongoClient> originals = clientsByConnection(datastore)

        and:
        def log = new CapturedLog('org.grails.datastore.mapping', Level.WARN)

        when: 'it is checkpointed and restored'
        datastore.stop()
        datastore.start()
        Map<String, MongoClient> restored = clientsByConnection(datastore)

        then: 'the datastore still hands out the replacements'
        restored.every { String name, MongoClient client -> !client.is(originals[name]) && !closed(client) }

        and: 'and says which connection sources are left handing out the closed ones'
        restored.keySet().every { String name ->
            log.events.any {
                it.level == Level.WARN &&
                        it.formattedMessage.contains("The connection source for [${name}] is a DefaultConnectionSource")
            }
        }

        when: 'the connection sources close only the clients they were built with, which stop already closed'
        datastore.close()

        then: 'the replacements are closed as well, rather than left holding sockets'
        restored.values().every { closed(it) }

        cleanup:
        log?.close()
    }

    void 'the default client is rebuilt after a restore with the client options the datastore was given'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()
        datastore.stop()

        when:
        datastore.start()

        then: 'the options passed to the constructor, not only those in the configuration'
        datastore.mongoClient.clusterDescription.clusterSettings.getServerSelectionTimeout(TimeUnit.MILLISECONDS) == 50

        cleanup:
        datastore.close()
    }

    void 'with a supplied default client, the clients GORM created for the named connections are still stopped and restarted'() {
        given:
        MongoClient supplied = Mock(MongoClient)
        MongoDatastore datastore = new MongoDatastore(supplied,
                DatastoreUtils.createPropertyResolver(withConnections()),
                new MongoMappingContext('test'),
                new DefaultApplicationEventPublisher())
        datastore.start()
        MongoClient reporting = datastore.getDatastoreForConnection('reporting').mongoClient

        when: 'the checkpoint stops it'
        datastore.stop()

        then: 'the supplied client is left to whoever created it, and the one GORM created is stopped'
        0 * supplied.close()
        closed(reporting)
        !datastore.running

        when: 'the restore starts it again'
        datastore.start()

        then: 'only what stop stopped is started again'
        datastore.running
        datastore.mongoClient.is(supplied)
        datastore.getDatastoreForConnection('reporting').mongoClient.is(reporting)
        !closed(reporting)

        cleanup:
        datastore.close()
    }

    private static Map<String, MongoClient> clientsByConnection(MongoDatastore datastore) {
        datastore.connectionSources.allConnectionSources.collectEntries { source ->
            [(source.name): datastore.getDatastoreForConnection(source.name).mongoClient]
        }
    }

    private static Map<String, Object> withConnections() {
        [(MongoSettings.SETTING_CONNECTIONS): [reporting: [url: unreachableUrl('reporting')]]] as Map<String, Object>
    }

    /**
     * Nothing listens on port 1, and the short server selection timeout lets {@link #closed} tell an open client
     * from a closed one quickly.
     */
    private static String unreachableUrl(String database) {
        "mongodb://localhost:1/${database}?serverSelectionTimeoutMS=50"
    }

    /**
     * Whether the driver has been closed, which needs no MongoDB to answer: selecting a server
     * from a closed cluster is rejected outright, while an open client with nothing to connect
     * to waits for the server selection timeout and gives up.
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
     * A datastore whose clients GORM owns, started as the application context starts it unless asked not to be.
     */
    private static MongoDatastore ownedClientDatastore(Map<String, Object> configuration = [:], boolean started = true,
                                                       DriverClients drivers = null) {
        MongoClientSettings.Builder clientOptions = MongoClientSettings.builder()
                .applyToClusterSettings { it.serverSelectionTimeout(50, TimeUnit.MILLISECONDS) }
                .applyToConnectionPoolSettings {
                    if (drivers != null) {
                        it.addConnectionPoolListener(drivers)
                    }
                }
        MongoDatastore datastore = new MongoDatastore(clientOptions,
                DatastoreUtils.createPropertyResolver(configuration),
                new MongoMappingContext('test'))
        if (started) {
            datastore.start()
        }
        datastore
    }
}

/**
 * Counts the driver clients the datastore's client builds, and those it closes, as the driver reports them.
 *
 * <p>Each is counted by its connection pool, which the driver creates as it builds the client and closes as it closes
 * it, telling its listeners on that same thread; each of these clients has one server, so one pool. Cluster events
 * would not do: the driver delivers them on a thread of its own, so one can still be on its way when a test looks.
 */
class DriverClients implements ConnectionPoolListener {

    private final AtomicInteger openedCount = new AtomicInteger()

    private final AtomicInteger closedCount = new AtomicInteger()

    @Override
    void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        openedCount.incrementAndGet()
    }

    @Override
    void connectionPoolClosed(ConnectionPoolClosedEvent event) {
        closedCount.incrementAndGet()
    }

    int getOpened() {
        openedCount.get()
    }

    int getClosed() {
        closedCount.get()
    }
}

@Entity
class LifecycleIndexedThing {
    String name

    static mapping = {
        name index: true
    }
}

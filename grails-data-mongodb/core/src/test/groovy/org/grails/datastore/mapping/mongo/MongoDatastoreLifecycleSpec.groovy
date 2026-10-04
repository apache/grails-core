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

import ch.qos.logback.classic.Level
import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
import com.mongodb.client.MongoClient

import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.mongo.CapturedLog
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings

import spock.lang.Specification

/**
 * Covers the {@code SmartLifecycle} contract the datastore takes part in.
 *
 * <p>CRaC refuses to checkpoint a process holding open sockets, and a connected driver holds
 * one per pooled connection plus its server monitors. Spring stops lifecycle beans before the
 * checkpoint and starts them again after the restore, so closing the client on stop is what
 * lets an application using MongoDB be snapshotted at all -- and building a replacement on
 * start is what leaves the restored process able to query anything.
 *
 * <p>No server is needed to tell an open client from a closed one: see {@link #closed}.
 */
class MongoDatastoreLifecycleSpec extends Specification {

    void 'a datastore is running from the moment it is built'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()

        expect:
        datastore.running

        and: 'stopping after the web server and before the embedded MongoDB it may be talking to'
        datastore.phase == MongoDatastore.LIFECYCLE_PHASE
        datastore.phase < 0

        cleanup:
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

    void 'starting after a stop builds a replacement from the same configuration'() {
        given:
        MongoDatastore datastore = ownedClientDatastore()
        MongoClient original = datastore.mongoClient
        datastore.stop()

        when: 'the restore starts it again'
        datastore.start()

        then:
        datastore.running

        and: 'a closed client cannot be reopened, so the restored process gets a new one'
        !datastore.mongoClient.is(original)
        !closed(datastore.mongoClient)

        cleanup:
        datastore.close()
    }

    void 'closing after a restore closes the replacement rather than only the client it replaced'() {
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

    void 'starting replaces every client stop closed, and each connection source hands out its replacement'() {
        given:
        MongoDatastore datastore = ownedClientDatastore(withConnections())
        datastore.connectionSources.addConnectionSource('late', [url: unreachableUrl('late')])
        Map<String, MongoClient> originals = clientsByConnection(datastore)
        datastore.stop()

        when: 'the restore starts it again'
        datastore.start()
        Map<String, MongoClient> restored = clientsByConnection(datastore)

        then: 'every connection has a new, open client'
        restored.every { String name, MongoClient client -> !client.is(originals[name]) && !closed(client) }

        and: 'which is the one its connection source hands out, so nothing reading it from there gets the closed one'
        restored.every { String name, MongoClient client ->
            datastore.connectionSources.getConnectionSource(name).source.is(client)
        }

        cleanup:
        datastore.close()
    }

    void 'closing after a restore closes the replacement of every connection'() {
        given: 'a datastore with named connections that has been through a checkpoint and a restore'
        MongoDatastore datastore = ownedClientDatastore(withConnections())
        Map<String, MongoClient> originals = clientsByConnection(datastore)
        datastore.stop()
        datastore.start()
        Map<String, MongoClient> restored = clientsByConnection(datastore)

        expect: 'every connection is on a replacement'
        restored.every { String name, MongoClient client -> !client.is(originals[name]) }

        when:
        datastore.close()

        then:
        restored.values().every { closed(it) }
    }

    void 'closing after a restore closes the replacements even when the connection sources cannot hand them out'() {
        given: 'a factory whose connection sources keep the client they were built with'
        def factory = new MongoConnectionSourceFactory() {
            @Override
            ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
                new DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings>(name, super.create(name, settings).source, settings)
            }
        }
        MongoDatastore datastore = new MongoDatastore(
                DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): unreachableUrl('test')] + withConnections()),
                factory, new DefaultApplicationEventPublisher())
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

    void 'the replacement of the default client is built with the client options the datastore was given'() {
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

    void 'with a supplied default client, the clients GORM created for the named connections are still closed and replaced'() {
        given:
        MongoClient supplied = Mock(MongoClient)
        MongoDatastore datastore = new MongoDatastore(supplied,
                DatastoreUtils.createPropertyResolver(withConnections()),
                new MongoMappingContext('test'),
                new DefaultApplicationEventPublisher())
        MongoClient reporting = datastore.getDatastoreForConnection('reporting').mongoClient

        when: 'the checkpoint stops it'
        datastore.stop()

        then: 'the supplied client is left to whoever created it, and the one GORM created is closed'
        0 * supplied.close()
        closed(reporting)
        !datastore.running

        when: 'the restore starts it again'
        datastore.start()
        MongoClient restored = datastore.getDatastoreForConnection('reporting').mongoClient

        then: 'only what stop closed is replaced'
        datastore.running
        datastore.mongoClient.is(supplied)
        !restored.is(reporting)
        !closed(restored)

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

    private static MongoDatastore ownedClientDatastore(Map<String, Object> configuration = [:]) {
        MongoClientSettings.Builder clientOptions = MongoClientSettings.builder()
                .applyToClusterSettings { it.serverSelectionTimeout(50, TimeUnit.MILLISECONDS) }
        new MongoDatastore(clientOptions,
                DatastoreUtils.createPropertyResolver(configuration),
                new MongoMappingContext('test'))
    }
}

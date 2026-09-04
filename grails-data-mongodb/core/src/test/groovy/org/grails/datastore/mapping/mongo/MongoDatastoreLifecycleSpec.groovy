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

import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
import com.mongodb.client.MongoClient

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.config.MongoMappingContext

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

    private static MongoDatastore ownedClientDatastore() {
        MongoClientSettings.Builder clientOptions = MongoClientSettings.builder()
                .applyToClusterSettings { it.serverSelectionTimeout(50, TimeUnit.MILLISECONDS) }
        new MongoDatastore(clientOptions,
                DatastoreUtils.createPropertyResolver([:]),
                new MongoMappingContext('test'))
    }
}

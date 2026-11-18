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

class InMemoryMongoBackendSpec extends Specification {

    InMemoryMongoBackend backend = new InMemoryMongoBackend()

    void 'the backend is named in-memory'() {
        expect:
        backend.name == InMemoryMongoBackend.NAME
        backend.name == 'in-memory'
    }

    void 'the backend is always available, since mongo-java-server is a direct dependency'() {
        expect:
        backend.isAvailable()
    }

    void 'starting with a persistent database directory is refused, since data cannot survive a restart'() {
        given:
        EmbeddedMongoSettings settings = new EmbeddedMongoSettings(0, null, './some-persistent-dir')

        when:
        backend.start(settings)

        then:
        IllegalStateException ex = thrown(IllegalStateException)
        ex.message.contains(EmbeddedMongoInitializer.DATABASE_DIR)
    }

    void 'starting without a database directory binds a real server on localhost'() {
        given:
        EmbeddedMongoSettings settings = new EmbeddedMongoSettings(0, null, null)

        when:
        RunningEmbeddedMongo running = backend.start(settings)

        then:
        running.host == 'localhost'
        running.port > 0

        cleanup:
        running?.stop()
    }

    void 'stopping a running server does not throw'() {
        given:
        RunningEmbeddedMongo running = backend.start(new EmbeddedMongoSettings(0, null, null))

        expect:
        running.stop() == null
    }

    void 'restarting after a stop rebinds the same port and keeps the data that was there'() {
        given: 'a document inserted before the server is stopped'
        RunningEmbeddedMongo running = backend.start(new EmbeddedMongoSettings(0, null, null))
        int originalPort = running.port
        insertOneDocument(running.host, running.port)

        when: 'the server is stopped, then restarted the way a CRaC checkpoint/restore would'
        running.stop()
        running.restart()

        then: 'the same port is bound again'
        running.port == originalPort

        and: 'the document survives, because RetainingMemoryBackend deliberately does not clear on close'
        countDocuments(running.host, running.port) == 1

        cleanup:
        running?.stop()
    }

    private static void insertOneDocument(String host, int port) {
        try (MongoClient client = MongoClients.create("mongodb://${host}:${port}")) {
            client.getDatabase('test').getCollection('things').insertOne(new Document('name', 'Bob'))
        }
    }

    private static long countDocuments(String host, int port) {
        try (MongoClient client = MongoClients.create("mongodb://${host}:${port}")) {
            return client.getDatabase('test').getCollection('things').countDocuments()
        }
    }
}

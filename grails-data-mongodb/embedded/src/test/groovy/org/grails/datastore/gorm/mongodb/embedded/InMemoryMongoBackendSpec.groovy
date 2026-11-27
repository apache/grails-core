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
import de.bwaldvogel.mongo.MongoServer

import org.bson.Document

import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

    void 'a server asked for any free port serves the request it sends itself on the port it bound'() {
        given: 'the backend loaded by a class loader of its own, which notes the classes the server loads'
        RecordingClassLoader loader = new RecordingClassLoader()
        def inMemory = loader.loadClass(InMemoryMongoBackend.name).getConstructor().newInstance()
        def settings = loader.loadClass(EmbeddedMongoSettings.name)
                .getConstructor(int, String, String).newInstance(0, null, null)

        when:
        def running = inMemory.start(settings)

        then: 'the server bound a port of its own'
        running.port > 0

        and: 'it has encoded a reply, which nothing but a request makes it do'
        loader.loaded.contains('de.bwaldvogel.mongo.wire.bson.BsonEncoder')

        cleanup:
        running?.stop()
        loader?.close()
    }

    void 'a reply #description to the request the server is sent as it starts does not keep it from being handed back'() {
        given: 'a peer that answers that request the way no MongoDB does'
        ServerSocket peer = new ServerSocket(0, 1, InetAddress.loopbackAddress)
        peer.soTimeout = 10000
        CompletableFuture<byte[]> request = CompletableFuture.supplyAsync { answer(peer, reply as byte[]) }
        InMemoryMongoBackend sendingToThePeer = new InMemoryMongoBackend() {
            @Override
            InetSocketAddress addressOf(MongoServer server) {
                peer.localSocketAddress as InetSocketAddress
            }
        }

        when:
        RunningEmbeddedMongo running = sendingToThePeer.start(new EmbeddedMongoSettings(0, null, null))

        then: 'the request reached the peer'
        new String(request.get(10, TimeUnit.SECONDS), StandardCharsets.ISO_8859_1).contains('isMaster')

        and: 'the server that was started is handed back, and answers a client'
        insertOneDocument(running.host, running.port)
        countDocuments(running.host, running.port) == 1

        cleanup:
        running?.stop()
        peer?.close()

        where:
        description                                      | reply
        'that ends before it gives a length'             | []
        'that ends part way through its length'          | [16, 0]
        'giving a length shorter than the length itself' | littleEndian(2)
        'giving a negative length'                       | littleEndian(-1)
        'that ends before the length it gave'            | littleEndian(64) + [1, 2, 3]
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

    /**
     * Reads the whole request, so that closing does not reset the connection, then answers with the
     * reply and waits for the client to close its end.
     */
    private static byte[] answer(ServerSocket peer, byte[] reply) {
        peer.accept().withCloseable { Socket client ->
            DataInputStream input = new DataInputStream(client.inputStream)
            byte[] length = input.readNBytes(Integer.BYTES)
            byte[] rest = input.readNBytes(ByteBuffer.wrap(length).order(ByteOrder.LITTLE_ENDIAN).int - Integer.BYTES)
            client.outputStream.write(reply)
            client.outputStream.flush()
            client.shutdownOutput()
            input.read()
            (length.toList() + rest.toList()) as byte[]
        }
    }

    private static List<Byte> littleEndian(int value) {
        ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array().toList()
    }

    /**
     * Loads the server's classes itself rather than leaving them to the class loader the spec runs in,
     * whose classes other features have already loaded, and notes each one it does.
     */
    private static final class RecordingClassLoader extends URLClassLoader {

        final Set<String> loaded = ConcurrentHashMap.newKeySet()

        RecordingClassLoader() {
            super(classPath(), ClassLoader.platformClassLoader)
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> found = super.findClass(name)
            loaded << name
            found
        }

        private static URL[] classPath() {
            System.getProperty('java.class.path').split(File.pathSeparator)
                    .collect { String entry -> new File(entry).toURI().toURL() } as URL[]
        }
    }
}

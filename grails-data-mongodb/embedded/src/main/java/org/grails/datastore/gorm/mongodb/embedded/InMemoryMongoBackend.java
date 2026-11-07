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
package org.grails.datastore.gorm.mongodb.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.netty.buffer.ByteBufAllocator;

import org.springframework.util.ClassUtils;

/**
 * Runs MongoDB inside this JVM using mongo-java-server, which reimplements the MongoDB
 * wire protocol in Java. It starts in milliseconds, downloads nothing and needs no
 * MongoDB installation, which is why it is the default.
 *
 * <p>Being a reimplementation rather than mongod, it does not support transactions,
 * change streams, {@code $text} or some {@code $expr} operators, and it cannot persist
 * anything. An application that needs those should add flapdoodle and let
 * {@link FlapdoodleMongoBackend} run a real mongod instead.
 *
 * @since 8.0
 */
public class InMemoryMongoBackend implements EmbeddedMongoBackend {

    public static final String NAME = "in-memory";

    private static final String SERVER_CLASS = "de.bwaldvogel.mongo.MongoServer";

    private static final int SERVE_ONE_REQUEST_TIMEOUT_MILLIS = 5000;

    /** Netty tracks about one buffer in 128, so this many allocations reach that all but certainly. */
    private static final int ALLOCATIONS_TO_TRACK_ONE_BUFFER = 2048;

    private static final int OP_MSG = 2013;

    private static final byte BSON_STRING = 0x02;

    private static final byte BSON_INT32 = 0x10;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent(SERVER_CLASS, getClass().getClassLoader());
    }

    @Override
    public RunningEmbeddedMongo start(EmbeddedMongoSettings settings) {
        if (settings.isPersistent()) {
            throw new IllegalStateException("The " + NAME + " backend keeps everything in memory and cannot honour " +
                    EmbeddedMongoInitializer.DATABASE_DIR + ". Add " +
                    "de.flapdoodle.embed:de.flapdoodle.embed.mongo to run a real mongod that can, or remove the " +
                    "directory to accept a database that is discarded when the server stops.");
        }

        if (settings.isReplicaSet()) {
            throw new IllegalStateException("The " + NAME + " backend reimplements the wire protocol and cannot be a " +
                    "replica set, which is what a transaction, a change stream and a causally consistent read need. " +
                    "Add de.flapdoodle.embed:de.flapdoodle.embed.mongo to run a real mongod that can, or unset " +
                    EmbeddedMongoInitializer.REPLICA_SET + " and " + EmbeddedMongoInitializer.TRANSACTIONAL + ".");
        }

        MemoryBackend backend = new RetainingMemoryBackend();
        MongoServer server = new MongoServer(backend);
        server.bind("localhost", settings.getPort());
        serveOneRequest(settings.getPort());
        allocateUntilOneBufferIsTracked();
        return new RunningInMemoryMongo(backend, server);
    }

    /**
     * Allocates buffers until Netty's leak detection has tracked one, so that what only a tracked
     * buffer needs is loaded as well. Netty picks the buffers it tracks at random, so the request
     * {@link #serveOneRequest(int)} sends rarely gets that far, and a buffer picked while the jars are
     * closed fails as the first one would.
     */
    private static void allocateUntilOneBufferIsTracked() {
        for (int i = 0; i < ALLOCATIONS_TO_TRACK_ONE_BUFFER; i++) {
            ByteBufAllocator.DEFAULT.ioBuffer(1).release();
        }
    }

    /**
     * Has the server answer one request, so that what its event loops need is loaded while the
     * application is starting rather than when its first client connects.
     *
     * <p>That client is the MongoDB driver, which connects as the application context finishes
     * refreshing, the moment Spring Boot closes the jars of an executable jar to clear its class
     * loader's caches. A class loaded right then fails with {@code IllegalStateException}. Reading
     * the driver's first bytes allocates the event loop's first buffer, and an event loop that fails
     * to load the class of it ends its thread. The driver's connections on it then never answer, and
     * stopping the server waits forever for them to close.
     *
     * <p>Whatever goes wrong here goes wrong again for the first real client, where it says more, so
     * it is not reported.
     */
    private static void serveOneRequest(int port) {
        try (Socket client = new Socket("localhost", port)) {
            client.setSoTimeout(SERVE_ONE_REQUEST_TIMEOUT_MILLIS);
            client.getOutputStream().write(isMaster());
            // the whole reply, since closing with some of it unread resets the connection, which the server logs as an error
            InputStream reply = client.getInputStream();
            int length = ByteBuffer.wrap(reply.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            reply.readNBytes(length - 4);
        }
        catch (IOException ignored) {
            return;
        }
    }

    /** The {@code isMaster} command the driver starts with, in the wire protocol's {@code OP_MSG}. */
    private static byte[] isMaster() {
        byte[] command = "isMaster".getBytes(StandardCharsets.UTF_8);
        byte[] databaseField = "$db".getBytes(StandardCharsets.UTF_8);
        byte[] database = "admin".getBytes(StandardCharsets.UTF_8);
        int documentLength = 4 + 1 + command.length + 1 + 4 + 1 + databaseField.length + 1 + 4 + database.length + 1 + 1;
        int messageLength = 16 + 4 + 1 + documentLength;
        ByteBuffer message = ByteBuffer.allocate(messageLength).order(ByteOrder.LITTLE_ENDIAN);
        message.putInt(messageLength).putInt(1).putInt(0).putInt(OP_MSG);
        message.putInt(0).put((byte) 0);
        message.putInt(documentLength);
        message.put(BSON_INT32).put(command).put((byte) 0).putInt(1);
        message.put(BSON_STRING).put(databaseField).put((byte) 0).putInt(database.length + 1).put(database).put((byte) 0);
        message.put((byte) 0);
        return message.array();
    }

    /**
     * A backend that keeps its collections when the server it is attached to shuts down.
     *
     * <p>{@code MongoServer.shutdownNow()} closes the backend, and
     * {@code AbstractMongoBackend.close()} clears every database. That is right for a server
     * that is finished with, but {@link RunningInMemoryMongo#restart()} shuts the server down
     * only to release its sockets for a CRaC checkpoint and then binds a new one onto the
     * same data. Without this, a restored process comes back with an empty database.
     *
     * <p>Nothing is leaked by not clearing: the data is unreachable once the last server
     * using it is gone, and the JVM is exiting in the ordinary shutdown case.
     */
    private static final class RetainingMemoryBackend extends MemoryBackend {

        @Override
        public void close() {
            // Deliberately empty; see the class comment.
        }
    }

    private static final class RunningInMemoryMongo implements RunningEmbeddedMongo {

        /**
         * Held so {@link #restart()} can bind a new server onto the data that is already
         * there. The data lives on the heap, so a CRaC checkpoint image preserves it and a
         * restored process comes back with the collections it had.
         */
        private final MemoryBackend backend;

        private volatile MongoServer server;

        private volatile boolean running = true;

        /**
         * The port that was actually bound, which is not the requested one when that was 0.
         * Restarting reuses it so the url published into the environment stays correct.
         */
        private final int port;

        private RunningInMemoryMongo(MemoryBackend backend, MongoServer server) {
            this.backend = backend;
            this.server = server;
            this.port = server.getLocalAddress().getPort();
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public int getPort() {
            return this.port;
        }

        /**
         * Stopping twice is what an ordinary shutdown does: the lifecycle bean stops the server when
         * the application context closes, and the JVM shutdown hook - which is there for a context
         * that never closes - runs after it. A server that is already stopped is left alone.
         *
         * <p>Shutting a {@code MongoServer} down a second time is not harmless. It closes the clients
         * it still tracks and waits, without a timeout, for them to close, and a client that connected
         * as the first shutdown began can be left tracked on an event loop that has since terminated,
         * where closing it never completes. From the shutdown hook, that wait kept the JVM from
         * exiting at all.
         *
         * <p>Synchronized because the context's shutdown hook and the initializer's run at the same
         * time on the way out.
         */
        @Override
        public synchronized void stop() {
            if (!this.running) {
                return;
            }
            this.server.shutdownNow();
            this.running = false;
        }

        @Override
        public boolean isRunning() {
            return this.running;
        }

        @Override
        public synchronized void restart() {
            MongoServer restarted = new MongoServer(this.backend);
            restarted.bind("localhost", this.port);
            this.server = restarted;
            this.running = true;
        }
    }
}

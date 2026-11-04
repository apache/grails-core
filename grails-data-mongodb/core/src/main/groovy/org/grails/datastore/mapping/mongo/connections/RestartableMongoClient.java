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
package org.grails.datastore.mapping.mongo.connections;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.mongodb.ClientBulkWriteException;
import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoDriverInformation;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCluster;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.bulk.ClientBulkWriteOptions;
import com.mongodb.client.model.bulk.ClientBulkWriteResult;
import com.mongodb.client.model.bulk.ClientNamespacedWriteModel;
import com.mongodb.connection.ClusterDescription;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 * The {@link MongoClient} GORM creates for a connection. It is a stable handle on a driver client
 * that it builds only when it is needed, and that it can close and build again without the handle
 * itself changing.
 *
 * <p>The driver connects as soon as a client exists: creating one starts the monitors that open
 * sockets to every server it was given. So this builds its driver client the first time it is
 * used, or when {@link #start()} asks for it, and not before. A datastore built while an
 * application context refreshes therefore holds no socket until its lifecycle starts it, which is
 * what lets the process be checkpointed with CRaC as the context refreshes
 * ({@code spring.context.checkpoint=onRefresh}).
 *
 * <p>{@link #stop()} closes the driver client, which releases every socket it held, and the handle
 * refuses to be used until {@link #start()} builds a new one. Whatever holds the handle - the
 * {@code mongo} bean, a service it was injected into, a Spring Data {@code MongoDatabaseFactory} -
 * goes on working after a checkpoint and restore without being told anything. A
 * {@link MongoDatabase}, {@link ClientSession} or other object obtained from the handle belongs to
 * the driver client it came from, so it is closed with it.
 *
 * <p>{@link #close()} is final: the driver client is closed and the handle cannot be started again.
 *
 * @since 8.0
 */
public final class RestartableMongoClient implements MongoClient {

    private enum State { NEW, STARTED, STOPPED, CLOSED }

    private final String name;

    private final Supplier<MongoClient> clientFactory;

    private final Object monitor = new Object();

    /**
     * Read without the monitor on every call, so a started handle costs one volatile read.
     */
    private volatile MongoClient client;

    private volatile State state = State.NEW;

    /**
     * @param name the name of the connection the client is for, which is what its messages name
     * @param clientFactory builds a driver client each time one is needed
     */
    public RestartableMongoClient(String name, Supplier<MongoClient> clientFactory) {
        if (name == null) {
            throw new IllegalArgumentException("Argument [name] cannot be null");
        }
        if (clientFactory == null) {
            throw new IllegalArgumentException("Argument [clientFactory] cannot be null");
        }
        this.name = name;
        this.clientFactory = clientFactory;
    }

    /**
     * @return the name of the connection the client is for
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return whether a driver client exists, and so whether the handle may hold sockets
     */
    public boolean isConnected() {
        return this.client != null;
    }

    /**
     * Builds the driver client now, unless there already is one. A handle that {@link #stop()} stopped
     * is usable again from here on.
     *
     * @throws IllegalStateException if the handle has been closed
     */
    public void start() {
        synchronized (this.monitor) {
            if (this.state == State.CLOSED) {
                throw closed();
            }
            if (this.client == null) {
                this.client = this.clientFactory.get();
            }
            this.state = State.STARTED;
        }
    }

    /**
     * Closes the driver client, which releases its sockets, and refuses every use of the handle until
     * {@link #start()} builds a new one. Stopping a handle that was never used builds nothing.
     */
    public void stop() {
        MongoClient stopping;
        synchronized (this.monitor) {
            if (this.state == State.CLOSED) {
                return;
            }
            this.state = State.STOPPED;
            stopping = this.client;
            this.client = null;
        }
        if (stopping != null) {
            stopping.close();
        }
    }

    /**
     * Closes the driver client for good. Closing again does nothing.
     */
    @Override
    public void close() {
        MongoClient closing;
        synchronized (this.monitor) {
            if (this.state == State.CLOSED) {
                return;
            }
            this.state = State.CLOSED;
            closing = this.client;
            this.client = null;
        }
        if (closing != null) {
            closing.close();
        }
    }

    /**
     * The driver client, built here on first use. A stopped or closed handle refuses rather than
     * building one: a stopped datastore must not be reconnected by whichever request happens to arrive,
     * since a socket opened then is one the checkpoint it was stopped for cannot be taken with.
     */
    private MongoClient client() {
        MongoClient current = this.client;
        if (current != null) {
            return current;
        }
        synchronized (this.monitor) {
            if (this.client != null) {
                return this.client;
            }
            if (this.state == State.CLOSED) {
                throw closed();
            }
            if (this.state == State.STOPPED) {
                throw new IllegalStateException("The MongoClient for connection [" + this.name + "] is stopped, " +
                        "as it is while the application is checkpointed. It reconnects when its datastore is started again.");
            }
            this.client = this.clientFactory.get();
            this.state = State.STARTED;
            return this.client;
        }
    }

    private IllegalStateException closed() {
        return new IllegalStateException("The MongoClient for connection [" + this.name + "] has been closed");
    }

    @Override
    public String toString() {
        return "RestartableMongoClient{connection=" + this.name + ", state=" + this.state + '}';
    }

    @Override
    public ClusterDescription getClusterDescription() {
        return client().getClusterDescription();
    }

    @Override
    public void appendMetadata(MongoDriverInformation mongoDriverInformation) {
        client().appendMetadata(mongoDriverInformation);
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return client().getCodecRegistry();
    }

    @Override
    public ReadPreference getReadPreference() {
        return client().getReadPreference();
    }

    @Override
    public WriteConcern getWriteConcern() {
        return client().getWriteConcern();
    }

    @Override
    public ReadConcern getReadConcern() {
        return client().getReadConcern();
    }

    @Override
    public Long getTimeout(TimeUnit timeUnit) {
        return client().getTimeout(timeUnit);
    }

    @Override
    public MongoCluster withCodecRegistry(CodecRegistry codecRegistry) {
        return client().withCodecRegistry(codecRegistry);
    }

    @Override
    public MongoCluster withReadPreference(ReadPreference readPreference) {
        return client().withReadPreference(readPreference);
    }

    @Override
    public MongoCluster withWriteConcern(WriteConcern writeConcern) {
        return client().withWriteConcern(writeConcern);
    }

    @Override
    public MongoCluster withReadConcern(ReadConcern readConcern) {
        return client().withReadConcern(readConcern);
    }

    @Override
    public MongoCluster withTimeout(long timeout, TimeUnit timeUnit) {
        return client().withTimeout(timeout, timeUnit);
    }

    @Override
    public MongoDatabase getDatabase(String databaseName) {
        return client().getDatabase(databaseName);
    }

    @Override
    public ClientSession startSession() {
        return client().startSession();
    }

    @Override
    public ClientSession startSession(ClientSessionOptions options) {
        return client().startSession(options);
    }

    @Override
    public MongoIterable<String> listDatabaseNames() {
        return client().listDatabaseNames();
    }

    @Override
    public MongoIterable<String> listDatabaseNames(ClientSession clientSession) {
        return client().listDatabaseNames(clientSession);
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases() {
        return client().listDatabases();
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases(ClientSession clientSession) {
        return client().listDatabases(clientSession);
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(Class<TResult> resultClass) {
        return client().listDatabases(resultClass);
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(ClientSession clientSession, Class<TResult> resultClass) {
        return client().listDatabases(clientSession, resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch() {
        return client().watch();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(Class<TResult> resultClass) {
        return client().watch(resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(List<? extends Bson> pipeline) {
        return client().watch(pipeline);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(List<? extends Bson> pipeline, Class<TResult> resultClass) {
        return client().watch(pipeline, resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession) {
        return client().watch(clientSession);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, Class<TResult> resultClass) {
        return client().watch(clientSession, resultClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession, List<? extends Bson> pipeline) {
        return client().watch(clientSession, pipeline);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, List<? extends Bson> pipeline,
            Class<TResult> resultClass) {
        return client().watch(clientSession, pipeline, resultClass);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> models)
            throws ClientBulkWriteException {
        return client().bulkWrite(models);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> models,
            ClientBulkWriteOptions options) throws ClientBulkWriteException {
        return client().bulkWrite(models, options);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models)
            throws ClientBulkWriteException {
        return client().bulkWrite(clientSession, models);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models,
            ClientBulkWriteOptions options) throws ClientBulkWriteException {
        return client().bulkWrite(clientSession, models, options);
    }
}

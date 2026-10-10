/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.mongo.connections

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import com.mongodb.ClientBulkWriteException
import com.mongodb.ClientSessionOptions
import com.mongodb.MongoDriverInformation
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.client.ChangeStreamIterable
import com.mongodb.client.ClientSession
import com.mongodb.client.ListDatabasesIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCluster
import com.mongodb.client.MongoDatabase
import com.mongodb.client.MongoIterable
import com.mongodb.client.model.bulk.ClientBulkWriteOptions
import com.mongodb.client.model.bulk.ClientBulkWriteResult
import com.mongodb.client.model.bulk.ClientNamespacedWriteModel
import com.mongodb.connection.ClusterDescription
import groovy.transform.CompileStatic
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson

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
@CompileStatic
final class RestartableMongoClient implements MongoClient {

    private enum State { NEW, STARTED, STOPPED, CLOSED }

    private final String name

    private final Supplier<MongoClient> clientFactory

    private final Object monitor = new Object()

    /**
     * Read without the monitor on every call, so a started handle costs one volatile read.
     */
    private volatile MongoClient client

    private volatile State state = State.NEW

    /**
     * @param name the name of the connection the client is for, which is what its messages name
     * @param clientFactory builds a driver client each time one is needed
     */
    RestartableMongoClient(String name, Supplier<MongoClient> clientFactory) {
        if (name == null) {
            throw new IllegalArgumentException('Argument [name] cannot be null')
        }
        if (clientFactory == null) {
            throw new IllegalArgumentException('Argument [clientFactory] cannot be null')
        }
        this.name = name
        this.clientFactory = clientFactory
    }

    /**
     * @return the name of the connection the client is for
     */
    String getName() {
        return this.name
    }

    /**
     * @return whether a driver client exists, and so whether the handle may hold sockets
     */
    boolean isConnected() {
        return this.client != null
    }

    /**
     * Builds the driver client now, unless there already is one. A handle that {@link #stop()} stopped
     * is usable again from here on.
     *
     * @throws IllegalStateException if the handle has been closed
     */
    void start() {
        synchronized (this.monitor) {
            if (this.state == State.CLOSED) {
                throw closed()
            }
            if (this.client == null) {
                this.client = this.clientFactory.get()
            }
            this.state = State.STARTED
        }
    }

    /**
     * Closes the driver client, which releases its sockets, and refuses every use of the handle until
     * {@link #start()} builds a new one. Stopping a handle that was never used builds nothing.
     */
    void stop() {
        MongoClient stopping
        synchronized (this.monitor) {
            if (this.state == State.CLOSED) {
                return
            }
            this.state = State.STOPPED
            stopping = this.client
            this.client = null
        }
        if (stopping != null) {
            stopping.close()
        }
    }

    /**
     * Closes the driver client for good. Closing again does nothing.
     */
    @Override
    void close() {
        MongoClient closing
        synchronized (this.monitor) {
            if (this.state == State.CLOSED) {
                return
            }
            this.state = State.CLOSED
            closing = this.client
            this.client = null
        }
        if (closing != null) {
            closing.close()
        }
    }

    /**
     * The driver client, built here on first use. A stopped or closed handle refuses rather than
     * building one: a stopped datastore must not be reconnected by whichever request happens to arrive,
     * since a socket opened then is one the checkpoint it was stopped for cannot be taken with.
     */
    private MongoClient driverClient() {
        MongoClient current = this.client
        if (current != null) {
            return current
        }
        synchronized (this.monitor) {
            if (this.client != null) {
                return this.client
            }
            if (this.state == State.CLOSED) {
                throw closed()
            }
            if (this.state == State.STOPPED) {
                throw new IllegalStateException('The MongoClient for connection [' + this.name + '] is stopped, ' +
                        'as it is while the application is checkpointed. It reconnects when its datastore is started again.')
            }
            this.client = this.clientFactory.get()
            this.state = State.STARTED
            return this.client
        }
    }

    private IllegalStateException closed() {
        return new IllegalStateException('The MongoClient for connection [' + this.name + '] has been closed')
    }

    @Override
    String toString() {
        return 'RestartableMongoClient{connection=' + this.name + ', state=' + this.state + '}'
    }

    @Override
    ClusterDescription getClusterDescription() {
        return driverClient().getClusterDescription()
    }

    @Override
    void appendMetadata(MongoDriverInformation mongoDriverInformation) {
        driverClient().appendMetadata(mongoDriverInformation)
    }

    @Override
    CodecRegistry getCodecRegistry() {
        return driverClient().getCodecRegistry()
    }

    @Override
    ReadPreference getReadPreference() {
        return driverClient().getReadPreference()
    }

    @Override
    WriteConcern getWriteConcern() {
        return driverClient().getWriteConcern()
    }

    @Override
    ReadConcern getReadConcern() {
        return driverClient().getReadConcern()
    }

    @Override
    Long getTimeout(TimeUnit timeUnit) {
        return driverClient().getTimeout(timeUnit)
    }

    @Override
    MongoCluster withCodecRegistry(CodecRegistry codecRegistry) {
        return driverClient().withCodecRegistry(codecRegistry)
    }

    @Override
    MongoCluster withReadPreference(ReadPreference readPreference) {
        return driverClient().withReadPreference(readPreference)
    }

    @Override
    MongoCluster withWriteConcern(WriteConcern writeConcern) {
        return driverClient().withWriteConcern(writeConcern)
    }

    @Override
    MongoCluster withReadConcern(ReadConcern readConcern) {
        return driverClient().withReadConcern(readConcern)
    }

    @Override
    MongoCluster withTimeout(long timeout, TimeUnit timeUnit) {
        return driverClient().withTimeout(timeout, timeUnit)
    }

    @Override
    MongoDatabase getDatabase(String databaseName) {
        return driverClient().getDatabase(databaseName)
    }

    @Override
    ClientSession startSession() {
        return driverClient().startSession()
    }

    @Override
    ClientSession startSession(ClientSessionOptions options) {
        return driverClient().startSession(options)
    }

    @Override
    MongoIterable<String> listDatabaseNames() {
        return driverClient().listDatabaseNames()
    }

    @Override
    MongoIterable<String> listDatabaseNames(ClientSession clientSession) {
        return driverClient().listDatabaseNames(clientSession)
    }

    @Override
    ListDatabasesIterable<Document> listDatabases() {
        return driverClient().listDatabases()
    }

    @Override
    ListDatabasesIterable<Document> listDatabases(ClientSession clientSession) {
        return driverClient().listDatabases(clientSession)
    }

    @Override
    <TResult> ListDatabasesIterable<TResult> listDatabases(Class<TResult> resultClass) {
        return driverClient().listDatabases(resultClass)
    }

    @Override
    <TResult> ListDatabasesIterable<TResult> listDatabases(ClientSession clientSession, Class<TResult> resultClass) {
        return driverClient().listDatabases(clientSession, resultClass)
    }

    @Override
    ChangeStreamIterable<Document> watch() {
        return driverClient().watch()
    }

    @Override
    <TResult> ChangeStreamIterable<TResult> watch(Class<TResult> resultClass) {
        return driverClient().watch(resultClass)
    }

    @Override
    ChangeStreamIterable<Document> watch(List<? extends Bson> pipeline) {
        return driverClient().watch(pipeline)
    }

    @Override
    <TResult> ChangeStreamIterable<TResult> watch(List<? extends Bson> pipeline, Class<TResult> resultClass) {
        return driverClient().watch(pipeline, resultClass)
    }

    @Override
    ChangeStreamIterable<Document> watch(ClientSession clientSession) {
        return driverClient().watch(clientSession)
    }

    @Override
    <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, Class<TResult> resultClass) {
        return driverClient().watch(clientSession, resultClass)
    }

    @Override
    ChangeStreamIterable<Document> watch(ClientSession clientSession, List<? extends Bson> pipeline) {
        return driverClient().watch(clientSession, pipeline)
    }

    @Override
    <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, List<? extends Bson> pipeline,
            Class<TResult> resultClass) {
        return driverClient().watch(clientSession, pipeline, resultClass)
    }

    @Override
    ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> models)
            throws ClientBulkWriteException {
        return driverClient().bulkWrite(models)
    }

    @Override
    ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> models,
            ClientBulkWriteOptions options) throws ClientBulkWriteException {
        return driverClient().bulkWrite(models, options)
    }

    @Override
    ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models)
            throws ClientBulkWriteException {
        return driverClient().bulkWrite(clientSession, models)
    }

    @Override
    ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> models,
            ClientBulkWriteOptions options) throws ClientBulkWriteException {
        return driverClient().bulkWrite(clientSession, models, options)
    }
}

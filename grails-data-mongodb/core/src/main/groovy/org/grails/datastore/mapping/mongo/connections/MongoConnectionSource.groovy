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

package org.grails.datastore.mapping.mongo.connections

import java.util.function.Consumer
import java.util.function.Function

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient

import org.apache.grails.data.mongodb.core.querytail.MongoMappedFields
import org.apache.grails.data.mongodb.core.querytail.QueryTailCommandListener
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource

/**
 * The connection source {@link MongoConnectionSourceFactory} creates, whose {@link MongoClient} can be replaced.
 *
 * <p>A closed {@code MongoClient} cannot be reopened, so a datastore that closes its clients when it is stopped for
 * a checkpoint builds new ones when it is started again after the restore. Putting each replacement here, where
 * the original was, means that anything reading the client from the connection source rather than from the
 * datastore gets the one in use, and that closing the connection source closes it when it is GORM's to close.
 *
 * <p>It also owns the query-tail listener registered on its client when its settings enable capture: the listener
 * is created and registered as the client is built ({@link #create}), goes with the client wherever the client
 * goes ({@link #adopt}, {@link #replaceWith}), and is closed with the connection source, or as soon as its client
 * is handed out to whoever builds it for themselves ({@link #detach}), since nothing would close a capture of it
 * then.</p>
 *
 * @since 8.0
 */
@CompileStatic
@Slf4j
class MongoConnectionSource extends DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings> {

    private volatile MongoClient client

    private QueryTailCommandListener queryTail

    /**
     * A connection source for a client GORM created, which {@link #close()} closes.
     *
     * @param name the name of the connection source
     * @param client the client
     * @param settings the settings
     */
    MongoConnectionSource(String name, MongoClient client, MongoConnectionSourceSettings settings) {
        this(name, client, settings, true)
    }

    /**
     * A connection source that optionally owns its client, for a factory that hands GORM one the application
     * supplied. The client is still replaceable, so a datastore that reads it from here after a restore gets
     * the one in use, but {@code closeable = false} leaves closing it to whoever created it.
     *
     * @param name the name of the connection source
     * @param client the client
     * @param settings the settings
     * @param closeable whether {@link #close()} should close the client
     */
    MongoConnectionSource(String name, MongoClient client, MongoConnectionSourceSettings settings, boolean closeable) {
        super(name, client, settings, closeable)
        this.client = client
    }

    /**
     * Creates the connection source of a client built with the query-tail listener its settings call for. The
     * listener is created here, and registered by {@code client} on the settings it builds the client from, as
     * it applies what it is given; when building the client fails, the listener is closed.
     *
     * @param name the name of the connection source
     * @param settings the settings, whose query-tail settings say whether a listener is registered
     * @param client builds the client, applying what it is given to the settings it builds it from, which
     *     registers the listener, if any
     * @return a connection source that owns the client and the listener
     * @since 9.0
     */
    static MongoConnectionSource create(String name, MongoConnectionSourceSettings settings,
            Function<Consumer<MongoClientSettings.Builder>, MongoClient> client) {
        QueryTailCommandListener queryTail = QueryTailCommandListener.create(name, settings.queryTail)
        Consumer<MongoClientSettings.Builder> register = { MongoClientSettings.Builder builder ->
            if (queryTail != null) {
                builder.addCommandListener(queryTail)
            }
        } as Consumer<MongoClientSettings.Builder>
        try {
            MongoConnectionSource connectionSource = new MongoConnectionSource(name, client.apply(register), settings)
            connectionSource.queryTail = queryTail
            return connectionSource
        }
        catch (RuntimeException | Error failure) {
            queryTail?.close()
            throw failure
        }
    }

    /**
     * Creates a connection source with other settings for the client of one a factory built, taking the client
     * and the query-tail listener registered on it, which the built source no longer closes.
     *
     * @param built the connection source a factory built
     * @param settings the settings of the new connection source
     * @return a connection source that owns the client of the built one, and its listener
     * @since 9.0
     */
    static MongoConnectionSource adopt(ConnectionSource<MongoClient, MongoConnectionSourceSettings> built,
            MongoConnectionSourceSettings settings) {
        MongoConnectionSource connectionSource = new MongoConnectionSource(built.name, built.source, settings)
        connectionSource.queryTail = takeQueryTail(built)
        connectionSource
    }

    /**
     * Hands out the client of a connection source a factory built to whoever builds it for themselves, and
     * closes the connection source's query-tail listener: nothing owns a capture of a client handed out so,
     * so the listener is closed before it has opened one and the client records nothing.
     *
     * @param built the connection source a factory built
     * @return its client, which the caller closes
     * @since 9.0
     */
    static MongoClient detach(ConnectionSource<MongoClient, MongoConnectionSourceSettings> built) {
        takeQueryTail(built)?.close()
        built.source
    }

    private static QueryTailCommandListener takeQueryTail(ConnectionSource<MongoClient, MongoConnectionSourceSettings> built) {
        if (!(built instanceof MongoConnectionSource)) {
            return null
        }
        MongoConnectionSource source = (MongoConnectionSource) built
        QueryTailCommandListener queryTail = source.queryTail
        source.queryTail = null
        queryTail
    }

    /**
     * @return the query-tail listener registered on the client, which {@link #close()} closes, or null when
     *     capture is not enabled
     * @since 9.0
     */
    QueryTailCommandListener getQueryTail() {
        queryTail
    }

    @Override
    MongoClient getSource() {
        return client
    }

    /**
     * Replaces the client, typically with one built from the same settings after the previous one was closed.
     * The client being replaced is not closed here.
     *
     * @param replacement the client to hand out from now on
     */
    void replaceSource(MongoClient replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException('Argument [replacement] cannot be null')
        }
        this.client = replacement
    }

    /**
     * Replaces the client with that of a connection source a factory built from the same settings, after this
     * one's was closed, and the query-tail listener with the one registered on it. The new listener is given the
     * mapping of the datastore and opens its capture before the old one closes, so that it joins the run the old
     * one writes. A replacement built without the listener, as a factory that builds its client other than
     * through {@link #create} builds it, records nothing, which is reported, as it is when such a factory builds
     * the client a datastore starts with. The client being replaced is not closed here.
     *
     * @param built the connection source a factory built in place of this one
     * @param fields the fields the mapping of the datastore the connection source belongs to knows
     * @since 9.0
     */
    void replaceWith(ConnectionSource<MongoClient, MongoConnectionSourceSettings> built, MongoMappedFields fields) {
        replaceSource(built.source)
        QueryTailCommandListener previous = queryTail
        queryTail = takeQueryTail(built)
        if (queryTail != null) {
            queryTail.fields = fields
            queryTail.open()
        }
        else if (previous != null) {
            log.warn('Query-tail capture is enabled, but the MongoDB client built to replace that of connection [{}] ' +
                    'was built without GORM\'s listener, so none of its commands is captured from now on. A connection ' +
                    'source factory that builds the client itself registers the listener by creating the connection ' +
                    'source with {}.create.', name, MongoConnectionSource.simpleName)
        }
        previous?.close()
    }

    @Override
    void close() throws IOException {
        try {
            if (closeable) {
                client.close()
            }
        }
        finally {
            closed = true
            queryTail?.close()
        }
    }
}

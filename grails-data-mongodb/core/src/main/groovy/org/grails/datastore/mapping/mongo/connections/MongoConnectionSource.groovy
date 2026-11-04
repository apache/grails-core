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

import groovy.transform.CompileStatic

import com.mongodb.client.MongoClient

import org.grails.datastore.mapping.core.connections.DefaultConnectionSource

/**
 * The connection source {@link MongoConnectionSourceFactory} creates, whose {@link MongoClient} can be replaced.
 *
 * <p>A closed {@code MongoClient} cannot be reopened, so a datastore that closes its clients when it is stopped for
 * a checkpoint builds new ones when it is started again after the restore. Putting each replacement here, where
 * the original was, means that anything reading the client from the connection source rather than from the
 * datastore gets the one in use, and that closing the connection source closes it when it is GORM's to close.
 *
 * @since 8.0
 */
@CompileStatic
class MongoConnectionSource extends DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings> {

    private volatile MongoClient client

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

    @Override
    void close() throws IOException {
        if (!closeable) {
            closed = true
            return
        }
        try {
            client.close()
        }
        finally {
            closed = true
        }
    }
}

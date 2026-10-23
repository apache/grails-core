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
package org.apache.grails.data.mongo.core.querytail

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import groovy.transform.CompileStatic

import org.apache.grails.data.core.querytail.QueryTailCapture
import org.apache.grails.data.core.querytail.QueryTailSettings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSource
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings

/**
 * The command listener GORM registers on the client of a MongoDB connection source when query-tail
 * capture is enabled. It records every command the driver is about to send to the
 * {@link QueryTailCapture} of the application, without its values, as {@link MongoCommandRedactor}
 * takes them out; the command itself is not changed. The driver calls it on the thread that issues
 * the command, so a command is attributed to the request that thread serves.
 *
 * <p>The connection source owns the capture: it opens with the listener, which is registered as the
 * client's settings are built, and closes when the connection source closes, so a client stopped
 * for a checkpoint and built again after the restore records to the same capture. The datastore
 * the connection source belongs to {@linkplain #bind binds} its mapping to the listener once it is
 * built, so that the keys of a command can be told to be field names; until then, and in a
 * collection no entity is stored in, only the keys at the top of a document are kept.</p>
 *
 * <p>Nothing of this is installed unless {@link QueryTailSettings#SETTING_ENABLED} is set, so an
 * application that does not capture pays nothing per command.</p>
 *
 * @since 8.1
 */
@CompileStatic
class QueryTailCommandListener implements CommandListener, Closeable {

    /** The connection source name commands are recorded under. */
    final String dataSourceName

    /** The file commands are recorded to. */
    final File file

    private final QueryTailCapture capture

    private volatile MongoMappedFields fields

    /**
     * Opens the capture.
     *
     * @param dataSourceName the connection source the client belongs to
     * @param file the capture file commands are recorded to
     * @throws java.io.UncheckedIOException when the capture file cannot be created
     */
    QueryTailCommandListener(String dataSourceName, File file) {
        this.dataSourceName = dataSourceName
        this.file = file
        this.capture = QueryTailCapture.open(file)
    }

    /**
     * Registers a listener on the settings of a client when capture is enabled.
     *
     * @param dataSourceName the connection source the client is built for
     * @param settings the query-tail settings of the connection source
     * @param builder the settings the client is built from
     * @return the registered listener, or null when capture is not enabled
     */
    static QueryTailCommandListener install(String dataSourceName, QueryTailSettings settings,
            MongoClientSettings.Builder builder) {
        if (settings == null || !settings.enabled) {
            return null
        }
        QueryTailCommandListener listener = new QueryTailCommandListener(dataSourceName, settings.resolveFile())
        builder.addCommandListener(listener)
        listener
    }

    /**
     * Gives the listeners of connection sources the mapping of the datastore they belong to.
     *
     * @param connectionSources the connection sources of the datastore
     * @param datastore the datastore
     */
    static void bind(Iterable<ConnectionSource<MongoClient, MongoConnectionSourceSettings>> connectionSources,
            MongoDatastore datastore) {
        MongoMappedFields mappedFields = null
        for (ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource in connectionSources) {
            if (connectionSource instanceof MongoConnectionSource) {
                QueryTailCommandListener listener = ((MongoConnectionSource) connectionSource).queryTail
                if (listener != null) {
                    mappedFields = mappedFields ?: new MongoMappedFields(datastore)
                    listener.fields = mappedFields
                }
            }
        }
    }

    /**
     * @return the fields the mapping of the datastore knows, or null before the datastore is built
     */
    MongoMappedFields getFields() {
        fields
    }

    void setFields(MongoMappedFields fields) {
        this.fields = fields
    }

    /**
     * @return where commands are recorded
     */
    QueryTailCapture getCapture() {
        capture
    }

    @Override
    void commandStarted(CommandStartedEvent event) {
        capture.recordRedacted(dataSourceName,
                MongoCommandRedactor.redact(event.databaseName, event.commandName, event.command, fields))
    }

    /**
     * Closes the capture.
     */
    @Override
    void close() {
        capture.close()
    }
}

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
package org.apache.grails.data.mongodb.core.querytail

import java.util.function.Supplier

import com.mongodb.client.MongoClient
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailRecorder
import org.apache.grails.datastore.core.querytail.QueryTailSettings
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
 * <p>The connection source owns the capture, which closes when the connection source closes, so a
 * client stopped for a checkpoint and built again after the restore records to the same capture,
 * whose file is closed for the checkpoint and opened again after it, as every capture file is.
 * The listener is registered as the client's settings are built, but opens the capture only once the
 * datastore the connection source belongs to {@linkplain #bind binds} its mapping to it, or the
 * client sends its first command, whichever comes first: a connection source that is built and
 * closed before either, as one built only for its client is, leaves the capture file as it found
 * it. The mapping lets the keys of a command be told to be field names; until it is bound, and in a
 * collection no entity is stored in, only the keys at the top of a condition, a pipeline, a sort or a
 * projection are kept.</p>
 *
 * <p>Nothing of this is installed unless {@link QueryTailSettings#SETTING_ENABLED} is set, so an
 * application that does not capture pays nothing per command.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailCommandListener implements CommandListener, Closeable {

    /** The connection source name commands are recorded under. */
    final String dataSourceName

    /** The file commands are recorded to. */
    final File file

    private final QueryTailRecorder recorder

    private volatile MongoMappedFields fields

    /**
     * A listener whose capture opens once it is {@linkplain #open() opened}, or bound, or the first
     * command is recorded; a capture file that cannot be created leaves that capture stopped, which
     * records nothing, rather than failing.
     *
     * @param dataSourceName the connection source the client belongs to
     * @param file the capture file commands are recorded to
     */
    QueryTailCommandListener(String dataSourceName, File file) {
        this.dataSourceName = dataSourceName
        this.file = file
        this.recorder = new QueryTailRecorder(dataSourceName, file)
    }

    /**
     * Creates a listener for the client of a connection source when capture is enabled, for whoever builds the
     * client to register on its settings.
     *
     * @param dataSourceName the connection source the client is built for
     * @param settings the query-tail settings of the connection source
     * @return the listener, or null when capture is not enabled
     */
    static QueryTailCommandListener create(String dataSourceName, QueryTailSettings settings) {
        settings != null && settings.enabled ? new QueryTailCommandListener(dataSourceName, settings.resolveFile()) : null
    }

    /**
     * Gives the listeners of connection sources the mapping of the datastore they belong to, which the
     * datastore and every other datastore built on its mapping share, and opens their captures.
     *
     * @param connectionSources the connection sources of the datastore
     * @param datastore the datastore
     */
    static void bind(Iterable<ConnectionSource<MongoClient, MongoConnectionSourceSettings>> connectionSources,
            MongoDatastore datastore) {
        for (ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource in connectionSources) {
            if (connectionSource instanceof MongoConnectionSource) {
                QueryTailCommandListener listener = ((MongoConnectionSource) connectionSource).queryTail
                if (listener != null) {
                    listener.fields = MongoMappedFields.of(datastore)
                    listener.open()
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
     * Opens the capture now rather than with the first command, unless it is open already, as
     * {@link #bind} does once the datastore is built.
     *
     * @return the capture, or null once the listener is closed without one
     */
    QueryTailCapture open() {
        recorder.open()
    }

    /**
     * @return where commands are recorded, or null before the capture has opened
     */
    QueryTailCapture getCapture() {
        recorder.capture
    }

    @Override
    void commandStarted(CommandStartedEvent event) {
        // redacted by the capture, which does no work once it has stopped and never fails the command
        recorder.recordRedacted({
            MongoCommandRedactor.redact(event.databaseName, event.commandName, event.command, fields)
        } as Supplier<String>)
    }

    /**
     * Closes the capture; a listener closed before its capture opened never opens it.
     */
    @Override
    void close() {
        recorder.close()
    }
}

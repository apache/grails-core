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

import java.util.function.Supplier

import groovy.transform.CompileStatic

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.PropertyResolver

import org.grails.datastore.mapping.core.connections.AbstractConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * A factory for building {@link MongoClient} instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class MongoConnectionSourceFactory extends AbstractConnectionSourceFactory<MongoClient, MongoConnectionSourceSettings> {

    /**
     * The client options builder
     */
    MongoClientSettings.Builder clientOptionsBuilder

    /**
     * An optional additional registry
     */
    @Autowired(required = false)
    CodecRegistry codecRegistry

    /**
     * Optional additional codecs
     */
    @Autowired(required = false)
    List<Codec> codecs = []

    /**
     * Optional customizers applied to the {@link MongoClientSettings.Builder} of the
     * default connection source before the {@link MongoClient} is created. This is the
     * supported hook for settings that have no {@code grails.mongodb.*} equivalent — e.g.
     * registering a driver {@link com.mongodb.event.CommandListener} for metrics/tracing.
     */
    @Autowired(required = false)
    List<MongoClientSettingsBuilderCustomizer> clientSettingsCustomizers = []

    @Override
    Serializable getConnectionSourcesConfigurationKey() {
        return MongoSettings.SETTING_CONNECTIONS
    }

    @Override
    protected <F extends ConnectionSourceSettings> MongoConnectionSourceSettings buildSettings(String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        String prefix = isDefaultDataSource ? MongoSettings.PREFIX : MongoSettings.SETTING_CONNECTIONS + ".$name"
        MongoConnectionSourceSettingsBuilder settingsBuilder = new MongoConnectionSourceSettingsBuilder(configuration, prefix, fallbackSettings)
        MongoClientSettings.Builder builder = clientOptionsBuilder ?: settingsBuilder.clientOptionsBuilder
        MongoConnectionSourceSettings settings = settingsBuilder.build()
        if (builder != null) {
            settings.options = builder
        }

        if (isDefaultDataSource) {
            CodecRegistry codecRegistry = CodecRegistries.fromCodecs(codecs as List<? extends Codec<?>>)
            if (this.codecRegistry != null) {
                codecRegistry = CodecRegistries.fromRegistries(codecRegistry, this.codecRegistry)
            }
            settings.codecRegistry = codecRegistry
        }
        return settings
    }

    /**
     * Creates the connection and its client, a {@link RestartableMongoClient} that connects the first time it is
     * used or its datastore is started, rather than now. The settings are built and checked here, so a connection
     * string that cannot be parsed is still reported as the datastore is created.
     *
     * <p>A subclass that builds the client itself should wrap it the same way, so that it opens no socket before
     * the datastore starts and can be stopped for a CRaC checkpoint and started again after the restore. A client
     * that is not one is closed for the checkpoint instead, and a replacement is built for the restore: return a
     * {@link MongoConnectionSource} for it, so that whatever reads the client from the connection source gets the
     * replacement.
     */
    @Override
    ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
        MongoClientSettings.Builder builder = settings.options
        if (builder != null) {
            builder = MongoClientSettings.builder(builder.build())
        } else {
            builder = MongoClientSettings.builder()
        }

        builder.applyConnectionString(settings.url)
        for (MongoClientSettingsBuilderCustomizer customizer : clientSettingsCustomizers) {
            customizer.customize(builder)
        }
        MongoClientSettings clientSettings = builder.build()
        MongoClient client = new RestartableMongoClient(name, { MongoClients.create(clientSettings) } as Supplier<MongoClient>)
        return new MongoConnectionSource(name, client, settings)
    }

    @Override
    def <F extends ConnectionSourceSettings> MongoConnectionSourceSettings buildRuntimeSettings(String name, PropertyResolver configuration, F fallbackSettings) {
        MongoConnectionSourceSettingsBuilder settingsBuilder = new MongoConnectionSourceSettingsBuilder(configuration, '', fallbackSettings)
        MongoClientSettings.Builder builder = clientOptionsBuilder ?: settingsBuilder.clientOptionsBuilder
        MongoConnectionSourceSettings settings = settingsBuilder.build()
        if (builder != null) {
            settings.options = builder
        }
        return settings
    }
}


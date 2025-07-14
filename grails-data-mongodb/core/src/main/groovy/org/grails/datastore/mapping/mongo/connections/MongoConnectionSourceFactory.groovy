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

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import groovy.transform.CompileStatic
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.mapping.core.connections.AbstractConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.PropertyResolver

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

    @Override
    ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
        MongoClientSettings.Builder builder = settings.options
        if (builder != null) {
            builder = MongoClientSettings.builder(builder.build())
        } else {
            builder = MongoClientSettings.builder()
        }

        builder.applyConnectionString(settings.url)
        MongoClient client = MongoClients.create(builder.build())
        return new DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings>(name, client, settings)
    }

    @Override
    def <F extends ConnectionSourceSettings> MongoConnectionSourceSettings buildRuntimeSettings(String name, PropertyResolver configuration, F fallbackSettings) {
        MongoConnectionSourceSettingsBuilder settingsBuilder = new MongoConnectionSourceSettingsBuilder(configuration, "", fallbackSettings)
        MongoClientSettings.Builder builder = clientOptionsBuilder ?: settingsBuilder.clientOptionsBuilder
        MongoConnectionSourceSettings settings = settingsBuilder.build()
        if (builder != null) {
            settings.options = builder
        }
        return settings
    }
}


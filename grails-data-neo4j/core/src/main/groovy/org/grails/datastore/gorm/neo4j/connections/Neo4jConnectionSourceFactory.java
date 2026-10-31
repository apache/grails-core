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
package org.grails.datastore.gorm.neo4j.connections;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import groovy.transform.CompileStatic;
import groovy.util.logging.Slf4j;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.harness.ServerControls;

import org.springframework.core.env.PropertyResolver;

import org.apache.grails.data.neo4j.core.querytail.QueryTailDriver;
import org.grails.datastore.gorm.neo4j.config.Settings;
import org.grails.datastore.gorm.neo4j.util.EmbeddedNeo4jServer;
import org.grails.datastore.mapping.core.connections.AbstractConnectionSourceFactory;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings;
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.reflect.ClassUtils;

/**
 * A {@link org.grails.datastore.mapping.core.connections.ConnectionSourceFactory} for Neo4j
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
public class Neo4jConnectionSourceFactory extends AbstractConnectionSourceFactory<Driver, Neo4jConnectionSourceSettings> {

    /**
     * @return Whether the embedded server capability is available or not
     */
    public static boolean isEmbeddedAvailable() {
        return ClassUtils.isPresent("org.neo4j.harness.ServerControls", Neo4jConnectionSourceFactory.class.getClassLoader());
    }

    @Override
    protected <F extends ConnectionSourceSettings> Neo4jConnectionSourceSettings buildSettings(String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        String prefix = isDefaultDataSource ? Settings.PREFIX : Settings.SETTING_CONNECTIONS + "." + name;
        Neo4jConnectionSourceSettingsBuilder settingsBuilder = new Neo4jConnectionSourceSettingsBuilder(configuration, prefix, fallbackSettings);
        Neo4jConnectionSourceSettings settings = settingsBuilder.build();
        return settings;
    }

    @Override
    public ConnectionSource<Driver, Neo4jConnectionSourceSettings> create(String name, Neo4jConnectionSourceSettings settings) {
        final String url = settings.getUrl();
        final String username = settings.getUsername();
        final String password = settings.getPassword();
        final Neo4jConnectionSourceSettings.ConnectionType type = settings.getType();
        if (type == Neo4jConnectionSourceSettings.ConnectionType.embedded && ConnectionSource.DEFAULT.equals(name)) {
            if (isEmbeddedAvailable()) {
                Neo4jConnectionSourceSettings.EmbeddedSettings embeddedSettings = settings.getEmbedded();
                final String location = settings.getLocation() != null ? settings.getLocation() : embeddedSettings.getDirectory();
                final Map options = embeddedSettings.getOptions();
                final File dataDir;
                try {
                    if (location != null) {
                        dataDir = new File(location);
                    } else if (embeddedSettings.isEphemeral()) {
                        dataDir = File.createTempFile("neo4j-temporary-data", "-tempdir");
                    } else {
                        dataDir = new File(Settings.DEFAULT_LOCATION);
                    }
                } catch (IOException e) {
                    throw new DatastoreConfigurationException("Unable to create temporary data directory: " + e.getMessage(), e);
                }
                if (embeddedSettings.isDropData() || embeddedSettings.isEphemeral()) {
                    dataDir.delete();
                }
                if (embeddedSettings.isEphemeral()) {
                    dataDir.deleteOnExit();
                }
                ServerControls serverControls = null;
                Driver driver = null;
                try {
                    serverControls = url != null ? EmbeddedNeo4jServer.start(url, dataDir, options) : EmbeddedNeo4jServer.start(dataDir, options);
                    Config config = Config.builder().withoutEncryption().build();
                    URI boltURI = serverControls.boltURI();
                    driver = QueryTailDriver.install(name, settings.getQueryTail(), GraphDatabase.driver(boltURI, config));
                    return createEmbeddedConnectionSource(name, driver, settings, serverControls);
                } catch (Throwable e) {
                    // nothing else would close what was opened before the failure: the driver, whose query-tail
                    // capture closes with it, and the server
                    closeAfterFailure(driver, e);
                    closeAfterFailure(serverControls, e);
                    throw new DatastoreConfigurationException("Unable to start embedded Neo4j server: " + e.getMessage(), e);
                }
            } else {
                throw new DatastoreConfigurationException("Embedded Neo4j server was configured but 'neo4j-harness' classes not found on classpath.");
            }
        }

        AuthToken authToken = null;

        if (username != null && password != null) {
            authToken = AuthTokens.basic(username, password);
        }

        Config driverConfig = settings.getOptions().build();
        Driver driver = QueryTailDriver.install(name, settings.getQueryTail(),
                GraphDatabase.driver(url != null ? url : Settings.DEFAULT_URL, authToken, driverConfig));
        return new DefaultConnectionSource<>(name, driver, settings);
    }

    /**
     * Creates the connection source of an embedded server. Should it fail, the driver and the server are closed.
     *
     * @param name the name of the connection source
     * @param driver the driver connected to the server, which records statements when query-tail capture is enabled
     * @param settings the settings
     * @param serverControls the server
     * @return the connection source, which closes the driver and the server
     * @since 9.0
     */
    protected ConnectionSource<Driver, Neo4jConnectionSourceSettings> createEmbeddedConnectionSource(String name, Driver driver,
            Neo4jConnectionSourceSettings settings, ServerControls serverControls) {
        return new Neo4jEmbeddedConnectionSource(name, driver, settings, serverControls);
    }

    private static void closeAfterFailure(AutoCloseable opened, Throwable failure) {
        if (opened == null) {
            return;
        }
        try {
            opened.close();
        } catch (Exception e) {
            failure.addSuppressed(e);
        }
    }

    @Override
    public Serializable getConnectionSourcesConfigurationKey() {
        return Settings.SETTING_CONNECTIONS;
    }

}

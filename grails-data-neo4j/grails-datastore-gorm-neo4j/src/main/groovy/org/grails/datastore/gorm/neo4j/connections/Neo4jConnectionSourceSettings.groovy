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
package org.grails.datastore.gorm.neo4j.connections

import groovy.transform.AutoClone
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.gorm.neo4j.config.Neo4jDriverConfigBuilder
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings

/**
 * Settings for Neo4j
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@AutoClone
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Neo4jConnectionSourceSettings extends ConnectionSourceSettings implements Settings {

    /**
     * The URL to connect to
     */
    String url

    /**
     * The username to use
     */
    String username

    /**
     * The password to use
     */
    String password

    /**
     * The Neo4j connection type
     */
    ConnectionType type = ConnectionType.remote

    /**
     * Whether to build the Neo4j index
     */
    boolean buildIndex = true

    /**
     * The data location when using embedded
     */
    String location


    /**
     * Neo4j driver options
     */
    Neo4jDriverConfigBuilder options

    /**
     * Neo4j embedded mode options
     */
    EmbeddedSettings embedded = new EmbeddedSettings()

    @AutoClone
    @Builder(builderStrategy = SimpleStrategy, prefix = '')
    static class EmbeddedSettings {
        /**
         * Options to pass to the embedded server
         */
        Map options = [:]
        /**
         * The directory to store embedded data
         */
        String directory
        /**
         * Whether to drop existing data
         */
        boolean dropData = false

        /**
         * Whether to
         */
        boolean ephemeral = false
    }

    static enum ConnectionType {
        remote, embedded
    }
}

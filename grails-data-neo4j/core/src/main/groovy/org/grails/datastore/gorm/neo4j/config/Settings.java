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
package org.grails.datastore.gorm.neo4j.config;

/**
 * Settings for Neo4j
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface Settings extends org.grails.datastore.mapping.config.Settings {
    /**
     * The default configuration prefix
     */
    String PREFIX = "grails.neo4j";

    /**
     * The default URL
     */
    String DEFAULT_URL = "bolt://localhost:7687";
    /**
     * The default embedded data location
     */
    String DEFAULT_LOCATION = "data/neo4j";

    /**
     * Configuration for multiple data sources (connections)
     */
    String SETTING_CONNECTIONS = PREFIX + ".connections";

    /**
     * The Neo4j Bolt URL to connect to
     */
    String SETTING_NEO4J_URL = PREFIX + ".url";

    /**
     * Whether to build the Neo4j index
     */
    String SETTING_NEO4J_BUILD_INDEX = PREFIX + ".buildIndex";

    /**
     * The Neo4j embedded data location
     */
    String SETTING_NEO4J_LOCATION = PREFIX+ ".location";

    /**
     * The connection type (either embedded or remote)
     */
    String SETTING_NEO4J_TYPE = PREFIX + ".type";

    /**
     * The default flush mode
     */
    String SETTING_NEO4J_FLUSH_MODE = PREFIX + ".flush.mode";

    /**
     * The username
     */
    String SETTING_NEO4J_USERNAME = PREFIX + ".username";

    /**
     * The password
     */
    String SETTING_NEO4J_PASSWORD = PREFIX + ".password";

    /**
     * The bolt driver options
     */
    String SETTING_NEO4J_DRIVER_PROPERTIES = PREFIX + ".options";

    /**
     * The embedded server options
     */
    String SETTING_NEO4J_EMBEDDED_DB_PROPERTIES = PREFIX + ".embedded.options";

    /**
     * Whether the data for this embedded server should be created in a temporary location and deleted on exit. useful for testing
     */
    String SETTING_NEO4J_EMBEDDED_EPHEMERAL = PREFIX + ".embedded.ephemeral";

    String DEFAULT_DATABASE_TYPE = "remote";
    String DATABASE_TYPE_EMBEDDED = "embedded";
    String SETTING_DEFAULT_MAPPING = "grails.neo4j.default.mapping";
}

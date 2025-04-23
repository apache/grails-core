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

package org.grails.datastore.mapping.mongo.config

import org.grails.datastore.mapping.config.Settings

/**
 * Additional settings for MongoDB
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface MongoSettings extends Settings {
    /**
     * The default database name if none is specified
     */
    String DEFAULT_DATABASE_NAME = "test"
    /**
     * The prefix
     */
    String PREFIX = "grails.mongodb"

    /**
     * The database name
     */
    String SETTING_DATABASE_NAME = "grails.mongodb.databaseName"

    /**
     * Whether to use the decimal type
     */
    String SETTING_DECIMAL_TYPE = "grails.mongodb.decimalType"

    /**
     * The connection string
     */
    String SETTING_CONNECTION_STRING = "grails.mongodb.connectionString"

    /**
     * All MongoDB connections
     */
    String SETTING_CONNECTIONS = "grails.mongodb.connections"

    /**
     * All MongoDB codecs
     */
    String SETTING_CODECS = "grails.mongodb.codecs"
    /**
     * The URL
     */
    String SETTING_URL = "grails.mongodb.url"
    /**
     * The default mapping
     */
    String SETTING_DEFAULT_MAPPING = "grails.mongodb.default.mapping"
    /**
     * The client options
     */
    String SETTING_OPTIONS = "grails.mongodb.options"
    /**
     * The host
     */
    String SETTING_HOST = "grails.mongodb.host"
    /**
     * The port
     */
    String SETTING_PORT = "grails.mongodb.port"
    /**
     * The username
     */
    String SETTING_USERNAME = "grails.mongodb.username"
    /**
     * The password
     */
    String SETTING_PASSWORD = "grails.mongodb.password"

    String SETTING_STATELESS = "grails.mongodb.stateless"

    String SETTING_ENGINE = "grails.mongodb.engine"

}
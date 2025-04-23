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

import com.mongodb.ConnectionString
import com.mongodb.ServerAddress
import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.mongo.MongoConstants
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * @author Graeme Rocher
 * @since 6.0
 */
@AutoClone
@Builder(builderStrategy = SimpleStrategy, prefix = '')
@CompileStatic
abstract class AbstractMongoConnectionSourceSettings extends ConnectionSourceSettings implements MongoSettings {

    /**
     * The connection string
     */
    protected ConnectionString connectionString

    /**
     * The default database name
     */
    String databaseName = DEFAULT_DATABASE_NAME


    /**
     * The host name to use
     */
    String host = ServerAddress.defaultHost();

    /**
     * The port to use
     */
    Integer port = ServerAddress.defaultPort()

    /**
     * The username to use
     */
    String username
    /**
     * The password to use
     */
    String password
    /**
     * The engine to use by default
     */
    String engine = MongoConstants.CODEC_ENGINE

    /**
     * Whether to use stateless mode by default
     */
    boolean stateless = false

    /**
     * Whether to use the decimal128 type for BigDecimal values
     *
     * @see org.bson.types.Decimal128
     */
    boolean decimalType = true

    /**
     * The collection name to use to resolve connections when using {@link MongoConnectionSources}
     */
    String connectionsCollection = "mongo.connections"

    /**
     * Custom MongoDB codecs
     */
    List<Class<? extends Codec>> codecs = []

    /**
     * An additional codec registry
     */
    CodecRegistry codecRegistry

    /**
     * @return Obtain the final URL whether from the connection string or the host/port setting
     */
    ConnectionString getUrl() {
        if(connectionString != null) {
            return connectionString
        }
        else {
            String uAndP = username && password ? "$username:$password@" : ''
            String portStr = port ? ":$port" : ''
            return new ConnectionString("mongodb://${uAndP}${host}${portStr}/$database")
        }
    }

    /**
     * @param connectionString The connection string
     */
    void url(ConnectionString connectionString) {
        this.connectionString = connectionString
    }

    /**
     * @return Obtain the database name
     */
    String getDatabase() {
        if(connectionString != null) {
            return connectionString.database ?: databaseName
        }
        else {
            return databaseName
        }
    }

}

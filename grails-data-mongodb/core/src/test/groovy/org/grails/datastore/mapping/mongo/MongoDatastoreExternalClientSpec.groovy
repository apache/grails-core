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
package org.grails.datastore.mapping.mongo

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import spock.lang.Specification

class MongoDatastoreExternalClientSpec extends Specification {

    void 'an externally supplied MongoClient is not closed when the datastore is closed'() {
        given: 'a datastore built around an externally managed MongoClient'
        def mongoClient = Mock(MongoClient)
        def datastore = new MongoDatastore(mongoClient)

        when: 'the default connection source is inspected'
        def defaultConnectionSource = datastore.connectionSources.defaultConnectionSource

        then: 'it wraps the supplied client but does not own its lifecycle'
        defaultConnectionSource instanceof DefaultConnectionSource
        defaultConnectionSource.source.is(mongoClient)
        !((DefaultConnectionSource) defaultConnectionSource).closeable

        when: 'the datastore is closed'
        datastore.close()

        then: 'the externally managed client is left open for its provider to manage'
        0 * mongoClient.close()
    }

    void 'a MongoClient that GORM builds from a settings builder is owned and closed by GORM'() {
        given: 'a datastore constructed from a MongoClientSettings builder (GORM creates the client)'
        def datastore = new MongoDatastore(
                MongoClientSettings.builder(),
                DatastoreUtils.createPropertyResolver([:]),
                new MongoMappingContext('test'))

        when: 'the default connection source is inspected'
        def defaultConnectionSource = datastore.connectionSources.defaultConnectionSource

        then: 'GORM owns the client it created and will close it on shutdown'
        ((DefaultConnectionSource) defaultConnectionSource).closeable

        cleanup:
        datastore?.close()
    }
}

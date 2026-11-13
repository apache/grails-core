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
package org.grails.datastore.gorm.mongo.connections

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSources

/**
 * A datastore stopped for a CRaC checkpoint closes the client of every connection and builds replacements when it
 * is started again. These check, against a real server, that what the application goes on using after the restore
 * reaches MongoDB through a replacement rather than through a client the checkpoint closed.
 */
class CheckpointRestoreConnectionsSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient inspector

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        inspector = MongoClients.create(dbContainer.getReplicaSetUrl('restoreInspectorDb'))
    }

    void cleanupSpec() {
        inspector?.close()
    }

    void "test a named connection saves through GORM after a restore"() {
        given: "a datastore with a named connection"
        def datastore = new MongoDatastore([
                (MongoSettings.SETTING_URL)        : dbContainer.getReplicaSetUrl('restoredDefaultDb'),
                (MongoSettings.SETTING_CONNECTIONS): [reporting: [url: dbContainer.getReplicaSetUrl('restoredReportingDb')]]
        ], RestoredThing)

        when: "it is stopped for a checkpoint, which closes the named connection's client as well, and restored"
        datastore.stop()
        datastore.start()

        and: "an entity is saved through that connection"
        RestoredThing.reporting.save(new RestoredThing(name: 'Fred'), [flush: true])

        then: "it reaches the connection's own database, and reads back through it"
        inspector.getDatabase('restoredReportingDb').getCollection('restoredThing').countDocuments() == 1
        RestoredThing.reporting.count() == 1

        cleanup:
        datastore?.close()
        inspector.getDatabase('restoredReportingDb').drop()
    }

    void "test a connection added at runtime after a restore is recorded through the restored client"() {
        given: "connection sources that record each connection added at runtime in the default connection's database"
        def datastore = new MongoDatastore([
                'grails.gorm.connectionSourcesClass': MongoConnectionSources,
                (MongoSettings.SETTING_URL)         : dbContainer.getReplicaSetUrl('restoredSourcesDb')
        ], RestoredThing)
        datastore.stop()
        datastore.start()

        when:
        datastore.connectionSources.addConnectionSource('addedAfterRestore',
                [url: dbContainer.getReplicaSetUrl('addedAfterRestoreDb')])

        then:
        inspector.getDatabase('restoredSourcesDb').getCollection('mongo.connections')
                .countDocuments(new Document('name', 'addedAfterRestore')) == 1

        cleanup:
        datastore?.close()
        inspector.getDatabase('restoredSourcesDb').drop()
    }
}

@Entity
class RestoredThing {
    String name

    static mapping = {
        version false
        collection 'restoredThing'
        connection ConnectionSource.ALL
    }
}

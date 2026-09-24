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
package org.grails.datastore.gorm.mongo

import ch.qos.logback.classic.Level
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.IndexOptions
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * A declared index whose name is held by an index on other keys is an {@code IndexKeySpecsConflict}, which
 * {@code recreateOnConflict} resolves as it does a conflict of options, and which is reported for what it is
 * when it is not declared.
 */
class BuildIndexesNameConflictSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient setupClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void cleanupSpec() {
        setupClient?.close()
    }

    private List<Document> indexesOf(String database, String collection) {
        setupClient.getDatabase(database).getCollection(collection).listIndexes().toList()
    }

    /**
     * An index named as the entity declares one, but on another field.
     */
    private void givenIndexHoldingTheName(String database, String collection) {
        setupClient = MongoClients.create(dbContainer.getReplicaSetUrl(database))
        setupClient.getDatabase(database).getCollection(collection)
                .createIndex(new Document('code', 1), new IndexOptions(name: 'byName'))
    }

    void "test an index whose name is taken by another index is recreated when that is declared"() {
        given:
        givenIndexHoldingTheName('nameConflictRecreateDb', 'nameConflictRecreateThing')

        when:
        def datastore = new MongoDatastore(
                ['grails.mongodb.url': dbContainer.getReplicaSetUrl('nameConflictRecreateDb')] as Map,
                NameConflictRecreateThing)

        then: "the name now belongs to the declared index"
        def indexes = indexesOf('nameConflictRecreateDb', 'nameConflictRecreateThing')
        indexes.find { it.name == 'byName' }.key == [name: 1]
        !indexes.any { it.key == [code: 1] }

        cleanup:
        datastore?.close()
    }

    void "test an index whose name is taken is reported as such when recreating is not declared"() {
        given:
        givenIndexHoldingTheName('nameConflictReportDb', 'nameConflictReportThing')
        def log = new CapturedLog('org.grails.datastore.mapping', Level.INFO)

        when:
        def datastore = new MongoDatastore(
                ['grails.mongodb.url': dbContainer.getReplicaSetUrl('nameConflictReportDb')] as Map,
                NameConflictReportThing)

        then: "the index that holds the name is left alone, and the build says why it could not be created"
        indexesOf('nameConflictReportDb', 'nameConflictReportThing').find { it.name == 'byName' }.key == [code: 1]
        log.events.any {
            it.level == Level.ERROR &&
                    it.formattedMessage.contains('the name [byName] is taken by an index on different keys')
        }

        and: "it is counted as a failure"
        log.events.any {
            it.formattedMessage.contains('database [nameConflictReportDb] finished') && it.formattedMessage.contains('1 failed')
        }

        cleanup:
        datastore?.close()
        log?.close()
    }
}

@Entity
class NameConflictRecreateThing {
    String name
    String code

    static mapping = {
        version false
        collection 'nameConflictRecreateThing'
        name index: true, indexAttributes: [name: 'byName', recreateOnConflict: true]
    }
}

@Entity
class NameConflictReportThing {
    String name
    String code

    static mapping = {
        version false
        collection 'nameConflictReportThing'
        name index: true, indexAttributes: [name: 'byName']
    }
}

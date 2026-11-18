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
package org.grails.datastore.gorm.mongo.transactions

import grails.gorm.annotation.Entity

import org.apache.grails.testing.mongo.EmbeddedReplicaSetSpec
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.MongoSession
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * A query run by a {@code beforeInsert} or {@code beforeUpdate} listener while the session flushes: a
 * query flushes the session first where it is allowed to (outside a transaction, and inside a
 * server-side one), which must not start the flush running that listener over again.
 */
class MongoFlushListenerQuerySpec extends EmbeddedReplicaSetSpec {

    @Shared
    @AutoCleanup
    MongoDatastore transactional

    @Shared
    @AutoCleanup
    MongoDatastore nonTransactional

    @Shared
    @AutoCleanup
    MongoDatastore mappingEngine

    void setupSpec() {
        transactional = new MongoDatastore(['grails.mongodb.url': mongoUrl, 'grails.mongodb.transactional': true], QueryingTxThing)
        nonTransactional = new MongoDatastore(['grails.mongodb.url': mongoUrl], QueryingThing)
        mappingEngine = new MongoDatastore(['grails.mongodb.url': mongoUrl, (MongoSettings.SETTING_ENGINE): 'mapping'], QueryingMappedThing)
    }

    void setup() {
        QueryingTxThing.withNewSession {
            QueryingTxThing.DB.drop()
            QueryingTxThing.DB.createCollection(QueryingTxThing.collectionName)
        }
        QueryingThing.withNewSession { QueryingThing.DB.drop() }
        QueryingMappedThing.withNewSession { QueryingMappedThing.DB.drop() }
    }

    void "a listener's query inside a server-side transaction does not flush the session again"() {
        when:
        QueryingTxThing saved = QueryingTxThing.withTransaction {
            new QueryingTxThing(name: "inserted").save(failOnError: true)
        }
        QueryingTxThing.withTransaction {
            QueryingTxThing found = QueryingTxThing.get(saved.id)
            found.name = "updated"
            found.save(failOnError: true)
        }

        then:
        QueryingTxThing.withNewSession { QueryingTxThing.list()*.name } == ["updated"]
    }

    void "a listener's query in a transaction without a server-side one does not flush the session again"() {
        when:
        QueryingThing saved = QueryingThing.withTransaction {
            new QueryingThing(name: "inserted").save(failOnError: true)
        }
        QueryingThing.withTransaction {
            QueryingThing found = QueryingThing.get(saved.id)
            found.name = "updated"
            found.save(failOnError: true)
        }

        then:
        QueryingThing.withNewSession { QueryingThing.list()*.name } == ["updated"]
    }

    void "a listener's query outside any transaction does not flush the session again"() {
        when:
        QueryingThing saved = QueryingThing.withNewSession {
            new QueryingThing(name: "inserted").save(flush: true, failOnError: true)
        }
        QueryingThing.withNewSession {
            QueryingThing found = QueryingThing.get(saved.id)
            found.name = "updated"
            found.save(flush: true, failOnError: true)
        }

        then:
        QueryingThing.withNewSession { QueryingThing.list()*.name } == ["updated"]
    }

    void "the deprecated mapping engine's session does not flush again either"() {
        given:
        Session session = mappingEngine.connect()
        assert session instanceof MongoSession
        session.disconnect()

        when:
        QueryingMappedThing saved = QueryingMappedThing.withNewSession {
            new QueryingMappedThing(name: "inserted").save(flush: true, failOnError: true)
        }
        QueryingMappedThing.withNewSession {
            QueryingMappedThing found = QueryingMappedThing.get(saved.id)
            found.name = "updated"
            found.save(flush: true, failOnError: true)
        }

        then:
        QueryingMappedThing.withNewSession { QueryingMappedThing.list()*.name } == ["updated"]
    }
}

@Entity
class QueryingTxThing {
    String name

    boolean beforeInsert() {
        QueryingTxThing.count()
        true
    }

    boolean beforeUpdate() {
        QueryingTxThing.count()
        true
    }
}

@Entity
class QueryingThing {
    String name

    boolean beforeInsert() {
        QueryingThing.count()
        true
    }

    boolean beforeUpdate() {
        QueryingThing.count()
        true
    }
}

@Entity
class QueryingMappedThing {
    String name

    boolean beforeInsert() {
        QueryingMappedThing.count()
        true
    }

    boolean beforeUpdate() {
        QueryingMappedThing.count()
        true
    }
}

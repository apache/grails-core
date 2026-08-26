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
package org.grails.datastore.gorm.mongodb.embedded

import com.mongodb.client.ClientSession
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients

import org.bson.Document

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource

import spock.lang.Specification

/**
 * What a replica set is for: a standalone MongoDB refuses a transaction, so an application that
 * asks GORM for one needs the embedded server to be a set of one.
 */
class EmbeddedMongoReplicaSetSpec extends Specification {

    void 'a server asked for a replica set commits a transaction across two collections'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)    : FlapdoodleMongoBackend.NAME,
                (EmbeddedMongoInitializer.REPLICA_SET): 'rs0',
                'grails.mongodb.url'                  : 'mongodb://embedded:27973/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')

        when:
        MongoClient client = MongoClients.create(url)
        ClientSession session = client.startSession()
        session.startTransaction()
        client.getDatabase('bookstore').getCollection('books')
                .insertOne(session, new Document('title', 'Making Java Groovy'))
        client.getDatabase('bookstore').getCollection('authors')
                .insertOne(session, new Document('name', 'Ken Kousen'))
        session.commitTransaction()

        then: 'both writes are there, which a standalone server would have refused to start'
        client.getDatabase('bookstore').getCollection('books').countDocuments() == 1
        client.getDatabase('bookstore').getCollection('authors').countDocuments() == 1

        cleanup:
        session?.close()
        client?.close()
        context?.close()
    }

    void 'a transaction that is not committed leaves nothing behind'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)    : FlapdoodleMongoBackend.NAME,
                (EmbeddedMongoInitializer.REPLICA_SET): 'rs0',
                'grails.mongodb.url'                  : 'mongodb://embedded:27972/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        MongoClient client = MongoClients.create(context.environment.getProperty('grails.mongodb.url'))

        when:
        ClientSession session = client.startSession()
        session.startTransaction()
        client.getDatabase('bookstore').getCollection('books')
                .insertOne(session, new Document('title', 'Groovy in Action'))
        session.abortTransaction()

        then:
        client.getDatabase('bookstore').getCollection('books').countDocuments() == 0

        cleanup:
        session?.close()
        client?.close()
        context?.close()
    }

    void 'an application that asks GORM for transactions is given a replica set without naming one'() {
        given: 'the setting an application writes, which says nothing of replica sets'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)      : FlapdoodleMongoBackend.NAME,
                (EmbeddedMongoInitializer.TRANSACTIONAL): 'true',
                'grails.mongodb.url'                    : 'mongodb://embedded:27971/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        MongoClient client = MongoClients.create(context.environment.getProperty('grails.mongodb.url'))

        expect:
        client.getDatabase('admin').runCommand(new Document('hello', 1)).setName == 'rs0'

        cleanup:
        client?.close()
        context?.close()
    }

    void 'a server nobody asked to replicate is left standalone'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): FlapdoodleMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27970/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(context)
        MongoClient client = MongoClients.create(context.environment.getProperty('grails.mongodb.url'))

        expect: 'no set name, and starting one is what the driver refuses'
        client.getDatabase('admin').runCommand(new Document('hello', 1)).setName == null

        cleanup:
        client?.close()
        context?.close()
    }

    private GenericApplicationContext contextWith(Map<String, Object> properties) {
        GenericApplicationContext context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        context
    }

}

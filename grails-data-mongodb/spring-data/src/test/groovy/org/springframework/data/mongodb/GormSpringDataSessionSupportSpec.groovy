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
package org.springframework.data.mongodb

import com.mongodb.client.ClientSession
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Smoke test for the deliberate coupling to Spring Data's package-private {@link MongoResourceHolder}.
 * Lives in {@code org.springframework.data.mongodb} so it can reference that type directly. If a
 * future Spring Data version changes the holder's construction or shape, this test (and the module
 * compilation) fails loudly rather than silently disabling the shared transaction.
 */
class GormSpringDataSessionSupportSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoClient mongoClient

    @Shared
    MongoDatabaseFactory factory

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        mongoClient = MongoClients.create(dbContainer.getReplicaSetUrl('myDb'))
        factory = new SimpleMongoClientDatabaseFactory(mongoClient, 'myDb')
    }

    void "test binding a ClientSession exposes a MongoResourceHolder carrying that session for the factory"() {
        given:
        ClientSession session = mongoClient.startSession()

        when:
        GormSpringDataSessionSupport.bindClientSession(factory, session)

        then: "a MongoResourceHolder for the factory is bound to the thread, carrying the supplied session"
        TransactionSynchronizationManager.hasResource(factory)
        def holder = TransactionSynchronizationManager.getResource(factory)
        holder instanceof MongoResourceHolder
        ((MongoResourceHolder) holder).session.is(session)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(factory)
        session?.close()
    }
}

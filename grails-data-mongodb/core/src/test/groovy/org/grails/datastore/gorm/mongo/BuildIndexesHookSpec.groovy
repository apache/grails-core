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

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.util.concurrent.PollingConditions

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.core.env.PropertyResolver

class BuildIndexesHookSpec extends AutoStartedMongoSpec {

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void "test the protected index hook customises startup and later entities with async #async"() {
        given:
        def conditions = new PollingConditions(timeout: 30)

        when:
        def datastore = new CustomIndexDatastore([
                'grails.mongodb.url': dbContainer.getReplicaSetUrl("indexHook${async}"),
                'grails.mongodb.buildIndexesAsync': async
        ])

        then:
        conditions.eventually {
            assert [name: 1] in StartupHookThing.collection.listIndexes()*.key
            assert [custom: 1] in StartupHookThing.collection.listIndexes()*.key
        }

        when: "another domain class is registered after startup"
        datastore.mappingContext.addPersistentEntity(LaterHookThing)

        then:
        [name: 1] in LaterHookThing.collection.listIndexes()*.key
        [custom: 1] in LaterHookThing.collection.listIndexes()*.key

        cleanup:
        datastore?.close()

        where:
        async << [false, true]
    }

    void "test a checked exception from the hook reaches the caller of a synchronous build unchanged"() {
        given:
        MongoClient client = MongoClients.create(dbContainer.getReplicaSetUrl('hookFailureDb'))

        when: "an override throws a checked exception it does not declare, as Groovy allows"
        new InterruptedHookDatastore(client,
                DatastoreUtils.createPropertyResolver(['grails.mongodb.databaseName': 'hookFailureDb']), HookFailureThing)

        then: "the caller gets that exception, not a wrapper around it"
        def e = thrown(InterruptedException)
        e.message == 'interrupted in the hook'

        and: "the interrupt it stands for is not lost"
        Thread.interrupted()

        cleanup:
        Thread.interrupted()
        client?.close()
    }
}

class InterruptedHookDatastore extends MongoDatastore {

    InterruptedHookDatastore(MongoClient client, PropertyResolver configuration, Class... classes) {
        super(client, configuration, classes)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        throw new InterruptedException('interrupted in the hook')
    }
}

@Entity
class HookFailureThing {
    String name

    static mapping = {
        name index: true
    }
}

class CustomIndexDatastore extends MongoDatastore {

    CustomIndexDatastore(Map configuration) {
        super(configuration, StartupHookThing)
    }

    @Override
    protected void initializeIndices(PersistentEntity entity) {
        super.initializeIndices(entity)
        getCollection(entity).createIndex(new Document('custom', 1))
    }
}

@Entity
class StartupHookThing {
    String name

    static mapping = {
        name index: true
    }
}

@Entity
class LaterHookThing {
    String name

    static mapping = {
        name index: true
    }
}

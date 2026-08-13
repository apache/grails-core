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
package grails.mongodb.bootstrap

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import grails.mongodb.MongoEntity
import spock.lang.Specification

/**
 * Pure unit coverage for {@link MongoDbDataStoreSpringInitializer} that does not require a
 * running MongoDB instance, covering the {@code isMappedClass} override and the deprecated
 * bean-style setters that {@link MongoDbDataStoreSpringInitializerSpec} does not reach.
 */
class MongoDbDataStoreSpringInitializerUnitSpec extends Specification {

    void 'isMappedClass and collectMappedClasses discriminate MongoEntity classes from unrelated ones for a secondary datastore'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer([MappedThing, UnmappedThing])
        initializer.setSecondaryDatastore(true)

        expect:
        initializer.isMappedClass('mongo', MappedThing)
        !initializer.isMappedClass('mongo', UnmappedThing)
        initializer.collectMappedClasses('mongo') == [MappedThing]
    }

    void 'setMongoBeanName updates the mongo bean name'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()

        when:
        initializer.setMongoBeanName('customMongo')

        then:
        initializer.mongoBeanName == 'customMongo'
    }

    void 'setMongoOptionsBeanName updates the mongo options bean name'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()

        when:
        initializer.setMongoOptionsBeanName('customMongoOptions')

        then:
        initializer.mongoOptionsBeanName == 'customMongoOptions'
    }

    void 'setDatabaseName updates the database name'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()

        when:
        initializer.setDatabaseName('customDb')

        then:
        initializer.databaseName == 'customDb'
    }

    void 'setDefaultMapping updates the default mapping closure'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def mapping = { -> }

        when:
        initializer.setDefaultMapping(mapping)

        then:
        initializer.defaultMapping.is(mapping)
    }

    void 'setMongoOptions updates the mongo client settings'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def settings = MongoClientSettings.builder().build()

        when:
        initializer.setMongoOptions(settings)

        then:
        initializer.mongoOptions.is(settings)
    }

    void 'setMongoClient records the pre-existing client to reuse'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def client = Mock(MongoClient)

        when:
        initializer.setMongoClient(client)

        then:
        initializer.mongo.is(client)
    }
}

class MappedThing implements MongoEntity<MappedThing> {
    Long id
}

class UnmappedThing {
    static mapWith = 'sql'
}

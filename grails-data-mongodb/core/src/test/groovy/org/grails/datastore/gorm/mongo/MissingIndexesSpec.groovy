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
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import grails.gorm.annotation.Entity
import org.bson.Document
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MissingIndex
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * {@link MongoDatastore#findMissingIndexes()} reports the indexes the domain classes declare that their
 * collections do not have.
 */
class MissingIndexesSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('missingIndexesDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    private Map config(String database, Map extra = [:]) {
        ['grails.mongodb.url'                 : dbContainer.getReplicaSetUrl(database),
         (MongoSettings.SETTING_BUILD_INDEXES): false] + extra
    }

    private static Set<List> described(List<MissingIndex> indexes) {
        indexes.collect { [it.collection(), it.domainClass(), [*: it.key()], [*: it.options()]] } as Set
    }

    void "test every declared index a collection does not have is reported, until it is built"() {
        given:
        def datastore = new MongoDatastore(config('missingReportDb'), MissingIndexesThing)

        when: "nothing has been built"
        def missing = datastore.findMissingIndexes()

        then: "each declaration, with the options declared for it"
        described(missing) == [
                ['missingIndexesThing', MissingIndexesThing.name, [name: 1], [:]],
                ['missingIndexesThing', MissingIndexesThing.name, [code: 1], [unique: true, sparse: true]],
                ['missingIndexesThing', MissingIndexesThing.name, [description: 'text'], [:]],
                ['missingIndexesThing', MissingIndexesThing.name, [code: 1, name: -1], [:]]
        ] as Set
        missing.every { it.database() == 'missingReportDb' }

        when: "the declared indexes are built"
        datastore.buildIndex()

        then:
        datastore.findMissingIndexes() == []

        cleanup:
        datastore?.close()
    }

    void "test an index on the declared keys is present whatever its name and options"() {
        given:
        def collection = realClient.getDatabase('missingPresentDb').getCollection('missingIndexesThing')
        collection.createIndex(new Document('name', 1), new IndexOptions().name('byName').unique(true))
        collection.createIndex(new Document('code', 1))
        collection.createIndex(new Document('description', 'text'), new IndexOptions().name('searchable'))
        collection.createIndex(new Document('name', -1).append('code', 1))
        def datastore = new MongoDatastore(config('missingPresentDb'), MissingIndexesThing)

        expect: "only the compound index is missing: the one on its fields in another order does not count"
        described(datastore.findMissingIndexes()) == [
                ['missingIndexesThing', MissingIndexesThing.name, [code: 1, name: -1], [:]]
        ] as Set

        cleanup:
        datastore?.close()
    }

    void "test a text index is the declared one only on the declared fields, with the declared keys around them"() {
        given: "each collection's one text index: on another field, and without the declared prefix"
        def db = realClient.getDatabase('missingTextDb')
        db.getCollection('missingIndexesThing').createIndex(new Document('title', 'text'))
        db.getCollection('missingIndexesTextThing').createIndex(new Document('body', 'text'))
        def datastore = new MongoDatastore(config('missingTextDb'), MissingIndexesThing, MissingIndexesTextThing)

        expect:
        datastore.findMissingIndexes().findAll { 'text' in it.key().values() }
                .collect { [it.collection(), [*: it.key()]] } as Set == [
                ['missingIndexesThing', [description: 'text']],
                ['missingIndexesTextThing', [category: 1, body: 'text']]
        ] as Set

        cleanup:
        datastore?.close()
    }

    void "test a text index on the declared fields and keys is present whatever its name and weights"() {
        given:
        def db = realClient.getDatabase('missingTextPresentDb')
        db.getCollection('missingIndexesThing').createIndex(new Document('description', 'text'),
                new IndexOptions().name('searchable').weights(new Document('description', 5)))
        db.getCollection('missingIndexesTextThing').createIndex(new Document('category', 1).append('body', 'text'),
                new IndexOptions().name('byCategory'))
        def datastore = new MongoDatastore(config('missingTextPresentDb'), MissingIndexesThing, MissingIndexesTextThing)

        expect:
        !datastore.findMissingIndexes().any { 'text' in it.key().values() }

        cleanup:
        datastore?.close()
    }

    void "test a collection whose classes declare no index is not listed"() {
        given:
        List<String> listed = []
        MongoClient counting = FailingMongoClient.wrap(realClient, 'listIndexes') { Closure proceed, target ->
            listed << (target as MongoCollection).namespace.collectionName
            proceed()
        }
        def datastore = new MongoDatastore(counting, DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName'        : 'missingUndeclaringDb',
                (MongoSettings.SETTING_BUILD_INDEXES): false
        ]), MissingIndexesThing, MissingIndexesNothingDeclared)

        when:
        datastore.findMissingIndexes()

        then:
        'missingIndexesThing' in listed
        !('missingIndexesNothingDeclared' in listed)

        cleanup:
        datastore?.close()
    }

    void "test keys declared by several classes mapped to one collection are reported once"() {
        given:
        def datastore = new MongoDatastore(config('missingSharedDb'), MissingIndexesSharedA, MissingIndexesSharedB,
                MissingIndexesAnimal, MissingIndexesDog)

        when:
        def missing = datastore.findMissingIndexes()

        then: "keys both classes declare are reported once, naming one of them"
        def shared = missing.findAll { [*: it.key()] == [shared: 1] }
        shared.size() == 1
        shared[0].collection() == 'missingIndexesShared'
        shared[0].domainClass() in [MissingIndexesSharedA.name, MissingIndexesSharedB.name]

        and: "the subclass's index belongs on its root's collection"
        described(missing - shared) == [
                ['missingIndexesShared', MissingIndexesSharedB.name, [beta: 1], [:]],
                ['missingIndexesAnimal', MissingIndexesAnimal.name, [name: 1], [:]],
                ['missingIndexesAnimal', MissingIndexesDog.name, [breed: 1], [:]]
        ] as Set

        cleanup:
        datastore?.close()
    }

    void "test each named connection reports the collections of its own database"() {
        given:
        def datastore = new MongoDatastore(config('missingDefaultDb', [
                'grails.mongodb.connections': [reporting: [url: dbContainer.getReplicaSetUrl('missingReportingDb')]]
        ]), MissingIndexesConnectionThing)
        realClient.getDatabase('missingDefaultDb').getCollection('missingIndexesConnectionThing')
                .createIndex(new Document('name', 1))

        expect:
        datastore.findMissingIndexes() == []
        (datastore.getDatastoreForConnection('reporting') as MongoDatastore).findMissingIndexes()
                .collect { [it.database(), [*: it.key()]] } == [['missingReportingDb', [name: 1]]]

        cleanup:
        datastore?.close()
    }

    void "test a connection reports only what the classes mapped to it declare"() {
        given: "a class mapped to the default connection, one mapped to reporting, and one mapped to both"
        def datastore = new MongoDatastore(config('missingOwnDefaultDb', [
                'grails.mongodb.connections': [reporting: [url: dbContainer.getReplicaSetUrl('missingOwnReportingDb')]]
        ]), MissingIndexesDefaultOnlyThing, MissingIndexesReportingOnlyThing, MissingIndexesConnectionThing)

        expect:
        datastore.findMissingIndexes()*.domainClass() as Set ==
                [MissingIndexesDefaultOnlyThing.name, MissingIndexesConnectionThing.name] as Set
        (datastore.getDatastoreForConnection('reporting') as MongoDatastore).findMissingIndexes()*.domainClass() as Set ==
                [MissingIndexesReportingOnlyThing.name, MissingIndexesConnectionThing.name] as Set

        cleanup:
        datastore?.close()
    }
}

@Entity
class MissingIndexesThing {

    String name
    String code
    String description

    static mapping = {
        version false
        collection 'missingIndexesThing'
        name index: true
        code index: true, indexAttributes: [unique: true, sparse: true]
        description index: true, indexAttributes: [type: 'text']
        compoundIndex([code: 1, name: -1])
    }
}

@Entity
class MissingIndexesNothingDeclared {

    String name

    static mapping = {
        version false
        collection 'missingIndexesNothingDeclared'
    }
}

@Entity
class MissingIndexesTextThing {

    String category
    String body

    static mapping = {
        version false
        collection 'missingIndexesTextThing'
        compoundIndex([category: 1, body: 'text'])
    }
}

@Entity
class MissingIndexesSharedA {

    String shared

    static mapping = {
        version false
        collection 'missingIndexesShared'
        compoundIndex([shared: 1])
    }
}

@Entity
class MissingIndexesSharedB {

    String shared
    String beta

    static mapping = {
        version false
        collection 'missingIndexesShared'
        compoundIndex([shared: 1])
        beta index: true
    }
}

@Entity
class MissingIndexesAnimal {

    String name

    static mapping = {
        name index: true
    }
}

@Entity
class MissingIndexesDog extends MissingIndexesAnimal {

    String breed

    static mapping = {
        breed index: true
    }
}

@Entity
class MissingIndexesConnectionThing {

    String name

    static mapping = {
        version false
        collection 'missingIndexesConnectionThing'
        connection ConnectionSource.ALL
        name index: true
    }
}

@Entity
class MissingIndexesDefaultOnlyThing {

    String name

    static mapping = {
        version false
        collection 'missingIndexesDefaultOnly'
        name index: true
    }
}

@Entity
class MissingIndexesReportingOnlyThing {

    String name

    static mapping = {
        version false
        collection 'missingIndexesReportingOnly'
        connection 'reporting'
        name index: true
    }
}

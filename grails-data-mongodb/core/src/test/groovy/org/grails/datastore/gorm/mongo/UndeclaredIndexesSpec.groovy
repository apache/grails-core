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
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.UndeclaredIndex
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * {@link MongoDatastore#findUndeclaredIndexes()} reports the indexes on mapped collections whose keys no domain
 * class declares, and {@link MongoDatastore#dropUndeclaredIndexes()} drops them.
 */
class UndeclaredIndexesSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('undeclaredIndexesDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    private Map config(String database, Map extra = [:]) {
        ['grails.mongodb.url'             : dbContainer.getReplicaSetUrl(database),
         'grails.gorm.default.constraints': { '*'(nullable: true) }] + extra
    }

    private MongoDatastore datastore(String database) {
        new MongoDatastore(config(database), UndeclaredIndexesThing, UndeclaredIndexesAnimal, UndeclaredIndexesDog,
                UndeclaredIndexesSharedA, UndeclaredIndexesSharedB).tap { start() }
    }

    private Set<String> indexNames(String database, String collection) {
        realClient.getDatabase(database).getCollection(collection).listIndexes()*.getString('name') as Set
    }

    private static Set<List> described(List<UndeclaredIndex> indexes) {
        indexes.collect { [it.collection(), it.name(), [*: it.key()]] } as Set
    }

    void "test the indexes that no domain class declares are reported, and only those"() {
        given:
        def datastore = datastore('undeclaredFindDb')
        def db = realClient.getDatabase('undeclaredFindDb')

        and: "indexes created outside the mappings, one of them on a collection no domain class maps"
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('code', 1))
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('name', -1))
        db.getCollection('undeclaredIndexesAnimal').createIndex(new Document('breed', 1).append('name', 1))
        db.getCollection('undeclaredIndexesUnmapped').createIndex(new Document('anything', 1))

        and: "declared keys that carry a name and options of their own"
        db.getCollection('undeclaredIndexesThing').dropIndex('code_1_name_-1')
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('code', 1).append('name', -1),
                new IndexOptions().name('byCodeAndName'))
        db.getCollection('undeclaredIndexesThing').dropIndex('name_1')
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('name', 1), new IndexOptions().sparse(true))

        when:
        def undeclared = datastore.findUndeclaredIndexes()

        then: "a key pattern no class mapped to the collection declares, whatever the direction or order"
        described(undeclared) == [
                ['undeclaredIndexesThing', 'code_1', [code: 1]],
                ['undeclaredIndexesThing', 'name_-1', [name: -1]],
                ['undeclaredIndexesAnimal', 'breed_1_name_1', [breed: 1, name: 1]]
        ] as Set
        undeclared.every { it.database() == 'undeclaredFindDb' && it.definition().getString('name') == it.name() }

        and: "nothing was dropped"
        indexNames('undeclaredFindDb', 'undeclaredIndexesThing').containsAll(['code_1', 'name_-1'])

        cleanup:
        datastore?.close()
    }

    void "test the declarations of every class mapped to a collection count, and a text index matches its declaration"() {
        given:
        def datastore = datastore('undeclaredDeclaredDb')

        expect: "the subclass's index on its root's collection, both classes sharing a collection, the text index, and _id"
        indexNames('undeclaredDeclaredDb', 'undeclaredIndexesAnimal') == ['_id_', 'name_1', 'breed_1'] as Set
        indexNames('undeclaredDeclaredDb', 'undeclaredIndexesShared') == ['_id_', 'alpha_1', 'beta_1__id_1'] as Set
        'description_text' in indexNames('undeclaredDeclaredDb', 'undeclaredIndexesThing')
        datastore.findUndeclaredIndexes() == []

        cleanup:
        datastore?.close()
    }

    void "test a text index on other fields, or without the declared keys around them, is undeclared"() {
        given: "nothing built, so each collection's one text index is the one created here"
        def datastore = new MongoDatastore(config('undeclaredTextDb', [(MongoSettings.SETTING_BUILD_INDEXES): false]),
                UndeclaredIndexesThing, UndeclaredIndexesTextThing)
        def db = realClient.getDatabase('undeclaredTextDb')
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('title', 'text'))
        db.getCollection('undeclaredIndexesTextThing').createIndex(new Document('body', 'text'))

        expect: "neither is the text index its class declares"
        described(datastore.findUndeclaredIndexes()) == [
                ['undeclaredIndexesThing', 'title_text', [_fts: 'text', _ftsx: 1]],
                ['undeclaredIndexesTextThing', 'body_text', [_fts: 'text', _ftsx: 1]]
        ] as Set

        when: "they are replaced by indexes on the declared fields and keys, under other names and weights"
        db.getCollection('undeclaredIndexesThing').dropIndex('title_text')
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('description', 'text'),
                new IndexOptions().name('searchable').weights(new Document('description', 5)))
        db.getCollection('undeclaredIndexesTextThing').dropIndex('body_text')
        db.getCollection('undeclaredIndexesTextThing').createIndex(new Document('category', 1).append('body', 'text'),
                new IndexOptions().name('byCategory'))

        then:
        datastore.findUndeclaredIndexes() == []

        cleanup:
        datastore?.close()
    }

    void "test dropping the undeclared indexes leaves the declared ones and unmapped collections alone"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping.mongo', Level.INFO)
        def datastore = datastore('undeclaredDropDb')
        def db = realClient.getDatabase('undeclaredDropDb')
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('code', 1))
        db.getCollection('undeclaredIndexesShared').createIndex(new Document('gamma', 1))
        db.getCollection('undeclaredIndexesUnmapped').createIndex(new Document('anything', 1))

        when:
        def dropped = datastore.dropUndeclaredIndexes()

        then: "exactly the undeclared indexes are gone"
        described(dropped) == [
                ['undeclaredIndexesThing', 'code_1', [code: 1]],
                ['undeclaredIndexesShared', 'gamma_1', [gamma: 1]]
        ] as Set
        indexNames('undeclaredDropDb', 'undeclaredIndexesThing') ==
                ['_id_', 'name_1', 'description_text', 'code_1_name_-1'] as Set
        indexNames('undeclaredDropDb', 'undeclaredIndexesShared') == ['_id_', 'alpha_1', 'beta_1__id_1'] as Set
        indexNames('undeclaredDropDb', 'undeclaredIndexesUnmapped') == ['_id_', 'anything_1'] as Set

        and: "each drop is logged with the keys no class declares"
        log.events*.formattedMessage.contains('Dropped index [code_1] on collection [undeclaredIndexesThing] of ' +
                'database [undeclaredDropDb]: no domain class declares its keys {"code": 1}')

        and: "a second run has nothing left to drop"
        datastore.dropUndeclaredIndexes() == []
        datastore.findUndeclaredIndexes() == []

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test dropping a reviewed list skips an index or a collection that has gone since"() {
        given:
        def datastore = datastore('undeclaredReviewedDb')
        def db = realClient.getDatabase('undeclaredReviewedDb')
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('code', 1))
        db.getCollection('undeclaredIndexesThing').createIndex(new Document('name', -1))
        db.getCollection('undeclaredIndexesShared').createIndex(new Document('gamma', 1))
        def reviewed = datastore.findUndeclaredIndexes()

        and: "an index and a whole collection go before the reviewed list is dropped"
        db.getCollection('undeclaredIndexesThing').dropIndex('name_-1')
        db.getCollection('undeclaredIndexesShared').drop()

        when:
        def dropped = datastore.dropUndeclaredIndexes(reviewed)

        then:
        reviewed*.name() as Set == ['code_1', 'name_-1', 'gamma_1'] as Set
        dropped*.name() == ['code_1']
        !('code_1' in indexNames('undeclaredReviewedDb', 'undeclaredIndexesThing'))

        cleanup:
        datastore?.close()
    }

    void "test a reviewed index whose name a declared index has taken since is not dropped"() {
        given:
        def log = new CapturedLog('org.grails.datastore.mapping.mongo', Level.WARN)
        def datastore = datastore('undeclaredRenamedDb')
        def things = realClient.getDatabase('undeclaredRenamedDb').getCollection('undeclaredIndexesThing')
        things.createIndex(new Document('code', 1), new IndexOptions().name('maintenance_idx'))
        def reviewed = datastore.findUndeclaredIndexes()

        and: "the reviewed index goes, and the declared unique index on name is built under its name"
        things.dropIndex('maintenance_idx')
        things.dropIndex('name_1')
        things.createIndex(new Document('name', 1), new IndexOptions().name('maintenance_idx').unique(true))

        when:
        def dropped = datastore.dropUndeclaredIndexes(reviewed)

        then:
        reviewed*.name() == ['maintenance_idx']
        dropped == []
        'maintenance_idx' in indexNames('undeclaredRenamedDb', 'undeclaredIndexesThing')
        log.events*.formattedMessage.contains('Index [maintenance_idx] on collection [undeclaredIndexesThing] of ' +
                'database [undeclaredRenamedDb] was not dropped: it is on {"name": 1} now, not on the keys reviewed, ' +
                '{"code": 1}')

        cleanup:
        datastore?.close()
        log?.close()
    }

    void "test a reviewed index that a domain class declares by the time it is dropped is not dropped"() {
        given: "the list is reviewed on a release whose classes do not declare the index"
        def log = new CapturedLog('org.grails.datastore.mapping.mongo', Level.WARN)
        def earlier = datastore('undeclaredNowDeclaredDb')
        realClient.getDatabase('undeclaredNowDeclaredDb').getCollection('undeclaredIndexesThing')
                .createIndex(new Document('code', 1))
        def reviewed = earlier.findUndeclaredIndexes()
        earlier.close()

        when: "it is dropped on a release where a class mapped to the collection declares it"
        def later = new MongoDatastore(config('undeclaredNowDeclaredDb'), UndeclaredIndexesThing,
                UndeclaredIndexesCodeThing).tap { start() }
        def dropped = later.dropUndeclaredIndexes(reviewed)

        then:
        reviewed*.name() == ['code_1']
        dropped == []
        'code_1' in indexNames('undeclaredNowDeclaredDb', 'undeclaredIndexesThing')
        log.events*.formattedMessage.contains('Index [code_1] on collection [undeclaredIndexesThing] of database ' +
                '[undeclaredNowDeclaredDb] was not dropped: a domain class declares its keys {"code": 1}')

        cleanup:
        later?.close()
        log?.close()
    }

    void "test a listed index on a collection the connection does not map is not dropped"() {
        given:
        def datastore = datastore('undeclaredNotMappedDb')
        def unmapped = realClient.getDatabase('undeclaredNotMappedDb').getCollection('undeclaredIndexesUnmapped')
        unmapped.createIndex(new Document('anything', 1))
        def listed = unmapped.listIndexes().find { it.getString('name') == 'anything_1' }

        when:
        def dropped = datastore.dropUndeclaredIndexes([new UndeclaredIndex('undeclaredNotMappedDb',
                'undeclaredIndexesUnmapped', 'anything_1', listed.get('key', Document), listed)])

        then:
        dropped == []
        'anything_1' in indexNames('undeclaredNotMappedDb', 'undeclaredIndexesUnmapped')

        cleanup:
        datastore?.close()
    }

    void "test each named connection reports the collections of its own database"() {
        given:
        def datastore = new MongoDatastore(config('undeclaredDefaultDb', [
                'grails.mongodb.connections': [reporting: [url: dbContainer.getReplicaSetUrl('undeclaredReportingDb')]]
        ]), UndeclaredIndexesConnectionThing)
        realClient.getDatabase('undeclaredReportingDb').getCollection('undeclaredIndexesConnectionThing')
                .createIndex(new Document('code', 1))

        expect:
        datastore.findUndeclaredIndexes() == []
        (datastore.getDatastoreForConnection('reporting') as MongoDatastore).findUndeclaredIndexes()*.name() == ['code_1']

        cleanup:
        datastore?.close()
    }

    void "test a class registered after startup is indexed on the collection and database its mapping names"() {
        given:
        def datastore = datastore('undeclaredLateNamedDb')

        when:
        datastore.mappingContext.addPersistentEntity(UndeclaredIndexesLateDefaultThing)
        datastore.mappingContext.addPersistentEntity(UndeclaredIndexesLateElsewhereThing)

        then: "on its mapped collection, and not on one named after the class"
        'name_1' in indexNames('undeclaredLateNamedDb', 'undeclaredIndexesLateDefault')
        !('undeclaredIndexesLateDefaultThing' in realClient.getDatabase('undeclaredLateNamedDb').listCollectionNames())

        and: "in its mapped database, and not in the default one"
        'name_1' in indexNames('undeclaredLateElsewhereDb', 'undeclaredIndexesLateElsewhere')
        !('undeclaredIndexesLateElsewhere' in realClient.getDatabase('undeclaredLateNamedDb').listCollectionNames())

        cleanup:
        datastore?.close()
    }

    void "test a class registered after startup is not indexed by a connection it is not mapped to"() {
        given:
        def datastore = new MongoDatastore(config('undeclaredLateDefaultDb', [
                'grails.mongodb.connections': [reporting: [url: dbContainer.getReplicaSetUrl('undeclaredLateReportingDb')]]
        ]), UndeclaredIndexesDefaultOnlyThing).tap { start() }

        when: "a class mapped only to reporting is registered"
        datastore.mappingContext.addPersistentEntity(UndeclaredIndexesLateReportingThing)

        then: "the default connection creates nothing for it, under its mapped name or the class's"
        !realClient.getDatabase('undeclaredLateDefaultDb').listCollectionNames().any { it.startsWith('undeclaredIndexesLateReporting') }

        cleanup:
        datastore?.close()
    }

    void "test a connection covers only the collections of the classes mapped to it"() {
        given: "a class mapped to the default connection, one mapped to reporting, and one mapped to both"
        def datastore = new MongoDatastore(config('undeclaredOwnDefaultDb', [
                'grails.mongodb.connections': [reporting: [url: dbContainer.getReplicaSetUrl('undeclaredOwnReportingDb')]]
        ]), UndeclaredIndexesDefaultOnlyThing, UndeclaredIndexesReportingOnlyThing, UndeclaredIndexesConnectionThing)
                .tap { start() }
        def reporting = datastore.getDatastoreForConnection('reporting') as MongoDatastore

        and: "an undeclared index on a collection named after each of them, in both databases"
        ['undeclaredOwnDefaultDb', 'undeclaredOwnReportingDb'].each { database ->
            ['undeclaredIndexesDefaultOnly', 'undeclaredIndexesReportingOnly', 'undeclaredIndexesConnectionThing'].each {
                realClient.getDatabase(database).getCollection(it).createIndex(new Document('code', 1))
            }
        }

        expect: "the build put each declared index only on the connections its class is mapped to"
        'name_1' in indexNames('undeclaredOwnDefaultDb', 'undeclaredIndexesDefaultOnly')
        !('name_1' in indexNames('undeclaredOwnReportingDb', 'undeclaredIndexesDefaultOnly'))
        'name_1' in indexNames('undeclaredOwnReportingDb', 'undeclaredIndexesReportingOnly')
        !('name_1' in indexNames('undeclaredOwnDefaultDb', 'undeclaredIndexesReportingOnly'))

        and: "each connection reports the collections of its own classes"
        datastore.findUndeclaredIndexes()*.collection() as Set ==
                ['undeclaredIndexesDefaultOnly', 'undeclaredIndexesConnectionThing'] as Set
        reporting.findUndeclaredIndexes()*.collection() as Set ==
                ['undeclaredIndexesReportingOnly', 'undeclaredIndexesConnectionThing'] as Set

        when:
        def dropped = reporting.dropUndeclaredIndexes()

        then: "a collection named after a class mapped only to default is left alone on reporting"
        dropped*.collection() as Set == ['undeclaredIndexesReportingOnly', 'undeclaredIndexesConnectionThing'] as Set
        'code_1' in indexNames('undeclaredOwnReportingDb', 'undeclaredIndexesDefaultOnly')

        and: "the default connection's database is untouched"
        ['undeclaredIndexesDefaultOnly', 'undeclaredIndexesReportingOnly', 'undeclaredIndexesConnectionThing'].every {
            'code_1' in indexNames('undeclaredOwnDefaultDb', it)
        }

        cleanup:
        datastore?.close()
    }
}

@Entity
class UndeclaredIndexesThing {

    String name
    String code
    String description

    static mapping = {
        name index: true
        description index: true, indexAttributes: [type: 'text']
        compoundIndex([code: 1, name: -1])
    }

    static constraints = {
        name blank: false
    }
}

@Entity
class UndeclaredIndexesTextThing {

    String category
    String body

    static mapping = {
        collection 'undeclaredIndexesTextThing'
        compoundIndex([category: 1, body: 'text'])
    }
}

@Entity
class UndeclaredIndexesCodeThing {

    String code

    static mapping = {
        collection 'undeclaredIndexesThing'
        code index: true
    }
}

@Entity
class UndeclaredIndexesAnimal {

    String name

    static mapping = {
        name index: true
    }
}

@Entity
class UndeclaredIndexesDog extends UndeclaredIndexesAnimal {

    String breed

    static mapping = {
        breed index: true
    }
}

@Entity
class UndeclaredIndexesSharedA {

    String alpha

    static mapping = {
        collection 'undeclaredIndexesShared'
        alpha index: true
    }
}

@Entity
class UndeclaredIndexesSharedB {

    String beta

    static mapping = {
        collection 'undeclaredIndexesShared'
        compoundIndex([beta: 1, _id: 1])
    }
}

@Entity
class UndeclaredIndexesConnectionThing {

    String name

    static mapping = {
        collection 'undeclaredIndexesConnectionThing'
        connection ConnectionSource.ALL
        name index: true
    }
}

@Entity
class UndeclaredIndexesDefaultOnlyThing {

    String name

    static mapping = {
        collection 'undeclaredIndexesDefaultOnly'
        name index: true
    }
}

@Entity
class UndeclaredIndexesLateDefaultThing {

    String name

    static mapping = {
        collection 'undeclaredIndexesLateDefault'
        name index: true
    }
}

@Entity
class UndeclaredIndexesLateElsewhereThing {

    String name

    static mapping = {
        collection 'undeclaredIndexesLateElsewhere'
        database 'undeclaredLateElsewhereDb'
        name index: true
    }
}

@Entity
class UndeclaredIndexesLateReportingThing {

    String name

    static mapping = {
        collection 'undeclaredIndexesLateReporting'
        connection 'reporting'
        name index: true
    }
}

@Entity
class UndeclaredIndexesReportingOnlyThing {

    String name

    static mapping = {
        collection 'undeclaredIndexesReportingOnly'
        connection 'reporting'
        name index: true
    }
}

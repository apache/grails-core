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
package org.apache.grails.data.mongodb.core.querytail

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import grails.gorm.annotation.Entity
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.types.ObjectId
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

class MongoCommandRedactorSpec extends Specification {

    static final String SECRET = 'example-secret'

    static final String NUMERIC_SECRET = '9876543210'

    @Shared
    @AutoCleanup
    MongoDatastore datastore = new MongoDatastore(QueryTailRedactedBook, QueryTailRedactedAuthor, QueryTailRedactedPaperback)

    @Shared
    MongoMappedFields fields = MongoMappedFields.of(datastore)

    void "every datastore built on one mapping is told its fields by the one instance, and another mapping by its own"() {
        given:
        MongoDatastore sameMapping = new MongoDatastore(DatastoreUtils.createPropertyResolver([:]), datastore.mappingContext,
                new DefaultApplicationEventPublisher())
        MongoDatastore otherMapping = new MongoDatastore(QueryTailRedactedAuthor)

        expect:
        MongoMappedFields.of(datastore).is(fields)
        MongoMappedFields.of(sameMapping).is(fields)
        !MongoMappedFields.of(otherMapping).is(fields)
        MongoMappedFields.of(otherMapping).is(MongoMappedFields.of(otherMapping))

        cleanup:
        sameMapping?.close()
        otherMapping?.close()
    }

    void "the fields of a mapping go on telling the keys of the next datastore built on it once the one that asked for them closes"() {
        given:
        MongoDatastore first = new MongoDatastore(QueryTailRedactedAuthor)
        MongoMappedFields shared = MongoMappedFields.of(first)
        first.close()

        when:
        MongoDatastore next = new MongoDatastore(DatastoreUtils.createPropertyResolver([:]), first.mappingContext,
                new DefaultApplicationEventPublisher())

        then:
        MongoMappedFields.of(next).is(shared)
        shared.isField('queryTailRedactedAuthor', ['name'])
        !shared.isField('queryTailRedactedAuthor', ['secret-key'])

        cleanup:
        next?.close()
    }

    @Timeout(60)
    void "a key asked for while the entities of the mapping are gathered waits for them, rather than read the none gathered before"() {
        given: 'a mapping whose entities take a while to be read, as the first command of a busy application finds them'
        QueryTailGatedMappingContext mapping = new QueryTailGatedMappingContext(QueryTailRedactedBook, QueryTailRedactedAuthor)
        MongoDatastore gatedDatastore = new MongoDatastore(DatastoreUtils.createPropertyResolver([:]), mapping,
                new DefaultApplicationEventPublisher())
        MongoMappedFields gatedFields = MongoMappedFields.of(gatedDatastore)
        BlockingQueue<Boolean> gathering = new ArrayBlockingQueue<>(1)
        BlockingQueue<Boolean> waiting = new ArrayBlockingQueue<>(1)

        when: 'one command gathers them, and another asks for a key meanwhile'
        Thread gatherer = Thread.startDaemon('query-tail-gathering') {
            mapping.gated = Thread.currentThread()
            gathering.put(gatedFields.isField('books', ['ttl']))
        }
        assert mapping.entered.await(10, TimeUnit.SECONDS)
        Thread asker = Thread.startDaemon('query-tail-asking') { waiting.put(gatedFields.isField('books', ['ttl'])) }
        new PollingConditions(timeout: 10).eventually {
            assert asker.state == Thread.State.BLOCKED || !waiting.isEmpty()
        }
        boolean answeredBeforeGathered = !waiting.isEmpty()
        mapping.release.countDown()

        then: 'both know the field'
        !answeredBeforeGathered
        gathering.poll(10, TimeUnit.SECONDS)
        waiting.poll(10, TimeUnit.SECONDS)

        cleanup:
        mapping?.release?.countDown()
        gatherer?.join(10000)
        asker?.join(10000)
        gatedDatastore?.close()
    }

    void "an entity is in the collection its mapping names, as every datastore built on the mapping stores it: #entity.simpleName"() {
        given:
        PersistentEntity persistentEntity = datastore.mappingContext.getPersistentEntity(entity.name)

        expect:
        MongoDatastore.getMappedCollectionName(persistentEntity) == collection
        datastore.getCollectionName(persistentEntity) == collection

        where:
        entity                     || collection
        QueryTailRedactedBook      || 'books'
        QueryTailRedactedPaperback || 'books'
        QueryTailRedactedAuthor    || 'queryTailRedactedAuthor'
    }

    void "a key is a field by the name the mapping stores it under, wherever in the collection's entities it is declared: #path"() {
        expect:
        fields.isField(collection, path) == known

        where:
        collection                | path                        || known
        'books'                   | ['ttl']                     || true
        'books'                   | ['title']                   || false
        'books'                   | ['binding']                 || true
        'books'                   | ['address', 'city']         || true
        'books'                   | ['stops', '0', 'city']      || true
        'books'                   | ['address', 'notes', 'k']   || false
        'books'                   | ['author', 'name']          || false
        'books'                   | ['version']                 || true
        'books'                   | ['_id']                     || true
        'books'                   | ['_class']                  || true
        'books'                   | ['address', '_id']          || true
        'books'                   | ['stops', '0', '_class']    || true
        'books'                   | ['ttl', '_id']              || false
        'books'                   | ['attrs', '_id']            || false
        'books'                   | ['_id', 'city']             || false
        'books'                   | ['address', '_id', 'city']  || false
        'books'                   | ['0']                       || false
        'queryTailRedactedAuthor' | ['name']                    || true
        'queryTailRedactedAuthor' | ['ttl']                     || false
        'other'                   | ['name']                    || false
    }

    @Unroll
    void "a command is recorded as its name, its namespace and its entries without their values: #redacted"() {
        expect:
        redact(command) == redacted

        where:
        command                                                                                                   || redacted
        '{"find": "books", "filter": {"ttl": "x", "pages": {"$gt": 1}, "author": {"$oid": "5f1d7f3e8b1c2a3d4e5f6a7b"}}, "sort": {"ttl": 1}, "projection": {"ttl": 1}, "limit": 10, "$db": "shop", "lsid": {"id": {"$binary": {"base64": "AAAAAAAAAAAAAAAAAAAAAA==", "subType": "04"}}}, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1, "i": 1}}, "signature": {"hash": {"$binary": {"base64": "AA==", "subType": "00"}}, "keyId": 1}}, "$readPreference": {"mode": "primary"}}' || 'find shop.books {"filter": {"ttl": ?, "pages": {"$gt": ?}, "author": ?}, "sort": {"ttl": ?}, "projection": {"ttl": ?}, "limit": ?}'
        '{"insert": "books", "ordered": true, "$db": "shop", "documents": [{"_id": {"$oid": "5f1d7f3e8b1c2a3d4e5f6a7b"}, "ttl": "x", "pages": 1, "version": 0, "_class": "Book"}], "txnNumber": 3, "autocommit": false, "startTransaction": true}' || 'insert shop.books {"ordered": ?, "documents": [{"_id": ?, "ttl": ?, "pages": ?, "version": ?, "_class": ?}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"ttl": "y"}, "$inc": {"version": 1}}, "multi": false, "upsert": false}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"ttl": ?}, "$inc": {"version": ?}}, "multi": ?, "upsert": ?}]}'
        '{"delete": "books", "deletes": [{"q": {"_id": {"$in": [1, 2]}}, "limit": 0}], "$db": "shop"}'          || 'delete shop.books {"deletes": [{"q": {"_id": {"$in": [?]}}, "limit": ?}]}'
        '{"aggregate": "books", "pipeline": [{"$match": {"ttl": "x", "$or": [{"pages": {"$gt": 1}}, {"pages": null}]}}, {"$group": {"_id": "$author", "total": {"$sum": "$pages"}}}, {"$lookup": {"from": "queryTailRedactedAuthor", "localField": "author", "foreignField": "_id", "as": "a"}}], "cursor": {"batchSize": 101}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$match": {"ttl": ?, "$or": [{"pages": {"$gt": ?}}, {"pages": ?}]}}, {"$group": {"_id": ?, "total": {"$sum": ?}}}, {"$lookup": {"from": ?, "localField": ?, "foreignField": ?, "as": ?}}], "cursor": {"batchSize": ?}}'
        '{"getMore": {"$numberLong": "123"}, "collection": "books", "batchSize": 100, "$db": "shop"}'           || 'getMore shop.books {"getMore": ?, "batchSize": ?}'
        '{"killCursors": "books", "cursors": [{"$numberLong": "1"}, {"$numberLong": "2"}], "$db": "shop"}'      || 'killCursors shop.books {"cursors": [?]}'
        '{"createIndexes": "books", "indexes": [{"key": {"ttl": 1, "address.city": -1}, "name": "ttl_1", "unique": true}], "$db": "shop"}' || 'createIndexes shop.books {"indexes": [{"key": {"ttl": ?, "address.city": ?}, "name": ?, "unique": ?}]}'
        '{"count": "books", "query": {"pages": {"$exists": true}}, "$db": "shop"}'                                || 'count shop.books {"query": {"pages": {"$exists": ?}}}'
        '{"findAndModify": "books", "query": {"_id": 1}, "update": {"$set": {"ttl": "z"}}, "new": true, "$db": "shop"}' || 'findAndModify shop.books {"query": {"_id": ?}, "update": {"$set": {"ttl": ?}}, "new": ?}'
        '{"aggregate": 1, "pipeline": [{"$currentOp": {}}], "cursor": {}, "$db": "admin"}'                        || 'aggregate admin {"aggregate": ?, "pipeline": [{"$currentOp": {}}], "cursor": {}}'
        '{"ping": 1, "$db": "admin"}'                                                                             || 'ping admin {"ping": ?}'
        '{"commitTransaction": 1, "txnNumber": 1, "autocommit": false, "$db": "admin"}'                           || 'commitTransaction admin {"commitTransaction": ?}'
        '{"endSessions": [{"id": 1}]}'                                                                            || 'endSessions {"endSessions": [{"id": ?}]}'
    }

    @Unroll
    void "an array is recorded with each of its different elements once, so that a batch or a list of values is recorded the same whatever its size, one or many: #command"() {
        expect:
        redact(command) == redacted

        where:
        command                                                                                                     || redacted
        '{"insert": "books", "documents": [{"ttl": "a"}], "$db": "shop"}'                                           || 'insert shop.books {"documents": [{"ttl": ?}]}'
        '{"insert": "books", "documents": [{"ttl": "a"}, {"ttl": "b"}], "$db": "shop"}'                             || 'insert shop.books {"documents": [{"ttl": ?}]}'
        '{"insert": "books", "documents": [{"ttl": "a"}, {"ttl": "b"}, {"ttl": "c"}, {"ttl": "d"}], "$db": "shop"}' || 'insert shop.books {"documents": [{"ttl": ?}]}'
        '{"insert": "books", "documents": [{"ttl": "a"}, {"ttl": "b", "pages": 2}, {"ttl": "c"}], "$db": "shop"}'   || 'insert shop.books {"documents": [{"ttl": ?}, {"ttl": ?, "pages": ?}]}'
        '{"find": "books", "filter": {"pages": {"$in": [1]}}, "$db": "shop"}'                                       || 'find shop.books {"filter": {"pages": {"$in": [?]}}}'
        '{"find": "books", "filter": {"pages": {"$in": [1, 2, 3]}}, "$db": "shop"}'                                 || 'find shop.books {"filter": {"pages": {"$in": [?]}}}'
        '{"find": "books", "filter": {"pages": {"$in": [1, 2, 3, 4, 5, 6, 7]}}, "$db": "shop"}'                     || 'find shop.books {"filter": {"pages": {"$in": [?]}}}'
        '{"find": "books", "filter": {"tags": {"$all": [["a", "b"], ["c", "d", "e"]]}}, "$db": "shop"}'             || 'find shop.books {"filter": {"tags": {"$all": [[?]]}}}'
        '{"find": "books", "filter": {"pages": {"$in": []}}, "$db": "shop"}'                                        || 'find shop.books {"filter": {"pages": {"$in": []}}}'
        '{"find": "books", "filter": {"attrs": {"$in": [1, {"k": "a"}, "x", {"k": "b"}, [2], null, [3]]}}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {"$in": [?, {?: ?}, [?]]}}}'
        '{"find": "books", "filter": {"attrs": {"$in": [{"k": "a"}, 1, [2], "x"]}}, "$db": "shop"}'                 || 'find shop.books {"filter": {"attrs": {"$in": [{?: ?}, ?, [?]]}}}'
    }

    void "a list of a hundred thousand values is recorded as a list of values"() {
        given:
        String values = (1..100000).join(', ')

        expect:
        redact("""{"find": "books", "filter": {"pages": {"\$in": [${values}]}}, "\$db": "shop"}""") ==
                'find shop.books {"filter": {"pages": {"$in": [?]}}}'
    }

    void "only a command that runs against a collection names one, and the first value of any other is recorded as a value: #redacted"() {
        expect:
        redact(command) == redacted
        !redacted.contains(SECRET)

        where:
        command                                                                                                   || redacted
        """{"dropUser": "${SECRET}", "writeConcern": {"w": "majority"}, "\$db": "admin"}"""                    || 'dropUser admin {"dropUser": ?, "writeConcern": {"w": ?}}'
        """{"usersInfo": "${SECRET}", "\$db": "shop"}"""                                                       || 'usersInfo shop {"usersInfo": ?}'
        """{"grantRolesToUser": "${SECRET}", "roles": ["read"], "\$db": "admin"}"""                            || 'grantRolesToUser admin {"grantRolesToUser": ?, "roles": [?]}'
        """{"renameCollection": "shop.${SECRET}", "to": "shop.archive", "\$db": "admin"}"""                    || 'renameCollection admin {"renameCollection": ?, "to": ?}'
        """{"enableSharding": "${SECRET}", "\$db": "admin"}"""                                                 || 'enableSharding admin {"enableSharding": ?}'
        """{"futureCommand": 1, "collection": "${SECRET}", "\$db": "shop"}"""                                  || 'futureCommand shop {"futureCommand": ?, "collection": ?}'
        '{"findandmodify": "books", "query": {"_id": 1}, "remove": true, "$db": "shop"}'                          || 'findandmodify shop.books {"query": {"_id": ?}, "remove": ?}'
        '{"distinct": "books", "key": "ttl", "query": {}, "$db": "shop"}'                                         || 'distinct shop.books {"key": ?, "query": {}}'
        '{"listIndexes": "books", "cursor": {}, "$db": "shop"}'                                                   || 'listIndexes shop.books {"cursor": {}}'
        '{"create": "books", "capped": true, "size": 4096, "$db": "shop"}'                                        || 'create shop.books {"capped": ?, "size": ?}'
        '{"drop": "books", "$db": "shop"}'                                                                        || 'drop shop.books'
    }

    @Unroll
    void "a key is kept where the mapping knows it as a field, and recorded as ? where it may be data: #redacted"() {
        expect:
        redact(command) == redacted

        where:
        command                                                                                                           || redacted
        '{"find": "books", "filter": {"address.city": "Paris", "attrs.k": "v", "attrs.k.l": 1, "address.notes.k": 1, "stops.0.city": "Rome", "stops.0.notes.k": 1, "author.name": "x"}, "$db": "shop"}' || 'find shop.books {"filter": {"address.city": ?, "attrs.?": ?, "attrs.?.?": ?, "address.notes.?": ?, "stops.0.city": ?, "stops.0.notes.?": ?, "author.?": ?}}'
        '{"insert": "books", "documents": [{"ttl": "x", "address": {"city": "Paris", "notes": {"secret-key": "v"}}, "stops": [{"city": "Rome", "notes": {"secret-key": 1}}], "attrs": {"secret-key": "v"}, "unknown": {"secret-key": 1}}], "$db": "shop"}' || 'insert shop.books {"documents": [{"ttl": ?, "address": {"city": ?, "notes": {?: ?}}, "stops": [{"city": ?, "notes": {?: ?}}], "attrs": {?: ?}, ?: {?: ?}}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"attrs": {"secret-key": "v"}, "address.notes.secret-key": "v", "address": {"city": "x", "notes": {"secret-key": 1}}}, "$unset": {"attrs.secret-key": 1}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"attrs": {?: ?}, "address.notes.?": ?, "address": {"city": ?, "notes": {?: ?}}}, "$unset": {"attrs.?": ?}}}]}'
        '{"find": "books", "filter": {"attrs": {"$elemMatch": {"secret-key": 1}}, "stops": {"$elemMatch": {"city": "x", "notes": {"secret-key": 1}}}}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {"$elemMatch": {?: ?}}, "stops": {"$elemMatch": {"city": ?, "notes": {?: ?}}}}}'
        '{"find": "books", "filter": {"binding": "x", "address.city": "y"}, "$db": "shop"}'                               || 'find shop.books {"filter": {"binding": ?, "address.city": ?}}'
        '{"find": "other", "filter": {"a": 1, "b": {"secret-key": 2}, "d.secret-key": 3, "$or": [{"e": {"secret-key": 1}}]}, "$db": "shop"}' || 'find shop.other {"filter": {"a": ?, "b": {?: ?}, "d.?": ?, "$or": [{"e": {?: ?}}]}}'
        '{"aggregate": "books", "pipeline": [{"$facet": {"byCity": [{"$match": {"address.city": "x"}}]}}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$facet": {"byCity": [{"$match": {?: ?}}]}}], "cursor": {}}'
        '{"bulkWrite": 1, "ops": [{"insert": 0, "document": {"ttl": "x", "attrs": {"secret-key": 1}}}, {"update": 0, "filter": {"_id": 1}, "updateMods": {"$set": {"attrs.secret-key": 1}}, "multi": false}], "nsInfo": [{"ns": "shop.books"}], "$db": "admin"}' || 'bulkWrite admin {"bulkWrite": ?, "ops": [{"insert": ?, "document": {?: ?, ?: {?: ?}}}, {"update": ?, "filter": {"_id": ?}, "updateMods": {"$set": {?: ?}}, "multi": ?}], "nsInfo": [{"ns": ?}]}'
        '{"find": "books", "filter": {"attrs": {"9876543210": "v"}, "attrs.9876543210": "v", "stops.0.city": "x", "stops.0.notes.9876543210": 1, "tags.0": "x", "tags": {"0": "x"}, "9876543210": 1}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {?: ?}, "attrs.?": ?, "stops.0.city": ?, "stops.0.notes.?": ?, "tags.0": ?, "tags": {?: ?}, ?: ?}}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"attrs.9876543210": "v", "stops.1.city": "x"}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"attrs.?": ?, "stops.1.city": ?}}}]}'
        '{"insert": "books", "documents": [{"attributes": [{"9876543210": "v", "k": "w"}], "stops": [{"city": "x", "notes": {"1": 2}}], "tags": ["a"]}], "$db": "shop"}' || 'insert shop.books {"documents": [{"attributes": [{?: ?, ?: ?}], "stops": [{"city": ?, "notes": {?: ?}}], "tags": [?]}]}'
        '{"find": "books", "filter": {"attributes.0.9876543210": "v", "attributes": {"$elemMatch": {"9876543210": "v"}}, "stops.0": {"city": "x"}, "stops": {"$elemMatch": {"0": 1, "city": "x"}}}, "$db": "shop"}' || 'find shop.books {"filter": {"attributes.0.?": ?, "attributes": {"$elemMatch": {?: ?}}, "stops.0": {"city": ?}, "stops": {"$elemMatch": {?: ?, "city": ?}}}}'
        '{"insert": "books", "documents": [{"ttl": "x", "$top-secret": 1, "attrs": {"$example-secret": "v"}, "address": {"city": "x", "$k": 1}}], "$db": "shop"}' || 'insert shop.books {"documents": [{"ttl": ?, ?: ?, "attrs": {?: ?}, "address": {"city": ?, ?: ?}}]}'
        '{"update": "books", "updates": [{"q": {"pages": {"$gt": 1}}, "u": {"$set": {"attrs": {"$example-secret": "v"}, "address": {"$k": 1}}, "$inc": {"version": 1}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"pages": {"$gt": ?}}, "u": {"$set": {"attrs": {?: ?}, "address": {?: ?}}, "$inc": {"version": ?}}}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"ttl": "x", "attrs": {"$example-secret": "v"}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"ttl": ?, "attrs": {?: ?}}}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": [{"$set": {"ttl": {"$concat": ["$ttl", "x"]}}}]}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": [{"$set": {"ttl": {"$concat": [?]}}}]}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$push": {"stops": {"$each": [{"city": "x", "$k": 1}], "$slice": 3, "$sort": {"city": 1}}}, "$addToSet": {"stops": {"city": "y", "$k": 2}}, "$pull": {"stops": {"city": {"$in": ["z"]}}}, "$currentDate": {"pages": {"$type": "date"}}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$push": {"stops": {"$each": [{"city": ?, ?: ?}], "$slice": ?, "$sort": {"city": ?}}}, "$addToSet": {"stops": {"city": ?, ?: ?}}, "$pull": {"stops": {"city": {"$in": [?]}}}, "$currentDate": {"pages": {"$type": ?}}}}]}'
        '{"findAndModify": "books", "query": {"_id": 1}, "update": {"$setOnInsert": {"attrs": {"$example-secret": "v"}}}, "$db": "shop"}' || 'findAndModify shop.books {"query": {"_id": ?}, "update": {"$setOnInsert": {"attrs": {?: ?}}}}'
        '{"aggregate": "books", "pipeline": [{"$project": {"ttl": {"$literal": {"$example-secret": 1}}}}, {"$documents": [{"$k": 1}]}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$project": {"ttl": {"$literal": {?: ?}}}}, {"$documents": [{?: ?}]}], "cursor": {}}'
        '{"aggregate": "books", "pipeline": [{"$project": {"ttl": {"$const": {"$example-secret": 1, "k": [{"$k": 1}]}}, "tags": {"$const": [{"$k": 1}]}}}, {"$match": {"$expr": {"$eq": ["$ttl", {"$const": {"$k": 1}}]}}}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$project": {"ttl": {"$const": {?: ?, ?: [{?: ?}]}}, "tags": {"$const": [{?: ?}]}}}, {"$match": {"$expr": {"$eq": [?, {"$const": {?: ?}}]}}}], "cursor": {}}'
        '{"find": "books", "filter": {"author": {"$id": {"$k": 1}, "$ref": "x"}, "$ref": "x", "$id": {"a": {"$k": 1}}, "stops": {"$elemMatch": {"$db": "d", "$id": {"$k": 1}, "city": "x"}}, "$and": [{"$id": {"$k": 1}}]}, "$db": "shop"}' || 'find shop.books {"filter": {"author": {?: {?: ?}, ?: ?}, ?: ?, ?: {?: {?: ?}}, "stops": {"$elemMatch": {?: ?, ?: {?: ?}, "city": ?}}, "$and": [{?: {?: ?}}]}}'
        '{"bulkWrite": 1, "ops": [{"insert": 0, "document": {"$k": 1, "attrs": {"$example-secret": 1}}}], "$db": "admin"}' || 'bulkWrite admin {"bulkWrite": ?, "ops": [{"insert": ?, "document": {?: ?, ?: {?: ?}}}]}'
        '{"find": "books", "filter": {"attrs": {"$eq": {"$example-secret": "v"}}, "address": {"$ne": {"city": "x", "$k": 1}}, "stops": {"$in": [{"city": "x", "$k": 1}]}, "tags": {"$all": [{"$k": 1}], "$nin": [{"$k": 2}]}, "pages": {"$gt": {"$k": 1}, "$gte": {"$k": 1}, "$lt": {"$k": 1}, "$lte": {"$k": 2}}}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {"$eq": {?: ?}}, "address": {"$ne": {"city": ?, ?: ?}}, "stops": {"$in": [{"city": ?, ?: ?}]}, "tags": {"$all": [{?: ?}], "$nin": [{?: ?}]}, "pages": {"$gt": {?: ?}, "$gte": {?: ?}, "$lt": {?: ?}, "$lte": {?: ?}}}}'
        '{"find": "books", "filter": {"attrs": {"$not": {"$eq": {"$k": 1}}}, "attributes": {"$elemMatch": {"$eq": {"$k": 1}}}, "$or": [{"attrs": {"$gte": {"$k": 1}}}], "$jsonSchema": {"properties": {"attrs": {"enum": [{"$k": 1}]}}}}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {"$not": {"$eq": {?: ?}}}, "attributes": {"$elemMatch": {"$eq": {?: ?}}}, "$or": [{"attrs": {"$gte": {?: ?}}}], "$jsonSchema": {?: {?: {?: [{?: ?}]}}}}}'
        '{"aggregate": "books", "pipeline": [{"$match": {"attrs": {"$eq": {"$k": 1}}, "$expr": {"$gt": [{"$size": "$tags"}, 1]}}}, {"$project": {"ttl": {"$eq": [{"$toLower": "$ttl"}, "x"]}, "tags": {"$in": ["x", "$tags"]}}}, {"$group": {"_id": {"$cond": [{"$lt": [{"$strLenCP": "$ttl"}, 3]}, "s", "l"]}, "n": {"$sum": {"$literal": {"$k": 1}}}}}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$match": {"attrs": {"$eq": {?: ?}}, "$expr": {"$gt": [{"$size": ?}, ?]}}}, {"$project": {"ttl": {"$eq": [{"$toLower": ?}, ?]}, "tags": {"$in": [?]}}}, {"$group": {"_id": {"$cond": [{"$lt": [{"$strLenCP": ?}, ?]}, ?]}, "n": {"$sum": {"$literal": {?: ?}}}}}], "cursor": {}}'
        '{"update": "books", "updates": [{"q": {"attrs": {"$in": [{"$k": 1}]}}, "u": {"$pull": {"attributes": {"$eq": {"$k": 1}}}}}, {"q": {"_id": 1}, "u": [{"$set": {"ttl": {"$cond": [{"$eq": [{"$type": "$ttl"}, "string"]}, "$$v", "x"]}}}], "c": {"v": {"$k": 1}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"attrs": {"$in": [{?: ?}]}}, "u": {"$pull": {"attributes": {"$eq": {?: ?}}}}}, {"q": {"_id": ?}, "u": [{"$set": {"ttl": {"$cond": [{"$eq": [{"$type": ?}, ?]}, ?]}}}], "c": {?: {?: ?}}}]}'
        '{"find": "books", "filter": {"ttl": "x", "$comment": {"secret-key": 1, "$k": 1}, "$expr": {"$eq": ["$ttl", "$$v"]}}, "let": {"v": {"$literal": {"$k": 1}}, "w": {"secret-key": 1}}, "comment": {"secret-key": 1, "$k": 1}, "$db": "shop"}' || 'find shop.books {"filter": {"ttl": ?, "$comment": {?: ?, ?: ?}, "$expr": {"$eq": [?]}}, "let": {"v": {"$literal": {?: ?}}, "w": {?: ?}}, "comment": {?: ?, ?: ?}}'
        '{"collMod": "books", "validator": {"attrs.secret-key": "v", "attrs": {"$eq": {"$k": 1}}}, "$db": "shop"}' || 'collMod shop.books {"validator": {"attrs.?": ?, "attrs": {"$eq": {?: ?}}}}'
        '{"createIndexes": "books", "indexes": [{"key": {"ttl": "text"}, "weights": {"ttl": 2, "attrs.secret-key": 1}, "name": "t"}], "$db": "shop"}' || 'createIndexes shop.books {"indexes": [{"key": {"ttl": ?}, "weights": {"ttl": ?, "attrs.?": ?}, "name": ?}]}'
        '{"insert": "books", "documents": [{"ttl": "x", "alice@example.com": 1}], "$db": "shop"}'                     || 'insert shop.books {"documents": [{"ttl": ?, ?: ?}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"ttl": "x", "alice@example.com": 1}, "$unset": {"alice@example.com": ""}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"ttl": ?, ?: ?}, "$unset": {?: ?}}}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"ttl": "x", "alice@example.com": 1}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"ttl": ?, ?: ?}}]}'
        '{"aggregate": 1, "pipeline": [{"$documents": [{"alice@example.com": 1}]}], "cursor": {}, "$db": "shop"}'     || 'aggregate shop {"aggregate": ?, "pipeline": [{"$documents": [{?: ?}]}], "cursor": {}}'
        '{"find": "books", "filter": {"$expr": {"$eq": ["$ttl", {"$literal": {"secretKey": 1, "ttl": 1}}]}}, "$db": "shop"}' || 'find shop.books {"filter": {"$expr": {"$eq": [?, {"$literal": {?: ?, "ttl": ?}}]}}}'
        '{"find": "books", "filter": {"$expr": {"$eq": ["$attrs", {"secretKey": 1, "ttl": "$ttl"}]}}, "$db": "shop"}' || 'find shop.books {"filter": {"$expr": {"$eq": [?, {?: ?, "ttl": ?}]}}}'
        '{"aggregate": "books", "pipeline": [{"$match": {"$expr": {"$and": [{"$in": [{"secretKey": "$ttl"}, [{"secretKey": 1}]]}]}}}, {"$project": {"ttl": {"$eq": ["$attrs", {"secretKey": 1}]}}}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$match": {"$expr": {"$and": [{"$in": [{?: ?}, [{?: ?}]]}]}}}, {"$project": {"ttl": {"$eq": [?, {?: ?}]}}}], "cursor": {}}'
        '{"find": "books", "filter": {"we\\"ird\\\\": 1, "line\\nbreak": 2}, "$db": "shop"}'                                 || 'find shop.books {"filter": {"we\\"ird\\\\": ?, "line\\u000abreak": ?}}'
    }

    @Unroll
    void "a field a pipeline update writes is kept only where the mapping knows it, as one a classic update writes: #redacted"() {
        given:
        BsonDocument parsed = BsonDocument.parse(command)

        expect:
        redact(command) == redacted
        !MongoCommandRedactor.redact('shop', parsed.firstKey, parsed, null).contains('alice')
        !MongoCommandRedactor.redact('shop', parsed.firstKey, parsed, null).contains('secret-key')

        where:
        command                                                                                                           || redacted
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": [{"$set": {"flag-alice@example.com": true, "ttl": "x"}}, {"$addFields": {"alice@example.com": 1, "address.city": "y", "address.secret-key": 1}}, {"$project": {"ttl": 1, "secret-key": 0}}, {"$unset": ["alice@example.com"]}]}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": [{"$set": {?: ?, "ttl": ?}}, {"$addFields": {?: ?, "address.city": ?, "address.?": ?}}, {"$project": {"ttl": ?, ?: ?}}, {"$unset": [?]}]}]}'
        '{"findAndModify": "books", "query": {"_id": 1}, "update": [{"$replaceWith": {"ttl": "x", "alice@example.com": 1, "attrs": {"secret-key": 1}}}], "$db": "shop"}' || 'findAndModify shop.books {"query": {"_id": ?}, "update": [{"$replaceWith": {"ttl": ?, ?: ?, "attrs": {?: ?}}}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": [{"$replaceRoot": {"newRoot": {"$mergeObjects": ["$$ROOT", {"ttl": "x", "flag-alice@example.com": true}]}}}, {"$replaceWith": {"$mergeObjects": [{"alice@example.com": 1}, "$address"]}}, {"$set": {"ttl": {"$literal": {"secret-key": 1}}, "address": {"city": {"$toUpper": "$address.city"}, "secret-key": 1}}}]}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": [{"$replaceRoot": {"newRoot": {"$mergeObjects": [?, {"ttl": ?, ?: ?}]}}}, {"$replaceWith": {"$mergeObjects": [{?: ?}, ?]}}, {"$set": {"ttl": {"$literal": {?: ?}}, "address": {"city": {"$toUpper": ?}, ?: ?}}}]}]}'
        '{"bulkWrite": 1, "ops": [{"update": 0, "filter": {"_id": 1}, "updateMods": [{"$set": {"alice@example.com": 1}}]}], "nsInfo": [{"ns": "shop.books"}], "$db": "admin"}' || 'bulkWrite admin {"bulkWrite": ?, "ops": [{"update": ?, "filter": {"_id": ?}, "updateMods": [{"$set": {?: ?}}]}], "nsInfo": [{"ns": ?}]}'
        '{"aggregate": "books", "pipeline": [{"$addFields": {"total": {"$sum": "$pages"}}}, {"$set": {"label": {"$concat": ["$ttl", "x"]}}}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$addFields": {"total": {"$sum": ?}}}, {"$set": {"label": {"$concat": [?]}}}], "cursor": {}}'
    }

    void "map-reduce scope keys are data even when they match mapped fields or operators"() {
        given:
        BsonDocument command = BsonDocument.parse('''{"mapReduce":"books","map":"function() {}","reduce":"function(key, values) {}",
                "scope":{"ttl":{"example-secret":"v"},"address":{"city":"Paris"},"nested":[{"$eq":{"9876543210":"w"}}]},"out":{"inline":1}}''')

        expect:
        MongoCommandRedactor.redact('shop', command.firstKey, command, mapping) ==
                'mapReduce shop.books {"map": ?, "reduce": ?, "scope": {?: {?: ?}, ?: {?: ?}, ?: [{?: {?: ?}}]}, "out": {"inline": ?}}'

        where:
        mapping << [fields, null]
    }

    void "before the datastore binds its mapping, only the keys at the top of a condition are kept, and none of stored data"() {
        expect:
        MongoCommandRedactor.redact('shop', 'find', BsonDocument.parse('{"find": "books", "filter": {"ttl": 1, "address.city": 1, "address": {"city": 1}}}'), null) ==
                'find shop.books {"filter": {"ttl": ?, "address.?": ?, "address": {?: ?}}}'
        MongoCommandRedactor.redact('shop', 'insert', BsonDocument.parse('{"insert": "books", "documents": [{"ttl": "x", "address": {"city": "y"}}]}'), null) ==
                'insert shop.books {"documents": [{?: ?, ?: {?: ?}}]}'
    }

    void "implicit equality operands are data in every matching context"() {
        given:
        BsonDocument command = BsonDocument.parse(json)

        expect:
        !MongoCommandRedactor.redact('shop', command.firstKey, command, fields).contains(SECRET)
        !MongoCommandRedactor.redact('shop', command.firstKey, command, null).contains(SECRET)

        where:
        json << [
                '{"find":"books","filter":{"attrs":{"nested":{"$example-secret":"v"}}}}',
                '{"find":"books","filter":{"attributes":[{"$example-secret":"v"}]}}',
                '{"find":"books","filter":{"$or":[{"attrs":{"nested":{"$example-secret":"v"}}}]}}',
                '{"find":"books","filter":{"stops":{"$elemMatch":{"notes":{"nested":{"$example-secret":"v"}}}}}}',
                '{"find":"books","filter":{"attrs":{"$ref":"other","$id":{"$example-secret":"v"}}}}',
                '{"aggregate":"books","pipeline":[{"$match":{"attrs":{"nested":{"$example-secret":"v"}}}}]}',
                '{"aggregate":"books","pipeline":[{"$graphLookup":{"from":"books","restrictSearchWithMatch":{"attrs":{"nested":{"$example-secret":"v"}}}}}]}',
                '{"aggregate":"books","pipeline":[{"$geoNear":{"near":[0,0],"distanceField":"distance","query":{"attrs":{"nested":{"$example-secret":"v"}}}}}]}',
                '{"count":"books","query":{"attrs":{"nested":{"$example-secret":"v"}}}}',
                '{"update":"books","updates":[{"q":{"attrs":{"nested":{"$example-secret":"v"}}},"u":{"$set":{"ttl":"x"}}}]}',
                '{"update":"books","updates":[{"q":{},"u":{"$pull":{"attributes":{"attrs":{"nested":{"$example-secret":"v"}}}}}}]}',
                '{"update":"books","updates":[{"q":{},"u":{"$set":{"attributes.$[entry].k":1}},"arrayFilters":[{"entry.attrs":{"nested":{"$example-secret":"v"}}}]}]}',
                '{"createIndexes":"books","indexes":[{"key":{"ttl":1},"partialFilterExpression":{"attrs":{"nested":{"$example-secret":"v"}}}}]}',
                '{"collMod":"books","validator":{"attrs":{"nested":{"$example-secret":"v"}}}}',
                '{"find":"books","projection":{"attributes":{"$elemMatch":{"nested":{"a":1,"$example-secret":"v"}}}}}',
                '{"findAndModify":"books","query":{},"fields":{"stops":{"$elemMatch":{"notes":{"nested":{"$example-secret":"v"}}}}},"update":{"$set":{"ttl":"x"}}}',
        ]
    }

    void "a DBRef is compared whole, whichever of its fields comes first and wherever a condition has fields"() {
        given:
        BsonDocument command = BsonDocument.parse(json)

        expect:
        !MongoCommandRedactor.redact('shop', command.firstKey, command, fields).contains(SECRET)
        !MongoCommandRedactor.redact('shop', command.firstKey, command, null).contains(SECRET)

        where:
        json << [
                '{"find":"books","filter":{"attrs":{"$id":{"$example-secret":"v"},"$ref":"other"}}}',
                '{"find":"books","filter":{"attrs":{"$db":"d","$id":{"$example-secret":"v"},"$ref":"other"}}}',
                '{"find":"books","filter":{"$ref":"other","$id":{"nested":{"$example-secret":"v"}}}}',
                '{"find":"books","filter":{"$id":{"$example-secret":"v"}}}',
                '{"find":"books","filter":{"$or":[{"$ref":"other","$id":{"nested":{"$example-secret":"v"}}}]}}',
                '{"find":"books","filter":{"stops":{"$elemMatch":{"$id":{"nested":{"$example-secret":"v"}},"$ref":"other"}}}}',
                '{"find":"books","filter":{"stops":{"$elemMatch":{"$db":"d","$id":{"$example-secret":"v"}}}}}',
                '{"aggregate":"books","pipeline":[{"$match":{"attrs":{"$id":{"$example-secret":"v"},"$ref":"other"}}}]}',
                '{"update":"books","updates":[{"q":{"attrs":{"$id":{"$example-secret":"v"},"$ref":"other"}},"u":{"$set":{"ttl":"x"}}}]}',
                '{"update":"books","updates":[{"q":{},"u":{"$pull":{"attributes":{"$id":{"$example-secret":"v"},"$ref":"other"}}}}]}',
        ]
    }

    void "a value written anywhere into a command never survives, nor does a key that may be data"() {
        given:
        String command = """{"insert": "books", "documents": [{"ttl": "${SECRET}", "pages": {"\$numberLong": "1"},
                "address": {"city": "${SECRET}", "notes": {"${SECRET}": "${SECRET}"}}, "attrs": {"${SECRET}": "${SECRET}"},
                "stops": [{"city": "${SECRET}", "notes": {"${SECRET}": 1}}, [["${SECRET}"]]], "unknown": {"${SECRET}": {"${SECRET}": 1}},
                "list": ["${SECRET}", {"${SECRET}": 1}], "numbers": {"${NUMERIC_SECRET}": 1, "attrs": {"${NUMERIC_SECRET}": 1}},
                "attrs.${NUMERIC_SECRET}": 1, "${NUMERIC_SECRET}": 1, "attributes": [{"${NUMERIC_SECRET}": 1}, [{"${NUMERIC_SECRET}": 1}]],
                "attributes.0.${NUMERIC_SECRET}": 1, "stops.0.${NUMERIC_SECRET}": 1, "\$${SECRET}": 1, "attrs": {"\$${SECRET}": 1},
                "address": {"\$${SECRET}": {"\$${SECRET}": 1}}}], "comment": "${SECRET}", "\$db": "shop", "lsid": {"id": "${SECRET}"}}"""
        String condition = """{"find": "books", "filter": {"attrs": {"\$eq": {"\$${SECRET}": "${SECRET}"}}, "tags": {"\$in": [{"\$${SECRET}": 1}]},
                "stops": {"\$elemMatch": {"\$ne": {"\$${SECRET}": 1}}}, "\$comment": {"${SECRET}": {"\$${SECRET}": 1}},
                "\$expr": {"\$eq": ["\$attrs", {"${SECRET}": {"${SECRET}": 1}, "ttl": [{"${SECRET}": 1}]}]}},
                "let": {"v": {"\$literal": {"\$${SECRET}": 1}}, "w": {"\$const": {"\$${SECRET}": 1}}}, "comment": {"\$${SECRET}": 1}, "\$db": "shop"}"""

        expect:
        !redact(command).contains(SECRET)
        !redact(command).contains(NUMERIC_SECRET)
        !redact(command.replace('"insert": "books"', '"insert": "other"')).contains(SECRET)
        !redact(command.replace('"insert": "books"', '"insert": "other"')).contains(NUMERIC_SECRET)
        !redact(condition).contains(SECRET)
        !redact(condition.replace('"find": "books"', '"find": "other"')).contains(SECRET)
    }

    void "extra data in a query geometry is not mistaken for operators"() {
        given:
        BsonDocument command = BsonDocument.parse(json)

        expect:
        !MongoCommandRedactor.redact('shop', command.firstKey, command, fields).contains(SECRET)
        !MongoCommandRedactor.redact('shop', command.firstKey, command, null).contains(SECRET)

        where:
        json << [
                '{"find":"books","filter":{"location":{"$geoWithin":{"$geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]],"metadata":{"$example-secret":"v"}}}}}}',
                '{"find":"books","filter":{"location":{"$near":{"$geometry":{"type":"Point","coordinates":[0,0],"metadata":{"$example-secret":"v"}}}}}}',
                '{"aggregate":"books","pipeline":[{"$geoNear":{"near":{"$literal":{"type":"Point","coordinates":[0,0],"metadata":{"$example-secret":"v"}}},"distanceField":"distance"}}]}',
        ]
    }

    @Unroll
    void "a dollar key in a condition that is no query operator is recorded as ?, with everything under it: #shape"() {
        expect:
        redact(command) == redacted
        !MongoCommandRedactor.redact('shop', BsonDocument.parse(command).firstKey, BsonDocument.parse(command), null).contains(SECRET)

        where:
        shape                         | command                                                                                                                                  || redacted
        'beside the geometry of near' | '{"find": "books", "filter": {"location": {"$near": {"$geometry": {"type": "Point", "coordinates": [0, 0]}, "$maxDistance": 5, "$example-secret": 1}}}, "$db": "shop"}' || 'find shop.books {"filter": {"location": {"$near": {"$geometry": {?: ?, ?: [?]}, "$maxDistance": ?, ?: ?}}}}'
        'in the operand of nearSphere' | '{"find": "books", "filter": {"location": {"$nearSphere": {"$example-secret": {"$k": 1}}}}, "$db": "shop"}'                               || 'find shop.books {"filter": {"location": {"$nearSphere": {?: {?: ?}}}}}'
        'beside the field operators'  | '{"find": "books", "filter": {"pages": {"$gt": 1, "$example-secret": 2}}, "$db": "shop"}'                                               || 'find shop.books {"filter": {"pages": {"$gt": ?, ?: ?}}}'
        'at the top of the condition' | '{"find": "books", "filter": {"ttl": "x", "$example-secret": {"k": 1}}, "$db": "shop"}'                                                 || 'find shop.books {"filter": {"ttl": ?, ?: {?: ?}}}'
        'in an element match'         | '{"find": "books", "filter": {"tags": {"$elemMatch": {"$example-secret": 1}}}, "$db": "shop"}'                                          || 'find shop.books {"filter": {"tags": {"$elemMatch": {?: ?}}}}'
        'in an element match of a projection' | '{"find": "books", "projection": {"tags": {"$elemMatch": {"$example-secret": 1}}}, "$db": "shop"}'                       || 'find shop.books {"projection": {"tags": {"$elemMatch": {?: ?}}}}'
        'in a match stage'            | '{"aggregate": "books", "pipeline": [{"$match": {"$example-secret": 1}}], "cursor": {}, "$db": "shop"}'                                  || 'aggregate shop.books {"pipeline": [{"$match": {?: ?}}], "cursor": {}}'
        'a stage name in a condition' | '{"find": "books", "filter": {"$project": {"ttl": 1}}, "$db": "shop"}'                                                                  || 'find shop.books {"filter": {?: {?: ?}}}'
    }

    @Unroll
    void "the query operators of a condition are kept: #redacted"() {
        expect:
        redact(command) == redacted

        where:
        command                                                                                                                                                      || redacted
        '{"find": "books", "filter": {"location": {"$geoWithin": {"$centerSphere": [[0, 0], 1]}}}, "$db": "shop"}'                                               || 'find shop.books {"filter": {"location": {"$geoWithin": {"$centerSphere": [[?], ?]}}}}'
        '{"find": "books", "filter": {"$text": {"$search": "x", "$language": "en", "$caseSensitive": true}, "$and": [{"pages": {"$exists": true, "$type": "int"}}]}, "$db": "shop"}' || 'find shop.books {"filter": {"$text": {"$search": ?, "$language": ?, "$caseSensitive": ?}, "$and": [{"pages": {"$exists": ?, "$type": ?}}]}}'
        '{"find": "books", "filter": {"location": {"$near": [0, 0], "$maxDistance": 5}, "pages": {"$not": {"$mod": [2, 0]}, "$bitsAllSet": 3}}, "$db": "shop"}' || 'find shop.books {"filter": {"location": {"$near": [?], "$maxDistance": ?}, "pages": {"$not": {"$mod": [?]}, "$bitsAllSet": ?}}}'
        '{"find": "books", "filter": {"location": {"$within": {"$box": [[0, 0], [1, 1]]}}}, "$db": "shop"}'                                                    || 'find shop.books {"filter": {"location": {"$within": {"$box": [[?]]}}}}'
        '{"find": "books", "filter": {"location": {"$within": {"$polygon": [[0, 0], [1, 0], [1, 1]]}}}, "$db": "shop"}'                                       || 'find shop.books {"filter": {"location": {"$within": {"$polygon": [[?]]}}}}'
        '{"find": "books", "filter": {"location": {"$within": {"$center": [[0, 0], 1]}}}, "$db": "shop"}'                                                      || 'find shop.books {"filter": {"location": {"$within": {"$center": [[?], ?]}}}}'
        '{"find": "books", "filter": {"location": {"$within": {"$box": [[0, 0], [1, 1]], "$example-secret": {"k": 1}}}}, "$db": "shop"}'                       || 'find shop.books {"filter": {"location": {"$within": {"$box": [[?]], ?: {?: ?}}}}}'
    }

    @Unroll
    void "the operand of an element match is a condition, in a projection as in a filter: #option"() {
        expect:
        redact("""{"find": "books", "${option}": {"stops": {"\$elemMatch": {"city": "x", "notes": {"a": 1, "\$example-secret": "v"},
                "\$or": [{"city": {"\$gt": "y"}}], "\$example-secret": 1}}}, "\$db": "shop"}""") ==
                """find shop.books {"${option}": {"stops": {"\$elemMatch": {"city": ?, "notes": {?: ?, ?: ?}, "\$or": [{"city": {"\$gt": ?}}], ?: ?}}}}""".toString()

        where:
        option << ['filter', 'projection']
    }

    @Unroll
    void "the document of an option that is not known to hold options, fields or a pipeline keeps none of its keys: #shape"() {
        expect:
        redact(command) == redacted
        !redact(command).contains('example-secret')

        where:
        shape                              | command                                                                                                                  || redacted
        'the documents applyOps applies'   | '{"applyOps": [{"op": "i", "ns": "shop.books", "o": {"_id": 1, "attrs": {"example-secret": 1}}}, {"op": "u", "ns": "shop.books", "o2": {"_id": 1}, "o": {"$set": {"attrs.example-secret": 2}}}], "$db": "admin"}' || 'applyOps admin {"applyOps": [{"op": ?, "ns": ?, "o": {?: ?, ?: {?: ?}}}, {"op": ?, "ns": ?, "o2": {?: ?}, "o": {?: {?: ?}}}]}'
        'the result a precondition expects' | '{"applyOps": [], "preCondition": [{"ns": "shop.books", "q": {"_id": 1}, "res": {"attrs": {"example-secret": 1}}}], "$db": "admin"}' || 'applyOps admin {"applyOps": [], "preCondition": [{"ns": ?, "q": {"_id": ?}, "res": {?: {?: ?}}}]}'
        'an option of a later server'      | '{"find": "books", "filter": {"ttl": "x"}, "laterOption": {"example-secret": {"k": 1}}, "$db": "shop"}'                  || 'find shop.books {"filter": {"ttl": ?}, "laterOption": {?: {?: ?}}}'
        'an option of a write statement'   | '{"delete": "books", "deletes": [{"q": {"ttl": "x"}, "limit": 0, "laterOption": {"example-secret": 1}}], "$db": "shop"}' || 'delete shop.books {"deletes": [{"q": {"ttl": ?}, "limit": ?, "laterOption": {?: ?}}]}'
        'a comment'                        | '{"find": "books", "filter": {}, "comment": {"example-secret": [{"k": 1}]}, "$db": "shop"}'                               || 'find shop.books {"filter": {}, "comment": {?: [{?: ?}]}}'
        'the command explain runs'         | '{"explain": {"find": "books", "filter": {"ttl": "x"}, "collation": {"locale": "fr"}}, "verbosity": "queryPlanner", "$db": "shop"}' || 'explain shop {"explain": {"find": ?, "filter": {"ttl": ?}, "collation": {"locale": ?}}, "verbosity": ?}'
        'the options of a write'           | '{"insert": "books", "documents": [], "writeConcern": {"w": "majority", "j": true}, "$db": "shop"}'                      || 'insert shop.books {"documents": [], "writeConcern": {"w": ?, "j": ?}}'
    }

    void "a regular expression written as operators keeps them, as a condition holds them"() {
        given: 'built as BSON, since extended JSON reads the two operators as a regular expression value'
        BsonDocument command = new BsonDocument('find', new BsonString('books'))
                .append('filter', new BsonDocument('ttl', new BsonDocument('$regex', new BsonString('^x')).append('$options', new BsonString('i'))))

        expect:
        MongoCommandRedactor.redact('shop', 'find', command, fields) == 'find shop.books {"filter": {"ttl": {"$regex": ?, "$options": ?}}}'
    }

    private String redact(String json) {
        BsonDocument command = BsonDocument.parse(json)
        String database = command.containsKey('$db') ? command.getString('$db').value : null
        MongoCommandRedactor.redact(database, command.firstKey, command, fields)
    }
}

/**
 * A mapping whose entities one thread reads only once it is let through, as a thread that reads them while the
 * mapping is busy waits; every other thread reads them at once.
 */
class QueryTailGatedMappingContext extends MongoMappingContext {

    final CountDownLatch entered = new CountDownLatch(1)

    final CountDownLatch release = new CountDownLatch(1)

    volatile Thread gated

    QueryTailGatedMappingContext(Class... classes) {
        super(new MongoConnectionSourceSettings(), classes)
    }

    @Override
    Collection<PersistentEntity> getPersistentEntities() {
        if (Thread.currentThread().is(gated)) {
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        super.getPersistentEntities()
    }
}

@Entity
class QueryTailRedactedBook {
    ObjectId id
    Long version
    String title
    Integer pages
    QueryTailRedactedAddress address
    List<QueryTailRedactedAddress> stops
    Map<String, String> attrs
    List<String> tags
    List<Map<String, String>> attributes
    QueryTailRedactedAuthor author

    static embedded = ['address', 'stops']

    static mapping = {
        collection 'books'
        title attr: 'ttl'
    }
}

class QueryTailRedactedAddress {
    String city
    Map<String, String> notes
}

@Entity
class QueryTailRedactedAuthor {
    ObjectId id
    Long version
    String name
}

@Entity
class QueryTailRedactedPaperback extends QueryTailRedactedBook {
    String binding
}

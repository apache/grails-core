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
package org.apache.grails.data.mongo.core.querytail

import grails.gorm.annotation.Entity
import org.bson.BsonDocument
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class MongoCommandRedactorSpec extends Specification {

    static final String SECRET = 'example-secret'

    static final String NUMERIC_SECRET = '9876543210'

    @Shared
    @AutoCleanup
    MongoDatastore datastore = new MongoDatastore(QueryTailRedactedBook, QueryTailRedactedAuthor, QueryTailRedactedPaperback)

    @Shared
    MongoMappedFields fields = new MongoMappedFields(datastore)

    @Unroll
    void "a command is recorded as its name, its namespace and its entries without their values: #redacted"() {
        expect:
        redact(command) == redacted

        where:
        command                                                                                                   || redacted
        '{"find": "books", "filter": {"ttl": "x", "pages": {"$gt": 1}, "author": {"$oid": "5f1d7f3e8b1c2a3d4e5f6a7b"}}, "sort": {"ttl": 1}, "projection": {"ttl": 1}, "limit": 10, "$db": "shop", "lsid": {"id": {"$binary": {"base64": "AAAAAAAAAAAAAAAAAAAAAA==", "subType": "04"}}}, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1, "i": 1}}, "signature": {"hash": {"$binary": {"base64": "AA==", "subType": "00"}}, "keyId": 1}}, "$readPreference": {"mode": "primary"}}' || 'find shop.books {"filter": {"ttl": ?, "pages": {"$gt": ?}, "author": ?}, "sort": {"ttl": ?}, "projection": {"ttl": ?}, "limit": ?}'
        '{"insert": "books", "ordered": true, "$db": "shop", "documents": [{"_id": {"$oid": "5f1d7f3e8b1c2a3d4e5f6a7b"}, "ttl": "x", "pages": 1, "version": 0, "_class": "Book"}], "txnNumber": 3, "autocommit": false, "startTransaction": true}' || 'insert shop.books {"ordered": ?, "documents": [{"_id": ?, "ttl": ?, "pages": ?, "version": ?, "_class": ?}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"ttl": "y"}, "$inc": {"version": 1}}, "multi": false, "upsert": false}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"ttl": ?}, "$inc": {"version": ?}}, "multi": ?, "upsert": ?}]}'
        '{"delete": "books", "deletes": [{"q": {"_id": {"$in": [1, 2]}}, "limit": 0}], "$db": "shop"}'          || 'delete shop.books {"deletes": [{"q": {"_id": {"$in": [?, ?]}}, "limit": ?}]}'
        '{"aggregate": "books", "pipeline": [{"$match": {"ttl": "x", "$or": [{"pages": {"$gt": 1}}, {"pages": null}]}}, {"$group": {"_id": "$author", "total": {"$sum": "$pages"}}}, {"$lookup": {"from": "queryTailRedactedAuthor", "localField": "author", "foreignField": "_id", "as": "a"}}], "cursor": {"batchSize": 101}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$match": {"ttl": ?, "$or": [{"pages": {"$gt": ?}}, {"pages": ?}]}}, {"$group": {"_id": ?, "total": {"$sum": ?}}}, {"$lookup": {"from": ?, "localField": ?, "foreignField": ?, "as": ?}}], "cursor": {"batchSize": ?}}'
        '{"getMore": {"$numberLong": "123"}, "collection": "books", "batchSize": 100, "$db": "shop"}'           || 'getMore shop.books {"getMore": ?, "batchSize": ?}'
        '{"killCursors": "books", "cursors": [{"$numberLong": "1"}, {"$numberLong": "2"}], "$db": "shop"}'      || 'killCursors shop.books {"cursors": [?, ?]}'
        '{"createIndexes": "books", "indexes": [{"key": {"ttl": 1, "address.city": -1}, "name": "ttl_1", "unique": true}], "$db": "shop"}' || 'createIndexes shop.books {"indexes": [{"key": {"ttl": ?, "address.city": ?}, "name": ?, "unique": ?}]}'
        '{"count": "books", "query": {"pages": {"$exists": true}}, "$db": "shop"}'                                || 'count shop.books {"query": {"pages": {"$exists": ?}}}'
        '{"findAndModify": "books", "query": {"_id": 1}, "update": {"$set": {"ttl": "z"}}, "new": true, "$db": "shop"}' || 'findAndModify shop.books {"query": {"_id": ?}, "update": {"$set": {"ttl": ?}}, "new": ?}'
        '{"aggregate": 1, "pipeline": [{"$currentOp": {}}], "cursor": {}, "$db": "admin"}'                        || 'aggregate admin {"aggregate": ?, "pipeline": [{"$currentOp": {}}], "cursor": {}}'
        '{"ping": 1, "$db": "admin"}'                                                                             || 'ping admin {"ping": ?}'
        '{"commitTransaction": 1, "txnNumber": 1, "autocommit": false, "$db": "admin"}'                           || 'commitTransaction admin {"commitTransaction": ?}'
        '{"endSessions": [{"id": 1}]}'                                                                            || 'endSessions {"endSessions": [{"id": ?}]}'
    }

    @Unroll
    void "a key is kept where the mapping knows it as a field, and recorded as ? where it may be data: #redacted"() {
        expect:
        redact(command) == redacted

        where:
        command                                                                                                           || redacted
        '{"find": "books", "filter": {"address.city": "Paris", "attrs.k": "v", "attrs.k.l": 1, "address.notes.k": 1, "stops.0.city": "Rome", "stops.0.notes.k": 1, "author.name": "x"}, "$db": "shop"}' || 'find shop.books {"filter": {"address.city": ?, "attrs.?": ?, "attrs.?.?": ?, "address.notes.?": ?, "stops.0.city": ?, "stops.0.notes.?": ?, "author.?": ?}}'
        '{"insert": "books", "documents": [{"ttl": "x", "address": {"city": "Paris", "notes": {"secret-key": "v"}}, "stops": [{"city": "Rome", "notes": {"secret-key": 1}}], "attrs": {"secret-key": "v"}, "unknown": {"secret-key": 1}}], "$db": "shop"}' || 'insert shop.books {"documents": [{"ttl": ?, "address": {"city": ?, "notes": {?: ?}}, "stops": [{"city": ?, "notes": {?: ?}}], "attrs": {?: ?}, "unknown": {?: ?}}]}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"attrs": {"secret-key": "v"}, "address.notes.secret-key": "v", "address": {"city": "x", "notes": {"secret-key": 1}}}, "$unset": {"attrs.secret-key": 1}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"attrs": {?: ?}, "address.notes.?": ?, "address": {"city": ?, "notes": {?: ?}}}, "$unset": {"attrs.?": ?}}}]}'
        '{"find": "books", "filter": {"attrs": {"$elemMatch": {"secret-key": 1}}, "stops": {"$elemMatch": {"city": "x", "notes": {"secret-key": 1}}}}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {"$elemMatch": {?: ?}}, "stops": {"$elemMatch": {"city": ?, "notes": {?: ?}}}}}'
        '{"find": "books", "filter": {"binding": "x", "address.city": "y"}, "$db": "shop"}'                               || 'find shop.books {"filter": {"binding": ?, "address.city": ?}}'
        '{"find": "other", "filter": {"a": 1, "b": {"secret-key": 2}, "d.secret-key": 3, "$or": [{"e": {"secret-key": 1}}]}, "$db": "shop"}' || 'find shop.other {"filter": {"a": ?, "b": {?: ?}, "d.?": ?, "$or": [{"e": {?: ?}}]}}'
        '{"aggregate": "books", "pipeline": [{"$facet": {"byCity": [{"$match": {"address.city": "x"}}]}}], "cursor": {}, "$db": "shop"}' || 'aggregate shop.books {"pipeline": [{"$facet": {"byCity": [{"$match": {?: ?}}]}}], "cursor": {}}'
        '{"bulkWrite": 1, "ops": [{"insert": 0, "document": {"ttl": "x", "attrs": {"secret-key": 1}}}, {"update": 0, "filter": {"_id": 1}, "updateMods": {"$set": {"attrs.secret-key": 1}}, "multi": false}], "nsInfo": [{"ns": "shop.books"}], "$db": "admin"}' || 'bulkWrite admin {"bulkWrite": ?, "ops": [{"insert": ?, "document": {"ttl": ?, "attrs": {?: ?}}}, {"update": ?, "filter": {"_id": ?}, "updateMods": {"$set": {"attrs.?": ?}}, "multi": ?}], "nsInfo": [{"ns": ?}]}'
        '{"find": "books", "filter": {"attrs": {"9876543210": "v"}, "attrs.9876543210": "v", "stops.0.city": "x", "stops.0.notes.9876543210": 1, "tags.0": "x", "tags": {"0": "x"}, "9876543210": 1}, "$db": "shop"}' || 'find shop.books {"filter": {"attrs": {?: ?}, "attrs.?": ?, "stops.0.city": ?, "stops.0.notes.?": ?, "tags.0": ?, "tags": {"0": ?}, ?: ?}}'
        '{"update": "books", "updates": [{"q": {"_id": 1}, "u": {"$set": {"attrs.9876543210": "v", "stops.1.city": "x"}}}], "$db": "shop"}' || 'update shop.books {"updates": [{"q": {"_id": ?}, "u": {"$set": {"attrs.?": ?, "stops.1.city": ?}}}]}'
        '{"find": "books", "filter": {"we\\"ird\\\\": 1, "line\\nbreak": 2}, "$db": "shop"}'                                 || 'find shop.books {"filter": {"we\\"ird\\\\": ?, "line\\u000abreak": ?}}'
    }

    void "before the datastore binds its mapping, only the keys at the top of a document are kept"() {
        expect:
        MongoCommandRedactor.redact('shop', 'find', BsonDocument.parse('{"find": "books", "filter": {"ttl": 1, "address.city": 1, "address": {"city": 1}}}'), null) ==
                'find shop.books {"filter": {"ttl": ?, "address.?": ?, "address": {?: ?}}}'
    }

    void "a value written anywhere into a command never survives, nor does a key that may be data"() {
        given:
        String command = """{"insert": "books", "documents": [{"ttl": "${SECRET}", "pages": {"\$numberLong": "1"},
                "address": {"city": "${SECRET}", "notes": {"${SECRET}": "${SECRET}"}}, "attrs": {"${SECRET}": "${SECRET}"},
                "stops": [{"city": "${SECRET}", "notes": {"${SECRET}": 1}}, [["${SECRET}"]]], "unknown": {"${SECRET}": {"${SECRET}": 1}},
                "list": ["${SECRET}", {"${SECRET}": 1}], "numbers": {"${NUMERIC_SECRET}": 1, "attrs": {"${NUMERIC_SECRET}": 1}},
                "attrs.${NUMERIC_SECRET}": 1, "${NUMERIC_SECRET}": 1}], "comment": "${SECRET}", "\$db": "shop", "lsid": {"id": "${SECRET}"}}"""

        expect:
        !redact(command).contains(SECRET)
        !redact(command).contains(NUMERIC_SECRET)
        !redact(command.replace('"insert": "books"', '"insert": "other"')).contains(SECRET)
        !redact(command.replace('"insert": "books"', '"insert": "other"')).contains(NUMERIC_SECRET)
    }

    private String redact(String json) {
        BsonDocument command = BsonDocument.parse(json)
        String database = command.containsKey('$db') ? command.getString('$db').value : null
        MongoCommandRedactor.redact(database, command.firstKey, command, fields)
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

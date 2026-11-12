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
package org.grails.datastore.gorm.mongo.extensions

import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.client.AggregateIterable
import com.mongodb.client.ChangeStreamIterable
import com.mongodb.client.DistinctIterable
import com.mongodb.client.FindIterable
import com.mongodb.client.ListCollectionNamesIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.DeleteOptions
import com.mongodb.client.model.DropIndexOptions
import com.mongodb.client.model.FindOneAndDeleteOptions
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.InsertManyOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.mongo.engine.AbstractMongoObectEntityPersister

class MongoExtensionsSpec extends Specification {

    void "asType on a Document to a Document-assignable type returns the document unchanged, without a datastore lookup"() {
        given:
        Document document = new Document(name: 'Bob')

        expect:
        MongoExtensions.asType(document, Document).is(document)
    }

    void "asType on a Document to an unregistered entity type propagates GormEnhancer's configuration error"() {
        given:
        Document document = new Document(name: 'Bob')

        when:
        MongoExtensions.asType(document, NotAGormEntity)

        then:
        thrown(IllegalStateException)
    }

    void "asType on a FindIterable to a FindIterable-assignable type returns the iterable unchanged, without a datastore lookup"() {
        given:
        FindIterable<Document> iterable = Mock(FindIterable)

        expect:
        MongoExtensions.asType(iterable, FindIterable).is(iterable)
    }

    void "asType on a FindIterable to an unregistered entity type propagates GormEnhancer's configuration error"() {
        given:
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.asType(iterable, NotAGormEntity)

        then:
        thrown(IllegalStateException)
    }

    void "toList on an unregistered entity type propagates GormEnhancer's configuration error"() {
        given:
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.toList(iterable, NotAGormEntity)

        then:
        thrown(IllegalStateException)
        0 * iterable.iterator()
    }

    private static class NotAGormEntity {
    }

    void "toDBObject converts a flat Document"() {
        given:
        def document = new Document([name: 'Bob', age: 42])

        when:
        def dbObject = MongoExtensions.toDBObject(document)

        then:
        dbObject.get('name') == 'Bob'
        dbObject.get('age') == 42
    }

    void "toDBObject recursively converts nested Documents"() {
        given:
        def document = new Document([address: new Document([city: 'London'])])

        when:
        def dbObject = MongoExtensions.toDBObject(document)

        then:
        dbObject.get('address').get('city') == 'London'
    }

    void "toDBObject recursively converts Documents nested inside a Collection"() {
        given:
        def document = new Document([items: [new Document([sku: 'A1']), 'plain']])

        when:
        def dbObject = MongoExtensions.toDBObject(document)

        then:
        def items = dbObject.get('items')
        items[0].get('sku') == 'A1'
        items[1] == 'plain'
    }

    void "propertyMissing returns the named collection from the database"() {
        given:
        MongoDatabase db = Mock(MongoDatabase)
        MongoCollection col = Mock(MongoCollection)

        when:
        def result = MongoExtensions.propertyMissing(db, 'books')

        then:
        1 * db.getCollection('books') >> col
        result.is(col)
    }

    void "getAt returns the named collection from the database"() {
        given:
        MongoDatabase db = Mock(MongoDatabase)
        MongoCollection col = Mock(MongoCollection)

        when:
        def result = MongoExtensions.getAt(db, 'books')

        then:
        1 * db.getCollection('books') >> col
        result.is(col)
    }

    void "getCollectionNames delegates to listCollectionNames"() {
        given:
        MongoDatabase db = Mock(MongoDatabase)
        ListCollectionNamesIterable names = Mock(ListCollectionNamesIterable)

        when:
        def result = MongoExtensions.getCollectionNames(db)

        then:
        1 * db.listCollectionNames() >> names
        result.is(names)
    }

    void "createAndGetCollection creates the collection with mapped options then returns it"() {
        given:
        MongoDatabase db = Mock(MongoDatabase)
        MongoCollection col = Mock(MongoCollection)

        when:
        def result = MongoExtensions.createAndGetCollection(db, 'books', [capped: true])

        then:
        1 * db.createCollection('books', { CreateCollectionOptions o -> o.isCapped() })
        1 * db.getCollection('books') >> col
        result.is(col)
    }

    @Unroll
    void "FindIterable extension #methodName delegates the map as Bson"() {
        given:
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        def result = MongoExtensions."$methodName"(iterable, [field: 1])

        then:
        1 * iterable."$methodName"({ Bson b -> ((Document) b).get('field') == 1 }) >> iterable
        result.is(iterable)

        where:
        methodName << ['filter', 'projection', 'sort', 'hint', 'max', 'min']
    }

    @Unroll
    void "FindIterable extension #methodName passes through a null map as null Bson"() {
        given:
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions."$methodName"(iterable, null)

        then:
        1 * iterable."$methodName"(null) >> iterable

        where:
        methodName << ['filter', 'projection', 'sort', 'hint', 'max', 'min']
    }

    void "DistinctIterable filter delegates the map as Bson"() {
        given:
        DistinctIterable<Document> iterable = Mock(DistinctIterable)

        when:
        def result = MongoExtensions.filter(iterable, [field: 1])

        then:
        1 * iterable.filter({ Bson b -> ((Document) b).get('field') == 1 }) >> iterable
        result.is(iterable)
    }

    void "count with no arguments delegates to countDocuments"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        def result = MongoExtensions.count(collection)

        then:
        1 * collection.countDocuments() >> 5L
        result == 5L
    }

    void "count with a query map delegates through getCount"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        def result = MongoExtensions.count(collection, [active: true])

        then:
        1 * collection.countDocuments({ Bson b -> ((Document) b).get('active') == true }) >> 2L
        result == 2L
    }

    void "count with a query and read preference switches read preference before counting"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withPreference = Mock(MongoCollection)

        when:
        def result = MongoExtensions.count(collection, [active: true], ReadPreference.secondary())

        then:
        1 * collection.withReadPreference(ReadPreference.secondary()) >> withPreference
        1 * withPreference.countDocuments(_ as Bson) >> 3L
        result == 3L
    }

    void "count with a query and an options map maps the options to CountOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        def result = MongoExtensions.count(collection, [active: true], [limit: 10])

        then:
        1 * collection.countDocuments(_ as Bson, { CountOptions o -> o.limit == 10 }) >> 1L
        result == 1L
    }

    void "getName reads the collection name from the namespace"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        collection.namespace >> new com.mongodb.MongoNamespace('db.books')

        expect:
        MongoExtensions.getName(collection) == 'books'
    }

    void "findOne with no arguments returns the first document"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        def result = MongoExtensions.findOne(collection)

        then:
        1 * collection.find() >> iterable
        1 * iterable.first() >> new Document(title: 'Book')
        result.get('title') == 'Book'
    }

    void "findOne with a query map limits to a single result"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, [title: 'Book'])

        then:
        1 * collection.find(_ as Bson) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "findOne by ObjectId queries on the mongo id field"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)
        ObjectId id = new ObjectId()

        when:
        MongoExtensions.findOne(collection, id)

        then:
        1 * collection.find({ Bson b -> ((Document) b).get(AbstractMongoObectEntityPersister.MONGO_ID_FIELD) == id }) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "findOne by CharSequence id queries on the mongo id field"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, (CharSequence) 'abc123')

        then:
        1 * collection.find({ Bson b -> ((Document) b).get(AbstractMongoObectEntityPersister.MONGO_ID_FIELD) == 'abc123' }) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "findOne by id and type finds using the typed collection view"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<String> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, 'abc123', String)

        then:
        1 * collection.find({ Bson b -> ((Document) b).get(AbstractMongoObectEntityPersister.MONGO_ID_FIELD) == 'abc123' }, String) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> 'abc123'
    }

    void "findOne with query and projection applies both before limiting"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, [title: 'Book'], [title: 1])

        then:
        1 * collection.find(_ as Bson) >> iterable
        1 * iterable.projection(_ as Bson) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "findOne with query, projection and sort applies all three before limiting"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, [title: 'Book'], [title: 1], [title: -1])

        then:
        1 * collection.find(_ as Bson) >> iterable
        1 * iterable.projection(_ as Bson) >> iterable
        1 * iterable.sort(_ as Bson) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "findOne with query, projection and read preference switches the collection's read preference first"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withPreference = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, [title: 'Book'], [title: 1], ReadPreference.secondary())

        then:
        1 * collection.withReadPreference(ReadPreference.secondary()) >> withPreference
        1 * withPreference.find(_ as Bson) >> iterable
        1 * iterable.projection(_ as Bson) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "findOne with query, projection, sort and read preference applies all before limiting"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withPreference = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        MongoExtensions.findOne(collection, [title: 'Book'], [title: 1], [title: -1], ReadPreference.secondary())

        then:
        1 * collection.withReadPreference(ReadPreference.secondary()) >> withPreference
        1 * withPreference.find(_ as Bson) >> iterable
        1 * iterable.projection(_ as Bson) >> iterable
        1 * iterable.sort(_ as Bson) >> iterable
        1 * iterable.limit(1) >> iterable
        1 * iterable.first() >> new Document()
    }

    void "find with a query map delegates to find"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        def result = MongoExtensions.find(collection, [active: true])

        then:
        1 * collection.find(_ as Bson) >> iterable
        result.is(iterable)
    }

    void "find with a query map and type delegates to the typed find"() {
        given:
        MongoCollection<String> collection = Mock(MongoCollection)
        FindIterable<String> iterable = Mock(FindIterable)

        when:
        def result = MongoExtensions.find(collection, [active: true], String)

        then:
        1 * collection.find(_ as Bson, String) >> iterable
        result.is(iterable)
    }

    void "find with a query and projection map applies the projection after finding"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        FindIterable<Document> iterable = Mock(FindIterable)

        when:
        def result = MongoExtensions.find(collection, [active: true], [title: 1])

        then:
        1 * collection.find(_ as Bson) >> iterable
        1 * iterable.projection(_ as Bson) >> iterable
        result.is(iterable)
    }

    void "aggregate converts each pipeline stage to Bson"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        AggregateIterable<Document> iterable = Mock(AggregateIterable)

        when:
        def result = MongoExtensions.aggregate(collection, [[$match: [active: true]]])

        then:
        1 * collection.aggregate({ List<Bson> stages -> stages.size() == 1 }) >> iterable
        result.is(iterable)
    }

    void "aggregate with a result class converts the pipeline and requests the typed iterable"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        AggregateIterable<String> iterable = Mock(AggregateIterable)

        when:
        def result = MongoExtensions.aggregate(collection, [[$match: [active: true]]], String)

        then:
        1 * collection.aggregate({ List<Bson> stages -> stages.size() == 1 }, String) >> iterable
        result.is(iterable)
    }

    void "distinct by field name defaults to Document results"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DistinctIterable<Document> iterable = Mock(DistinctIterable)

        when:
        def result = MongoExtensions.distinct(collection, 'title')

        then:
        1 * collection.distinct('title', Document) >> iterable
        result.is(iterable)
    }

    void "distinct by field name and read preference switches read preference first"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withPreference = Mock(MongoCollection)
        DistinctIterable<Document> iterable = Mock(DistinctIterable)

        when:
        def result = MongoExtensions.distinct(collection, 'title', ReadPreference.secondary())

        then:
        1 * collection.withReadPreference(ReadPreference.secondary()) >> withPreference
        1 * withPreference.distinct('title', Document) >> iterable
        result.is(iterable)
    }

    void "distinct by field name and query filters the distinct iterable"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DistinctIterable<Document> iterable = Mock(DistinctIterable)

        when:
        def result = MongoExtensions.distinct(collection, 'title', [active: true])

        then:
        1 * collection.distinct('title', Document) >> iterable
        1 * iterable.filter(_ as Bson) >> iterable
        result.is(iterable)
    }

    void "distinct by field name, query and result class requests the typed iterable then filters"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DistinctIterable<String> iterable = Mock(DistinctIterable)

        when:
        def result = MongoExtensions.distinct(collection, 'title', [active: true], String)

        then:
        1 * collection.distinct('title', String) >> iterable
        1 * iterable.filter(_ as Bson) >> iterable
        result.is(iterable)
    }

    void "distinct by field name, query and read preference switches preference before filtering"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withPreference = Mock(MongoCollection)
        DistinctIterable<Document> iterable = Mock(DistinctIterable)

        when:
        def result = MongoExtensions.distinct(collection, 'title', [active: true], ReadPreference.secondary())

        then:
        1 * collection.withReadPreference(ReadPreference.secondary()) >> withPreference
        1 * withPreference.distinct('title', Document) >> iterable
        1 * iterable.filter(_ as Bson) >> iterable
        result.is(iterable)
    }

    void "watch converts each pipeline stage to Bson"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        ChangeStreamIterable<Document> iterable = Mock(ChangeStreamIterable)

        when:
        def result = MongoExtensions.watch(collection, [[$match: [active: true]]])

        then:
        1 * collection.watch({ List<Bson> stages -> stages.size() == 1 }) >> iterable
        result.is(iterable)
    }

    void "watch with a result class converts the pipeline and requests the typed stream"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        ChangeStreamIterable<String> iterable = Mock(ChangeStreamIterable)

        when:
        def result = MongoExtensions.watch(collection, [[$match: [active: true]]], String)

        then:
        1 * collection.watch({ List<Bson> stages -> stages.size() == 1 }, String) >> iterable
        result.is(iterable)
    }

    void "deleteMany converts the query map to Bson"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DeleteResult deleteResult = Mock(DeleteResult)

        when:
        def result = MongoExtensions.deleteMany(collection, [active: false])

        then:
        1 * collection.deleteMany(_ as Bson) >> deleteResult
        result.is(deleteResult)
    }

    void "remove is an alias for deleteMany"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DeleteResult deleteResult = Mock(DeleteResult)

        when:
        def result = MongoExtensions.remove(collection, [active: false])

        then:
        1 * collection.deleteMany(_ as Bson) >> deleteResult
        result.is(deleteResult)
    }

    void "the rightShift operator deletes matching documents and returns the collection"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        def result = MongoExtensions.rightShift(collection, [active: false])

        then:
        1 * collection.deleteMany(_ as Bson) >> Mock(DeleteResult)
        result.is(collection)
    }

    void "deleteMany with a write concern switches write concern before deleting"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)

        when:
        MongoExtensions.deleteMany(collection, [active: false], WriteConcern.MAJORITY)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.deleteMany(_ as Bson) >> Mock(DeleteResult)
    }

    void "deleteOne converts the query map to Bson"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DeleteResult deleteResult = Mock(DeleteResult)

        when:
        def result = MongoExtensions.deleteOne(collection, [active: false])

        then:
        1 * collection.deleteOne(_ as Bson) >> deleteResult
        result.is(deleteResult)
    }

    void "deleteOne with a write concern switches write concern before deleting"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)

        when:
        MongoExtensions.deleteOne(collection, [active: false], WriteConcern.MAJORITY)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.deleteOne(_ as Bson) >> Mock(DeleteResult)
    }

    void "deleteOne with an options map maps the options to DeleteOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.deleteOne(collection, [active: false], [:])

        then:
        1 * collection.deleteOne(_ as Bson, _ as DeleteOptions) >> Mock(DeleteResult)
    }

    void "deleteMany with an options map maps the options to DeleteOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.deleteMany(collection, [active: false], [:])

        then:
        1 * collection.deleteMany(_ as Bson, _ as DeleteOptions) >> Mock(DeleteResult)
    }

    @Unroll
    void "#methodName updates a single document matching the filter"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateResult updateResult = Mock(UpdateResult)

        when:
        def result = MongoExtensions."$methodName"(collection, [_id: 1], [$set: [name: 'Bob']])

        then:
        1 * collection.updateOne(_ as Bson, _ as Bson) >> updateResult
        result.is(updateResult)

        where:
        methodName << ['updateOne', 'update']
    }

    @Unroll
    void "#methodName maps an options map to UpdateOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions."$methodName"(collection, [_id: 1], [$set: [name: 'Bob']], [upsert: true])

        then:
        1 * collection.updateOne(_ as Bson, _ as Bson, { UpdateOptions o -> o.isUpsert() }) >> Mock(UpdateResult)

        where:
        methodName << ['updateOne', 'update']
    }

    @Unroll
    void "#methodName passes an explicit UpdateOptions through unchanged"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateOptions options = new UpdateOptions().upsert(true)

        when:
        MongoExtensions."$methodName"(collection, [_id: 1], [$set: [name: 'Bob']], options)

        then:
        1 * collection.updateOne(_ as Bson, _ as Bson, options) >> Mock(UpdateResult)

        where:
        methodName << ['updateOne', 'update']
    }

    void "updateMany updates every document matching the filter"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateResult updateResult = Mock(UpdateResult)

        when:
        def result = MongoExtensions.updateMany(collection, [active: false], [$set: [active: true]])

        then:
        1 * collection.updateMany(_ as Bson, _ as Bson) >> updateResult
        result.is(updateResult)
    }

    void "updateMany maps an options map to UpdateOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.updateMany(collection, [active: false], [$set: [active: true]], [upsert: true])

        then:
        1 * collection.updateMany(_ as Bson, _ as Bson, { UpdateOptions o -> o.isUpsert() }) >> Mock(UpdateResult)
    }

    void "updateMany passes an explicit UpdateOptions through unchanged"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateOptions options = new UpdateOptions().upsert(true)

        when:
        MongoExtensions.updateMany(collection, [active: false], [$set: [active: true]], options)

        then:
        1 * collection.updateMany(_ as Bson, _ as Bson, options) >> Mock(UpdateResult)
    }

    void "updateOne accepts an aggregation-pipeline style update"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateResult updateResult = Mock(UpdateResult)

        when:
        def result = MongoExtensions.updateOne(collection, [_id: 1], [[$set: [name: 'Bob']]])

        then:
        1 * collection.updateOne(_ as Bson, { List<Bson> stages -> stages.size() == 1 }) >> updateResult
        result.is(updateResult)
    }

    void "updateOne with a pipeline update and an options map maps the options"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.updateOne(collection, [_id: 1], [[$set: [name: 'Bob']]], [upsert: true])

        then:
        1 * collection.updateOne(_ as Bson, _ as List<Bson>, { UpdateOptions o -> o.isUpsert() }) >> Mock(UpdateResult)
    }

    void "updateOne with a pipeline update and explicit UpdateOptions passes them through unchanged"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateOptions options = new UpdateOptions().upsert(true)

        when:
        MongoExtensions.updateOne(collection, [_id: 1], [[$set: [name: 'Bob']]], options)

        then:
        1 * collection.updateOne(_ as Bson, _ as List<Bson>, options) >> Mock(UpdateResult)
    }

    void "updateMany accepts an aggregation-pipeline style update"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateResult updateResult = Mock(UpdateResult)

        when:
        def result = MongoExtensions.updateMany(collection, [active: false], [[$set: [active: true]]])

        then:
        1 * collection.updateMany(_ as Bson, { List<Bson> stages -> stages.size() == 1 }) >> updateResult
        result.is(updateResult)
    }

    void "updateMany with a pipeline update and an options map maps the options"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.updateMany(collection, [active: false], [[$set: [active: true]]], [upsert: true])

        then:
        1 * collection.updateMany(_ as Bson, _ as List<Bson>, { UpdateOptions o -> o.isUpsert() }) >> Mock(UpdateResult)
    }

    void "updateMany with a pipeline update and explicit UpdateOptions passes them through unchanged"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateOptions options = new UpdateOptions().upsert(true)

        when:
        MongoExtensions.updateMany(collection, [active: false], [[$set: [active: true]]], options)

        then:
        1 * collection.updateMany(_ as Bson, _ as List<Bson>, options) >> Mock(UpdateResult)
    }

    void "createIndex with keys and a name builds named IndexOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.createIndex(collection, [name: 1], 'name_idx')

        then:
        1 * collection.createIndex(_ as Bson, { IndexOptions o -> o.getName() == 'name_idx' && !o.isUnique() })
    }

    void "createIndex with keys, a name and unique builds named unique IndexOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.createIndex(collection, [name: 1], 'name_idx', true)

        then:
        1 * collection.createIndex(_ as Bson, { IndexOptions o -> o.getName() == 'name_idx' && o.isUnique() })
    }

    void "createIndex with just keys uses default IndexOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.createIndex(collection, [name: 1])

        then:
        1 * collection.createIndex(_ as Bson)
    }

    void "createIndex with explicit IndexOptions passes them through unchanged"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        IndexOptions options = new IndexOptions().unique(true)

        when:
        MongoExtensions.createIndex(collection, [name: 1], options)

        then:
        1 * collection.createIndex(_ as Bson, options)
    }

    void "createIndex with an options map maps the options to IndexOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.createIndex(collection, [name: 1], [unique: true])

        then:
        1 * collection.createIndex(_ as Bson, { IndexOptions o -> o.isUnique() })
    }

    void "dropIndex with just the index specification uses default options"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.dropIndex(collection, [name: 1])

        then:
        1 * collection.dropIndex(_ as Bson)
    }

    void "dropIndex with an options map maps the options to DropIndexOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.dropIndex(collection, [name: 1], [:])

        then:
        1 * collection.dropIndex(_ as Bson, _ as DropIndexOptions)
    }

    void "dropIndex with explicit DropIndexOptions passes them through unchanged"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        DropIndexOptions options = new DropIndexOptions()

        when:
        MongoExtensions.dropIndex(collection, [name: 1], options)

        then:
        1 * collection.dropIndex(_ as Bson, options)
    }

    void "insert of a single document map wraps it in a list"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.insert(collection, [name: 'Bob'])

        then:
        1 * collection.insertMany({ List<Document> docs -> docs.size() == 1 && docs[0].name == 'Bob' })
    }

    void "insert of a single document map with a write concern switches write concern first"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)

        when:
        def result = MongoExtensions.insert(collection, [name: 'Bob'], WriteConcern.MAJORITY)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.insertMany({ List<Document> docs -> docs.size() == 1 }, null)
        result.is(collection)
    }

    void "insert of varargs documents inserts them all in one call"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.insert(collection, [name: 'Bob'], [name: 'Alice'])

        then:
        1 * collection.insertMany({ List<Document> docs -> docs.size() == 2 })
    }

    void "the leftShift operator inserts varargs documents and returns the collection"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        def result = MongoExtensions.leftShift(collection, [name: 'Bob'])

        then:
        1 * collection.insertMany({ List<Document> docs -> docs.size() == 1 })
        result.is(collection)
    }

    void "insert with a write concern first and varargs documents switches write concern then inserts"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)

        when:
        def result = MongoExtensions.insert(collection, WriteConcern.MAJORITY, [name: 'Bob'])

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.insertMany({ List<Document> docs -> docs.size() == 1 }, null)
        result.is(collection)
    }

    void "insert of a document array with a write concern switches write concern then inserts"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)
        Map[] documents = [[name: 'Bob']] as Map[]

        when:
        def result = MongoExtensions.insert(collection, documents, WriteConcern.MAJORITY)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.insertMany({ List<Document> docs -> docs.size() == 1 }, null)
        result.is(collection)
    }

    void "insert of a document list inserts them and returns the collection"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        def result = MongoExtensions.insert(collection, [[name: 'Bob'], [name: 'Alice']])

        then:
        1 * collection.insertMany({ List<Document> docs -> docs.size() == 2 })
        result.is(collection)
    }

    void "insert of a document list with a write concern switches write concern first"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)

        when:
        def result = MongoExtensions.insert(collection, [[name: 'Bob']], WriteConcern.MAJORITY)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.insertMany({ List<Document> docs -> docs.size() == 1 }, null)
        result.is(collection)
    }

    void "insert of a document list with write concern and insert options passes both through"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)
        InsertManyOptions options = new InsertManyOptions().ordered(false)

        when:
        def result = MongoExtensions.insert(collection, [[name: 'Bob']], WriteConcern.MAJORITY, options)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.insertMany({ List<Document> docs -> docs.size() == 1 }, options)
        result.is(collection)
    }

    void "insert of a document list with insert options only passes them through"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        InsertManyOptions options = new InsertManyOptions().ordered(false)

        when:
        def result = MongoExtensions.insert(collection, [[name: 'Bob']], options)

        then:
        1 * collection.insertMany({ List<Document> docs -> docs.size() == 1 }, options)
        result.is(collection)
    }

    void "save delegates to insert for a single document"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.save(collection, [name: 'Bob'])

        then:
        1 * collection.insertMany({ List<Document> docs -> docs.size() == 1 })
    }

    void "save with a write concern delegates to insert with that write concern"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        MongoCollection<Document> withConcern = Mock(MongoCollection)

        when:
        MongoExtensions.save(collection, [name: 'Bob'], WriteConcern.MAJORITY)

        then:
        1 * collection.withWriteConcern(WriteConcern.MAJORITY) >> withConcern
        1 * withConcern.insertMany({ List<Document> docs -> docs.size() == 1 }, null)
    }

    void "replaceOne converts the filter map to Bson and passes the replacement Document through"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        UpdateResult updateResult = Mock(UpdateResult)
        Document replacement = new Document(name: 'Bob')

        when:
        def result = MongoExtensions.replaceOne(collection, [_id: 1], replacement)

        then:
        1 * collection.replaceOne(_ as Bson, replacement) >> updateResult
        result.is(updateResult)
    }

    void "replaceOne with an options map maps the options to ReplaceOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        Document replacement = new Document(name: 'Bob')

        when:
        MongoExtensions.replaceOne(collection, [_id: 1], replacement, [upsert: true])

        then:
        1 * collection.replaceOne(_ as Bson, replacement, { ReplaceOptions o -> o.isUpsert() }) >> Mock(UpdateResult)
    }

    void "findOneAndDelete converts the filter map to Bson"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        Document deleted = new Document(name: 'Bob')

        when:
        def result = MongoExtensions.findOneAndDelete(collection, [_id: 1])

        then:
        1 * collection.findOneAndDelete(_ as Bson) >> deleted
        result.is(deleted)
    }

    void "findOneAndDelete with an options map maps the options to FindOneAndDeleteOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.findOneAndDelete(collection, [_id: 1], [:])

        then:
        1 * collection.findOneAndDelete(_ as Bson, _ as FindOneAndDeleteOptions) >> new Document()
    }

    void "findOneAndReplace wraps the replacement map in a Document"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        Document replaced = new Document(name: 'Bob')

        when:
        def result = MongoExtensions.findOneAndReplace(collection, [_id: 1], [name: 'Bob'])

        then:
        1 * collection.findOneAndReplace(_ as Bson, { Document d -> d.get('name') == 'Bob' }) >> replaced
        result.is(replaced)
    }

    void "findOneAndReplace with an options map maps the options to FindOneAndReplaceOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.findOneAndReplace(collection, [_id: 1], [name: 'Bob'], [upsert: true])

        then:
        1 * collection.findOneAndReplace(_ as Bson, _ as Document, { FindOneAndReplaceOptions o -> o.isUpsert() }) >> new Document()
    }

    void "findOneAndUpdate wraps the update map in a Document"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)
        Document updated = new Document(name: 'Bob')

        when:
        def result = MongoExtensions.findOneAndUpdate(collection, [_id: 1], [$set: [name: 'Bob']])

        then:
        1 * collection.findOneAndUpdate(_ as Bson, { Document d -> d.containsKey('$set') }) >> updated
        result.is(updated)
    }

    void "findOneAndUpdate with an options map maps the options to FindOneAndUpdateOptions"() {
        given:
        MongoCollection<Document> collection = Mock(MongoCollection)

        when:
        MongoExtensions.findOneAndUpdate(collection, [_id: 1], [$set: [name: 'Bob']], [upsert: true])

        then:
        1 * collection.findOneAndUpdate(_ as Bson, _ as Document, { FindOneAndUpdateOptions o -> o.isUpsert() }) >> new Document()
    }
}

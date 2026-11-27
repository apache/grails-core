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

import java.util.function.Consumer
import java.util.function.Function

import ch.qos.logback.classic.Level
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import grails.gorm.annotation.Entity
import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.Document
import org.bson.types.ObjectId
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.mongo.CapturedLog
import org.grails.datastore.gorm.mongo.MongoGormEnhancer
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.core.connections.InMemoryConnectionSources
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSource
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettingsBuilder
import org.springframework.core.env.PropertyResolver
import spock.lang.TempDir

/**
 * Runs against the MongoDB the test infrastructure starts in a container, which {@code dbContainer} is.
 */
class QueryTailCommandListenerSpec extends AutoStartedMongoSpec {

    static final String DATABASE = 'queryTail'

    @TempDir
    File tempDir

    List<MongoDatastore> datastores = []

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void cleanup() {
        datastores*.close()
    }

    void "an application started without the setting carries no command listener"() {
        when:
        MongoDatastore datastore = open([:])

        then:
        listener(datastore) == null
        tempDir.list().length == 0
    }

    void "commands run with capture enabled are recorded with their data source and thread, without their values"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))

        when:
        QueryTailBook.withNewSession {
            new QueryTailBook(title: 'Grails', attrs: ['secret-key': 'v', '9876543210': 'w'], attributes: [['9876543210': 'x']],
                    address: new QueryTailAddress(city: 'Paris')).save(flush: true)
            QueryTailBook.findAllByTitle('Grails')
            QueryTailBook.createCriteria().list { eq('address.city', 'Paris') }
            QueryTailBook.createCriteria().list { eq('attrs.9876543210', 'w') }
            QueryTailBook.createCriteria().list { eq('attributes.0.9876543210', 'x') }
        }
        List<QueryTailRecord> records = records(file)
        String insert = records.find { it.sql.startsWith("insert ${DATABASE}.queryTailBook ") }?.sql

        then:
        listener(datastore) instanceof QueryTailCommandListener
        insert.contains('"title": ?')
        insert.contains('"address": {"city": ?}')
        insert.contains('"attrs": {?: ?, ?: ?}')
        insert.contains('"attributes": [{?: ?}]')
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"title": ?}') }
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"address.city": ?}') }
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"attrs.?": ?}') }
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"attributes.0.?": ?}') }
        records.every { it.dataSource == ConnectionSource.DEFAULT && it.page == "thread:${Thread.currentThread().name}" && !it.webRequest }
        !file.text.contains('Grails')
        !file.text.contains('secret-key')
        !file.text.contains('9876543210')
        !file.text.contains('Paris')
    }

    void "a command run through the driver is recorded, and the database runs it as written"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        MongoCollection<Document> collection = datastore.mongoClient.getDatabase(DATABASE).getCollection('queryTailBook')

        when:
        collection.insertOne(new Document('title', 'example-secret').append('attrs', new Document('secret-key', 'v')))
        Document found = collection.find(Filters.eq('title', 'example-secret')).first()

        then:
        found.getString('title') == 'example-secret'
        found.get('attrs', Document).getString('secret-key') == 'v'
        records(file).any { it.sql.startsWith("find ${DATABASE}.queryTailBook {\"filter\": {\"title\": ?}") }
        !file.text.contains('example-secret')
        !file.text.contains('secret-key')
    }

    void "map-reduce executes with its scope while capture redacts every key and value inside it"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.getCollection('queryTailBook').insertOne(new Document('title', 'Scoped'))

        when:
        Document result = database.runCommand(Document.parse('''{"mapReduce":"queryTailBook",
                "map":"function() { emit(this.title, context['example-secret'] + title.city + nested[0]['9876543210']); }",
                "reduce":"function(key, values) { return Array.sum(values); }",
                "query":{"title":"Scoped"},"scope":{"context":{"example-secret":2},"title":{"city":3},"nested":[{"9876543210":4}]},
                "out":{"inline":1}}'''))
        String captured = records(file).find { it.sql.startsWith("mapReduce ${DATABASE}.queryTailBook ") }?.sql

        then:
        result.getList('results', Document)*.get('value') == [9.0d]
        captured.contains('"scope": {?: {?: ?}, ?: {?: ?}, ?: [{?: ?}]}')
        captured.contains('"query": {"title": ?}')
        !file.text.contains('example-secret')
        !file.text.contains('9876543210')
        !file.text.contains('Scoped')
    }

    void "dropUser runs on the user it names while capture records that name as a value, not as a collection"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse('{"createUser": "alice@example.com", "pwd": "example-secret", "roles": []}'))

        when:
        Document dropped = database.runCommand(Document.parse('{"dropUser": "alice@example.com"}'))
        Document remaining = database.runCommand(Document.parse('{"usersInfo": "alice@example.com"}'))

        then:
        dropped.get('ok') == 1
        remaining.getList('users', Document).isEmpty()
        records(file)*.sql.containsAll(["dropUser ${DATABASE} {\"dropUser\": ?}".toString(),
                "usersInfo ${DATABASE} {\"usersInfo\": ?}".toString()])
        !file.text.contains('alice@example.com')
        !file.text.contains('example-secret')
    }

    void "a key starting with a dollar in stored data is recorded as data, while the operators of the command are kept"() {
        given: 'MongoDB 5 and later store a field whose name starts with a dollar'
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)

        when: 'the commands are sent as written, past the checks the driver makes on a document it is handed'
        database.runCommand(Document.parse('{"insert": "queryTailBook", "documents": [{"title": "t", "attrs": {"$example-secret": "v"}}]}'))
        database.runCommand(Document.parse('{"update": "queryTailBook", "updates": [{"q": {"title": {"$eq": "t"}}, "u": {"$set": {"attrs": {"$example-secret": "w"}}}}]}'))
        Document stored = database.getCollection('queryTailBook').find(Filters.eq('title', 't')).first()
        List<QueryTailRecord> records = records(file)

        then: 'the database stored the field, and the capture kept every operator but not the key'
        stored.get('attrs', Document).getString('\$example-secret') == 'w'
        records.find { it.sql.startsWith("insert ${DATABASE}.queryTailBook ") }?.sql?.contains('"attrs": {?: ?}')
        records.find { it.sql.startsWith("update ${DATABASE}.queryTailBook ") }?.sql?.contains('"q": {"title": {"\$eq": ?}}, "u": {"\$set": {"attrs": {?: ?}}}')
        !file.text.contains('example-secret')
    }

    void "a key starting with a dollar in the value a condition compares with is recorded as data, while the operators of an expression are kept"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse('{"insert": "queryTailBook", "documents": [{"title": "Compared", "attrs": {"$example-secret": "v"}}]}'))

        when: 'a condition compares the map with a document whose key starts with a dollar, and an expression compares the title'
        Document compared = database.runCommand(Document.parse('{"find": "queryTailBook", "filter": {"attrs": {"$eq": {"$example-secret": "v"}}}}'))
        Document computed = database.runCommand(Document.parse(
                '{"aggregate": "queryTailBook", "pipeline": [{"$match": {"$expr": {"$eq": [{"$toLower": "$title"}, "compared"]}}}], "cursor": {}}'))
        List<QueryTailRecord> records = records(file)

        then: 'the database read the operand of $eq as a value and the operands of the expression as expressions'
        compared.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == ['Compared']
        computed.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == ['Compared']

        and: 'the capture kept every operator but not the key'
        records.find { it.sql.startsWith("find ${DATABASE}.queryTailBook ") }?.sql?.contains('{"filter": {"attrs": {"\$eq": {?: ?}}}')
        records.find { it.sql.startsWith("aggregate ${DATABASE}.queryTailBook ") }?.sql?.contains('{"\$match": {"\$expr": {"\$eq": [{"\$toLower": ?}, ?]}}}')
        !file.text.contains('example-secret')
        !file.text.contains('Compared')
    }

    void "every connection source of the application records to the one capture"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file) +
                [(MongoSettings.SETTING_CONNECTIONS): [reporting: [url: url("${DATABASE}Reporting")]]])

        when:
        probe(datastore.getDatastoreForConnection('reporting'), "${DATABASE}Reporting", 'reporting_probe')
        probe(datastore, DATABASE, 'default_probe')
        List<QueryTailRecord> recorded = records(file)

        then:
        recorded.find { it.sql.startsWith("find ${DATABASE}Reporting.reporting_probe {\"filter\": {\"_id\": ?}") }?.dataSource == 'reporting'
        recorded.find { it.sql.startsWith("find ${DATABASE}.default_probe {\"filter\": {\"_id\": ?}") }?.dataSource == ConnectionSource.DEFAULT

        and: 'one mapping tells the fields of both'
        listener(datastore.getDatastoreForConnection('reporting')).fields.is(listener(datastore).fields)
    }

    void "implicit equality with a #shape operand does not record dollar-prefixed data keys"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse("""{"insert":"queryTailBook","documents":[{"title":"${shape}","${property}":${value}}]}"""))

        when:
        Document found = database.runCommand(Document.parse("""{"find":"queryTailBook","filter":{"${property}":${value}}}"""))
        Document aggregated = database.runCommand(Document.parse("""{"aggregate":"queryTailBook","pipeline":[{"\$match":{"${property}":${value}}}],"cursor":{}}"""))

        then:
        found.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == [shape]
        aggregated.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == [shape]
        records(file).any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains(redacted) }
        records(file).any { it.sql.startsWith("aggregate ${DATABASE}.queryTailBook ") && it.sql.contains(redacted) }
        !file.text.contains('example-secret')

        where:
        shape      | property     | value                                        | redacted
        'document' | 'attrs'      | '{"nested":{"$example-secret":"v"}}'       | '"attrs": {?: {?: ?}}'
        'array'    | 'attributes' | '[{"$example-secret":"v"}]'                | '"attributes": [{?: ?}]'
    }

    void "an element match in a projection records no dollar-prefixed key of the document it compares with"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse('''{"insert":"queryTailBook","documents":[{"title":"Projected",
                "attributes":[{"nested":{"a":2}},{"nested":{"a":1,"$example-secret":"v"}}]}]}'''))

        when: 'a projection returns the first element that matches'
        Document found = database.runCommand(Document.parse('''{"find":"queryTailBook","filter":{"title":"Projected"},
                "projection":{"attributes":{"$elemMatch":{"nested":{"a":1,"$example-secret":"v"}}}}}'''))
        Document projected = found.get('cursor', Document).getList('firstBatch', Document).first()

        then: 'the database matched the element the condition describes'
        projected.getList('attributes', Document)*.get('nested', Document)*.getInteger('a') == [1]

        and: 'the capture kept the operator but no key of the document the element is compared with'
        records(file).any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") &&
                it.sql.contains('"projection": {"attributes": {"$elemMatch": {?: {?: ?, ?: ?}}}}') }
        !file.text.contains('example-secret')
    }

    void "a connection source added at runtime records as the application does"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))

        when:
        datastore.connectionSources.addConnectionSource('runtime', [url: url("${DATABASE}Runtime")])
        MongoDatastore runtime = datastore.getDatastoreForConnection('runtime')
        probe(runtime, "${DATABASE}Runtime", 'runtime_probe')

        then:
        listener(runtime).file == file
        listener(runtime).fields.is(listener(datastore).fields)
        records(file).find { it.sql.startsWith("find ${DATABASE}Runtime.runtime_probe ") }?.dataSource == 'runtime'
    }

    void "connection sources added at runtime, as tenants are, share the one mapping's fields however many are added"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))

        when:
        List<MongoDatastore> tenants = (1..3).collect { int tenant ->
            datastore.connectionSources.addConnectionSource("tenant${tenant}", [url: url("${DATABASE}Tenant${tenant}")])
            datastore.getDatastoreForConnection("tenant${tenant}")
        }

        then: 'each is open, and bound to the fields of the datastore, made once for its mapping'
        tenants.every { listener(it).capture.open }
        tenants.every { listener(it).fields.is(listener(datastore).fields) }
        listener(datastore).fields.is(MongoMappedFields.of(datastore))
    }

    void "a graph lookup's matching document redacts implicit equality operands"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse('{"insert":"queryTailBook","documents":[{"title":"Graph","attrs":{"nested":{"$example-secret":"v"}}}]}'))

        when:
        Document result = database.runCommand(Document.parse('''{"aggregate":"queryTailBook","pipeline":[
                {"$match":{"title":"Graph"}},
                {"$graphLookup":{"from":"queryTailBook","startWith":"$_id","connectFromField":"_id",
                    "connectToField":"_id","as":"related","restrictSearchWithMatch":{"attrs":{"nested":{"$example-secret":"v"}}}}}
                ],"cursor":{}}'''))

        then:
        result.get('cursor', Document).getList('firstBatch', Document).first().getList('related', Document)*.getString('title') == ['Graph']
        records(file).any { it.sql.contains('"$graphLookup"') }
        !file.text.contains('example-secret')
    }

    void "the capture closes with the connection sources, and the next start truncates the file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore first = open(enabled(file) +
                [(MongoSettings.SETTING_CONNECTIONS): [reporting: [url: url("${DATABASE}Reporting")]]])
        probe(first, DATABASE, 'first_run')
        probe(first.getDatastoreForConnection('reporting'), "${DATABASE}Reporting", 'first_run')
        List<QueryTailCapture> captures = [listener(first).capture, listener(first.getDatastoreForConnection('reporting')).capture]
        String firstRun = QueryTailRecord.parseHeader(file.readLines().first())

        expect:
        captures.every { it.open }

        when:
        close(first)

        then: 'neither connection source leaves the file open'
        captures.every { !it.open }

        when:
        MongoDatastore second = open(enabled(file))
        probe(second, DATABASE, 'second_run')

        then: 'the file holds the new run only'
        QueryTailRecord.parseHeader(file.readLines().first()) != firstRun
        records(file).any { it.sql.startsWith("find ${DATABASE}.second_run ") }
        !records(file).any { it.sql.startsWith("find ${DATABASE}.first_run ") }
    }

    void "GeoJSON query geometries do not record dollar-prefixed data keys"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.getCollection('queryTailBook').createIndex(new Document('location', '2dsphere'))
        database.runCommand(Document.parse('{"insert":"queryTailBook","documents":[{"title":"Geometry","location":{"type":"Point","coordinates":[0.75,0.25]}}]}'))

        when:
        Document found = database.runCommand(Document.parse('''{"find":"queryTailBook","filter":{"location":{"$geoWithin":{"$geometry":{
                "type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]],"metadata":{"$example-secret":"v"}
                }}}}}'''))
        Document nearby = database.runCommand(Document.parse('''{"find":"queryTailBook","filter":{"title":"Geometry","location":{"$near":{"$geometry":{
                "type":"Point","coordinates":[0,0],"metadata":{"$example-secret":"v"}
                }}}}}'''))

        then:
        found.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == ['Geometry']
        nearby.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == ['Geometry']
        records(file).any { it.sql.contains('"$geoWithin"') }
        records(file).any { it.sql.contains('"$near"') }
        !file.text.contains('example-secret')
    }

    void "a query GORM writes for a box, a polygon or a circle keeps its operators, while the coordinates of the shape are values"() {
        given:
        File file = new File(tempDir, 'capture.log')
        open(enabled(file), QueryTailBook, QueryTailPlace)
        QueryTailPlace.withNewSession {
            new QueryTailPlace(name: 'example-secret', location: [50, 50]).save(flush: true)
        }

        when:
        List<String> found = QueryTailPlace.withNewSession {
            [QueryTailPlace.findByLocationWithinBox([[40, 30], [60, 70]]),
             QueryTailPlace.createCriteria().get { withinPolygon('location', [[40, 30], [40, 70], [60, 70], [60, 30]]) },
             QueryTailPlace.findByLocationWithinCircle([[40, 30], 40])]*.name
        }
        List<String> finds = records(file)*.sql.findAll { it.startsWith("find ${DATABASE}.queryTailPlace ") }

        then: 'the database found the place inside each shape'
        found == ['example-secret'] * 3

        and: 'the capture kept the operator GORM writes, and the shape'
        finds.any { it.contains('{"location": {"$within": {"$box": [[?]]}}}') }
        finds.any { it.contains('{"location": {"$within": {"$polygon": [[?]]}}}') }
        finds.any { it.contains('{"location": {"$within": {"$center": [[?], ?]}}}') }
        !file.text.contains('example-secret')
    }

    void "a literal written with \$const does not record the keys inside it, while the expression around it is kept"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse('{"insert":"queryTailBook","documents":[{"title":"Constant"}]}'))

        when: 'a projection computes fields from literals whose keys start with a dollar'
        Document result = database.runCommand(Document.parse('''{"aggregate":"queryTailBook","pipeline":[{"$match":{"title":"Constant"}},
                {"$project":{"attrs":{"$const":{"$example-secret":"v","nested":[{"$example-secret":"w"}]}},"attributes":{"$const":[{"$example-secret":"x"}]}}}
                ],"cursor":{}}'''))
        Document projected = result.get('cursor', Document).getList('firstBatch', Document).first()

        then: 'the database read the operand of $const as a literal'
        projected.get('attrs', Document).getString('$example-secret') == 'v'
        projected.get('attrs', Document).getList('nested', Document).first().getString('$example-secret') == 'w'
        projected.getList('attributes', Document).first().getString('$example-secret') == 'x'

        and: 'the capture kept the operator but no key of its operand'
        records(file).find { it.sql.startsWith("aggregate ${DATABASE}.queryTailBook ") }?.sql?.contains(
                '{"$project": {"attrs": {"$const": {?: ?, ?: [{?: ?}]}}, "attributes": {"$const": [{?: ?}]}}}')
        !file.text.contains('example-secret')
    }

    void "a DBRef is compared whole, whichever of its fields comes first: #shape"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def database = datastore.mongoClient.getDatabase(DATABASE)
        database.runCommand(Document.parse("""{"insert":"queryTailReference","documents":[{"title":"${shape}",${stored}}]}"""))

        when:
        Document found = database.runCommand(Document.parse("""{"find":"queryTailReference","filter":${condition},"projection":{"title":1}}"""))
        Document aggregated = database.runCommand(Document.parse(
                """{"aggregate":"queryTailReference","pipeline":[{"\$match":${condition}},{"\$project":{"title":1}}],"cursor":{}}"""))

        then: 'the database matched the reference'
        found.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == [shape]
        aggregated.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == [shape]

        and: 'the capture recorded no key inside the reference'
        records(file).any { it.sql.startsWith("find ${DATABASE}.queryTailReference ") && it.sql.contains("\"filter\": ${redacted}") }
        records(file).any { it.sql.startsWith("aggregate ${DATABASE}.queryTailReference ") && it.sql.contains("{\"\$match\": ${redacted}}") }
        !file.text.contains('example-secret')

        where:
        shape                         | stored                                                                | condition                                                                       || redacted
        'its reference first'         | '"ref":{"$ref":"other","$id":{"$example-secret":"v"}}'               | '{"ref":{"$ref":"other","$id":{"$example-secret":"v"}}}'                       || '{"ref": {?: ?, ?: {?: ?}}}'
        'its id first'                | '"ref":{"$id":{"$example-secret":"v"},"$ref":"other"}'               | '{"ref":{"$id":{"$example-secret":"v"},"$ref":"other"}}'                       || '{"ref": {?: {?: ?}, ?: ?}}'
        'at the top of the condition' | '"$ref":"other","$id":{"nested":{"$example-secret":"v"}}'            | '{"$ref":"other","$id":{"nested":{"$example-secret":"v"}}}'                    || '{?: ?, ?: {?: {?: ?}}}'
        'in an element match'         | '"refs":[{"$id":{"nested":{"$example-secret":"v"}},"$ref":"other"}]' | '{"refs":{"$elemMatch":{"$id":{"nested":{"$example-secret":"v"}},"$ref":"other"}}}' || '{"refs": {"$elemMatch": {?: {?: {?: ?}}, ?: ?}}}'
    }

    void "batches of every size, and lists of values of every length, are recorded as the one statement each is"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def collection = datastore.mongoClient.getDatabase(DATABASE).getCollection('queryTailBatch')

        when:
        collection.insertOne(new Document('title', 'single-1'))
        collection.insertMany((1..2).collect { new Document('title', "small-${it}".toString()) })
        collection.insertMany((1..50).collect { new Document('title', "large-${it}".toString()) })
        List<Document> found = collection.find(new Document('title', new Document('$in', ['small-1', 'large-1', 'large-2']))).into([])
        collection.find(new Document('title', new Document('$in', ['small-2']))).into([])
        List<String> recorded = records(file)*.sql.findAll { it.contains("${DATABASE}.queryTailBatch ") }

        then: 'the database wrote and read every document, and the capture holds one statement for each kind of command'
        collection.countDocuments() == 53
        found.size() == 3
        recorded.count { it.startsWith('insert ') } == 3
        recorded.findAll { it.startsWith('insert ') }.unique().size() == 1
        recorded.find { it.startsWith('insert ') }.contains('"documents": [{?: ?, ?: ?}]')
        recorded.count { it.startsWith('find ') } == 2
        recorded.findAll { it.startsWith('find ') }.unique() == [
                "find ${DATABASE}.queryTailBatch {\"filter\": {\"title\": {\"\$in\": [?]}}}".toString()]
        !file.text.contains('large-')
    }

    void "field names that differ only in the spaces after a quote stay apart in the capture, in a collection named #collectionName"() {
        given: 'documents in a collection no domain class maps, whose field names hold a quote and differ in the spaces after it'
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        def collection = datastore.mongoClient.getDatabase(DATABASE).getCollection(collectionName)
        collection.insertMany([new Document('title', 'wide').append('a"  b', 1), new Document('title', 'narrow').append('a" b', 1)])

        when:
        List<Document> wide = collection.find(new Document('a"  b', 1)).into([])
        List<Document> narrow = collection.find(new Document('a" b', 1)).into([])
        List<QueryTailRecord> finds = records(file).findAll { it.sql.startsWith("find ${DATABASE}.${collectionName} ") }

        then: 'the database told the names apart, and so does the capture, which recorded the commands as documents'
        wide*.getString('title') == ['wide']
        narrow*.getString('title') == ['narrow']
        finds*.sql == ["find ${DATABASE}.${collectionName} {\"filter\": {\"a\\\"  b\": ?}}".toString(),
                       "find ${DATABASE}.${collectionName} {\"filter\": {\"a\\\" b\": ?}}".toString()]
        finds*.document == [true, true]

        where:
        collectionName << ['queryTailFieldName', 'queryTailFieldName(archive)']
    }

    void "commands that have no options left stay apart when their collections differ only in their spaces"() {
        given:
        File file = new File(tempDir, 'capture.log')
        def database = open(enabled(file)).mongoClient.getDatabase(DATABASE)
        database.getCollection('queryTail  spaced').insertOne(new Document('title', 'wide'))
        database.getCollection('queryTail spaced').insertOne(new Document('title', 'narrow'))

        when:
        Document wide = database.runCommand(new Document('find', 'queryTail  spaced'))
        Document narrow = database.runCommand(new Document('find', 'queryTail spaced'))

        then:
        wide.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == ['wide']
        narrow.get('cursor', Document).getList('firstBatch', Document)*.getString('title') == ['narrow']
        records(file)*.sql.findAll { it.startsWith('find ') } == ["find ${DATABASE}.queryTail  spaced".toString(),
                                                                   "find ${DATABASE}.queryTail spaced".toString()]
    }

    void "a client GORM builds from options it is handed records with the mapping bound, until the datastore closes"() {
        given:
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + enabled(file))
        MongoDatastore datastore = new MongoDatastore(MongoClientSettings.builder(), configuration,
                new MongoMappingContext(DATABASE))
        datastores << datastore

        when:
        probe(datastore, DATABASE, 'options_probe')
        QueryTailCommandListener queryTail = listener(datastore)

        then: 'the listener the client was built with belongs to the datastore, which bound its mapping to it'
        queryTail != null
        queryTail.fields != null
        records(file).any { it.sql.startsWith("find ${DATABASE}.options_probe ") }

        when:
        close(datastore)

        then:
        !queryTail.capture.open
    }

    void "a client a factory builds from the settings GORM hands it records with the mapping bound"() {
        given:
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + enabled(file))
        Function<Consumer<MongoClientSettings.Builder>, MongoClient> clientFactory = { Consumer<MongoClientSettings.Builder> gormSettings ->
            MongoClientSettings.Builder builder = MongoClientSettings.builder().applyConnectionString(new ConnectionString(url(DATABASE)))
            gormSettings.accept(builder)
            MongoClients.create(builder.build())
        } as Function<Consumer<MongoClientSettings.Builder>, MongoClient>
        MongoDatastore datastore = MongoDatastore.fromClientFactory(clientFactory, configuration, new MongoMappingContext(DATABASE),
                new DefaultApplicationEventPublisher())
        datastores << datastore

        when:
        probe(datastore, DATABASE, 'factory_probe')
        QueryTailCommandListener queryTail = listener(datastore)

        then:
        queryTail != null
        queryTail.fields != null
        records(file).any { it.sql.startsWith("find ${DATABASE}.factory_probe ") }

        when:
        close(datastore)

        then:
        !queryTail.capture.open
    }

    void "a client GORM is handed already built cannot be captured, which GORM warns of"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoClient client = MongoClients.create(url(DATABASE))
        CapturedLog log = new CapturedLog(MongoDatastore.name, Level.WARN)

        when:
        MongoDatastore datastore = new MongoDatastore(client, DatastoreUtils.createPropertyResolver(enabled(file)), QueryTailBook)
        datastores << datastore

        then:
        log.events*.formattedMessage.any { it.startsWith('Query-tail capture is enabled, but GORM cannot register its listener') }
        !file.exists()

        cleanup:
        log?.close()
        client?.close()
    }

    void "a connection whose client a factory builds #built is #outcome"() {
        given: 'a default connection and another, each built by a connection source factory of the application'
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE),
                ("${MongoSettings.SETTING_CONNECTIONS}.reporting.url".toString()): url(DATABASE)] + enabled(file))
        MongoConnectionSourceFactory factory = registered ? new QueryTailRegisteringConnectionSourceFactory() :
                new QueryTailOwnClientConnectionSourceFactory()
        CapturedLog log = new CapturedLog(MongoDatastore.name, Level.WARN)

        when:
        MongoDatastore datastore = new MongoDatastore(new InMemoryConnectionSources<>(factory.create(ConnectionSource.DEFAULT, configuration),
                factory, configuration), new DefaultApplicationEventPublisher(), QueryTailBook)
        datastores << datastore
        probe(datastore, DATABASE, 'factory_probe')
        List<String> warnings = log.events*.formattedMessage.findAll { it.startsWith('Query-tail capture is enabled, but GORM cannot register') }

        then: 'GORM warns of each connection it captures nothing of, once, though a datastore of its own is built on the other'
        warnings.size() == (registered ? 0 : 2)
        warnings.any { it.contains("connection [${ConnectionSource.DEFAULT}]") } == !registered
        warnings.any { it.contains('connection [reporting]') } == !registered
        file.exists() == registered
        !registered || records(file).any { it.sql.startsWith("find ${DATABASE}.factory_probe ") }

        cleanup:
        log?.close()

        where:
        registered << [false, true]
        built = registered ? 'through MongoConnectionSource.create' : 'itself'
        outcome = registered ? 'captured' : 'not captured, which GORM warns of'
    }

    void "a datastore whose other connection cannot be built fails, capturing nothing: built from #source"() {
        given: 'a default connection GORM builds, and another whose URL cannot be parsed'
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE),
                ("${MongoSettings.SETTING_CONNECTIONS}.reporting.url".toString()): 'not a connection string'] + enabled(file))

        when:
        if (fromOptions) {
            datastores << new MongoDatastore(MongoClientSettings.builder(), configuration, new MongoMappingContext(DATABASE))
        }
        else {
            datastores << MongoDatastore.fromClientFactory({ Consumer<MongoClientSettings.Builder> gormSettings ->
                MongoClients.create(url(DATABASE))
            } as Function<Consumer<MongoClientSettings.Builder>, MongoClient>, configuration, new MongoMappingContext(DATABASE),
                    new DefaultApplicationEventPublisher())
        }

        then:
        thrown(ConfigurationException)
        !file.exists()

        when: 'the next datastore opens the capture'
        probe(open(enabled(file)), DATABASE, 'next_probe')

        then: 'it starts a run of its own'
        runOf(file) != null
        records(file).any { it.sql.startsWith("find ${DATABASE}.next_probe ") }

        where:
        fromOptions << [true, false]
        source = fromOptions ? 'options' : 'a client factory'
    }

    void "a datastore that fails to build opens no capture, neither of its own connection nor of another, which the next datastore built on them opens"() {
        given: 'connection sources built with their listeners, and a datastore built on them that fails as it ends being built'
        File file = new File(tempDir, 'capture.log')
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources = ConnectionSourcesInitializer.create(
                new MongoConnectionSourceFactory(), DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE),
                (MongoSettings.SETTING_CONNECTIONS): [reporting: [url: url("${DATABASE}Reporting")]]] + enabled(file)))

        when:
        new MongoDatastore(connectionSources, new MongoMappingContext(DATABASE), new DefaultApplicationEventPublisher()) {
            @Override
            protected MongoGormEnhancer initialize(MongoConnectionSourceSettings settings) {
                throw new IllegalStateException('cannot be built')
            }
        }

        then:
        thrown(IllegalStateException)
        !file.exists()
        connectionSources.allConnectionSources.every { ((MongoConnectionSource) it).queryTail.capture == null }

        when: 'the next datastore is built on them'
        MongoDatastore datastore = new MongoDatastore(connectionSources, new MongoMappingContext(DATABASE), new DefaultApplicationEventPublisher())
        datastores << datastore
        probe(datastore.getDatastoreForConnection('reporting'), "${DATABASE}Reporting", 'reporting_probe')

        then: 'it opens the captures of both, in a run of its own'
        listener(datastore).capture.open
        listener(datastore.getDatastoreForConnection('reporting')).capture.open
        runOf(file) != null
        records(file).find { it.sql.startsWith("find ${DATABASE}Reporting.reporting_probe ") }?.dataSource == 'reporting'

        cleanup:
        connectionSources?.close()
    }

    void "a datastore that closes leaves another writing to the same file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore first = open(enabled(file))
        MongoDatastore second = open(enabled(file))
        probe(first, DATABASE, 'first_probe')

        when:
        close(first)
        probe(second, DATABASE, 'second_probe')

        then:
        listener(second).capture.open
        records(file).any { it.sql.startsWith("find ${DATABASE}.first_probe ") }
        records(file).any { it.sql.startsWith("find ${DATABASE}.second_probe ") }
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
    }

    void "a client stopped for a checkpoint records to the same capture once started again"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        probe(datastore, DATABASE, 'before_stop')

        when:
        datastore.stop()
        datastore.start()
        probe(datastore, DATABASE, 'after_start')

        then:
        listener(datastore).capture.open
        records(file).any { it.sql.startsWith("find ${DATABASE}.before_stop ") }
        records(file).any { it.sql.startsWith("find ${DATABASE}.after_start ") }
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
    }

    void "a client replaced after a restore records through the listener it was built with, #previously"() {
        given: 'a connection source GORM owns whose client is not one it can restart in place'
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + enabled(file))
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettingsBuilder(configuration).build()
        MongoClientSettings.Builder builder = MongoClientSettings.builder().applyConnectionString(new ConnectionString(url(DATABASE)))
        MongoConnectionSource own = withListener ?
                MongoConnectionSource.create(ConnectionSource.DEFAULT, settings, { Consumer<MongoClientSettings.Builder> gormSettings ->
                    gormSettings.accept(builder)
                    MongoClients.create(builder.build())
                }) :
                new MongoConnectionSource(ConnectionSource.DEFAULT, MongoClients.create(builder.build()), settings)
        QueryTailCommandListener first = own.queryTail
        MongoDatastore datastore = new MongoDatastore(new InMemoryConnectionSources<>(own, new MongoConnectionSourceFactory(),
                configuration), new DefaultApplicationEventPublisher(), QueryTailBook)
        datastores << datastore

        when:
        datastore.stop()
        datastore.start()
        probe(datastore, DATABASE, 'replaced_probe')
        QueryTailCommandListener replacement = listener(datastore)

        then: 'the replacement belongs to the connection source, with the mapping bound, and the closed client\'s is closed'
        replacement != null
        !replacement.is(first)
        replacement.fields != null
        first == null || (!first.capture.open && replacement.fields.is(first.fields))
        records(file).any { it.sql.startsWith("find ${DATABASE}.replaced_probe ") }
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1

        when:
        close(datastore)

        then:
        !replacement.capture.open

        where:
        withListener << [true, false]
        previously = withListener ? 'taking the place of the closed client\'s' : 'when the closed client had none'
    }

    void "a connection whose settings hold no query-tail settings builds its datastore, capturing nothing and warning of nothing"() {
        given: 'a connection source whose settings were built without query-tail settings, as a settings subclass may build them'
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)])
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettingsBuilder(configuration).build()
        settings.queryTail = null
        MongoClient client = MongoClients.create(url(DATABASE))
        DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings> own = new DefaultConnectionSource<>(ConnectionSource.DEFAULT,
                client, settings)
        CapturedLog log = new CapturedLog(MongoDatastore.name, Level.WARN)

        when:
        MongoDatastore datastore = new MongoDatastore(new InMemoryConnectionSources<>(own, new MongoConnectionSourceFactory(),
                configuration), new DefaultApplicationEventPublisher(), QueryTailBook)
        datastores << datastore
        probe(datastore, DATABASE, 'unset_probe')

        then:
        !log.events*.formattedMessage.any { it.startsWith('Query-tail capture is enabled') }

        cleanup:
        log?.close()
    }

    void "a listener built for a restore that the connection source cannot hold is closed at once"() {
        given: 'a connection source of a kind other than the one the factory creates'
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + enabled(file))
        DefaultConnectionSource<MongoClient, MongoConnectionSourceSettings> own = new DefaultConnectionSource<>(ConnectionSource.DEFAULT,
                MongoClients.create(url(DATABASE)), new MongoConnectionSourceSettingsBuilder(configuration).build())
        MongoDatastore datastore = new MongoDatastore(new InMemoryConnectionSources<>(own, new MongoConnectionSourceFactory(),
                configuration), new DefaultApplicationEventPublisher(), QueryTailBook)
        datastores << datastore

        when:
        datastore.stop()
        datastore.start()

        then: 'the listener was closed before it opened the capture, which never touched the file'
        !file.exists()

        when:
        probe(open(enabled(file)), DATABASE, 'next_probe')

        then: 'nothing holds the file, so the next datastore starts a run of its own'
        runOf(file) != null
        records(file).any { it.sql.startsWith("find ${DATABASE}.next_probe ") }
    }

    void "a client a subclass builds with createMongoClient leaves no capture open and records nothing, as GORM warns"() {
        given:
        File file = new File(tempDir, 'capture.log')
        PropertyResolver configuration = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + enabled(file))
        CapturedLog log = new CapturedLog(MongoDatastore.name, Level.WARN)

        when: 'another run is being written to the file as the client is built'
        file.text = QueryTailRecord.header('earlier-run') + '\n'
        MongoDatastore datastore = new QueryTailClientBuildingDatastore(configuration, new MongoMappingContext(DATABASE))
        datastores << datastore
        probe(datastore, DATABASE, 'subclass_probe')

        then: 'the listener the client was built with was closed before it opened the capture, so the file is as it was'
        log.events*.formattedMessage.any { it.startsWith('Query-tail capture is enabled, but GORM cannot register its listener') }
        file.readLines() == [QueryTailRecord.header('earlier-run')]

        when:
        probe(open(enabled(file)), DATABASE, 'next_probe')

        then: 'nothing holds the file, so the next datastore starts a run of its own'
        runOf(file) != 'earlier-run'
        records(file).any { it.sql.startsWith("find ${DATABASE}.next_probe ") }

        cleanup:
        log?.close()
    }

    void "a capture file that cannot be created leaves the datastore starting, recording nothing, and says why"() {
        given:
        File blocker = new File(tempDir, 'blocker')
        blocker.text = 'a file where the directory should be'
        CapturedLog log = new CapturedLog(QueryTailCapture.name, Level.WARN)

        when:
        MongoDatastore datastore = open(enabled(new File(blocker, 'capture.log')))
        probe(datastore, DATABASE, 'blocked_probe')

        then:
        listener(datastore) != null
        !listener(datastore).capture.open
        log.events*.formattedMessage.any { it.startsWith("Cannot capture statements to ${new File(blocker, 'capture.log')} for query-tail") }

        cleanup:
        log?.close()
    }

    void "an entity mapped after the datastore has recorded commands has its fields kept from then on"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore datastore = open(enabled(file))
        MongoCollection<Document> late = datastore.mongoClient.getDatabase(DATABASE).getCollection('queryTailLateBook')
        late.find(Filters.eq('address.city', 'before')).first()

        when:
        datastore.mappingContext.addPersistentEntity(QueryTailLateBook)
        late.find(Filters.eq('address.city', 'after')).first()

        then: 'the field the mapping did not know was recorded as data, and is kept once it does'
        records(file)*.sql.findAll { it.startsWith("find ${DATABASE}.queryTailLateBook ") }
                .collect { it.contains('"address.city": ?') } == [false, true]
    }

    private MongoDatastore open(Map config, Class... classes = [QueryTailBook] as Class[]) {
        MongoDatastore datastore = new MongoDatastore(
                DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + config), classes)
        datastores << datastore
        datastore
    }

    /** The URL of a database on the MongoDB started for the spec. */
    private String url(CharSequence database) {
        dbContainer.getReplicaSetUrl(database.toString())
    }

    private void close(MongoDatastore datastore) {
        datastores.remove(datastore)
        datastore.close()
    }

    private static Map enabled(File file) {
        [(QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): file.path]
    }

    private static QueryTailCommandListener listener(MongoDatastore datastore) {
        ((MongoConnectionSource) datastore.connectionSources.defaultConnectionSource).queryTail
    }

    /** Runs a find by id on a collection, which is recorded as {@code find database.collection {"filter": {"_id": ?}, ...}}. */
    private static void probe(MongoDatastore datastore, String database, String collection) {
        datastore.mongoClient.getDatabase(database).getCollection(collection).find(Filters.eq('_id', new ObjectId())).first()
    }

    /** The run the header of a capture file names. */
    private static String runOf(File file) {
        QueryTailRecord.parseHeader(file.readLines().first())
    }

    private static List<QueryTailRecord> records(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }
}

@Entity
class QueryTailBook {
    ObjectId id
    Long version
    String title
    QueryTailAddress address
    Map<String, String> attrs
    List<Map<String, String>> attributes

    static embedded = ['address']
}

class QueryTailAddress {
    String city
}

@Entity
class QueryTailPlace {
    ObjectId id
    String name
    List location

    static mapping = {
        location geoIndex: true
    }
}

@Entity
class QueryTailLateBook {
    ObjectId id
    QueryTailAddress address

    static embedded = ['address']
}

/**
 * A datastore built as a subclass may build it: around a client from {@link MongoDatastore#createMongoClient}.
 */
class QueryTailClientBuildingDatastore extends MongoDatastore {

    QueryTailClientBuildingDatastore(PropertyResolver configuration, MongoMappingContext mappingContext) {
        super(createDefaultConnectionSources(createMongoClient(configuration, MongoClientSettings.builder(), mappingContext),
                configuration, mappingContext, true), mappingContext, new DefaultApplicationEventPublisher())
    }
}

/**
 * A connection source factory that builds the client itself, around a connection source of its own making, as a
 * subclass written before GORM captured commands does.
 */
class QueryTailOwnClientConnectionSourceFactory extends MongoConnectionSourceFactory {

    @Override
    ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
        new MongoConnectionSource(name, MongoClients.create(MongoClientSettings.builder().applyConnectionString(settings.url).build()), settings)
    }
}

/**
 * A connection source factory that builds the client itself through {@link MongoConnectionSource#create}, which registers
 * GORM's listener on the settings it builds the client from.
 */
class QueryTailRegisteringConnectionSourceFactory extends MongoConnectionSourceFactory {

    @Override
    ConnectionSource<MongoClient, MongoConnectionSourceSettings> create(String name, MongoConnectionSourceSettings settings) {
        MongoConnectionSource.create(name, settings, { Consumer<MongoClientSettings.Builder> gormSettings ->
            MongoClientSettings.Builder builder = MongoClientSettings.builder().applyConnectionString(settings.url)
            gormSettings.accept(builder)
            MongoClients.create(builder.build())
        } as Function<Consumer<MongoClientSettings.Builder>, MongoClient>)
    }
}

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

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import grails.gorm.annotation.Entity
import org.apache.grails.data.core.querytail.QueryTailCapture
import org.apache.grails.data.core.querytail.QueryTailRecord
import org.apache.grails.data.core.querytail.QueryTailSettings
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.Document
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSource
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
            new QueryTailBook(title: 'Grails', attrs: ['secret-key': 'v', '9876543210': 'w'], address: new QueryTailAddress(city: 'Paris')).save(flush: true)
            QueryTailBook.findAllByTitle('Grails')
            QueryTailBook.createCriteria().list { eq('address.city', 'Paris') }
            QueryTailBook.createCriteria().list { eq('attrs.9876543210', 'w') }
        }
        List<QueryTailRecord> records = records(file)
        String insert = records.find { it.sql.startsWith("insert ${DATABASE}.queryTailBook ") }?.sql

        then:
        listener(datastore) instanceof QueryTailCommandListener
        insert.contains('"title": ?')
        insert.contains('"address": {"city": ?}')
        insert.contains('"attrs": {?: ?, ?: ?}')
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"title": ?}') }
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"address.city": ?}') }
        records.any { it.sql.startsWith("find ${DATABASE}.queryTailBook ") && it.sql.contains('"filter": {"attrs.?": ?}') }
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
        listener(runtime).fields != null
        records(file).find { it.sql.startsWith("find ${DATABASE}Runtime.runtime_probe ") }?.dataSource == 'runtime'
    }

    void "the capture closes with the connection sources, and the next start truncates the file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoDatastore first = open(enabled(file) +
                [(MongoSettings.SETTING_CONNECTIONS): [reporting: [url: url("${DATABASE}Reporting")]]])
        probe(first, DATABASE, 'first_run')
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

    private MongoDatastore open(Map config) {
        MongoDatastore datastore = new MongoDatastore(
                DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_URL): url(DATABASE)] + config), QueryTailBook)
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

    static embedded = ['address']
}

class QueryTailAddress {
    String city
}

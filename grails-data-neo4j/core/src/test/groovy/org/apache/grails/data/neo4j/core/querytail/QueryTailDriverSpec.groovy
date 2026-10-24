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
package org.apache.grails.data.neo4j.core.querytail

import grails.gorm.annotation.Entity
import org.apache.grails.data.core.querytail.QueryTailCapture
import org.apache.grails.data.core.querytail.QueryTailRecord
import org.apache.grails.data.core.querytail.QueryTailSettings
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.gorm.neo4j.connections.Neo4jEmbeddedConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Session
import org.neo4j.driver.Transaction
import org.neo4j.driver.TransactionConfig
import org.neo4j.driver.TransactionWork
import org.neo4j.driver.Values
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

class QueryTailDriverSpec extends Specification {

    @TempDir
    File tempDir

    @Shared
    @AutoCleanup
    Neo4jDatastore server

    @Shared
    String boltUrl

    List<Neo4jDatastore> datastores = []

    void setupSpec() {
        server = new Neo4jDatastore([(Settings.SETTING_NEO4J_TYPE)             : Settings.DATABASE_TYPE_EMBEDDED,
                                     (Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL): true], new Class[0])
        Neo4jEmbeddedConnectionSource embedded = (Neo4jEmbeddedConnectionSource) server.connectionSources.defaultConnectionSource
        boltUrl = embedded.serverControls.boltURI().toString()
    }

    void cleanup() {
        datastores*.close()
    }

    void "an application started without the setting carries no recording driver"() {
        when:
        Neo4jDatastore datastore = open([:])

        then:
        !(datastore.boltDriver instanceof QueryTailDriver)
        tempDir.list().length == 0
    }

    void "statements run with capture enabled are recorded with their data source and thread"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore datastore = open(enabled(file))

        when:
        QueryTailBook.withTransaction {
            new QueryTailBook(title: 'Grails').save(flush: true)
            QueryTailBook.list()
        }
        List<QueryTailRecord> records = records(file)

        then:
        datastore.boltDriver instanceof QueryTailDriver
        records.find { it.sql.contains('CREATE') && it.sql.contains('QueryTailBook') }?.dataSource == ConnectionSource.DEFAULT
        records.find { it.sql.startsWith('MATCH') && it.sql.contains('QueryTailBook') }?.dataSource == ConnectionSource.DEFAULT
        records.every { it.page == "thread:${Thread.currentThread().name}" && !it.webRequest }
        !file.text.contains('Grails')
    }

    void "a value written into a statement is not recorded, and the database runs the statement as written"() {
        given:
        File file = new File(tempDir, 'capture.log')
        open(enabled(file))

        when:
        List<String> values = QueryTailBook.withTransaction {
            QueryTailBook.cypherStatic("RETURN 'example-secret' /* api-key=abc123 */ AS v").list()*.get('v')*.asString()
        }

        then:
        values == ['example-secret']
        records(file)*.sql.contains('RETURN ? AS v')
        !file.text.contains('example-secret')
        !file.text.contains('abc123')
    }

    void "statements run on a session, in a transaction and in transaction functions are recorded, and their parameters are not"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Driver driver = open(enabled(file)).boltDriver

        when:
        Session session = driver.session()
        session.run('RETURN 1 AS a').consume()
        session.run('RETURN $x AS b', [x: 'example-secret']).consume()
        session.run('RETURN $x AS c', Values.parameters('x', 'example-secret')).consume()
        session.run(new Query('RETURN 4 AS d')).consume()
        session.run('RETURN 5 AS e', TransactionConfig.empty()).consume()
        session.run('RETURN $x AS f', [x: 'example-secret'], TransactionConfig.empty()).consume()
        session.run(new Query('RETURN 7 AS g'), TransactionConfig.empty()).consume()
        Transaction transaction = session.beginTransaction()
        transaction.run('RETURN 8 AS h').consume()
        transaction.run('RETURN $x AS i', [x: 'example-secret']).consume()
        transaction.run(new Query('RETURN 10 AS j')).consume()
        transaction.commit()
        transaction.close()
        session.readTransaction({ Transaction tx -> tx.run('RETURN 11 AS k').consume() } as TransactionWork)
        session.writeTransaction({ Transaction tx -> tx.run('RETURN 12 AS l', [x: 'example-secret']).consume() } as TransactionWork)
        session.close()
        List<String> recorded = records(file)*.sql

        then:
        recorded.containsAll(['RETURN ? AS a', 'RETURN $x AS b', 'RETURN $x AS c', 'RETURN ? AS d', 'RETURN ? AS e',
                              'RETURN $x AS f', 'RETURN ? AS g', 'RETURN ? AS h', 'RETURN $x AS i', 'RETURN ? AS j',
                              'RETURN ? AS k', 'RETURN ? AS l'])
        !file.text.contains('example-secret')
    }

    void "every connection source of the application records to the one capture"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore datastore = open(enabled(file) + [(Settings.SETTING_CONNECTIONS): [reporting: [url: boltUrl]]])

        when:
        run(datastore.getDatastoreForConnection('reporting') as Neo4jDatastore, 'RETURN 1 AS reporting_probe')
        run(datastore, 'RETURN 2 AS default_probe')
        List<List<String>> recorded = records(file).collect { [it.dataSource, it.sql] }

        then:
        recorded.contains(['reporting', 'RETURN ? AS reporting_probe'])
        recorded.contains([ConnectionSource.DEFAULT, 'RETURN ? AS default_probe'])
    }

    void "a connection source added at runtime records as the application does"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore datastore = open(enabled(file))

        when:
        ConnectionSource<Driver, ?> runtime = datastore.connectionSources.addConnectionSource('runtime', [url: boltUrl])
        Session session = runtime.source.session()
        session.run('RETURN 7 AS runtime_probe').consume()
        session.close()

        then:
        ((QueryTailDriver) runtime.source).file == file
        records(file).find { it.sql == 'RETURN ? AS runtime_probe' }?.dataSource == 'runtime'
    }

    void "the capture closes with the driver, and the next start truncates the file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore first = open(enabled(file) + [(Settings.SETTING_CONNECTIONS): [reporting: [url: boltUrl]]])
        run(first, 'RETURN 1 AS first_run')
        List<QueryTailCapture> captures = [driver(first).capture, driver(first.getDatastoreForConnection('reporting') as Neo4jDatastore).capture]
        String firstRun = QueryTailRecord.parseHeader(file.readLines().first())

        expect:
        captures.every { it.open }

        when:
        close(first)

        then: 'neither connection source leaves the file open'
        captures.every { !it.open }

        when:
        Neo4jDatastore second = open(enabled(file))
        run(second, 'RETURN 2 AS second_run')

        then: 'the file holds the new run only'
        QueryTailRecord.parseHeader(file.readLines().first()) != firstRun
        records(file)*.sql.contains('RETURN ? AS second_run')
        !records(file)*.sql.contains('RETURN ? AS first_run')
    }

    void "a datastore that closes leaves another writing to the same file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore first = open(enabled(file))
        Neo4jDatastore second = open(enabled(file))
        run(first, 'RETURN 1 AS first_probe')

        when:
        close(first)
        run(second, 'RETURN 2 AS second_probe')

        then:
        driver(second).capture.open
        records(file)*.sql.containsAll(['RETURN ? AS first_probe', 'RETURN ? AS second_probe'])
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
    }

    void "the driver of an embedded server records too"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore datastore = new Neo4jDatastore([(Settings.SETTING_NEO4J_TYPE)             : Settings.DATABASE_TYPE_EMBEDDED,
                                                       (Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL): true] + enabled(file), QueryTailBook)
        datastores << datastore

        when:
        run(datastore, 'RETURN 1 AS embedded_probe')

        then:
        datastore.boltDriver instanceof QueryTailDriver
        records(file)*.sql.contains('RETURN ? AS embedded_probe')
    }

    private Neo4jDatastore open(Map config) {
        Neo4jDatastore datastore = new Neo4jDatastore([(Settings.SETTING_NEO4J_URL): boltUrl] + config, QueryTailBook)
        datastores << datastore
        datastore
    }

    private void close(Neo4jDatastore datastore) {
        datastores.remove(datastore)
        datastore.close()
    }

    private static Map enabled(File file) {
        [(QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): file.path]
    }

    private static QueryTailDriver driver(Neo4jDatastore datastore) {
        datastore.boltDriver as QueryTailDriver
    }

    private static void run(Neo4jDatastore datastore, String cypher) {
        Session session = datastore.boltDriver.session()
        try {
            session.run(cypher).consume()
        }
        finally {
            session.close()
        }
    }

    private static List<QueryTailRecord> records(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }
}

@Entity
class QueryTailBook {
    Long id
    Long version
    String title
}

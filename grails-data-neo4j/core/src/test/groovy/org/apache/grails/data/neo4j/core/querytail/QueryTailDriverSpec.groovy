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
import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRedactor
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceFactory
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import org.grails.datastore.gorm.neo4j.connections.Neo4jEmbeddedConnectionSource
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Query
import org.neo4j.driver.Session
import org.neo4j.driver.Transaction
import org.neo4j.driver.TransactionConfig
import org.neo4j.driver.TransactionWork
import org.neo4j.driver.Values
import org.neo4j.driver.exceptions.ClientException
import org.neo4j.harness.ServerControls
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

    void "a capture file that cannot be created leaves the driver running, recording nothing"() {
        given:
        File blocker = new File(tempDir, 'blocker')
        blocker.text = 'not a directory'
        QueryTailSettings settings = new QueryTailSettings(enabled: true, file: new File(blocker, 'capture.log').path)
        Session delegateSession = Mock(Session)
        Driver delegate = Mock(Driver) {
            session() >> delegateSession
        }

        when:
        Driver driver = QueryTailDriver.install(ConnectionSource.DEFAULT, settings, delegate)

        then: 'nothing is opened before the first statement'
        driver instanceof QueryTailDriver
        ((QueryTailDriver) driver).delegate.is(delegate)
        ((QueryTailDriver) driver).capture == null

        when:
        driver.session().run('RETURN 1')

        then: 'the statement runs, and the capture it opened records nothing'
        notThrown(Exception)
        1 * delegateSession.run('RETURN 1')
        0 * delegate.close()
        !((QueryTailDriver) driver).capture.open
    }

    void "a driver the application hands the datastore opens no capture when another connection source cannot be created"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Driver supplied = GraphDatabase.driver(boltUrl, Config.builder().withoutEncryption().build())

        when:
        new Neo4jDatastore(supplied, DatastoreUtils.createPropertyResolver(enabled(file) +
                [(Settings.SETTING_CONNECTIONS): [broken: [url: 'unsupported://localhost']]]), QueryTailBook)

        then: 'the datastore fails before any statement, and leaves no capture behind to join'
        thrown(Exception)
        !file.exists()

        when: 'the application starts again'
        Neo4jDatastore datastore = open(enabled(file))
        run(datastore, 'RETURN 1 AS restarted')

        then: 'it starts a run of its own'
        QueryTailRecord.readRun(file) != null
        records(file)*.sql.contains('RETURN ? AS restarted')

        cleanup:
        supplied?.close()
    }

    void "a driver the application hands the datastore records as one GORM builds, until the datastore closes"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Driver supplied = GraphDatabase.driver(boltUrl, Config.builder().withoutEncryption().build())
        Neo4jDatastore datastore = new Neo4jDatastore(supplied, DatastoreUtils.createPropertyResolver(enabled(file)), QueryTailBook)
        datastores << datastore

        when:
        run(datastore, 'RETURN 1 AS supplied')

        then:
        driver(datastore).delegate.is(supplied)
        records(file).any { it.sql.contains('supplied') }

        when:
        close(datastore)

        then:
        !driver(datastore).capture.open
    }

    void "a recording driver handed to another datastore, as the neo4jDriver bean is, records each statement once"() {
        given: 'a datastore that captures, and the driver it hands out, which records already'
        File file = new File(tempDir, 'capture.log')
        Neo4jDatastore capturing = open(enabled(file))
        Driver recording = capturing.boltDriver

        when:
        Neo4jDatastore datastore = new Neo4jDatastore(recording, DatastoreUtils.createPropertyResolver(enabled(file)), QueryTailBook)
        datastores << datastore
        run(datastore, 'RETURN 1 AS handed_on')

        then: 'it is not wrapped again'
        datastore.boltDriver.is(recording)
        records(file).count { it.sql == 'RETURN ? AS handed_on' } == 1

        and: 'installing capture on it again changes nothing'
        QueryTailDriver.install('other', new QueryTailSettings(enabled: true, file: file.path), recording).is(recording)
    }

    void "a driver the application hands the datastore takes only its query-tail settings from grails.gorm"() {
        given: 'an application whose grails.gorm settings are those of its other datastores, one of which only they can load'
        File file = new File(tempDir, 'capture.log')
        File own = new File(tempDir, 'neo4j-capture.log')
        Driver supplied = GraphDatabase.driver(boltUrl, Config.builder().withoutEncryption().build())
        Map<String, Object> config = enabled(file) + [
                'grails.gorm.multiTenancy.mode'               : 'DISCRIMINATOR',
                'grails.gorm.multiTenancy.tenantResolverClass': 'com.example.OtherDatastoreTenantResolver',
                'grails.gorm.failOnError'                     : 'true',
                'grails.gorm.autoFlush'                       : 'true',
        ]

        when:
        Neo4jDatastore datastore = new Neo4jDatastore(supplied, DatastoreUtils.createPropertyResolver(config), QueryTailBook)
        datastores << datastore
        Neo4jConnectionSourceSettings settings = datastore.connectionSources.defaultConnectionSource.settings

        then: 'it is built, it captures, and its other settings are its own, as they were before query-tail'
        settings.queryTail.enabled
        settings.queryTail.file == file.path
        settings.multiTenancy.mode == MultiTenancySettings.MultiTenancyMode.NONE
        datastore.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
        !settings.failOnError
        !settings.autoFlush

        when: 'grails.neo4j sets query-tail and the others itself'
        Neo4jDatastore configured = new Neo4jDatastore(GraphDatabase.driver(boltUrl, Config.builder().withoutEncryption().build()),
                DatastoreUtils.createPropertyResolver(config + ['grails.neo4j.queryTail.file': own.path, 'grails.neo4j.failOnError': 'true']),
                QueryTailBook)
        datastores << configured
        Neo4jConnectionSourceSettings configuredSettings = configured.connectionSources.defaultConnectionSource.settings

        then: 'its own settings win'
        configuredSettings.queryTail.enabled
        configuredSettings.queryTail.file == own.path
        configuredSettings.failOnError
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

    void "an embedded server whose connection source cannot be created closes its driver, its capture and the server"() {
        given: 'a factory that cannot create the connection source once the server has started and its driver captures'
        File file = new File(tempDir, 'capture.log')
        Neo4jConnectionSourceSettings settings = new Neo4jConnectionSourceSettings(
                type: Neo4jConnectionSourceSettings.ConnectionType.embedded,
                location: new File(tempDir, 'database').path,
                queryTail: new QueryTailSettings(enabled: true, file: file.path))
        List<Object> opened = []
        Neo4jConnectionSourceFactory factory = new Neo4jConnectionSourceFactory() {
            @Override
            protected ConnectionSource<Driver, Neo4jConnectionSourceSettings> createEmbeddedConnectionSource(String name, Driver driver,
                    Neo4jConnectionSourceSettings connectionSettings, ServerControls serverControls) {
                opened << driver << serverControls.boltURI()
                Session session = driver.session()
                try {
                    session.run('RETURN 1 AS before_failure').consume()
                }
                finally {
                    session.close()
                }
                throw new IllegalStateException('cannot create the connection source')
            }
        }

        when:
        factory.create(ConnectionSource.DEFAULT, settings)

        then:
        DatastoreConfigurationException failure = thrown()
        failure.cause instanceof IllegalStateException

        and: 'the capture the driver opened is closed with it, and so is the server'
        QueryTailDriver driver = (QueryTailDriver) opened[0]
        QueryTailRecord.readRun(file) != null
        !driver.capture.open
        !listening((URI) opened[1])
    }

    void "an embedded server whose capture file cannot be created starts, recording nothing"() {
        given:
        File blocker = new File(tempDir, 'blocker')
        blocker.text = 'not a directory'
        Neo4jConnectionSourceSettings settings = new Neo4jConnectionSourceSettings(
                type: Neo4jConnectionSourceSettings.ConnectionType.embedded,
                location: new File(tempDir, 'database').path,
                queryTail: new QueryTailSettings(enabled: true, file: new File(blocker, 'capture.log').path))

        when:
        ConnectionSource<Driver, Neo4jConnectionSourceSettings> embedded = new Neo4jConnectionSourceFactory().create(ConnectionSource.DEFAULT, settings)
        Session session = embedded.source.session()

        then:
        session.run('RETURN 1 AS value').single().get('value').asInt() == 1
        !((QueryTailDriver) embedded.source).capture.open

        cleanup:
        session?.close()
        embedded?.close()
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

    void "a statement whose strings Neo4j 5 may end at a Unicode escape is not recorded, unlike one whose escape ends nothing, and the database runs each as written"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Session session = open(enabled(file)).boltDriver.session()

        when: 'a string this server reads whole, which Neo4j 5 ends at its escape'
        String value = session.run("RETURN 'it" + '\\' + "u0027s example-secret' AS v").single().get('v').asString()

        and: 'a string both read whole, as its escape ends nothing'
        String letter = session.run("RETURN 'caf" + '\\' + "u00e9' AS v").single().get('v').asString()

        and: 'three strings to Neo4j 5, which this server reads as two around a name'
        session.run("RETURN 'a" + '\\' + "u0027 + 'secret' + " + '\\' + "u0027b'").consume()

        then:
        thrown(ClientException)
        value == "it's example-secret"
        letter == 'caf\u00e9'
        records(file)*.sql.count(QueryTailRedactor.WITHHELD) == 2
        records(file)*.sql.contains('RETURN ? AS v')
        !file.text.contains('secret')

        cleanup:
        session?.close()
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
        Neo4jDatastore reporting = first.getDatastoreForConnection('reporting') as Neo4jDatastore
        run(first, 'RETURN 1 AS first_run')
        run(reporting, 'RETURN 1 AS reporting_run')
        List<QueryTailCapture> captures = [driver(first).capture, driver(reporting).capture]
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
        run(second, 'RETURN 1 AS second_opened')

        when:
        close(first)
        run(second, 'RETURN 2 AS second_probe')

        then:
        driver(second).capture.open
        records(file)*.sql.containsAll(['RETURN ? AS first_probe', 'RETURN ? AS second_opened', 'RETURN ? AS second_probe'])
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

    private static boolean listening(URI uri) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.host, uri.port), 1000)
            return true
        }
        catch (IOException ignored) {
            return false
        }
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

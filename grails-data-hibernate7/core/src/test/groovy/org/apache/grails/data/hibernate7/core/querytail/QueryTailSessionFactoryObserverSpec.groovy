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
package org.apache.grails.data.hibernate7.core.querytail

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

import grails.gorm.annotation.Entity
import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailDialects
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceInitiator
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.community.dialect.InformixDialect
import org.hibernate.community.dialect.SybaseAnywhereDialect
import org.hibernate.dialect.CockroachDialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.DB2iDialect
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.HANADialect
import org.hibernate.dialect.HSQLDialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.PostgresPlusDialect
import org.hibernate.dialect.SQLServerDialect
import org.hibernate.dialect.SpannerDialect
import org.hibernate.dialect.SpannerPostgreSQLDialect
import org.hibernate.dialect.SybaseASEDialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.jdbc.connections.internal.DataSourceConnectionProvider
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.hibernate.service.spi.InjectService
import org.hibernate.service.spi.ServiceRegistryAwareService
import org.hibernate.service.spi.ServiceRegistryImplementor
import org.hibernate.service.spi.Startable
import spock.lang.Specification
import spock.lang.TempDir

import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.COCKROACH
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.DB2
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.GOOGLE_SQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.H2
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.HANA
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.HSQLDB
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.INFORMIX
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.MYSQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.ORACLE
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.POSTGRESQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.TRANSACT_SQL
import static org.apache.grails.datamapping.core.querytail.QueryTailSyntax.UNKNOWN

class QueryTailSessionFactoryObserverSpec extends Specification {

    private static final String INSPECTOR = 'hibernate.session_factory.statement_inspector'
    private static final String CONFIG_CLASS = 'hibernate.configClass'

    @TempDir
    File tempDir

    List<HibernateDatastore> datastores = []

    void cleanup() {
        datastores*.close()
        QueryTailRewritingInspector.seen.clear()
        QueryTailNamedInspector.seen.clear()
        QueryTailRecordingObserver.events.clear()
        QueryTailProvidedConfiguration.provided.clear()
        QueryTailInitiatedConfiguration.initiated.clear()
    }

    void "an application started without the setting carries no capture, and its connection provider is Hibernate's own"() {
        when:
        HibernateDatastore datastore = open([:])
        runNative(datastore, 'select 1 as uncaptured')

        then:
        observer(datastore) == null
        !Proxy.isProxyClass(connectionProvider(datastore).getClass())
        statementInspector(datastore) == null
        tempDir.list().length == 0
    }

    void "statements run with capture enabled are recorded with their data source and thread"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file))

        when:
        QueryTailBook.withTransaction {
            new QueryTailBook(title: 'Grails').save(flush: true)
            QueryTailBook.list()
        }
        List<QueryTailRecord> records = records(file)

        then: 'the session factory runs no inspector of the capture'
        observer(datastore) != null
        statementInspector(datastore) == null
        records.find { it.sql.contains('insert into query_tail_book') }?.dataSource == ConnectionSource.DEFAULT
        records.find { it.sql.startsWith('select') && it.sql.contains('query_tail_book') }?.dataSource == ConnectionSource.DEFAULT
        records.every { it.page == "thread:${Thread.currentThread().name}" && !it.webRequest }
    }

    void "a new session opened with capture enabled runs and is recorded, within a transaction and outside one"() {
        given:
        File file = new File(tempDir, 'capture.log')
        open(enabled(file))

        when: 'a new session runs outside a transaction'
        String saved = QueryTailBook.withNewSession {
            QueryTailBook.withTransaction { new QueryTailBook(title: 'Grails').save(flush: true) }
            QueryTailBook.findByTitle('Grails')?.title
        }

        and: 'and within one, beside the session of the transaction'
        Long counted = QueryTailBook.withTransaction {
            QueryTailBook.withNewSession { QueryTailBook.count() }
        }

        then:
        saved == 'Grails'
        counted == 1L
        records(file).any { it.sql.contains('insert into query_tail_book') }
        records(file).count { it.sql.startsWith('select count(') && it.sql.contains('query_tail_book') } == 1
        !file.text.contains('Grails')
    }

    void "every inserted updated and deleted row is counted with batch size #batchSize and session override #sessionBatchSize"() {
        given:
        File file = new File(tempDir, 'capture.log')
        open(enabled(file) + ['hibernate.jdbc.batch_size': batchSize,
                'hibernate.session_factory.statement_inspector': QueryTailRewritingInspector.name], QueryTailBatchBook)

        when:
        QueryTailBatchBook.withTransaction {
            QueryTailBatchBook.withSession { Session session ->
                if (sessionBatchSize != null) {
                    session.jdbcBatchSize = sessionBatchSize
                }
                (1..51).each { int id ->
                    new QueryTailBatchBook(id: id, title: 'example-secret').save(insert: true, failOnError: true)
                }
                session.flush()
                assert QueryTailBatchBook.count() == 51
                List<QueryTailBatchBook> books = QueryTailBatchBook.list()
                books.each { it.title = 'updated-secret' }
                session.flush()
                books.each { it.delete() }
                session.flush()
                assert QueryTailBatchBook.count() == 0
            }
        }
        List<QueryTailRecord> captured = records(file)

        then:
        captured.count { it.sql.startsWith('insert into query_tail_batch_book ') } == 51
        captured.count { it.sql.startsWith('update query_tail_batch_book ') } == 51
        captured.count { it.sql.startsWith('delete from query_tail_batch_book ') } == 51
        captured.every { it.dataSource == ConnectionSource.DEFAULT && it.page == "thread:${Thread.currentThread().name}" }
        QueryTailRewritingInspector.seen.count { it.startsWith('insert into query_tail_batch_book ') } ==
                ((sessionBatchSize == null ? batchSize : sessionBatchSize) > 1 ? 1 : 51)
        !file.text.contains('example-secret')
        !file.text.contains('updated-secret')

        where:
        batchSize | sessionBatchSize
        0         | null
        25        | null
        0         | 25
        25        | 0
    }

    void "JDBC work counts executions and submitted batches but not preparation cleared or abandoned entries"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file))
        Session session = datastore.sessionFactory.openSession()

        when:
        session.doWork { Connection connection ->
            assert connection.unwrap(Connection).is(connection)
            assert connection.isWrapperFor(Connection)
            assert connection == connection
            assert connection.hashCode() == System.identityHashCode(connection)
            connection.prepareStatement('select 1 as unused_probe').close()
            connection.prepareStatement('select ? as repeated_probe').withCloseable { statement ->
                assert statement.connection.is(connection)
                assert statement.unwrap(PreparedStatement).is(statement)
                assert statement.isWrapperFor(PreparedStatement)
                assert statement == statement
                assert statement.hashCode() == System.identityHashCode(statement)
                (1..3).each {
                    statement.setString(1, 'example-secret')
                    statement.executeQuery().withCloseable { result ->
                        assert result.next()
                        assert result.getString(1) == 'example-secret'
                    }
                }
            }
            connection.createStatement().withCloseable { statement ->
                statement.execute('create local temporary table capture_entries (id int primary key)')
                statement.addBatch('insert into capture_entries values (99)')
                statement.clearBatch()
                statement.addBatch('insert into capture_entries values (1)')
                statement.addBatch('insert into capture_entries values (2)')
                assert statement.executeLargeBatch().toList() == [1L, 1L]
                assert statement.executeLargeBatch().length == 0
                statement.addBatch('insert into capture_entries values (98)')
            }
            connection.prepareStatement('insert into capture_entries values (?)').withCloseable { statement ->
                statement.setInt(1, 97)
                statement.addBatch()
                statement.clearBatch()
                (3..5).each { int id ->
                    statement.setInt(1, id)
                    statement.addBatch()
                }
                assert statement.executeBatch().toList() == [1, 1, 1]
                statement.setInt(1, 6)
                statement.addBatch()
                assert statement.executeBatch().toList() == [1]
                assert statement.executeBatch().length == 0
                statement.setInt(1, 7)
                assert statement.executeUpdate() == 1
                statement.setInt(1, 8)
                assert statement.executeLargeUpdate() == 1L
                statement.setInt(1, 1)
                statement.addBatch()
                try {
                    statement.executeBatch()
                    assert false: 'a duplicate primary key must fail'
                }
                catch (SQLException e) {
                    assert e.SQLState == '23505'
                }
                statement.clearBatch()
                statement.setInt(1, 9)
                statement.addBatch()
                assert statement.executeBatch().toList() == [1]
            }
            connection.prepareCall('call abs(-2)').withCloseable { statement ->
                statement.executeQuery().withCloseable { result ->
                    assert result.next()
                    assert result.getInt(1) == 2
                }
            }
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery('select count(*) from capture_entries').withCloseable { result ->
                    assert result.next()
                    assert result.getInt(1) == 9
                }
            }
        }
        List<String> captured = records(file)*.sql

        then:
        captured.count('select ? as repeated_probe') == 3
        captured.count('insert into capture_entries values (?)') == 10
        captured.count('call abs(-?)') == 1
        !captured.any { it.contains('unused_probe') }
        !file.text.contains('example-secret')

        cleanup:
        session?.close()
    }

    void "a configured connection provider keeps its injections lifecycle and original connection identities"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailLifecycleProvider provider = new QueryTailLifecycleProvider()
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.connection.provider_class': provider])

        when:
        List selected = runNative(datastore, 'select 1 as provider_probe')

        then:
        selected == [1]
        provider.events == ['injected', 'registry', 'configured', 'started']
        provider.borrowed.empty
        records(file)*.sql.count('select ? as provider_probe') == 1

        when:
        close(datastore)

        then: 'its own registry stops it, once'
        provider.events.last() == 'stopped'
        provider.events.count('stopped') == 1
        provider.borrowed.empty
    }

    void "a value written into a statement is not recorded, and the database runs the statement as written"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.use_sql_comments': 'true',
                'hibernate.session_factory.statement_inspector': QueryTailRewritingInspector.name])

        when:
        List selected = runNative(datastore, "select 'example-secret' /* api-key=abc123 */")
        QueryTailBook.withTransaction {
            QueryTailBook.executeQuery("from QueryTailBook b where b.title = 'example-secret'")
        }

        then: 'the database received the statements with their values'
        selected == ['example-secret']
        QueryTailRewritingInspector.seen.any { it.contains("select 'example-secret' /* api-key=abc123 */") }
        QueryTailRewritingInspector.seen.any { it.contains('query_tail_book') && it.contains("'example-secret'") }

        and: 'the capture holds neither the value nor the comment'
        records(file)*.sql.contains('select ?')
        records(file).any { it.sql.contains('query_tail_book') && it.sql.contains('title=?') }
        !file.text.contains('example-secret')
        !file.text.contains('abc123')
    }

    void "statements are redacted in the syntax of the dialect"() {
        given:
        HibernateDatastore datastore = open(enabled(new File(tempDir, 'capture.log')))

        expect:
        observer(datastore).capture.syntax == H2
    }

    void "the statements of a #dialect.class.simpleName session factory are redacted as #syntax"() {
        expect:
        QueryTailDialects.syntaxOf(dialect.class) == syntax

        where:
        dialect                        || syntax
        new H2Dialect()                || H2
        new PostgreSQLDialect()        || POSTGRESQL
        new PostgresPlusDialect()      || POSTGRESQL
        new SpannerPostgreSQLDialect() || UNKNOWN
        new CockroachDialect()         || COCKROACH
        new OracleDialect()            || ORACLE
        new DB2Dialect()               || DB2
        new DB2iDialect()              || DB2
        new HSQLDialect()              || HSQLDB
        new HANADialect()              || HANA
        new MySQLDialect()             || MYSQL
        new MariaDBDialect()           || MYSQL
        new SpannerDialect()           || GOOGLE_SQL
        new SQLServerDialect()         || TRANSACT_SQL
        new SybaseASEDialect()         || TRANSACT_SQL
        new SybaseAnywhereDialect()    || UNKNOWN
        new InformixDialect()          || INFORMIX
        new QueryTailCommunityDialect()  || UNKNOWN
        new QueryTailBacktickDialect()   || UNKNOWN
    }

    void "every data source of the application records to the one capture"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) +
                ['dataSources.reporting': [url: 'jdbc:h2:mem:queryTailReporting;LOCK_TIMEOUT=10000']])

        when:
        runNative(datastore.getDatastoreForConnection('reporting'), 'select 1 as reporting_probe')
        runNative(datastore, 'select 2 as default_probe')
        List<List<String>> recorded = records(file).collect { [it.dataSource, it.sql] }

        then:
        recorded.contains(['reporting', 'select ? as reporting_probe'])
        recorded.contains([ConnectionSource.DEFAULT, 'select ? as default_probe'])
    }

    void "a data source added at runtime records as the application does"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file))

        when:
        datastore.connectionSources.addConnectionSource('runtime',
                [url: 'jdbc:h2:mem:queryTailRuntime;LOCK_TIMEOUT=10000', dbCreate: 'create-drop'])
        HibernateDatastore runtime = datastore.getDatastoreForConnection('runtime')
        runNative(runtime, 'select 7 as runtime_probe')

        then:
        observer(runtime).file == file
        records(file).find { it.sql == 'select ? as runtime_probe' }?.dataSource == 'runtime'
    }

    void "the capture closes with the session factories, and the next start truncates the file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore first = open(enabled(file) +
                ['dataSources.reporting': [url: 'jdbc:h2:mem:queryTailClosing;LOCK_TIMEOUT=10000']])
        runNative(first, 'select 1 as first_run')
        List<QueryTailCapture> captures = [observer(first).capture, observer(first.getDatastoreForConnection('reporting')).capture]
        String firstRun = QueryTailRecord.parseHeader(file.readLines().first())

        expect:
        captures.every { it.open }

        when:
        close(first)

        then: 'neither data source leaves the file open'
        captures.every { !it.open }

        when:
        HibernateDatastore second = open(enabled(file))
        runNative(second, 'select 2 as second_run')

        then: 'the file holds the new run only'
        QueryTailRecord.parseHeader(file.readLines().first()) != firstRun
        records(file)*.sql.contains('select ? as second_run')
        !records(file)*.sql.contains('select ? as first_run')
    }

    void "the schema create-drop drops as the session factory closes is not recorded, as the schema it creates is not"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file))
        runNative(datastore, 'select 1 as before_close')

        when:
        close(datastore)

        then: 'what the application ran is recorded, and neither the creation nor the drop of the schema'
        records(file)*.sql.contains('select ? as before_close')
        !records(file).any { it.sql =~ /(?i)^(create|drop|alter) / }
    }

    void "a connection provider a configuration class provides as a service hands out the connections that run the statements recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + [(CONFIG_CLASS): QueryTailProvidedConfiguration.name])

        when:
        List selected = runNative(datastore, 'select 1 as provided_probe')

        then:
        selected == [1]
        QueryTailProvidedConfiguration.provided.size() == 1
        QueryTailProvidedConfiguration.provided.first().handed.get() > 0
        records(file)*.sql.count('select ? as provided_probe') == 1
    }

    void "a connection provider a configuration class initiates hands out the connections that run the statements recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + [(CONFIG_CLASS): QueryTailInitiatedConfiguration.name])

        when:
        List selected = runNative(datastore, 'select 1 as initiated_probe')

        then:
        selected == [1]
        QueryTailInitiatedConfiguration.initiated.size() == 1
        QueryTailInitiatedConfiguration.initiated.first().handed.get() > 0
        records(file)*.sql.count('select ? as initiated_probe') == 1
    }

    void "a datastore that closes leaves another writing to the same file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore first = open(enabled(file) + ['dataSource.url': 'jdbc:h2:mem:queryTailFirst;LOCK_TIMEOUT=10000'])
        HibernateDatastore second = open(enabled(file) + ['dataSource.url': 'jdbc:h2:mem:queryTailSecond;LOCK_TIMEOUT=10000'])
        runNative(first, 'select 1 as first_probe')

        when:
        close(first)
        runNative(second, 'select 2 as second_probe')

        then:
        observer(second).capture.open
        records(file)*.sql.containsAll(['select ? as first_probe', 'select ? as second_probe'])
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
    }

    void "an inspector the application configured by class name keeps running and the statement it returns is recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) +
                ['hibernate.session_factory.statement_inspector': QueryTailRewritingInspector.name])

        when:
        List selected = runNative(datastore, 'select 3')

        then:
        statementInspector(datastore) instanceof QueryTailRewritingInspector
        observer(datastore).dataSourceName == ConnectionSource.DEFAULT
        QueryTailRewritingInspector.seen.contains('select 3')
        selected == [3]
        records(file)*.sql.contains('select ? as rewritten')
    }

    void "an inspector the application registered under a name with Hibernate keeps running and the statement it returns is recorded"() {
        given: 'the name runs the inspector without the capture'
        HibernateDatastore uncaptured = open([(CONFIG_CLASS): QueryTailAliasConfiguration.name])
        List uncapturedSelected = runNative(uncaptured, 'select 3')
        close(uncaptured)

        when:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + [(CONFIG_CLASS): QueryTailAliasConfiguration.name])
        List selected = runNative(datastore, 'select 3')

        then:
        statementInspector(datastore) instanceof QueryTailRewritingInspector
        selected == uncapturedSelected
        QueryTailRewritingInspector.seen.count('select 3') == 2
        records(file)*.sql.contains('select ? as rewritten')
    }

    void "an inspector the application configured as an instance is the one the session factory runs"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRewritingInspector configured = new QueryTailRewritingInspector()
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.session_factory.statement_inspector': configured])

        when:
        List selected = runNative(datastore, 'select 3')

        then:
        statementInspector(datastore).is(configured)
        selected == [3]
        records(file)*.sql.contains('select ? as rewritten')
    }

    void "an inspector a configClass sets on the configuration keeps running and the statement it returns is recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.configClass': QueryTailInspectedConfiguration.name])

        when:
        List selected = runNative(datastore, 'select 3')

        then:
        statementInspector(datastore) instanceof QueryTailRewritingInspector
        QueryTailRewritingInspector.seen.contains('select 3')
        selected == [3]
        records(file)*.sql.contains('select ? as rewritten')
    }

    void "an inspector a configClass sets in the settings of the service registry keeps running and the statement it returns is recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.configClass': QueryTailRegistryConfiguration.name])

        when:
        List selected = runNative(datastore, 'select 3')

        then:
        statementInspector(datastore) instanceof QueryTailRewritingInspector
        QueryTailRewritingInspector.seen.contains('select 3')
        selected == [3]
        records(file)*.sql.contains('select ? as rewritten')
    }

    void "the session factory runs the inspector it runs without the capture, set #place"() {
        given:
        HibernateDatastore uncaptured = open(config)
        Class<?> runs = statementInspector(uncaptured)?.getClass()
        close(uncaptured)

        when:
        HibernateDatastore captured = open(enabled(new File(tempDir, 'capture.log')) + config)

        then:
        runs == type
        statementInspector(captured)?.getClass() == type

        where:
        place                                                      | config                                                                                            || type
        'nowhere'                                                  | [:]                                                                                               || null
        'with the property'                                        | [(INSPECTOR): QueryTailRewritingInspector.name]                                                   || QueryTailRewritingInspector
        'in the settings of the service registry'                  | [(CONFIG_CLASS): QueryTailRegistryConfiguration.name]                                             || QueryTailRewritingInspector
        'in the settings of the service registry and the property' | [(CONFIG_CLASS): QueryTailRegistryConfiguration.name, (INSPECTOR): QueryTailNamedInspector.name]  || QueryTailNamedInspector
        'under a name registered with the strategy selector'       | [(CONFIG_CLASS): QueryTailAliasConfiguration.name]                                                || QueryTailRewritingInspector
        'on the configuration'                                     | [(CONFIG_CLASS): QueryTailInspectedConfiguration.name]                                            || QueryTailRewritingInspector
        'on the configuration and with the property'               | [(CONFIG_CLASS): QueryTailInspectedConfiguration.name, (INSPECTOR): QueryTailNamedInspector.name] || QueryTailRewritingInspector
        'on the configuration as it builds the session factory'    | [(CONFIG_CLASS): QueryTailBuildInspectedConfiguration.name]                                       || QueryTailRewritingInspector
    }

    void "an observer the application set on the configuration #set keeps being told, and the capture still closes with the session factory"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) + [(CONFIG_CLASS): configuration.name])
        QueryTailSessionFactoryObserver observer = observer(datastore)
        runNative(datastore, 'select 1 as first_run')
        String firstRun = QueryTailRecord.parseHeader(file.readLines().first())

        expect:
        QueryTailRecordingObserver.events == ['created']

        when:
        close(datastore)

        then:
        QueryTailRecordingObserver.events == ['created', 'closing', 'closed']
        !observer.capture.open

        when: 'the file is captured again'
        runNative(open(enabled(file)), 'select 2 as second_run')

        then: 'a new run starts, without the statements of the closed one'
        QueryTailRecord.parseHeader(file.readLines().first()) != firstRun
        records(file)*.sql.contains('select ? as second_run')
        !records(file)*.sql.contains('select ? as first_run')

        where:
        set                                | configuration
        'when it is created'               | QueryTailObservedConfiguration
        'as it builds the session factory' | QueryTailBuildObservedConfiguration
    }

    void "a configured inspector that cannot be loaded or is not one fails the configuration as it does without the capture, and opens no capture: [#configured]"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        open([(INSPECTOR): configured])

        then:
        Exception uncaptured = thrown()

        when:
        open(enabled(file) + [(INSPECTOR): configured])

        then:
        Exception captured = thrown()
        rootCause(captured).getClass() == rootCause(uncaptured).getClass()
        rootCause(captured).message == rootCause(uncaptured).message
        !file.exists()

        where:
        configured << ['does.not.Exist', '', " ${QueryTailRewritingInspector.name} ", QueryTailBook.name, QueryTailBook]
    }

    private HibernateDatastore open(Map config, Class... entities) {
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([(Settings.SETTING_DB_CREATE): 'create-drop'] + config),
                entities.length == 0 ? [QueryTailBook] as Class[] : entities)
        datastores << datastore
        datastore
    }

    private void close(HibernateDatastore datastore) {
        datastores.remove(datastore)
        datastore.close()
    }

    private static Map enabled(File file) {
        [(QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): file.path]
    }

    private static StatementInspector statementInspector(HibernateDatastore datastore) {
        ((SessionFactoryImplementor) datastore.sessionFactory).sessionFactoryOptions.statementInspector
    }

    private static QueryTailSessionFactoryObserver observer(HibernateDatastore datastore) {
        ((SessionFactoryImplementor) datastore.sessionFactory).sessionFactoryOptions.sessionFactoryObservers
                .find { it instanceof QueryTailSessionFactoryObserver } as QueryTailSessionFactoryObserver
    }

    private static ConnectionProvider connectionProvider(HibernateDatastore datastore) {
        ((SessionFactoryImplementor) datastore.sessionFactory).serviceRegistry.getService(ConnectionProvider)
    }

    private static List runNative(HibernateDatastore datastore, String sql) {
        Session session = datastore.sessionFactory.openSession()
        try {
            session.createNativeQuery(sql).list()
        }
        finally {
            session.close()
        }
    }

    private static List<QueryTailRecord> records(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable root = e
        while (root.cause != null && !root.cause.is(root)) {
            root = root.cause
        }
        root
    }
}

@Entity
class QueryTailBook {
    Long id
    Long version
    String title
}

@Entity
class QueryTailBatchBook {
    Long id
    Long version
    String title

    static mapping = {
        id generator: 'assigned'
    }
}

class QueryTailLifecycleProvider extends DataSourceConnectionProvider implements ServiceRegistryAwareService, Startable {

    final List<String> events = []
    final Set<Connection> borrowed = Collections.newSetFromMap(new IdentityHashMap<Connection, Boolean>())

    @InjectService
    void classLoaderService(ClassLoaderService service) {
        assert service != null
        events << 'injected'
    }

    @Override
    void injectServices(ServiceRegistryImplementor registry) {
        assert registry != null
        events << 'registry'
    }

    @Override
    void configure(Map settings) {
        super.configure(settings)
        events << 'configured'
    }

    @Override
    void start() {
        events << 'started'
    }

    @Override
    Connection getConnection() {
        Connection connection = super.getConnection()
        borrowed.add(connection)
        connection
    }

    @Override
    void closeConnection(Connection connection) {
        assert borrowed.remove(connection)
        super.closeConnection(connection)
    }

    @Override
    void stop() {
        events << 'stopped'
        super.stop()
    }
}

class QueryTailRewritingInspector implements StatementInspector {

    static final List<String> seen = [].asSynchronized()

    @Override
    String inspect(String sql) {
        seen << sql
        sql == 'select 3' ? 'select 3 as rewritten' : sql
    }
}

class QueryTailNamedInspector implements StatementInspector {

    static final List<String> seen = [].asSynchronized()

    @Override
    String inspect(String sql) {
        seen << sql
        sql
    }
}

class QueryTailInspectedConfiguration extends HibernateMappingContextConfiguration {

    QueryTailInspectedConfiguration() {
        setStatementInspector(new QueryTailRewritingInspector())
    }
}

class QueryTailRegistryConfiguration extends HibernateMappingContextConfiguration {

    @Override
    protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder(BootstrapServiceRegistry bootstrapServiceRegistry) {
        super.createStandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySetting('hibernate.session_factory.statement_inspector', new QueryTailRewritingInspector())
    }
}

class QueryTailBuildInspectedConfiguration extends HibernateMappingContextConfiguration {

    @Override
    SessionFactory buildSessionFactory() {
        setStatementInspector(new QueryTailRewritingInspector())
        super.buildSessionFactory()
    }
}

class QueryTailObservedConfiguration extends HibernateMappingContextConfiguration {

    QueryTailObservedConfiguration() {
        setSessionFactoryObserver(new QueryTailRecordingObserver())
    }
}

/**
 * Gives the service registry a connection provider of its own as a service, which takes the place of the one
 * Hibernate would select.
 */
class QueryTailProvidedConfiguration extends HibernateMappingContextConfiguration {

    static final List<QueryTailProvidedProvider> provided = [].asSynchronized()

    @Override
    protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder(BootstrapServiceRegistry bootstrapServiceRegistry) {
        QueryTailProvidedProvider provider = new QueryTailProvidedProvider((DataSource) properties.values().find { it instanceof DataSource })
        provided << provider
        super.createStandardServiceRegistryBuilder(bootstrapServiceRegistry).addService(ConnectionProvider, provider)
    }
}

/**
 * Gives the service registry an initiator of its own for the connection provider, which takes the place of
 * Hibernate's.
 */
class QueryTailInitiatedConfiguration extends HibernateMappingContextConfiguration {

    static final List<QueryTailProvidedProvider> initiated = [].asSynchronized()

    @Override
    protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder(BootstrapServiceRegistry bootstrapServiceRegistry) {
        DataSource dataSource = (DataSource) properties.values().find { it instanceof DataSource }
        super.createStandardServiceRegistryBuilder(bootstrapServiceRegistry).addInitiator(new QueryTailProvidingInitiator(dataSource))
    }
}

/**
 * Initiates a provider of the connections of a data source.
 */
class QueryTailProvidingInitiator implements StandardServiceInitiator<ConnectionProvider> {

    final DataSource dataSource

    QueryTailProvidingInitiator(DataSource dataSource) {
        this.dataSource = dataSource
    }

    @Override
    Class<ConnectionProvider> getServiceInitiated() {
        ConnectionProvider
    }

    @Override
    ConnectionProvider initiateService(Map configurationValues, ServiceRegistryImplementor registry) {
        QueryTailProvidedProvider provider = new QueryTailProvidedProvider(dataSource)
        QueryTailInitiatedConfiguration.initiated << provider
        provider
    }
}

/**
 * Hands out the connections of a data source, counting them.
 */
class QueryTailProvidedProvider implements ConnectionProvider {

    final DataSource dataSource
    final AtomicInteger handed = new AtomicInteger()

    QueryTailProvidedProvider(DataSource dataSource) {
        this.dataSource = dataSource
    }

    @Override
    Connection getConnection() {
        handed.incrementAndGet()
        dataSource.connection
    }

    @Override
    void closeConnection(Connection connection) {
        connection.close()
    }

    @Override
    boolean supportsAggressiveRelease() {
        false
    }

    @Override
    boolean isUnwrappableAs(Class<?> type) {
        type.isInstance(this) || type.isInstance(dataSource)
    }

    @Override
    <T> T unwrap(Class<T> type) {
        type.isInstance(this) ? type.cast(this) : type.cast(dataSource)
    }
}

class QueryTailBuildObservedConfiguration extends HibernateMappingContextConfiguration {

    @Override
    SessionFactory buildSessionFactory() {
        setSessionFactoryObserver(new QueryTailRecordingObserver())
        super.buildSessionFactory()
    }
}

/**
 * Selects its inspector by a name it registers with the strategy selector of Hibernate, as an integration may.
 */
class QueryTailAliasConfiguration extends HibernateMappingContextConfiguration {

    QueryTailAliasConfiguration() {
        properties.put('hibernate.session_factory.statement_inspector', 'query-tail-alias')
    }

    @Override
    protected BootstrapServiceRegistryBuilder createBootstrapServiceRegistryBuilder() {
        super.createBootstrapServiceRegistryBuilder().applyStrategySelector(StatementInspector, 'query-tail-alias', QueryTailRewritingInspector)
    }
}

class QueryTailRecordingObserver implements SessionFactoryObserver {

    static final List<String> events = [].asSynchronized()

    @Override
    void sessionFactoryCreated(SessionFactory factory) {
        events << 'created'
    }

    @Override
    void sessionFactoryClosing(SessionFactory factory) {
        events << 'closing'
    }

    @Override
    void sessionFactoryClosed(SessionFactory factory) {
        events << 'closed'
    }
}

/**
 * A dialect GORM does not know, such as SQLite's or Informix's, which quote identifiers with double quotes.
 */
class QueryTailCommunityDialect extends Dialect {
}

/**
 * A dialect GORM does not know that quotes identifiers with backticks, as a community dialect for
 * BigQuery would: its database may read strings as MySQL does, or as GoogleSQL does.
 */
class QueryTailBacktickDialect extends Dialect {

    @Override
    char openQuote() {
        '`' as char
    }

    @Override
    char closeQuote() {
        '`' as char
    }
}

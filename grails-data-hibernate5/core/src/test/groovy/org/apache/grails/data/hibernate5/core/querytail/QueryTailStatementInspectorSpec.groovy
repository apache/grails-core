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
package org.apache.grails.data.hibernate5.core.querytail

import grails.gorm.annotation.Entity
import org.apache.grails.data.core.querytail.QueryTailCapture
import org.apache.grails.data.core.querytail.QueryTailRecord
import org.apache.grails.data.core.querytail.QueryTailSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.dialect.CockroachDB201Dialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.DerbyTenSevenDialect
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.HANAColumnStoreDialect
import org.hibernate.dialect.HSQLDialect
import org.hibernate.dialect.InformixDialect
import org.hibernate.dialect.MariaDB103Dialect
import org.hibernate.dialect.MySQL8Dialect
import org.hibernate.dialect.Oracle12cDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQL10Dialect
import org.hibernate.dialect.SQLServer2012Dialect
import org.hibernate.dialect.SybaseASE15Dialect
import org.hibernate.dialect.SybaseAnywhereDialect
import org.hibernate.dialect.TeradataDialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.Specification
import spock.lang.TempDir

import static org.apache.grails.data.core.querytail.QueryTailSyntax.COCKROACH
import static org.apache.grails.data.core.querytail.QueryTailSyntax.DB2
import static org.apache.grails.data.core.querytail.QueryTailSyntax.H2
import static org.apache.grails.data.core.querytail.QueryTailSyntax.HANA
import static org.apache.grails.data.core.querytail.QueryTailSyntax.HSQLDB
import static org.apache.grails.data.core.querytail.QueryTailSyntax.INFORMIX
import static org.apache.grails.data.core.querytail.QueryTailSyntax.MYSQL
import static org.apache.grails.data.core.querytail.QueryTailSyntax.ORACLE
import static org.apache.grails.data.core.querytail.QueryTailSyntax.POSTGRESQL
import static org.apache.grails.data.core.querytail.QueryTailSyntax.TRANSACT_SQL
import static org.apache.grails.data.core.querytail.QueryTailSyntax.UNKNOWN

class QueryTailStatementInspectorSpec extends Specification {

    @TempDir
    File tempDir

    List<HibernateDatastore> datastores = []

    void cleanup() {
        datastores*.close()
        QueryTailRewritingInspector.seen.clear()
        QueryTailRecordingObserver.events.clear()
    }

    void "an application started without the setting carries no statement inspector"() {
        when:
        HibernateDatastore datastore = open([:])

        then:
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

        then:
        statementInspector(datastore) instanceof QueryTailStatementInspector
        records.find { it.sql.contains('insert into query_tail_book') }?.dataSource == ConnectionSource.DEFAULT
        records.find { it.sql.startsWith('select') && it.sql.contains('query_tail_book') }?.dataSource == ConnectionSource.DEFAULT
        records.every { it.page == "thread:${Thread.currentThread().name}" && !it.webRequest }
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
        inspector(datastore).capture.syntax == H2
    }

    void "the statements of a #dialect.class.simpleName session factory are redacted as #syntax"() {
        expect:
        QueryTailStatementInspector.syntaxOf(dialect) == syntax

        where:
        dialect                      || syntax
        new H2Dialect()              || H2
        new PostgreSQL10Dialect()    || POSTGRESQL
        new CockroachDB201Dialect()  || COCKROACH
        new Oracle12cDialect()       || ORACLE
        new OracleDialect()          || ORACLE
        new DB2Dialect()             || DB2
        new DerbyTenSevenDialect()   || DB2
        new HSQLDialect()            || HSQLDB
        new HANAColumnStoreDialect() || HANA
        new MySQL8Dialect()          || MYSQL
        new MariaDB103Dialect()      || MYSQL
        new SQLServer2012Dialect()   || TRANSACT_SQL
        new SybaseASE15Dialect()     || TRANSACT_SQL
        new SybaseAnywhereDialect()  || UNKNOWN
        new InformixDialect()        || INFORMIX
        new TeradataDialect()        || UNKNOWN
        new QueryTailSpannerDialect()  || UNKNOWN
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
        inspector(runtime).file == file
        records(file).find { it.sql == 'select ? as runtime_probe' }?.dataSource == 'runtime'
    }

    void "the capture closes with the session factories, and the next start truncates the file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore first = open(enabled(file) +
                ['dataSources.reporting': [url: 'jdbc:h2:mem:queryTailClosing;LOCK_TIMEOUT=10000']])
        runNative(first, 'select 1 as first_run')
        List<QueryTailCapture> captures = [inspector(first).capture, inspector(first.getDatastoreForConnection('reporting')).capture]
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
        inspector(second).capture.open
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
        QueryTailStatementInspector inspector = inspector(datastore)
        inspector.delegate instanceof QueryTailRewritingInspector
        inspector.dataSourceName == ConnectionSource.DEFAULT
        QueryTailRewritingInspector.seen.contains('select 3')
        selected == [3]
        records(file)*.sql.contains('select ? as rewritten')
    }

    void "an inspector the application configured as an instance is the delegate"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRewritingInspector configured = new QueryTailRewritingInspector()
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.session_factory.statement_inspector': configured])

        expect:
        inspector(datastore).delegate.is(configured)
    }

    void "an observer the application set on the configuration keeps being told"() {
        given:
        HibernateDatastore datastore = open(enabled(new File(tempDir, 'capture.log')) +
                ['hibernate.configClass': QueryTailObservedConfiguration.name])
        QueryTailStatementInspector inspector = inspector(datastore)

        expect:
        inspector.observer instanceof QueryTailRecordingObserver
        QueryTailRecordingObserver.events == ['created']

        when:
        close(datastore)

        then:
        QueryTailRecordingObserver.events == ['created', 'closing', 'closed']
        !inspector.capture.open
    }

    void "a configured inspector that cannot be loaded fails the configuration and opens no capture"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        open(enabled(file) + ['hibernate.session_factory.statement_inspector': 'does.not.Exist'])

        then:
        Exception e = thrown()
        messages(e).any { it.contains('Cannot load the statement inspector [does.not.Exist]') }
        !file.exists()
    }

    private HibernateDatastore open(Map config) {
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([(Settings.SETTING_DB_CREATE): 'create-drop'] + config), QueryTailBook)
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

    private static QueryTailStatementInspector inspector(HibernateDatastore datastore) {
        statementInspector(datastore) as QueryTailStatementInspector
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

    private static List<String> messages(Throwable e) {
        List<String> messages = []
        for (Throwable t = e; t != null && !messages.contains(String.valueOf(t.message)); t = t.cause) {
            messages << String.valueOf(t.message)
        }
        messages
    }
}

@Entity
class QueryTailBook {
    Long id
    Long version
    String title
}

class QueryTailRewritingInspector implements StatementInspector {

    static final List<String> seen = [].asSynchronized()

    @Override
    String inspect(String sql) {
        seen << sql
        sql == 'select 3' ? 'select 3 as rewritten' : sql
    }
}

class QueryTailObservedConfiguration extends HibernateMappingContextConfiguration {

    QueryTailObservedConfiguration() {
        setSessionFactoryObserver(new QueryTailRecordingObserver())
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
 * A dialect that is not Hibernate's, such as the Cloud Spanner dialect for Hibernate 5, which quotes
 * identifiers with backticks: its GoogleSQL strings can be triple-quoted, which is not read as MySQL.
 */
class QueryTailSpannerDialect extends Dialect {

    @Override
    char openQuote() {
        '`' as char
    }

    @Override
    char closeQuote() {
        '`' as char
    }
}

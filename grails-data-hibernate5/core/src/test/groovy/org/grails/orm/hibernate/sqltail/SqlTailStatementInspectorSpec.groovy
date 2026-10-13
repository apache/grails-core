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
package org.grails.orm.hibernate.sqltail

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord
import org.grails.datastore.gorm.jdbc.sqltail.SqlTailSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.Specification
import spock.lang.TempDir

class SqlTailStatementInspectorSpec extends Specification {

    @TempDir
    File tempDir

    List<HibernateDatastore> datastores = []

    void cleanup() {
        datastores*.close()
        SqlTailCommentingInspector.seen.clear()
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
        SqlTailBook.withTransaction {
            new SqlTailBook(title: 'Grails').save(flush: true)
            SqlTailBook.list()
        }
        List<SqlTailRecord> records = records(file)

        then:
        statementInspector(datastore) instanceof SqlTailStatementInspector
        records.find { it.sql.contains('insert into sql_tail_book') }?.dataSource == ConnectionSource.DEFAULT
        records.find { it.sql.startsWith('select') && it.sql.contains('sql_tail_book') }?.dataSource == ConnectionSource.DEFAULT
        records.every { it.page == "thread:${Thread.currentThread().name}" && !it.webRequest }
    }

    void "every data source of the application records to the one capture"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) +
                ['dataSources.reporting': [url: 'jdbc:h2:mem:sqlTailReporting;LOCK_TIMEOUT=10000']])

        when:
        runNative(datastore.getDatastoreForConnection('reporting'), 'select 1')
        runNative(datastore, 'select 2')
        List<List<String>> recorded = records(file).collect { [it.dataSource, it.sql] }

        then:
        recorded.contains(['reporting', 'select 1'])
        recorded.contains([ConnectionSource.DEFAULT, 'select 2'])
    }

    void "an inspector the application configured by class name keeps running and the statement it returns is recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateDatastore datastore = open(enabled(file) +
                ['hibernate.session_factory.statement_inspector': SqlTailCommentingInspector.name])

        when:
        runNative(datastore, 'select 3')

        then:
        SqlTailStatementInspector inspector = statementInspector(datastore) as SqlTailStatementInspector
        inspector.delegate instanceof SqlTailCommentingInspector
        inspector.dataSourceName == ConnectionSource.DEFAULT
        SqlTailCommentingInspector.seen.contains('select 3')
        records(file)*.sql.contains('/* commented */ select 3')
    }

    void "an inspector the application configured as an instance is the delegate"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SqlTailCommentingInspector configured = new SqlTailCommentingInspector()
        HibernateDatastore datastore = open(enabled(file) + ['hibernate.session_factory.statement_inspector': configured])

        expect:
        (statementInspector(datastore) as SqlTailStatementInspector).delegate.is(configured)
    }

    void "a configured inspector that cannot be loaded fails the configuration"() {
        when:
        open(enabled(new File(tempDir, 'capture.log')) +
                ['hibernate.session_factory.statement_inspector': 'does.not.Exist'])

        then:
        Exception e = thrown()
        messages(e).any { it.contains('Cannot load the statement inspector [does.not.Exist]') }
    }

    private HibernateDatastore open(Map config) {
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([(Settings.SETTING_DB_CREATE): 'create-drop'] + config), SqlTailBook)
        datastores << datastore
        datastore
    }

    private static Map enabled(File file) {
        [(SqlTailSettings.SETTING_ENABLED): 'true', (SqlTailSettings.SETTING_FILE): file.path]
    }

    private static StatementInspector statementInspector(HibernateDatastore datastore) {
        ((SessionFactoryImplementor) datastore.sessionFactory).sessionFactoryOptions.statementInspector
    }

    private static void runNative(HibernateDatastore datastore, String sql) {
        Session session = datastore.sessionFactory.openSession()
        try {
            session.createNativeQuery(sql).list()
        }
        finally {
            session.close()
        }
    }

    private static List<SqlTailRecord> records(File file) {
        file.readLines().collect { SqlTailRecord.parse(it) }.findAll { it != null }
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
class SqlTailBook {
    Long id
    Long version
    String title
}

class SqlTailCommentingInspector implements StatementInspector {

    static final List<String> seen = [].asSynchronized()

    @Override
    String inspect(String sql) {
        seen << sql
        "/* commented */ ${sql}".toString()
    }
}

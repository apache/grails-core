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
package org.grails.orm.hibernate.connections

import javax.sql.DataSource

import grails.gorm.annotation.Entity
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettingsBuilder
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.Environment
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.Oracle8iDialect
import org.springframework.context.ApplicationContext
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Created by graemerocher on 06/07/2016.
 */
class HibernateConnectionSourceFactorySpec extends Specification {

    @TempDir
    File tempDir

    ClassLoader originalContextClassLoader

    def setup() {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
    }

    void "Test hibernate connection factory"() {
        when:"A factory is used to create a session factory"

        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        Map config = [
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create'
        ]
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config))

        then:"The session factory is created"
        connectionSource.source instanceof SessionFactory
        connectionSource.source.getMetamodel().entity(Foo.name)
        connectionSource.source.openSession().createCriteria(Foo).list().size() == 0

        when:"The connection source is closed"
        connectionSource.close()

        then:"The session factory is closed"
        connectionSource.source.isClosed()
    }

    void "buildConfiguration uses the connection source DataSource and the application context class loader"() {
        given:
        ClassLoader applicationClassLoader = loaderUnder(getClass().classLoader)
        DataSource connectionSourceDataSource = Stub(DataSource)
        ApplicationContext applicationContext = Stub(ApplicationContext) {
            containsBean("dataSource") >> true
            getBean("dataSource") >> Stub(DataSource)
            getClassLoader() >> applicationClassLoader
        }
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory()
        factory.setApplicationContext(applicationContext)

        when:
        def configuration = factory.buildConfiguration(ConnectionSource.DEFAULT,
                connectionSource(ConnectionSource.DEFAULT, connectionSourceDataSource), new HibernateConnectionSourceSettings())

        then:
        configuration.getProperties().get(AvailableSettings.CLASSLOADERS).is(applicationClassLoader)
        configuration.getProperties().get(Environment.DATASOURCE).is(connectionSourceDataSource)
    }

    void "buildConfiguration keeps the connection source DataSource for a named connection source"() {
        given:
        ClassLoader applicationClassLoader = loaderUnder(getClass().classLoader)
        DataSource connectionSourceDataSource = Stub(DataSource)
        ApplicationContext applicationContext = Stub(ApplicationContext) {
            containsBean("dataSource") >> true
            containsBean("dataSource_secondary") >> true
            getBean("dataSource") >> Stub(DataSource)
            getBean("dataSource_secondary") >> Stub(DataSource)
            getClassLoader() >> applicationClassLoader
        }
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory()
        factory.setApplicationContext(applicationContext)

        when:
        def configuration = factory.buildConfiguration("secondary",
                connectionSource("secondary", connectionSourceDataSource), new HibernateConnectionSourceSettings())

        then:
        configuration.dataSourceName == "secondary"
        configuration.getProperties().get(AvailableSettings.CLASSLOADERS).is(applicationClassLoader)
        configuration.getProperties().get(Environment.DATASOURCE).is(connectionSourceDataSource)
    }

    void "the query-tail settings are read from grails.gorm.queryTail on every settings path and inherited at runtime"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        Map config = ['dataSource.url': 'jdbc:h2:mem:queryTailFactory', 'dataSource.dialect': H2Dialect.name]
        Map enabled = [(QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): 'build/query-tail/factory.log']

        when:
        def defaults = settingsOf(factory, ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config + enabled), true)
        def named = settingsOf(factory, 'secondary', DatastoreUtils.createPropertyResolver(config + enabled), false)
        def runtime = factory.buildRuntimeSettings('runtime', DatastoreUtils.createPropertyResolver(url: 'jdbc:h2:mem:runtime'), defaults)
        def plain = settingsOf(factory, ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config), true)

        then:
        [defaults, named, runtime].every { it.queryTail.enabled && it.queryTail.file == 'build/query-tail/factory.log' }
        !plain.queryTail.enabled
        plain.queryTail.file == null
    }

    void "two session factories one configuration builds each record until they close, and one it builds after them records too"() {
        given:
        File file = new File(tempDir, 'capture.log')
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        Map config = ['dataSource.url': 'jdbc:h2:mem:queryTailTwoFactories;LOCK_TIMEOUT=10000', 'dataSource.dialect': H2Dialect.name,
                'dataSource.dbCreate': 'none', (QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): file.path]
        HibernateConnectionSourceSettings settings =
                settingsOf(factory, ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config), true)
        ConnectionSource<DataSource, DataSourceSettings> dataSource =
                new DataSourceConnectionSourceFactory().create(ConnectionSource.DEFAULT, settings.dataSource)
        HibernateMappingContextConfiguration configuration = factory.buildConfiguration(ConnectionSource.DEFAULT, dataSource, settings)

        when: 'the configuration builds two session factories, and the first of them closes'
        SessionFactory first = configuration.buildSessionFactory()
        SessionFactory second = configuration.buildSessionFactory()
        runNative(first, 'select 1 as first_open')
        runNative(second, 'select 2 as second_open')
        first.close()
        runNative(second, 'select 3 as after_first_closed')

        then: 'the other still records'
        recorded(file).containsAll(['select ? as first_open', 'select ? as second_open', 'select ? as after_first_closed'])

        when: 'the other closes as well, and the configuration builds a third'
        second.close()
        SessionFactory third = configuration.buildSessionFactory()
        runNative(third, 'select 4 as third_open')

        then: 'the third records, in a run of its own'
        recorded(file).contains('select ? as third_open')
        !recorded(file).contains('select ? as first_open')

        cleanup:
        third?.close()
        dataSource?.close()
    }

    void "a configuration built again tells the observer the application set of each session factory once, with capture #capture"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        Map config = ['dataSource.url': "jdbc:h2:mem:rebuiltConfiguration${capture};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dialect': H2Dialect.name, 'dataSource.dbCreate': 'none',
                (QueryTailSettings.SETTING_ENABLED): String.valueOf(capture == 'on'),
                (QueryTailSettings.SETTING_FILE): new File(tempDir, 'capture.log').path]
        HibernateConnectionSourceSettings settings =
                settingsOf(factory, ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config), true)
        ConnectionSource<DataSource, DataSourceSettings> dataSource =
                new DataSourceConnectionSourceFactory().create(ConnectionSource.DEFAULT, settings.dataSource)
        HibernateMappingContextConfiguration configuration = factory.buildConfiguration(ConnectionSource.DEFAULT, dataSource, settings)
        RebuiltConfigurationObserver observer = new RebuiltConfigurationObserver()
        configuration.sessionFactoryObserver = observer

        when:
        SessionFactory first = configuration.buildSessionFactory()
        SessionFactory second = configuration.buildSessionFactory()
        first.close()
        second.close()

        then:
        observer.events == ['created', 'created', 'closing', 'closed', 'closing', 'closed']

        cleanup:
        dataSource?.close()

        where:
        capture << ['off', 'on']
    }

    void "buildConfiguration uses the connection source class loader when the application context has no class loader"() {
        given:
        ApplicationContext applicationContext = Stub(ApplicationContext) {
            containsBean(_) >> false
            getClassLoader() >> null
        }
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory()
        factory.setApplicationContext(applicationContext)
        ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource =
                connectionSource(ConnectionSource.DEFAULT, Stub(DataSource))

        when:
        def configuration = factory.buildConfiguration(
                ConnectionSource.DEFAULT, dataSourceConnectionSource, new HibernateConnectionSourceSettings())

        then:
        configuration.getProperties().get(AvailableSettings.CLASSLOADERS).is(dataSourceConnectionSource.getClass().getClassLoader())
    }

    void "buildConfiguration uses the connection source class loader without an application context"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory()
        ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource =
                connectionSource(ConnectionSource.DEFAULT, Stub(DataSource))

        when:
        def configuration = factory.buildConfiguration(
                ConnectionSource.DEFAULT, dataSourceConnectionSource, new HibernateConnectionSourceSettings())

        then:
        configuration.getProperties().get(AvailableSettings.CLASSLOADERS).is(dataSourceConnectionSource.getClass().getClassLoader())
    }

    void "buildConfiguration uses the context class loader that delegates to the RestartClassLoader during servlet container start"() {
        given: "the container's web application loader is the thread context class loader and the context reports it"
        ClassLoader containerLoader = loaderUnder(restartClassLoader())
        ApplicationContext applicationContext = Stub(ApplicationContext) {
            containsBean(_) >> false
            getClassLoader() >> containerLoader
        }
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory()
        factory.setApplicationContext(applicationContext)

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        def configuration = factory.buildConfiguration(ConnectionSource.DEFAULT,
                connectionSource(ConnectionSource.DEFAULT, Stub(DataSource)), new HibernateConnectionSourceSettings())

        then:
        configuration.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)
    }

    void "buildConfiguration uses a thread context class loader that delegates to the RestartClassLoader without an application context"() {
        given:
        ClassLoader containerLoader = loaderUnder(restartClassLoader())
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory()

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        def configuration = factory.buildConfiguration(ConnectionSource.DEFAULT,
                connectionSource(ConnectionSource.DEFAULT, Stub(DataSource)), new HibernateConnectionSourceSettings())

        then:
        configuration.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)
    }

    private ConnectionSource<DataSource, DataSourceSettings> connectionSource(String name, DataSource dataSource) {
        Stub(ConnectionSource) {
            getName() >> name
            getSource() >> dataSource
            getSettings() >> new DataSourceSettings()
        } as ConnectionSource<DataSource, DataSourceSettings>
    }

    private static ClassLoader restartClassLoader() {
        new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
    }

    private static ClassLoader loaderUnder(ClassLoader parent) {
        new URLClassLoader([] as URL[], parent)
    }

    /**
     * Builds the settings of a connection source as a datastore does: over the settings of the configuration every
     * connection source falls back to.
     */
    private static HibernateConnectionSourceSettings settingsOf(HibernateConnectionSourceFactory factory, String name,
            PropertyResolver configuration, boolean isDefault) {
        factory.buildSettings(name, configuration, new ConnectionSourceSettingsBuilder(configuration).build(), isDefault)
    }

    private static void runNative(SessionFactory sessionFactory, String sql) {
        def session = sessionFactory.openSession()
        try {
            session.createNativeQuery(sql).list()
        }
        finally {
            session.close()
        }
    }

    private static List<String> recorded(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }*.sql
    }
}

@Entity
class Foo {
    String name
}

class RebuiltConfigurationObserver implements SessionFactoryObserver {

    final List<String> events = []

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

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
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.Environment
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.Oracle8iDialect
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * Created by graemerocher on 06/07/2016.
 */
class HibernateConnectionSourceFactorySpec extends Specification {

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
}

@Entity
class Foo {
    String name
}

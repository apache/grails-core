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
package org.grails.orm.hibernate.cfg

import javax.sql.DataSource

import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.Environment
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class HibernateMappingContextConfigurationSpec extends Specification {

    ClassLoader originalContextClassLoader

    def setup() {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
    }

    void "setApplicationContext uses the context class loader when DevTools is not active"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader cl = loaderUnder(originalContextClassLoader)

        when:
        config.setApplicationContext(applicationContext(cl))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(cl)
    }

    void "setApplicationContext leaves CLASSLOADERS unset when the context class loader is null"() {
        given:
        def config = new HibernateMappingContextConfiguration()

        when:
        config.setApplicationContext(applicationContext(null))

        then:
        !config.getProperties().containsKey(AvailableSettings.CLASSLOADERS)
    }

    void "setApplicationContext prefers RestartClassLoader thread context class loader over the context class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader restartLoader = restartClassLoader()

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setApplicationContext(applicationContext(loaderUnder(originalContextClassLoader)))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(restartLoader)
    }

    void "setApplicationContext prefers a thread context class loader that delegates to the RestartClassLoader"() {
        given: "a servlet container has swapped its web application loader in during context start"
        def config = new HibernateMappingContextConfiguration()
        ClassLoader containerLoader = loaderUnder(restartClassLoader())

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        config.setApplicationContext(applicationContext(loaderUnder(originalContextClassLoader)))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)
    }

    void "setApplicationContext keeps a context class loader that delegates to the RestartClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader restartLoader = restartClassLoader()
        ClassLoader contextLoader = loaderUnder(restartLoader)

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setApplicationContext(applicationContext(contextLoader))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(contextLoader)
    }

    void "setApplicationContext with a null context class loader uses a thread context class loader that delegates to the RestartClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader containerLoader = loaderUnder(restartClassLoader())

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        config.setApplicationContext(applicationContext(null))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)
    }

    void "setApplicationContext resolves the dataSource bean when no DataSource is configured"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource beanDataSource = Stub(DataSource)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> true
            getBean("dataSource") >> beanDataSource
            getClassLoader() >> originalContextClassLoader
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(Environment.DATASOURCE).is(beanDataSource)
    }

    void "setApplicationContext resolves the named dataSource bean for a named connection source"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        config.setDataSourceName("secondary")
        DataSource beanDataSource = Stub(DataSource)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource_secondary") >> true
            getBean("dataSource_secondary") >> beanDataSource
            getClassLoader() >> originalContextClassLoader
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(Environment.DATASOURCE).is(beanDataSource)
    }

    void "setApplicationContext keeps the DataSource configured by setDataSourceConnectionSource"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource connectionSourceDataSource = Stub(DataSource)
        DataSource beanDataSource = Stub(DataSource)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> true
            getBean("dataSource") >> beanDataSource
            getClassLoader() >> originalContextClassLoader
        }

        when:
        config.setDataSourceConnectionSource(connectionSource(ConnectionSource.DEFAULT, connectionSourceDataSource))
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(Environment.DATASOURCE).is(connectionSourceDataSource)
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(originalContextClassLoader)
    }

    void "setDataSourceConnectionSource uses RestartClassLoader thread context class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource ds = Stub(DataSource)
        ClassLoader restartLoader = restartClassLoader()

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setDataSourceConnectionSource(connectionSource(ConnectionSource.DEFAULT, ds))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(restartLoader)
        config.getProperties().get(Environment.DATASOURCE).is(ds)
    }

    void "setDataSourceConnectionSource uses a thread context class loader that delegates to the RestartClassLoader"() {
        given: "a servlet container has swapped its web application loader in during context start"
        def config = new HibernateMappingContextConfiguration()
        ClassLoader containerLoader = loaderUnder(restartClassLoader())

        when:
        Thread.currentThread().contextClassLoader = containerLoader
        config.setDataSourceConnectionSource(connectionSource(ConnectionSource.DEFAULT, Stub(DataSource)))

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(containerLoader)
    }

    void "setDataSourceConnectionSource uses the connection source class loader when DevTools is not active"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ConnectionSource<DataSource, DataSourceSettings> connSrc = connectionSource("secondary", Stub(DataSource))

        when:
        config.setDataSourceConnectionSource(connSrc)

        then:
        config.dataSourceName == "secondary"
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(connSrc.getClass().getClassLoader())
    }

    void "resolveSessionFactoryClassLoader prefers RestartClassLoader thread context class loader over configured class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader restartLoader = restartClassLoader()
        config.getProperties().put(AvailableSettings.CLASSLOADERS, loaderUnder(originalContextClassLoader))

        when:
        Thread.currentThread().contextClassLoader = restartLoader

        then:
        config.resolveSessionFactoryClassLoader().is(restartLoader)
    }

    void "resolveSessionFactoryClassLoader keeps a configured class loader that delegates to the RestartClassLoader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader configuredLoader = loaderUnder(restartClassLoader())
        config.getProperties().put(AvailableSettings.CLASSLOADERS, configuredLoader)

        when:
        Thread.currentThread().contextClassLoader = loaderUnder(originalContextClassLoader)

        then:
        config.resolveSessionFactoryClassLoader().is(configuredLoader)
    }

    private ApplicationContext applicationContext(ClassLoader classLoader) {
        Stub(ApplicationContext) {
            containsBean(_) >> false
            getClassLoader() >> classLoader
        }
    }

    private ConnectionSource<DataSource, DataSourceSettings> connectionSource(String name, DataSource dataSource) {
        Stub(ConnectionSource) {
            getName() >> name
            getSource() >> dataSource
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

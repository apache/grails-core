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
        ClassLoader cl = new URLClassLoader([] as URL[], originalContextClassLoader)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> cl
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(cl)
    }

    void "setApplicationContext leaves CLASSLOADERS unset when the context class loader is null"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> null
        }

        when:
        config.setApplicationContext(appCtx)

        then:
        !config.getProperties().containsKey(AvailableSettings.CLASSLOADERS)
    }

    void "setApplicationContext prefers RestartClassLoader thread context class loader over the context class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader contextLoader = new URLClassLoader([] as URL[], originalContextClassLoader)
        ApplicationContext appCtx = Stub(ApplicationContext) {
            containsBean("dataSource") >> false
            getClassLoader() >> contextLoader
        }
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setApplicationContext(appCtx)

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(restartLoader)
    }

    void "setDataSourceConnectionSource uses RestartClassLoader thread context class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource ds = Stub(DataSource)
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader

        when:
        Thread.currentThread().contextClassLoader = restartLoader
        config.setDataSourceConnectionSource(Stub(ConnectionSource) {
            getSource() >> ds
            getName() >> "default"
        })

        then:
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(restartLoader)
        config.getProperties().get(org.hibernate.cfg.Environment.DATASOURCE).is(ds)
    }

    void "setDataSourceConnectionSource uses the connection source class loader when DevTools is not active"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        DataSource ds = Stub(DataSource)
        ConnectionSource<DataSource, DataSourceSettings> connSrc = Stub(ConnectionSource) {
            getName() >> "secondary"
            getSource() >> ds
        }

        when:
        config.setDataSourceConnectionSource(connSrc)

        then:
        config.dataSourceName == "secondary"
        config.getProperties().get(AvailableSettings.CLASSLOADERS).is(connSrc.getClass().getClassLoader())
    }

    void "resolveSessionFactoryClassLoader prefers RestartClassLoader thread context class loader over configured class loader"() {
        given:
        def config = new HibernateMappingContextConfiguration()
        ClassLoader configuredLoader = new URLClassLoader([] as URL[], originalContextClassLoader)
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        config.getProperties().put(AvailableSettings.CLASSLOADERS, configuredLoader)

        when:
        Thread.currentThread().contextClassLoader = restartLoader

        then:
        config.resolveSessionFactoryClassLoader().is(restartLoader)
    }
}

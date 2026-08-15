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
package org.grails.datastore.gorm.jdbc.connections

import javax.sql.DataSource

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification

class SpringDataSourceConnectionSourceFactorySpec extends Specification {

    void "create returns the Spring-managed 'dataSource' bean for the default connection source name"() {
        given:
        def springDataSource = Mock(DataSource)
        def context = new StaticApplicationContext()
        context.beanFactory.registerSingleton('dataSource', springDataSource)
        context.refresh()

        def factory = new SpringDataSourceConnectionSourceFactory()
        factory.applicationContext = context

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, new DataSourceSettings())

        then:
        connectionSource.source.is(springDataSource)
    }

    void "create returns the Spring-managed 'dataSource_<name>' bean for a named connection source"() {
        given:
        def springDataSource = Mock(DataSource)
        def context = new StaticApplicationContext()
        context.beanFactory.registerSingleton('dataSource_secondary', springDataSource)
        context.refresh()

        def factory = new SpringDataSourceConnectionSourceFactory()
        factory.applicationContext = context

        when:
        def connectionSource = factory.create('secondary', new DataSourceSettings())

        then:
        connectionSource.source.is(springDataSource)
    }

    void "create falls back to building its own DataSource when no matching Spring bean exists"() {
        given:
        def context = new StaticApplicationContext()
        context.refresh()

        def factory = new SpringDataSourceConnectionSourceFactory()
        factory.applicationContext = context
        def settings = new DataSourceSettings(url: 'jdbc:h2:mem:springFallbackTest;DB_CLOSE_DELAY=-1')

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, settings)

        then:
        connectionSource.source instanceof DataSource
        connectionSource instanceof DataSourceConnectionSource

        cleanup:
        connectionSource?.close()
    }
}

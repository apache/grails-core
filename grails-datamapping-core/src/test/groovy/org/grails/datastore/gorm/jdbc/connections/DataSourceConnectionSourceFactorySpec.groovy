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

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.datasource.DelegatingDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import spock.lang.Specification

import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource

class DataSourceConnectionSourceFactorySpec extends Specification {

    void "test getConnectionSourcesConfigurationKey returns the dataSources config key"() {
        expect:
        new DataSourceConnectionSourceFactory().connectionSourcesConfigurationKey == Settings.SETTING_DATASOURCES
    }

    void "test create wraps the built DataSource in lazy and transaction-aware proxies by default"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:dataSourceConnectionSourceFactorySpecDefault;DB_CLOSE_DELAY=-1',
                type: DriverManagerDataSource)

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, settings)

        then:
        connectionSource instanceof DataSourceConnectionSource
        connectionSource.source instanceof TransactionAwareDataSourceProxy
        connectionSource.source.targetDataSource instanceof LazyConnectionDataSourceProxy
    }

    void "test create does not wrap the DataSource when lazy and transactionAware are disabled"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:dataSourceConnectionSourceFactorySpecPlain;DB_CLOSE_DELAY=-1',
                type: DriverManagerDataSource,
                lazy: false,
                transactionAware: false)

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, settings)

        then:
        connectionSource.source.class == DriverManagerDataSource
    }

    void "test DataSourceConnectionSource#close closes a directly closeable DataSource"() {
        given:
        def dataSource = new HikariDataSource()
        dataSource.jdbcUrl = 'jdbc:h2:mem:dataSourceConnectionSourceCloseDirect;DB_CLOSE_DELAY=-1'
        def connectionSource = new DataSourceConnectionSource('default', dataSource, new DataSourceSettings())

        when:
        connectionSource.close()

        then:
        dataSource.isClosed()
    }

    void "test DataSourceConnectionSource#close unwraps a chain of DelegatingDataSource to find the real close method"() {
        given:
        def dataSource = new HikariDataSource()
        dataSource.jdbcUrl = 'jdbc:h2:mem:dataSourceConnectionSourceCloseDelegating;DB_CLOSE_DELAY=-1'
        def delegating = new DelegatingDataSource(new DelegatingDataSource(dataSource))
        def connectionSource = new DataSourceConnectionSource('default', delegating, new DataSourceSettings())

        when:
        connectionSource.close()

        then:
        dataSource.isClosed()
    }

    void "test DataSourceConnectionSource#close is a no-op when the DataSource has no close method"() {
        given:
        def dataSource = new DriverManagerDataSource('jdbc:h2:mem:dataSourceConnectionSourceCloseNoop;DB_CLOSE_DELAY=-1')
        def connectionSource = new DataSourceConnectionSource('default', dataSource, new DataSourceSettings())

        when:
        connectionSource.close()

        then:
        noExceptionThrown()
    }

    void "test DataSourceConnectionSource#close swallows an exception thrown by the underlying close method"() {
        given:
        def dataSource = new ThrowingCloseDataSource(new HikariDataSource())
        def connectionSource = new DataSourceConnectionSource('default', dataSource, new DataSourceSettings())

        when:
        connectionSource.close()

        then:
        noExceptionThrown()
    }

    void "test CachedDataSourceConnectionSourceFactory returns the same connection source for repeated create(name, settings) calls"() {
        given:
        def factory = new CachedDataSourceConnectionSourceFactory()
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:cachedDataSourceConnectionSourceFactorySpecSettings;DB_CLOSE_DELAY=-1',
                type: DriverManagerDataSource)

        when:
        def first = factory.create('default', settings)
        def second = factory.create('default', settings)
        def other = factory.create('other', new DataSourceSettings(
                url: 'jdbc:h2:mem:cachedDataSourceConnectionSourceFactorySpecOther;DB_CLOSE_DELAY=-1',
                type: DriverManagerDataSource))

        then:
        first.is(second)
        !other.is(first)
    }

    void "test CachedDataSourceConnectionSourceFactory returns the same connection source for repeated create(name, PropertyResolver) calls"() {
        given:
        def factory = new CachedDataSourceConnectionSourceFactory()
        def configuration = DatastoreUtils.createPropertyResolver([
                'dataSource.url' : 'jdbc:h2:mem:cachedDataSourceConnectionSourceFactorySpecResolver;DB_CLOSE_DELAY=-1',
                'dataSource.type': DriverManagerDataSource.name
        ])

        when:
        def first = factory.create(ConnectionSource.DEFAULT, configuration)
        def second = factory.create(ConnectionSource.DEFAULT, configuration)

        then:
        first.is(second)
    }

    void "test SpringDataSourceConnectionSourceFactory uses the matching Spring bean when present"() {
        given:
        def springManagedDataSource = new DriverManagerDataSource('jdbc:h2:mem:springDataSourceConnectionSourceFactorySpecBean;DB_CLOSE_DELAY=-1')
        def applicationContext = Stub(ApplicationContext) {
            getBean(Settings.SETTING_DATASOURCE, DataSource) >> springManagedDataSource
        }
        def factory = new SpringDataSourceConnectionSourceFactory()
        factory.setApplicationContext(applicationContext)

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, new DataSourceSettings())

        then:
        connectionSource instanceof DataSourceConnectionSource
        connectionSource.source.is(springManagedDataSource)
    }

    void "test SpringDataSourceConnectionSourceFactory falls back to building its own DataSource when no matching bean exists"() {
        given:
        def applicationContext = Stub(ApplicationContext) {
            getBean(Settings.SETTING_DATASOURCE, DataSource) >> { throw new NoSuchBeanDefinitionException(Settings.SETTING_DATASOURCE) }
        }
        def factory = new SpringDataSourceConnectionSourceFactory()
        factory.setApplicationContext(applicationContext)
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:springDataSourceConnectionSourceFactorySpecFallback;DB_CLOSE_DELAY=-1',
                type: DriverManagerDataSource,
                lazy: false,
                transactionAware: false)

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, settings)

        then:
        connectionSource instanceof DataSourceConnectionSource
        connectionSource.source.class == DriverManagerDataSource
    }

    static class ThrowingCloseDataSource extends DelegatingDataSource {

        ThrowingCloseDataSource(DataSource targetDataSource) {
            super(targetDataSource)
        }

        void close() {
            throw new RuntimeException('boom')
        }

    }

}

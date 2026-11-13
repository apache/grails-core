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

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import spock.lang.Specification

class DataSourceConnectionSourceFactorySpec extends Specification {

    void "create resolves settings for the default data source from the 'dataSource' prefix"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def config = DatastoreUtils.createPropertyResolver([
                'dataSource.url'    : 'jdbc:h2:mem:factoryDefaultTest;DB_CLOSE_DELAY=-1',
                'dataSource.lazy'   : 'false',
                'dataSource.transactionAware': 'false'
        ])

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, config)

        then:
        connectionSource.name == ConnectionSource.DEFAULT
        connectionSource.settings.url == 'jdbc:h2:mem:factoryDefaultTest;DB_CLOSE_DELAY=-1'
        connectionSource.source != null

        cleanup:
        connectionSource?.close()
    }

    void "create resolves settings for a named data source from the 'dataSources.<name>' prefix"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def config = DatastoreUtils.createPropertyResolver([
                'dataSources.secondary.url'          : 'jdbc:h2:mem:factoryNamedTest;DB_CLOSE_DELAY=-1',
                'dataSources.secondary.lazy'          : 'false',
                'dataSources.secondary.transactionAware': 'false'
        ])

        when:
        def connectionSource = factory.create('secondary', config)

        then:
        connectionSource.name == 'secondary'
        connectionSource.settings.url == 'jdbc:h2:mem:factoryNamedTest;DB_CLOSE_DELAY=-1'

        cleanup:
        connectionSource?.close()
    }

    void "create(name, settings) wraps the built DataSource with lazy and transaction-aware proxies when configured"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:factoryProxyTest;DB_CLOSE_DELAY=-1',
                lazy: true,
                transactionAware: true)

        when:
        def connectionSource = factory.create('default', settings)

        then:
        connectionSource.source instanceof TransactionAwareDataSourceProxy
        ((TransactionAwareDataSourceProxy) connectionSource.source).targetDataSource instanceof LazyConnectionDataSourceProxy

        cleanup:
        connectionSource?.close()
    }

    void "create(name, settings) does not wrap the DataSource when lazy and transactionAware are disabled"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:factoryNoProxyTest;DB_CLOSE_DELAY=-1',
                lazy: false,
                transactionAware: false)

        when:
        def connectionSource = factory.create('default', settings)

        then:
        !(connectionSource.source instanceof TransactionAwareDataSourceProxy)
        !(connectionSource.source instanceof LazyConnectionDataSourceProxy)

        cleanup:
        connectionSource?.close()
    }

    void "getConnectionSourcesConfigurationKey returns the dataSources settings key"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()

        expect:
        factory.connectionSourcesConfigurationKey == 'dataSources'
    }
}

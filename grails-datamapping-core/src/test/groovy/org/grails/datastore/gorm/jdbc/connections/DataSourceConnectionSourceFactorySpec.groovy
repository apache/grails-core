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

import com.zaxxer.hikari.HikariDataSource
import groovy.util.ConfigObject
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import spock.lang.Specification

import javax.sql.DataSource


class DataSourceConnectionSourceFactorySpec extends Specification {

    void "create binds flattened default data source pool properties from the Spring Environment"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application.yml', [
                'dataSource.url': 'jdbc:h2:mem:factoryEnvironmentTest',
                'dataSource.driverClassName': 'org.h2.Driver',
                'dataSource.username': 'sa',
                'dataSource.password': '',
                'dataSource.type': 'com.zaxxer.hikari.HikariDataSource',
                'dataSource.properties.maximumPoolSize': 16,
                'dataSource.properties.minimumIdle': 8,
                'dataSource.properties.connectionTimeout': 2000,
                'dataSource.properties.dataSourceProperties.connectionCollation': 'utf8mb4_unicode_ci'
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.url == 'jdbc:h2:mem:factoryEnvironmentTest'
        connectionSource.settings.username == 'sa'
        connectionSource.settings.password == ''
        connectionSource.source instanceof TransactionAwareDataSourceProxy
        def lazyDataSource = ((TransactionAwareDataSourceProxy) connectionSource.source).targetDataSource
        lazyDataSource instanceof LazyConnectionDataSourceProxy
        def dataSource = ((LazyConnectionDataSourceProxy) lazyDataSource).targetDataSource
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 16
        dataSource.minimumIdle == 8
        dataSource.connectionTimeout == 2000
        dataSource.dataSourceProperties.getProperty('connectionCollation') == 'utf8mb4_unicode_ci'

        cleanup:
        connectionSource?.close()
    }

    void "create binds flattened named data source pool properties from the Spring Environment"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application.yml', [
                'dataSources.secondary.url': 'jdbc:h2:mem:factoryNamedEnvironmentTest',
                'dataSources.secondary.type': HikariDataSource.name,
                'dataSources.secondary.properties.maximumPoolSize': 24
        ]))

        when:
        def connectionSource = factory.create('secondary', environment)

        then:
        connectionSource.name == 'secondary'
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 24

        cleanup:
        connectionSource?.close()
    }

    void "create uses the highest precedence Spring property source for pool properties"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application.yml', [
                'dataSource.url': 'jdbc:h2:mem:factoryPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.maximumPoolSize': 16,
                'dataSource.properties.minimumIdle': 8
        ]))
        environment.propertySources.addFirst(new MapPropertySource('override', [
                'dataSource.properties.maximumPoolSize': 32
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 32
        dataSource.minimumIdle == 8

        cleanup:
        connectionSource?.close()
    }

    void "create keeps a higher precedence scalar over a lower flattened descendant"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.url': 'jdbc:h2:mem:factoryScalarPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.dataSourceProperties.foo.x': 'low',
                'dataSource.properties.foo.x': 'low',
                'dataSource.properties.maximumPoolSize.nested': '${unresolvedLowerPoolSize}'
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.dataSourceProperties.foo': 'bar',
                'dataSource.properties.foo': 'bar',
                'dataSource.properties.maximumPoolSize': 50
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties == [maximumPoolSize: 50, dataSourceProperties: [foo: 'bar'], foo: 'bar']
        def dataSource = unwrap(connectionSource.source)
        dataSource.maximumPoolSize == 50
        dataSource.dataSourceProperties.getProperty('foo') == 'bar'
        !dataSource.dataSourceProperties.containsKey('foo.x')

        cleanup:
        connectionSource?.close()
    }

    void "create merges a lower direct map behind higher flattened pool properties"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        def lowerProperties = [maximumPoolSize: 20, minimumIdle: 2]
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.url': 'jdbc:h2:mem:factoryDirectMapPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties': lowerProperties
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.maximumPoolSize': 50
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties == [maximumPoolSize: 50, minimumIdle: 2]
        def dataSource = unwrap(connectionSource.source)
        dataSource.maximumPoolSize == 50
        dataSource.minimumIdle == 2
        lowerProperties == [maximumPoolSize: 20, minimumIdle: 2]

        cleanup:
        connectionSource?.close()
    }

    void "create collects indexed pool properties into a list in numeric order"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application', [
                'dataSource.url': 'jdbc:h2:mem:factoryIndexedPropertiesTest',
                'dataSource.type': ListDataSource.name,
                'dataSource.properties.connectionInitSqls[1]': 'SET B=2',
                'dataSource.properties.connectionInitSqls[0]': 'SET A=1'
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties == [connectionInitSqls: ['SET A=1', 'SET B=2']]
        unwrap(connectionSource.source).connectionInitSqls == ['SET A=1', 'SET B=2']

        cleanup:
        connectionSource?.close()
    }

    void "create still binds an existing nested properties map from DatastoreUtils"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def dataSourceConfig = new ConfigObject()
        dataSourceConfig.putAll([
                url: 'jdbc:h2:mem:factoryNestedMapTest',
                type: HikariDataSource,
                properties: [maximumPoolSize: 20]
        ])
        def applicationConfig = new ConfigObject()
        applicationConfig.put('dataSource', dataSourceConfig)
        def config = DatastoreUtils.createPropertyResolver(applicationConfig)

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, config)

        then:
        config.getProperty('dataSource.properties', Map) == [maximumPoolSize: 20]
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 20

        cleanup:
        connectionSource?.close()
    }

    void "create preserves a nonempty direct properties map without merging flattened keys"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def properties = [maximumPoolSize: 20]
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application', [
                'dataSource.url': 'jdbc:h2:mem:factoryDirectMapTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties': properties,
                'dataSource.properties.maximumPoolSize': 32,
                'dataSource.properties.minimumIdle': 8
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.is(properties)
        connectionSource.settings.properties == [maximumPoolSize: 20]
        unwrap(connectionSource.source).maximumPoolSize == 20

        cleanup:
        connectionSource?.close()
    }

    void "create rebuilds flattened properties when the direct map is #directValue and conversion fails is #conversionFails"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        if (conversionFails) {
            environment.conversionService = new DefaultConversionService()
            environment.conversionService.addConverter(String, Map, { String value ->
                throw new IllegalArgumentException('Cannot convert value to a map')
            } as Converter<String, Map>)
        }
        environment.propertySources.addFirst(new MapPropertySource('application', [
                'dataSource.url': 'jdbc:h2:mem:factoryMapFallbackTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties': directValue,
                'dataSource.properties.maximumPoolSize': '${poolSize}',
                'poolSize': 16,
                'dataSource.propertiesFoo.maximumPoolSize': 99,
                'dataSource.properties.dataSourceProperties': 'replaced by nested map',
                'dataSource.properties.dataSourceProperties.connectionCollation': 'utf8mb4_unicode_ci',
                'dataSource.properties.dataSourceProperties.empty': '',
                'dataSource.properties.dataSourceProperties.absent': null
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties == [
                maximumPoolSize: '16',
                dataSourceProperties: [connectionCollation: 'utf8mb4_unicode_ci', empty: '']
        ]
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 16
        dataSource.dataSourceProperties.getProperty('connectionCollation') == 'utf8mb4_unicode_ci'
        dataSource.dataSourceProperties.getProperty('empty') == ''
        !dataSource.dataSourceProperties.containsKey('absent')

        cleanup:
        connectionSource?.close()

        where:
        directValue | conversionFails
        null        | false
        [:]         | false
        'not a map' | false
        'not a map' | true
    }

    void "create binds a map-valued descendant without forcing it to a string"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application', [
                'dataSource.url': 'jdbc:h2:mem:factoryMapDescendantTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.maximumPoolSize': 16,
                'dataSource.properties.dataSourceProperties': [connectionCollation: 'utf8mb4_unicode_ci', empty: '']
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.maximumPoolSize == 16
        connectionSource.settings.properties.dataSourceProperties == [connectionCollation: 'utf8mb4_unicode_ci', empty: '']
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 16
        dataSource.dataSourceProperties.getProperty('connectionCollation') == 'utf8mb4_unicode_ci'
        dataSource.dataSourceProperties.getProperty('empty') == ''

        cleanup:
        connectionSource?.close()
    }

    void "create keeps a higher precedence map entry over a lower flattened descendant"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addLast(new MapPropertySource('low', [
                'dataSource.url': 'jdbc:h2:mem:factoryMapPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.maximumPoolSize': 16,
                'dataSource.properties.dataSourceProperties.connectionCollation': 'low'
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.dataSourceProperties': [connectionCollation: 'high']
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.dataSourceProperties.connectionCollation == 'high'
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 16
        dataSource.dataSourceProperties.getProperty('connectionCollation') == 'high'

        cleanup:
        connectionSource?.close()
    }

    void "create keeps a higher precedence nested JDBC property over a lower dotted map key"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addLast(new MapPropertySource('low', [
                'dataSource.url': 'jdbc:h2:mem:factoryDottedKeyPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.dataSourceProperties': ['oracle.jdbc.sendBooleanAsNativeBoolean': 'false']
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.dataSourceProperties.oracle.jdbc.sendBooleanAsNativeBoolean': 'true'
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.dataSourceProperties.getProperty('oracle.jdbc.sendBooleanAsNativeBoolean') == 'true'
        !dataSource.dataSourceProperties.containsKey('oracle')

        cleanup:
        connectionSource?.close()
    }

    void "create keeps a higher precedence dotted map key over a lower flattened JDBC property"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addLast(new MapPropertySource('low', [
                'dataSource.url': 'jdbc:h2:mem:factoryLiteralKeyPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.dataSourceProperties.oracle.jdbc.sendBooleanAsNativeBoolean': false
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.dataSourceProperties': ['oracle.jdbc.sendBooleanAsNativeBoolean': 'true']
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.dataSourceProperties == ['oracle.jdbc.sendBooleanAsNativeBoolean': 'true']
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.dataSourceProperties.getProperty('oracle.jdbc.sendBooleanAsNativeBoolean') == 'true'
        !dataSource.dataSourceProperties.containsKey('oracle')

        cleanup:
        connectionSource?.close()
    }

    void "create keeps a higher literal JDBC key over a lower nested map"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addLast(new MapPropertySource('low', [
                'dataSource.url': 'jdbc:h2:mem:factoryNestedMapPrecedenceTest',
                'dataSource.type': HikariDataSource.name,
                'dataSource.properties.dataSourceProperties.oracle': [jdbc: [sendBooleanAsNativeBoolean: false], pool: 'kept']
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.dataSourceProperties': ['oracle.jdbc.sendBooleanAsNativeBoolean': 'true']
        ]))

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.dataSourceProperties['oracle.jdbc.sendBooleanAsNativeBoolean'] == 'true'
        connectionSource.settings.properties.dataSourceProperties.oracle.pool == 'kept'
        connectionSource.settings.properties.dataSourceProperties.oracle.jdbc == null
        def dataSource = unwrap(connectionSource.source)
        dataSource instanceof HikariDataSource
        dataSource.dataSourceProperties.getProperty('oracle.jdbc.sendBooleanAsNativeBoolean') == 'true'
        dataSource.dataSourceProperties.getProperty('oracle.pool') == 'kept'

        cleanup:
        connectionSource?.close()
    }

    void "create rejects a non-map properties value when no flattened properties exist"() {
        given:
        def factory = new DataSourceConnectionSourceFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('application', [
                'dataSource.url': 'jdbc:h2:mem:factoryInvalidPropertiesTest',
                'dataSource.properties': 'not a map'
        ]))

        when:
        factory.create(ConnectionSource.DEFAULT, environment)

        then:
        thrown(ConfigurationException)
    }

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
        connectionSource.settings.properties.isEmpty()
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

    static class ListDataSource extends HikariDataSource {

        List<String> connectionInitSqls
    }

    private static DataSource unwrap(DataSource dataSource) {
        DataSource current = dataSource
        while (current instanceof TransactionAwareDataSourceProxy || current instanceof LazyConnectionDataSourceProxy) {
            current = current instanceof TransactionAwareDataSourceProxy ?
                    ((TransactionAwareDataSourceProxy) current).targetDataSource :
                    ((LazyConnectionDataSourceProxy) current).targetDataSource
        }
        return current
    }
}


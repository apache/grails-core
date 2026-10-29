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
package org.grails.plugins.datasource

import java.nio.file.Files
import java.nio.file.Path

import javax.sql.DataSource

import com.zaxxer.hikari.HikariDataSource

import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy

import grails.util.Environment
import org.grails.config.yaml.YamlPropertySourceLoader
import org.grails.core.cfg.GroovyConfigPropertySourceLoader
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.ConnectionSource

import spock.lang.Specification

class DataSourceEnvironmentYamlSpec extends Specification {

    private static final String APPLICATION_YML = '''\
dataSource:
    pooled: true
    driverClassName: org.h2.Driver
    username: sa
    password: ''
    type: com.zaxxer.hikari.HikariDataSource
    properties:
        maximumPoolSize: 4
        minimumIdle: 1
        connectionTimeout: 1000
environments:
    development:
        dataSource:
            dbCreate: create-drop
            url: jdbc:h2:mem:devDb;DB_CLOSE_DELAY=-1
            properties:
                maximumPoolSize: 16
                minimumIdle: 8
                connectionTimeout: 2000
    test:
        dataSource:
            dbCreate: update
            url: jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1
            properties:
                maximumPoolSize: 2
                minimumIdle: 1
    production:
        dataSource:
            dbCreate: none
            url: jdbc:h2:mem:prodDb;DB_CLOSE_DELAY=-1
            properties:
                maximumPoolSize: 32
                minimumIdle: 4
                connectionTimeout: 5000
'''

    private String previousEnvironment

    def setup() {
        previousEnvironment = System.getProperty(Environment.KEY)
    }

    def cleanup() {
        if (previousEnvironment == null) {
            System.clearProperty(Environment.KEY)
        }
        else {
            System.setProperty(Environment.KEY, previousEnvironment)
        }
        Environment.reset()
    }

    void "application.yml #environment block supplies the dataSource properties"() {
        given:
        System.setProperty(Environment.KEY, environment)
        Environment.reset()
        def connectionSource = createFromApplicationYaml()
        def dataSource = unwrap(connectionSource.source)

        expect:
        connectionSource.settings.url == url
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == maximumPoolSize
        dataSource.minimumIdle == minimumIdle
        dataSource.connectionTimeout == connectionTimeout

        cleanup:
        connectionSource?.close()

        where:
        environment   | url                                    | maximumPoolSize | minimumIdle | connectionTimeout
        'development' | 'jdbc:h2:mem:devDb;DB_CLOSE_DELAY=-1'  | 16              | 8           | 2000
        'test'        | 'jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1' | 2               | 1           | 1000
        'production'  | 'jdbc:h2:mem:prodDb;DB_CLOSE_DELAY=-1' | 32              | 4           | 5000
    }

    void "unset grails.env uses the development dataSource properties"() {
        given:
        System.clearProperty(Environment.KEY)
        Environment.reset()
        def connectionSource = createFromApplicationYaml()
        def dataSource = unwrap(connectionSource.source)

        expect:
        connectionSource.settings.url == 'jdbc:h2:mem:devDb;DB_CLOSE_DELAY=-1'
        dataSource instanceof HikariDataSource
        dataSource.maximumPoolSize == 16
        dataSource.minimumIdle == 8
        dataSource.connectionTimeout == 2000

        cleanup:
        connectionSource?.close()
    }

    void "application.yml binds a list under dataSource properties"() {
        given:
        def yaml = """\
dataSource:
    url: jdbc:h2:mem:yamlListPropertiesTest
    type: ${ListDataSource.name}
    properties:
        connectionInitSqls:
            - SET A=1
            - SET B=2
"""
        def loaded = new YamlPropertySourceLoader().load(
                'application.yml', new ByteArrayResource(yaml.getBytes('UTF-8')))
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(loaded.first())

        when:
        def connectionSource = new DataSourceConnectionSourceFactory().create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.connectionInitSqls == ['SET A=1', 'SET B=2']
        unwrap(connectionSource.source).connectionInitSqls == ['SET A=1', 'SET B=2']

        cleanup:
        connectionSource?.close()
    }

    void "application.groovy dotted properties configure the connection pool"() {
        given:
        def directory = Files.createTempDirectory(Path.of('build'), 'datasource-groovy-').toFile()
        def application = new File(directory, 'application.groovy')
        application.setText('''\
dataSource.url = 'jdbc:h2:mem:groovyDottedPropertiesTest'
dataSource.type = 'com.zaxxer.hikari.HikariDataSource'
dataSource.properties.maximumPoolSize = 100
''', 'UTF-8')
        def loaded = new GroovyConfigPropertySourceLoader().load(
                'application.groovy', new FileSystemResource(application))
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(loaded.first())

        when:
        def connectionSource = new DataSourceConnectionSourceFactory().create(ConnectionSource.DEFAULT, environment)

        then:
        connectionSource.settings.properties.maximumPoolSize == 100
        unwrap(connectionSource.source).maximumPoolSize == 100

        cleanup:
        connectionSource?.close()
        directory?.deleteDir()
    }

    static class ListDataSource extends HikariDataSource {

        List<String> connectionInitSqls
    }

    private static createFromApplicationYaml() {
        def loaded = new YamlPropertySourceLoader().load(
                'application.yml',
                new ByteArrayResource(APPLICATION_YML.getBytes('UTF-8')))
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(loaded.first())
        new DataSourceConnectionSourceFactory().create(ConnectionSource.DEFAULT, environment)
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

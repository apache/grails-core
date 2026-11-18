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
package org.grails.plugins.databasemigration

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus

import grails.core.GrailsApplication
import liquibase.parser.ChangeLogParserFactory
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.databasemigration.liquibase.GrailsLiquibase
import org.grails.plugins.databasemigration.liquibase.GrailsLiquibaseFactory
import org.grails.plugins.databasemigration.liquibase.GroovyChangeLogParser
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification
import spock.lang.Unroll

import javax.sql.DataSource

class DatabaseMigrationGrailsPluginSpec extends Specification {

    void "test beanRegistrar registers beans"() {
        given:
        def plugin = new DatabaseMigrationGrailsPlugin()
        def application = Mock(GrailsApplication)
        def ctx = new GenericApplicationContext()
        application.getConfig() >> new PropertySourcesConfig()

        plugin.setGrailsApplication(application)
        plugin.setApplicationContext(ctx)

        // Ensure GroovyChangeLogParser is in the factory for configureLiquibase()
        if (!ChangeLogParserFactory.instance.parsers.find { it instanceof GroovyChangeLogParser }) {
            ChangeLogParserFactory.instance.register(new GroovyChangeLogParser())
        }

        when:
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(ctx.defaultListableBeanFactory, ctx.environment, registrar.getClass()).register(registrar)
        ctx.refresh()

        then:
        ctx.containsBean('grailsLiquibaseFactory')
        ctx.getBean('grailsLiquibaseFactory') instanceof GrailsLiquibase

        cleanup:
        ctx.close()
    }

    @Unroll
    void "test getDataSourceNames with config: #configMap"() {
        given:
        def plugin = new DatabaseMigrationGrailsPlugin()
        def application = Mock(GrailsApplication)
        application.getConfig() >> new PropertySourcesConfig(configMap)
        plugin.setGrailsApplication(application)

        expect:
        plugin.getDataSourceNames() as Set == expectedNames as Set

        where:
        configMap                               | expectedNames
        [:]                                     | ['dataSource']
        [dataSources: [other: [:]]]             | ['dataSource', 'other']
        [dataSources: [dataSource: [:]]]        | ['dataSource']
        [dataSources: [ds1: [:], ds2: [:]]]     | ['dataSource', 'ds1', 'ds2']
    }

    @Unroll
    void "test getDataSourceName for #input is #expected"() {
        expect:
        DatabaseMigrationGrailsPlugin.getDataSourceName(input) == expected

        where:
        input           | expected
        null            | null
        ''              | ''
        'dataSource'    | 'dataSource'
        'other'         | 'dataSource_other'
    }

    @Unroll
    void "test isDefaultDataSource for #input is #expected"() {
        expect:
        DatabaseMigrationGrailsPlugin.isDefaultDataSource(input) == expected

        where:
        input           | expected
        null            | true
        ''              | true
        'dataSource'    | true
        'other'         | false
    }

    void "test doWithApplicationContext skip when no updateOnStart"() {
        given:
        def plugin = new DatabaseMigrationGrailsPlugin()
        def application = Mock(GrailsApplication)
        def applicationContext = Mock(ApplicationContext)
        
        // Config with updateOnStart = false
        application.getConfig() >> new PropertySourcesConfig([
            'grails.plugin.databasemigration.updateOnStart': false
        ])
        
        plugin.setGrailsApplication(application)
        plugin.setApplicationContext(applicationContext)

        when:
        plugin.doWithApplicationContext()

        then:
        0 * applicationContext.getBean('grailsLiquibaseFactory', GrailsLiquibase)
    }

    void "test doWithApplicationContext triggers update when updateOnStart is true"() {
        given:
        def plugin = new DatabaseMigrationGrailsPlugin()
        def application = Mock(GrailsApplication)
        def applicationContext = Mock(ConfigurableApplicationContext)
        
        application.getConfig() >> new PropertySourcesConfig([
            'grails.plugin.databasemigration.updateOnStart': true,
            'grails.plugin.databasemigration.updateOnStartFileName': 'test-changelog.groovy'
        ])
        
        plugin.setGrailsApplication(application)
        plugin.setApplicationContext(applicationContext)

        def dataSource = Mock(DataSource)
        def transactionManager = Mock(PlatformTransactionManager)
        def grailsLiquibase = Mock(GrailsLiquibase)

        applicationContext.getBean('dataSource', DataSource) >> dataSource
        applicationContext.getBean('transactionManager', PlatformTransactionManager) >> transactionManager
        applicationContext.getBean('&grailsLiquibaseFactory') >> Mock(GrailsLiquibaseFactory)
        applicationContext.getBean('grailsLiquibaseFactory', GrailsLiquibase) >> grailsLiquibase

        // Mock PlatformTransactionManager and TransactionStatus
        def transactionStatus = Mock(TransactionStatus)
        transactionManager.getTransaction(_ as TransactionDefinition) >> transactionStatus

        // DatabaseMigrationTransactionManager uses applicationContext.getBean(beanName, PlatformTransactionManager)
        // Ensure ALL calls to getBean with any string and PlatformTransactionManager are handled
        applicationContext.getBean(_ as String, PlatformTransactionManager) >> transactionManager

        when:
        plugin.doWithApplicationContext()

        then:
        1 * grailsLiquibase.setChangeLog('test-changelog.groovy')
        1 * grailsLiquibase.afterPropertiesSet()
    }
}

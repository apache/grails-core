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

import javax.sql.DataSource

import groovy.sql.Sql

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.util.ClassUtils

import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import org.grails.config.PropertySourcesConfig
import org.grails.transaction.ChainedTransactionManagerPostProcessor

import spock.lang.Specification

/**
 * Created by graemerocher on 19/01/2017.
 */
class DataSourceGrailsPluginSpec extends Specification {

    void "test data sources Grails plugin Spring configuration"() {
        given:
        def ctx = new GenericApplicationContext()
        applyRegistrar(ctx, false, [
                'dataSource.pooled': true,
                'dataSource.url': 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'])

        when:
        ctx.refresh()

        then:
        ctx.containsBean('dataSource')
        ctx.getBean('dataSource', DataSource)

        when: 'a query is executed'
        def ds = ctx.getBean('dataSource', DataSource)
        def sql = new Sql(ds)
        int result = sql.call('CREATE TABLE `user` (username VARCHAR(50), password VARCHAR(50)); select * from `user`')

        then:
        result == 0

        cleanup:
        ctx.close()
    }

    void "no data source beans are registered without data source configuration"() {
        given:
        def ctx = new GenericApplicationContext()

        when:
        applyRegistrar(ctx, false)

        then:
        !ctx.containsBeanDefinition('dataSourceConnectionSources')
        !ctx.containsBeanDefinition('dataSource')
    }

    void "the chained transaction manager post-processor is registered when enabled with hibernate present"() {
        given:
        def ctx = new GenericApplicationContext()
        ctx.environment.propertySources.addFirst(new MapPropertySource('test',
                [(DataSourceGrailsPlugin.TRANSACTION_MANAGER_ENABLED): 'true']))

        when:
        applyRegistrar(ctx, true)

        then:
        ctx.beanFactory.getBeanDefinition('chainedTransactionManagerPostProcessor').beanClassName ==
                ChainedTransactionManagerPostProcessor.name

        and: 'the embedded database shutdown hook follows the h2 driver presence'
        ctx.containsBeanDefinition('embeddedDatabaseShutdownHook') ==
                ClassUtils.isPresent('org.h2.Driver', DataSourceGrailsPlugin.classLoader)
    }

    private void applyRegistrar(GenericApplicationContext ctx, boolean hibernatePresent, Map config = [:]) {
        def application = Mock(GrailsApplication)
        application.getConfig() >> new PropertySourcesConfig(config)
        def pluginManager = Mock(GrailsPluginManager)
        pluginManager.hasGrailsPlugin('hibernate') >> hibernatePresent

        def plugin = new DataSourceGrailsPlugin()
        plugin.setGrailsApplication(application)
        plugin.setPluginManager(pluginManager)
        plugin.setApplicationContext(ctx)

        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(ctx.defaultListableBeanFactory, (StandardEnvironment) ctx.environment,
                registrar.getClass()).register(registrar)
    }
}

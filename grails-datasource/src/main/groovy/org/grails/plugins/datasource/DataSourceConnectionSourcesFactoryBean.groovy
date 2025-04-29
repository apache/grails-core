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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertyResolver
import org.springframework.jdbc.datasource.DataSourceTransactionManager

import javax.sql.DataSource

/**
 * A factory bean for creating the data sources
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
class DataSourceConnectionSourcesFactoryBean implements InitializingBean, FactoryBean<ConnectionSources<DataSource, DataSourceSettings>>, ApplicationContextAware {

    final PropertyResolver configuration
    ApplicationContext applicationContext
    private ConnectionSources<DataSource, DataSourceSettings> connectionSources

    DataSourceConnectionSourcesFactoryBean(PropertyResolver configuration) {
        this.configuration = configuration
    }

    @Override
    ConnectionSources<DataSource, DataSourceSettings> getObject() throws Exception {
        return connectionSources
    }

    @Override
    Class<?> getObjectType() {
        return ConnectionSources
    }

    @Override
    boolean isSingleton() {
        return true
    }

    @Override
    void afterPropertiesSet() throws Exception {
        DataSourceConnectionSourceFactory factory = new DataSourceConnectionSourceFactory()
        this.connectionSources = ConnectionSourcesInitializer.create(factory, configuration)
        if(applicationContext instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext)applicationContext
            for(ConnectionSource<DataSource, ConnectionSourceSettings> connectionSource in connectionSources.allConnectionSources) {
                if (connectionSource.name != ConnectionSource.DEFAULT) {
                    String suffix = "_${connectionSource.name}"
                    String dsName = "dataSource${suffix}"
                    String tmName = "transactionManager${suffix}"
                    if(!applicationContext.containsBean(dsName)) {
                        DataSource dataSource = connectionSource.source
                        configurableApplicationContext.beanFactory.registerSingleton(
                                dsName,
                                dataSource
                        )
                    }
                    if(!applicationContext.containsBean(tmName)) {
                        DataSource dataSource = connectionSource.source
                        configurableApplicationContext.beanFactory.registerSingleton(
                                tmName,
                                new DataSourceTransactionManager(dataSource)
                        )
                    }
                }

            }
        }
    }

}

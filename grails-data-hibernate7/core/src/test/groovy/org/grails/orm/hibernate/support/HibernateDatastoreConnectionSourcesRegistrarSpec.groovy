/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.support

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.gorm.bootstrap.support.InstanceFactoryBean
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.hibernate.SessionFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.transaction.PlatformTransactionManager

import javax.sql.DataSource

class HibernateDatastoreConnectionSourcesRegistrarSpec extends HibernateGormDatastoreSpec {

    def 'test postProcessBeanDefinitionRegistry registers expected beans'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def dataSourceNames = [Settings.SETTING_DATASOURCE, 'readOnly']
        def registrar = new HibernateDatastoreConnectionSourcesRegistrar(dataSourceNames)

        when:
        registrar.postProcessBeanDefinitionRegistry(registry)

        then:
        // Default dataSource bean
        registry.containsBeanDefinition(Settings.SETTING_DATASOURCE)
        def defaultDs = registry.getBeanDefinition(Settings.SETTING_DATASOURCE)
        defaultDs.beanClass == InstanceFactoryBean
        defaultDs.targetType == DataSource
        defaultDs.constructorArgumentValues.genericArgumentValues[0].value == "#{dataSourceConnectionSourceFactory.create('dataSource', environment).source}"

        // Secondary dataSource bean
        registry.containsBeanDefinition("${Settings.SETTING_DATASOURCE}_readOnly")
        def readOnlyDs = registry.getBeanDefinition("${Settings.SETTING_DATASOURCE}_readOnly")
        readOnlyDs.beanClass == InstanceFactoryBean
        readOnlyDs.targetType == DataSource
        readOnlyDs.constructorArgumentValues.genericArgumentValues[0].value == "#{dataSourceConnectionSourceFactory.create('readOnly', environment).source}"

        // Secondary sessionFactory bean
        registry.containsBeanDefinition('sessionFactory_readOnly')
        def readOnlySf = registry.getBeanDefinition('sessionFactory_readOnly')
        readOnlySf.beanClass == InstanceFactoryBean
        readOnlySf.targetType == SessionFactory
        readOnlySf.constructorArgumentValues.genericArgumentValues[0].value == "#{hibernateDatastore.getDatastoreForConnection('readOnly').sessionFactory}"

        // Secondary transactionManager bean
        registry.containsBeanDefinition('transactionManager_readOnly')
        def readOnlyTm = registry.getBeanDefinition('transactionManager_readOnly')
        readOnlyTm.beanClass == InstanceFactoryBean
        readOnlyTm.targetType == PlatformTransactionManager
        readOnlyTm.constructorArgumentValues.genericArgumentValues[0].value == "#{hibernateDatastore.getDatastoreForConnection('readOnly').transactionManager}"

        // Default sessionFactory and transactionManager should NOT be registered by this registrar
        // (they are usually registered elsewhere for the default connection)
        !registry.containsBeanDefinition('sessionFactory_dataSource')
        !registry.containsBeanDefinition('transactionManager_dataSource')
    }
}

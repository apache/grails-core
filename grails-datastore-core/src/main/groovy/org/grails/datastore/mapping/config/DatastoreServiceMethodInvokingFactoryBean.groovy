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
package org.grails.datastore.mapping.config

import groovy.transform.CompileStatic
import groovy.transform.Internal

import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.lang.Nullable

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.services.Service

/**
 * Variant of {#link MethodInvokingFactoryBean} which returns the correct
 * data service type instead of {@code java.lang.Object} so the Autowire
 * with type works correctly.
 */
@Internal
@CompileStatic
class DatastoreServiceMethodInvokingFactoryBean extends MethodInvokingFactoryBean {

    private Class<?> serviceClass

    DatastoreServiceMethodInvokingFactoryBean(Class<?> serviceClass) {
        this.serviceClass = serviceClass
    }

    @Nullable
    private ConfigurableBeanFactory beanFactory

    @Override
    Class<?> getObjectType() {
        return serviceClass
    }

    @Override
    protected Object invokeWithTargetException() throws Exception {
        def object = super.invokeWithTargetException()
        if (object) {
            def effectiveDatastore = resolveEffectiveDatastore((Datastore) targetObject)
            ((Service) object).datastore = effectiveDatastore
            if (beanFactory instanceof AutowireCapableBeanFactory) {
                ((AutowireCapableBeanFactory) beanFactory).autowireBeanProperties(
                        object,
                        AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE,
                        false
                )
            }
        }
        object
    }

    private Datastore resolveEffectiveDatastore(Datastore defaultDatastore) {
        if (!(defaultDatastore instanceof MultipleConnectionSourceCapableDatastore)) {
            return defaultDatastore
        }

        // Check for explicit @Transactional(connection=...) on the service first - it takes precedence
        String serviceConnection = getServiceTransactionalConnection()
        if (serviceConnection != null
                && ConnectionSource.DEFAULT != serviceConnection
                && ConnectionSource.ALL != serviceConnection) {
            return ((MultipleConnectionSourceCapableDatastore) defaultDatastore)
                    .getDatastoreForConnection(serviceConnection)
        }

        // Fall back to domain class mapping datasource
        def domainClass = serviceDomainClass
        if (domainClass == null || domainClass == Object) {
            return defaultDatastore
        }

        def entity = defaultDatastore.mappingContext?.getPersistentEntity(domainClass.name)
        if (entity == null) {
            return defaultDatastore
        }

        def domainConnection = ConnectionSourcesSupport.getDefaultConnectionSourceName(entity)
        if (domainConnection != null
                && ConnectionSource.DEFAULT != domainConnection
                && ConnectionSource.ALL != domainConnection) {
            return ((MultipleConnectionSourceCapableDatastore) defaultDatastore)
                    .getDatastoreForConnection(domainConnection)
        }

        return defaultDatastore
    }

    private String getServiceTransactionalConnection() {
        try {
            for (def ann : serviceClass.annotations) {
                if ('grails.gorm.transactions.Transactional' == ann.annotationType().name) {
                    def connection = ann.annotationType().getMethod('connection').invoke(ann) as String
                    if (connection != null && !connection.isEmpty()) {
                        return connection
                    }
                }
            }
        }
        catch (Exception ignored) {
        }
        return null
    }

    private Class<?> getServiceDomainClass() {
        try {
            for (def ann : serviceClass.annotations) {
                if ('grails.gorm.services.Service' == ann.annotationType().name) {
                    return (Class<?>) ann.annotationType().getMethod('value').invoke(ann)
                }
            }
        }
        catch (Exception ignored) {
        }
        return null
    }

    @Override
    void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory)
        if (beanFactory instanceof ConfigurableBeanFactory) {
            this.beanFactory = (ConfigurableBeanFactory) beanFactory
        }
    }
}

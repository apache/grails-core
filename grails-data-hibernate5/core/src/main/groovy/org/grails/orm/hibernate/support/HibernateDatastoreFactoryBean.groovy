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

package org.grails.orm.hibernate.support

import groovy.transform.CompileStatic

import org.hibernate.SessionFactory

import org.springframework.beans.BeansException
import org.springframework.beans.factory.FactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.env.PropertyResolver

import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.AbstractHibernateDatastore

/**
 * Helper for constructing the datastore
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
public class HibernateDatastoreFactoryBean<T extends AbstractHibernateDatastore> implements FactoryBean<T>, ApplicationContextAware {

    private final Class<T> objectType
    private final MappingContext mappingContext
    private final SessionFactory sessionFactory
    private final PropertyResolver configuration
    private final String dataSourceName
    ApplicationContext applicationContext

    HibernateDatastoreFactoryBean(Class<T> objectType, MappingContext mappingContext, SessionFactory sessionFactory, PropertyResolver configuration, String dataSourceName) {
        this.objectType = objectType
        this.mappingContext = mappingContext
        this.sessionFactory = sessionFactory
        this.configuration = configuration
        this.dataSourceName = dataSourceName
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext
    }

    @Override
    public T getObject() throws Exception {
        AbstractHibernateDatastore datastore = objectType.newInstance(mappingContext, sessionFactory, configuration, dataSourceName)


        if(applicationContext != null) {
            datastore.setApplicationContext(applicationContext)
        }

        return datastore
    }

    @Override
    public Class<?> getObjectType() {
        return objectType
    }

    @Override
    public boolean isSingleton() {
        return true
    }
}

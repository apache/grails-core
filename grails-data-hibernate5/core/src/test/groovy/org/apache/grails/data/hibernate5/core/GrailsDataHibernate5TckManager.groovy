/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.hibernate5.core

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import groovy.sql.Sql
import org.apache.grails.data.testing.tck.base.GrailsDataTckManager
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.orm.hibernate.GrailsHibernateTransactionManager
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.h2.Driver
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationContext
import org.grails.orm.hibernate.support.hibernate5.SessionFactoryUtils
import org.grails.orm.hibernate.support.hibernate5.SessionHolder
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class GrailsDataHibernate5TckManager extends GrailsDataTckManager {
    GrailsApplication grailsApplication
    HibernateDatastore hibernateDatastore
    org.hibernate.Session hibernateSession
    GrailsHibernateTransactionManager transactionManager
    SessionFactory sessionFactory
    TransactionStatus transactionStatus
    HibernateMappingContextConfiguration hibernateConfig
    ApplicationContext applicationContext
    HibernateDatastore multiDataSourceDatastore
    HibernateDatastore multiTenantMultiDataSourceDatastore

    @Override
    void setup(Class<? extends Specification> spec) {
        cleanRegistry()
        super.setup(spec)
    }

    @Override
    Session createSession() {
        ConfigObject grailsConfig = new ConfigObject()
        boolean isTransactional = true

        System.setProperty('hibernate5.gorm.suite', "true")
        grailsApplication = new DefaultGrailsApplication(domainClasses as Class[], new GroovyClassLoader(GrailsDataHibernate5TckManager.getClassLoader()))
        if (grailsConfig) {
            grailsApplication.config.putAll(grailsConfig)
        }

        grailsConfig.dataSource.dbCreate = "create-drop"
        hibernateDatastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(grailsConfig), domainClasses as Class[])
        transactionManager = hibernateDatastore.getTransactionManager()
        sessionFactory = hibernateDatastore.sessionFactory
        if (transactionStatus == null && isTransactional) {
            transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition())
        } else if (isTransactional) {
            throw new RuntimeException("new transaction started during active transaction")
        }
        if (!isTransactional) {
            hibernateSession = sessionFactory.openSession()
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(hibernateSession))
        } else {
            hibernateSession = sessionFactory.currentSession
        }

        return hibernateDatastore.connect()
    }

    @Override
    void destroy() {
        super.destroy()

        if (transactionStatus != null) {
            def tx = transactionStatus
            transactionStatus = null
            transactionManager.rollback(tx)
        }
        if (hibernateSession != null) {
            SessionFactoryUtils.closeSession( (org.hibernate.Session)hibernateSession )
        }

        if(hibernateConfig != null) {
            hibernateConfig = null
        }
        hibernateDatastore.destroy()
        grailsApplication = null
        hibernateDatastore = null
        hibernateSession = null
        transactionManager = null
        sessionFactory = null
        if(applicationContext instanceof DisposableBean) {
            applicationContext.destroy()
        }
        applicationContext = null
        shutdownInMemDb()
    }

    @Override
    boolean supportsMultipleDataSources() {
        true
    }

    @Override
    void setupMultiDataSource(Class... domainClasses) {
        Map config = [
                'dataSource.url'           : "jdbc:h2:mem:tckDefaultDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'      : 'create-drop',
                'dataSource.dialect'       : H2Dialect.name,
                'dataSource.formatSql'     : 'true',
                'hibernate.flush.mode'     : 'COMMIT',
                'hibernate.cache.queries'  : 'true',
                'hibernate.hbm2ddl.auto'   : 'create-drop',
                'dataSources.secondary'    : [url: "jdbc:h2:mem:tckSecondaryDB;LOCK_TIMEOUT=10000"],
        ]
        multiDataSourceDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(config), domainClasses
        )
    }

    @Override
    void cleanupMultiDataSource() {
        if (multiDataSourceDatastore != null) {
            multiDataSourceDatastore.destroy()
            multiDataSourceDatastore = null
            shutdownInMemDb('jdbc:h2:mem:tckDefaultDB')
            shutdownInMemDb('jdbc:h2:mem:tckSecondaryDB')
        }
    }

    @Override
    def getServiceForConnection(Class serviceType, String connectionName) {
        multiDataSourceDatastore
                .getDatastoreForConnection(connectionName)
                .getService(serviceType)
    }

    @Override
    boolean supportsMultiTenantMultiDataSource() {
        true
    }

    @Override
    void setupMultiTenantMultiDataSource(Class... domainClasses) {
        Map config = [
                'grails.gorm.multiTenancy.mode'            : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'dataSource.url'                            : "jdbc:h2:mem:tckMtDefaultDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'                       : 'create-drop',
                'dataSource.dialect'                        : H2Dialect.name,
                'dataSource.formatSql'                      : 'true',
                'hibernate.flush.mode'                      : 'COMMIT',
                'hibernate.cache.queries'                   : 'true',
                'hibernate.hbm2ddl.auto'                    : 'create-drop',
                'dataSources.secondary'                     : [url: "jdbc:h2:mem:tckMtSecondaryDB;LOCK_TIMEOUT=10000"],
        ]
        multiTenantMultiDataSourceDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(config), domainClasses
        )
    }

    @Override
    void cleanupMultiTenantMultiDataSource() {
        if (multiTenantMultiDataSourceDatastore != null) {
            multiTenantMultiDataSourceDatastore.destroy()
            multiTenantMultiDataSourceDatastore = null
            shutdownInMemDb('jdbc:h2:mem:tckMtDefaultDB')
            shutdownInMemDb('jdbc:h2:mem:tckMtSecondaryDB')
        }
    }

    @Override
    def getServiceForMultiTenantConnection(Class serviceType, String connectionName) {
        multiTenantMultiDataSourceDatastore
                .getDatastoreForConnection(connectionName)
                .getService(serviceType)
    }

    private void shutdownInMemDb() {
        shutdownInMemDb('jdbc:h2:mem:grailsDb')
    }

    private void shutdownInMemDb(String url) {
        Sql sql = null
        try {
            sql = Sql.newInstance(url, 'sa', '', Driver.name)
            sql.executeUpdate('SHUTDOWN')
        } catch (e) {
            // already closed, ignore
        } finally {
            try { sql?.close() } catch (ignored) {}
        }
    }
}

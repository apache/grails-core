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
import org.h2.Driver
import org.hibernate.SessionFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationContext
import org.springframework.orm.hibernate5.SessionFactoryUtils
import org.springframework.orm.hibernate5.SessionHolder
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

    private void shutdownInMemDb() {
        Sql sql = null
        try {
            sql = Sql.newInstance('jdbc:h2:mem:grailsDb', 'sa', '', Driver.name)
            sql.executeUpdate('SHUTDOWN')
        } catch (e) {
            // already closed, ignore
        } finally {
            try { sql?.close() } catch (ignored) {}
        }
    }
}

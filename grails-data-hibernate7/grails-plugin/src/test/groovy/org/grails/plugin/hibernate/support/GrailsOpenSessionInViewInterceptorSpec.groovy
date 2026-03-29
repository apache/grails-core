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
package org.grails.plugin.hibernate.support

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.WebRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GrailsOpenSessionInViewInterceptorSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:osivSpecDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.secondary':[url:"jdbc:h2:mem:osivSecondaryDb;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), OsivSpecBook, OsivSpecAuthor)

    def "test hibernateFlushMode is correctly applied to default session"() {
        given: "An OSIV interceptor"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        when: "preHandle is called"
        interceptor.preHandle(webRequest)

        then: "the session is bound with the correct flush mode"
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore.sessionFactory)
        sessionHolder != null
        sessionHolder.session.hibernateFlushMode == FlushMode.COMMIT

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "test hibernateFlushMode is correctly applied to secondary session"() {
        given: "An OSIV interceptor"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')

        when: "preHandle is called"
        interceptor.preHandle(webRequest)

        then: "the secondary session is bound with the correct flush mode"
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(secondaryDatastore.sessionFactory)
        sessionHolder != null
        sessionHolder.session.hibernateFlushMode == FlushMode.COMMIT

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "test sessions are unbound and closed after completion"() {
        given: "An OSIV interceptor with bound sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)
        interceptor.preHandle(webRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        SessionFactory primarySf = datastore.sessionFactory
        SessionFactory secondarySf = secondaryDatastore.sessionFactory

        expect: "Sessions are bound"
        TransactionSynchronizationManager.hasResource(primarySf)
        TransactionSynchronizationManager.hasResource(secondarySf)

        when: "afterCompletion is called"
        interceptor.afterCompletion(webRequest, null)

        then: "Sessions are unbound"
        !TransactionSynchronizationManager.hasResource(primarySf)
        !TransactionSynchronizationManager.hasResource(secondarySf)
    }

    def "test postHandle flushes session if not manual"() {
        given: "An OSIV interceptor with a mocked session"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        def mockSessionFactory = Mock(SessionFactory)
        def mockSession = Mock(Session)
        interceptor.setSessionFactory(mockSessionFactory)

        mockSession.getHibernateFlushMode() >> FlushMode.AUTO
        SessionHolder sessionHolder = new SessionHolder(mockSession)
        TransactionSynchronizationManager.bindResource(mockSessionFactory, sessionHolder)

        WebRequest webRequest = Mock(WebRequest)

        when: "postHandle is called"
        interceptor.postHandle(webRequest, null)

        then: "session.flush() was called exactly once"
        1 * mockSession.flush()

        cleanup:
        TransactionSynchronizationManager.unbindResource(mockSessionFactory)
    }
}

@Entity
class OsivSpecBook {
    String title
    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class OsivSpecAuthor {
    String name
}

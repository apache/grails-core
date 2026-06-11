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
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.WebRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MultiDataSourceSessionSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.secondary':[url:"jdbc:h2:mem:secondaryDb;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), OsivBook, OsivAuthor)

    def "withSession on default datasource works with OSIV"() {
        given: "OSIV interceptor configured with the datastore"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        when: "OSIV preHandle is called"
        interceptor.preHandle(webRequest)

        then: "a session is bound for the default SessionFactory"
        TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        when: "withSession is called on default datasource"
        boolean sessionObtained = false
        OsivAuthor.withSession { Session s ->
            sessionObtained = s != null
        }

        then: "session is available"
        sessionObtained

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "withSession on secondary datasource works with OSIV"() {
        given: "OSIV interceptor configured with the datastore"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        when: "OSIV preHandle is called"
        interceptor.preHandle(webRequest)

        then: "a session is bound for both the default and secondary SessionFactory"
        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        TransactionSynchronizationManager.hasResource(secondaryDatastore.sessionFactory)

        when: "withSession is called on secondary datasource"
        boolean sessionObtained = false
        OsivBook.secondary.withSession { Session s ->
            sessionObtained = s != null
        }

        then: "session is available without 'No Session found for current thread' error"
        sessionObtained

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "afterCompletion cleans up sessions for all datasources"() {
        given: "OSIV interceptor with preHandle already called"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)
        interceptor.preHandle(webRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')

        when: "afterCompletion is called"
        interceptor.afterCompletion(webRequest, null)

        then: "sessions are unbound for all datasources"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondaryDatastore.sessionFactory)
    }

    def "OSIV skips secondary datasource if session already bound"() {
        given: "a pre-bound session for the secondary datasource"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        SessionFactory secondarySf = secondaryDatastore.sessionFactory
        Session preBoundSession = secondarySf.openSession()
        SessionHolder preBoundHolder = new SessionHolder(preBoundSession)
        TransactionSynchronizationManager.bindResource(secondarySf, preBoundHolder)

        when: "OSIV preHandle is called"
        interceptor.preHandle(webRequest)

        then: "the pre-bound session is preserved (not replaced)"
        def currentHolder = TransactionSynchronizationManager.getResource(secondarySf)
        currentHolder.is(preBoundHolder)

        cleanup:
        interceptor.afterCompletion(webRequest, null)
        if (TransactionSynchronizationManager.hasResource(secondarySf)) {
            TransactionSynchronizationManager.unbindResource(secondarySf)
        }
        preBoundSession.close()
    }

    def "CRUD operations work on secondary datasource with OSIV"() {
        given: "OSIV interceptor configured and preHandle called"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)
        interceptor.preHandle(webRequest)

        when: "data is saved to secondary datasource within a transaction"
        OsivBook book = OsivBook.withTransaction {
            new OsivBook(title: "Test Book").save(flush: true)
            OsivBook.first()
        }

        then: "the book is saved successfully"
        book != null
        book.title == "Test Book"

        when: "data is read from secondary datasource using withSession"
        int count = 0
        OsivBook.secondary.withSession { Session s ->
            count = OsivBook.count()
        }

        then: "the count is correct"
        count == 1

        cleanup:
        OsivBook.withTransaction {
            OsivBook.list()*.delete(flush: true)
        }
        interceptor.afterCompletion(webRequest, null)
    }
}

@Entity
class OsivBook {
    String title
    Date dateCreated
    Date lastUpdated

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class OsivAuthor {
    String name
}

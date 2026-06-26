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
package org.grails.orm.hibernate

import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.SingletonConnectionSources
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSource
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.VoidSessionCallback
import org.grails.orm.hibernate.connections.HibernateConnectionSource
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import spock.lang.AutoCleanup

class ChildHibernateDatastoreUnitSpec extends HibernateGormDatastoreSpec {

    void "test child datastore with real objects"() {
        given: "A primary datastore (parent)"
        HibernateDatastore parent = getDatastore()
        
        and: "A secondary connection source"
        def secondaryUrl = "jdbc:h2:mem:secondaryDB;LOCK_TIMEOUT=10000"
        def dataSource = new DriverManagerDataSource(secondaryUrl, "sa", "")
        def settings = new HibernateConnectionSourceSettings()
        
        def factory = parent.connectionSources.getFactory()
        def dataSourceConnectionSource = new DataSourceConnectionSource("secondary", dataSource, settings.getDataSource())
        
        def secondaryConnectionSource = factory.create("secondary", dataSourceConnectionSource, settings)
        
        when: "A child datastore is created"
        def child = new ChildHibernateDatastore(
                parent, 
                new SingletonConnectionSources(secondaryConnectionSource, parent.connectionSources.getBaseConfiguration()),
                parent.mappingContext,
                parent.eventPublisher
        )

        then: "It has its own session factory"
        child.getSessionFactory() != parent.getSessionFactory()
        
        when: "Executing a session on the child"
        String url = null
        child.withNewSession { Session s ->
            url = s.doReturningWork { it.getMetaData().getURL() }
        }

        then: "It uses the secondary database URL"
        url.startsWith("jdbc:h2:mem:secondaryDB")

        when: "Asking for the default connection from the child"
        def resolved = child.getDatastoreForConnection(ConnectionSource.DEFAULT)

        then: "It returns the parent"
        resolved == parent

        when: "Asking via the 'dataSource' setting name"
        def resolvedBySettingName = child.getDatastoreForConnection(Settings.SETTING_DATASOURCE)

        then: "It also returns the parent"
        resolvedBySettingName == parent

        when: "Asking for an unknown named connection"
        child.getDatastoreForConnection("nonExistentDs")

        then: "A ConfigurationException is thrown"
        thrown(ConfigurationException)

        cleanup:
        secondaryConnectionSource?.close()
    }

    // -------------------------------------------------------------------------
    // withSession — SCHEMA multi-tenancy session contract
    // -------------------------------------------------------------------------

    // Documents the contract that must hold for SCHEMA multi-tenancy to work:
    // calling withSession on a ChildHibernateDatastore must open and bind a real
    // Hibernate session so that GORM operations inside the closure can execute
    // without a surrounding transaction. This test currently FAILS before the fix
    // and PASSES after withSession() is routed through withNewSession() for children.
    void "withSession on a child datastore opens a native Hibernate session accessible inside the closure"() {
        given: "a child datastore on a separate in-memory H2 database"
        def child = buildChildDatastore()

        when: "withSession is called on the child without a surrounding transaction"
        String url = null
        child.withSession { Session s ->
            url = s.doReturningWork { conn -> conn.metaData.getURL() }
        }

        then: "the session was open and connected to the child database"
        url != null
        url.startsWith("jdbc:h2:mem:secondaryDB")

        cleanup:
        child?.close()
    }

    // Documents that DatastoreUtils.execute(child, callback) — the path used by
    // GormStaticApi.count() and other finders — provides a HibernateSession whose
    // getNativeSession() returns a valid open Hibernate session, not a fallback that
    // throws "No Session found for current thread".
    void "DatastoreUtils.execute on a child datastore provides a HibernateSession with a valid native session"() {
        given: "a child datastore"
        def child = buildChildDatastore()

        when: "DatastoreUtils.execute is used (the path taken by GormStaticApi.count() etc.)"
        boolean sessionWasOpen = false
        boolean sessionWasNonNull = false
        DatastoreUtils.execute(child, { session ->
            org.hibernate.Session nativeSession = (session as HibernateSession).getNativeSession()
            sessionWasNonNull = (nativeSession != null)
            sessionWasOpen = nativeSession?.isOpen()
        } as VoidSessionCallback)

        then: "the native Hibernate session was non-null and open while inside the callback"
        sessionWasNonNull
        sessionWasOpen

        cleanup:
        child?.close()
    }

    // -------------------------------------------------------------------------
    // Shared setup helper
    // -------------------------------------------------------------------------

    private ChildHibernateDatastore buildChildDatastore() {
        HibernateDatastore parent = getDatastore()
        def dataSource = new DriverManagerDataSource("jdbc:h2:mem:secondaryDB;LOCK_TIMEOUT=10000", "sa", "")
        def settings = new HibernateConnectionSourceSettings()
        def factory = parent.connectionSources.getFactory()
        def dataSourceConnectionSource = new DataSourceConnectionSource("secondary", dataSource, settings.getDataSource())
        def secondaryConnectionSource = factory.create("secondary", dataSourceConnectionSource, settings)
        return new ChildHibernateDatastore(
                parent,
                new SingletonConnectionSources(secondaryConnectionSource, parent.connectionSources.getBaseConfiguration()),
                parent.mappingContext,
                parent.eventPublisher
        )
    }
}

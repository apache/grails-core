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
}

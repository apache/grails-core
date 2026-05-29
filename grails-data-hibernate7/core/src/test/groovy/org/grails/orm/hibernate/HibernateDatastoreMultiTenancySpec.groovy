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

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.FlushMode
import spock.lang.Issue

import javax.sql.DataSource

class HibernateDatastoreMultiTenancySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.grailsConfig = [
                'dataSource.url'               : "jdbc:h2:mem:grailsDB-multi;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'          : 'create-drop',
                'hibernate.flush.mode'         : 'COMMIT',
                'grails.gorm.multiTenancy.mode': MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolver': new SystemPropertyTenantResolver()
        ]
        manager.addAllDomainClasses([MultiTenantBook])
    }

    void "test discriminator multi-tenancy filter"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "tenant1")
        
        when:
        def result = datastore.withSession {
            new MultiTenantBook(title: "Book 1").save()
            MultiTenantBook.list()
        }

        then:
        result.size() == 1
        result[0].tenantId == "tenant1"

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "tenant2")
        result = datastore.withSession {
            new MultiTenantBook(title: "Book 2").save()
            MultiTenantBook.list()
        }

        then:
        result.size() == 1
        result[0].tenantId == "tenant2"

        cleanup:
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
    }

    void "test getDatastoreForConnection throws exception for invalid connection"() {
        when:
        datastore.getDatastoreForConnection("invalid")

        then:
        thrown(org.grails.datastore.mapping.core.exceptions.ConfigurationException)
    }

    void "test resolveTenantIdentifier returns current tenant"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "tenant1")

        expect:
        datastore.resolveTenantIdentifier() == "tenant1"

        cleanup:
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
    }
}

@Entity
class MultiTenantBook implements MultiTenant<MultiTenantBook> {
    Long id
    String title
    String tenantId
}

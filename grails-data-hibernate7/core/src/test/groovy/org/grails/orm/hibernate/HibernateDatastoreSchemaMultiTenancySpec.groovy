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
package org.grails.orm.hibernate

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.Stepwise

class HibernateDatastoreSchemaMultiTenancySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.isTransactional = false
        manager.grailsConfig = [
                'dataSource.url'               : 'jdbc:h2:mem:grailsDB-schema;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1',
                'dataSource.dbCreate'          : 'create-drop',
                'hibernate.flush.mode'         : 'COMMIT',
                'grails.gorm.multiTenancy.mode': MultiTenancySettings.MultiTenancyMode.SCHEMA,
                'grails.gorm.multiTenancy.tenantResolver': new SystemPropertyTenantResolver()
        ]
        manager.addAllDomainClasses([SchemaBook])
    }

    void 'test schema multi-tenancy'() {
        when: 'A tenant is added'
        datastore.addTenantForSchema('tenant1')
        
        then: 'The child datastore is created'
        datastore.datastoresByConnectionSource.containsKey('tenant1')
        datastore.getDatastoreForConnection('tenant1') != null

        when: 'A book is saved for tenant1'
        Tenants.withId('tenant1') {
            SchemaBook.withTransaction {
                new SchemaBook(title: 'Book 1').save(flush: true)
            }
        }
        
        then: 'The book is found for tenant1'
        Tenants.withId('tenant1') {
            SchemaBook.count() == 1
        }

        when: 'Another tenant is added'
        datastore.addTenantForSchema('tenant2')
        
        then: 'The second tenant has no books'
        Tenants.withId('tenant2') {
            SchemaBook.count() == 0
        }
    }
}

@Entity
class SchemaBook implements MultiTenant<SchemaBook> {

    Long id
    String title
}

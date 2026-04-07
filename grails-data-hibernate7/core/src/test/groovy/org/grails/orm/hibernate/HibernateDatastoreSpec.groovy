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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.FlushMode
import org.springframework.context.support.GenericApplicationContext

class HibernateDatastoreSpec extends HibernateGormDatastoreSpec {

    void "test basic properties"() {
        expect:
        datastore.sessionFactory != null
        datastore.dataSource != null
        datastore.transactionManager != null
        datastore.mappingContext != null
        datastore.applicationEventPublisher != null
        datastore.dataSourceName == 'default'
    }

    void "test configuration settings"() {
        expect:
        !datastore.autoFlush // COMMIT mode in setupSpec
        datastore.defaultFlushMode == FlushMode.COMMIT
        !datastore.failOnError
        datastore.cacheQueries
    }

    void "test getDatastoreForConnection"() {
        expect:
        datastore.getDatastoreForConnection('dataSource') == datastore
        datastore.getDatastoreForConnection('default') == datastore
        datastore.getDatastoreForConnection('DEFAULT') == datastore
    }

    void "test withFlushMode"() {
        when:
        boolean result = false
        datastore.withFlushMode(FlushMode.ALWAYS) {
            result = datastore.sessionFactory.currentSession.hibernateFlushMode == FlushMode.ALWAYS
            return true
        }

        then:
        result
        datastore.sessionFactory.currentSession.hibernateFlushMode == FlushMode.COMMIT
    }

    void "test application context integration"() {
        given:
        def ctx = new GenericApplicationContext()
        ctx.refresh()

        when:
        datastore.setApplicationContext(ctx)

        then:
        datastore.applicationContext == ctx
    }

    void "test configure via map (Legacy/Test constructor)"() {
        when:"The map constructor is used"
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE,  "create-drop")
        HibernateDatastore testDatastore = new HibernateDatastore(config, GHUBook)

        then:"GORM is configured correctly"
        testDatastore.getMappingContext().getPersistentEntity(GHUBook.name) != null
        
        cleanup:
        testDatastore.close()
    }

    void "test resolveTenantIds returns empty list in non-multi-tenant mode"() {
        expect:
        datastore.resolveTenantIds() == []
    }

    void "test resolveTenantIdentifier throws TenantNotFoundException when no tenant is set"() {
        when:
        datastore.resolveTenantIdentifier()

        then:
        thrown(TenantNotFoundException)
    }

    void "test getDataSource(connectionName) returns the default DataSource for default connection"() {
        expect:
        datastore.getDataSource('default') != null
        datastore.getDataSource('default').is(datastore.dataSource)
    }
}

@Entity
class GHUBook {
    Long id
    String title
}

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
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.GrailsHibernateTemplate
import org.hibernate.FlushMode
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext

import java.io.IOException

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

    void "test getHibernateTemplate returns a template for the given flush mode"() {
        when:
        def template = datastore.getHibernateTemplate(GrailsHibernateTemplate.FLUSH_AUTO)

        then:
        template != null
        template instanceof GrailsHibernateTemplate
    }

    void "test openSession opens a session with the default flush mode"() {
        when:
        def session = datastore.openSession()

        then:
        session != null
        session.hibernateFlushMode == datastore.defaultFlushMode

        cleanup:
        session.close()
    }

    void "test hasCurrentSession returns true when a session is bound to the transaction"() {
        expect:
        // The per-feature transaction from the TCK manager binds a session
        datastore.hasCurrentSession()
    }

    void "test withFlushMode does not restore mode when callable throws"() {
        given:
        def session = datastore.sessionFactory.currentSession
        def originalMode = session.hibernateFlushMode

        when:
        datastore.withFlushMode(FlushMode.ALWAYS) {
            throw new RuntimeException("fail")
        }

        then:
        // callable threw, so reset=false — mode is NOT restored
        session.hibernateFlushMode == FlushMode.ALWAYS

        cleanup:
        session.setHibernateFlushMode(originalMode)
    }

    void "test setApplicationContext with non-ConfigurableApplicationContext is a no-op"() {
        given:
        def ctx = Mock(ApplicationContext)

        when:
        datastore.setApplicationContext(ctx)

        then:
        noExceptionThrown()
    }

    void "test getDatastoreForTenantId returns self in non-DATABASE multi-tenancy mode"() {
        expect:
        datastore.getDatastoreForTenantId('someTenant').is(datastore)
    }

    void "test addTenantForSchema throws ConfigurationException in non-SCHEMA mode"() {
        when:
        datastore.addTenantForSchema('some_schema')

        then:
        thrown(ConfigurationException)
    }

    void "test destroy is idempotent — second call is a no-op"() {
        given:
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")
        def ds = new HibernateDatastore(config, GHUBook)
        ds.destroy()

        when:
        ds.destroy()

        then:
        noExceptionThrown()

        cleanup:
        ds.close()
    }

    void "test destroy logs error when closeConnectionSources throws IOException"() {
        given:
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")
        def ds = new HibernateDatastore(config, GHUBook) {
            @Override
            protected void closeConnectionSources() throws IOException {
                throw new IOException("connection close failure")
            }
        }

        when:
        ds.destroy()

        then:
        noExceptionThrown()

        cleanup:
        ds.close()
    }

    void "test destroy logs error when closeGormEnhancer throws IOException"() {
        given:
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")
        def ds = new HibernateDatastore(config, GHUBook) {
            @Override
            protected void closeGormEnhancer() throws IOException {
                throw new IOException("enhancer close failure")
            }
        }

        when:
        ds.destroy()

        then:
        noExceptionThrown()

        cleanup:
        ds.close()
    }

    void "test isAutoFlush reflects defaultFlushMode"() {
        expect:
        !datastore.autoFlush
        datastore.defaultFlushMode == FlushMode.COMMIT
        datastore.defaultFlushModeName == 'COMMIT'
    }

    void "test isFailOnError, isOsivReadOnly, isPassReadOnlyToHibernate, isCacheQueries"() {
        expect:
        !datastore.failOnError
        !datastore.osivReadOnly
        !datastore.passReadOnlyToHibernate
        datastore.cacheQueries
    }

    void "test getMetadata returns non-null Metadata"() {
        expect:
        datastore.getMetadata() != null
    }

    void "test toString returns datasource name"() {
        expect:
        datastore.toString() == "HibernateDatastore: default"
    }

    void "test withSession and withNewSession for connectionName"() {
        when:
        def result1 = datastore.withSession("default") { session -> return "session1" }
        def result2 = datastore.withNewSession("default") { session -> return "session2" }

        then:
        result1 == "session1"
        result2 == "session2"
    }

    void "test withNewSession with tenantId for non-DATABASE multitenancy"() {
        when:
        def result = datastore.withNewSession((Serializable) "someTenant") { session -> return "sessionTenant" }

        then:
        result == "sessionTenant"
    }

    void "test close handles exception"() {
        given:
        def ds = new HibernateDatastore(Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop"), GHUBook) {
            @Override
            void destroy() throws Exception {
                throw new RuntimeException("destroy failed")
            }
        }
        
        when:
        ds.close()

        then:
        noExceptionThrown()

        cleanup:
        ds.close()
    }
    
    void "test constructors"() {
        given:
        def configMap = new HashMap<>(manager.grailsConfig)
        configMap.put("dataSource.url", "jdbc:h2:mem:grailsDB-constructors;LOCK_TIMEOUT=10000") // avoid clash with datastore
        org.springframework.core.env.PropertyResolver propertyResolver = org.grails.datastore.mapping.core.DatastoreUtils.createPropertyResolver(configMap)
        def dataSource = datastore.dataSource
        def eventPublisher = datastore.applicationEventPublisher as org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
        Package pkg = String.getPackage()
        
        when: "Constructor with EventPublisher and Classes"
        def ds1 = new HibernateDatastore(propertyResolver, eventPublisher, GHUBook)
        then:
        ds1 != null
        ds1.close()

        when: "Constructor with DataSource, EventPublisher and Classes"
        def ds2 = new HibernateDatastore(dataSource, propertyResolver, eventPublisher, GHUBook)
        then:
        ds2 != null
        ds2.close()

        when: "Constructor with EventPublisher and Packages"
        def ds3 = new HibernateDatastore(propertyResolver, eventPublisher, pkg)
        then:
        ds3 != null
        ds3.close()

        when: "Constructor with DataSource, EventPublisher and Packages"
        def ds4 = new HibernateDatastore(dataSource, propertyResolver, eventPublisher, pkg)
        then:
        ds4 != null
        ds4.close()

        when: "Constructor with Configuration Map and Packages"
        def ds5 = new HibernateDatastore(configMap, pkg)
        then:
        ds5 != null
        ds5.close()
        
        when: "Constructor with Packages only"
        def ds6 = new HibernateDatastore(pkg)
        then:
        ds6 != null
        ds6.close()
        
        when: "Constructor with MappingContext, SessionFactory, PropertyResolver, ApplicationContext, dataSourceName"
        def ds7 = new HibernateDatastore(datastore.mappingContext, datastore.sessionFactory, propertyResolver, datastore.applicationContext, "default")
        then:
        ds7 != null
        ds7.close()
        
        when: "Constructor with MappingContext, SessionFactory, PropertyResolver"
        def ds8 = new HibernateDatastore(datastore.mappingContext, datastore.sessionFactory, propertyResolver)
        then:
        ds8 != null
        ds8.close()
    }

    void "test setMessageSource"() {
        when:
        datastore.setMessageSource(Mock(org.springframework.context.MessageSource))

        then:
        noExceptionThrown()
    }
}

@Entity
class GHUBook {
    Long id
    String title
}

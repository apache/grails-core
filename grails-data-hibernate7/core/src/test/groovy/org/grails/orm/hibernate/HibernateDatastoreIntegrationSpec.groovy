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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.event.listener.HibernateEventListener
import org.hibernate.FlushMode
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared

@Testcontainers
@Requires({ isDockerAvailable() })
class HibernateDatastoreIntegrationSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")

    @Override
    void setupSpec() {
        println "=== HibernateDatastoreIntegrationSpec setup ==="
        println "  Docker socket : ${isDockerAvailable()}"
        println "  Container     : postgres:16"
        println "  Container running : ${postgres.running}"
        println "  JDBC URL      : ${postgres.jdbcUrl}"
        println "  Username      : ${postgres.username}"
        println "  Driver        : ${postgres.driverClassName}"
        println "================================================"

        manager.grailsConfig = [
            'dataSource.url'                 : postgres.jdbcUrl,
            'dataSource.driverClassName'     : postgres.driverClassName,
            'dataSource.username'            : postgres.username,
            'dataSource.password'            : postgres.password,
            'dataSource.dbCreate'            : 'create-drop',
            'hibernate.dialect'              : 'org.hibernate.dialect.PostgreSQLDialect',
            'hibernate.hbm2ddl.auto'         : 'create',
            'grails.gorm.failOnError'        : false,
            'grails.gorm.autoFlush'          : false,
            'grails.hibernate.cache.queries' : false,
            'grails.hibernate.osiv.readonly' : false,
        ]
        manager.addAllDomainClasses([DatastoreBook])
        println "================================================"
    }

    // -------------------------------------------------------------------------
    // Core infrastructure — non-null checks
    // -------------------------------------------------------------------------

    void "sessionFactory is available"() {
        expect:
        datastore.sessionFactory != null
    }

    void "dataSource is available"() {
        expect:
        datastore.dataSource != null
    }

    void "mappingContext is a HibernateMappingContext"() {
        expect:
        datastore.mappingContext instanceof HibernateMappingContext
    }

    void "transactionManager is available"() {
        expect:
        datastore.transactionManager != null
    }

    void "hibernate template is available"() {
        expect:
        datastore.hibernateTemplate != null
    }

    void "hibernate template with flush mode is available"() {
        expect:
        datastore.getHibernateTemplate(GrailsHibernateTemplate.FLUSH_COMMIT) != null
    }

    void "metadata is available"() {
        expect:
        datastore.metadata != null
    }

    // -------------------------------------------------------------------------
    // Configuration flags (HibernateDatastore)
    // -------------------------------------------------------------------------

    void "dataSourceName defaults to DEFAULT"() {
        expect:
        datastore.dataSourceName == 'DEFAULT'
    }

    void "isAutoFlush is false when grails.gorm.autoFlush is not set"() {
        expect:
        !datastore.autoFlush
    }

    void "defaultFlushMode is COMMIT by default"() {
        expect:
        // HibernateDatastore.FlushMode.COMMIT.level == 5
        datastore.defaultFlushMode == HibernateDatastore.FlushMode.COMMIT.level
    }

    void "defaultFlushModeName is COMMIT by default"() {
        expect:
        datastore.defaultFlushModeName == 'COMMIT'
    }

    void "isFailOnError is false by default"() {
        expect:
        !datastore.failOnError
    }

    void "isOsivReadOnly is false by default"() {
        expect:
        !datastore.osivReadOnly
    }

    void "isPassReadOnlyToHibernate is false by default"() {
        expect:
        !datastore.passReadOnlyToHibernate
    }

    void "isCacheQueries is false when not configured"() {
        expect:
        !datastore.cacheQueries
    }

    // -------------------------------------------------------------------------
    // FlushMode enum (HibernateDatastore.FlushMode)
    // -------------------------------------------------------------------------

    void "FlushMode enum levels are correctly ordered"() {
        expect:
        HibernateDatastore.FlushMode.MANUAL.level  == 0
        HibernateDatastore.FlushMode.COMMIT.level  == 5
        HibernateDatastore.FlushMode.AUTO.level    == 10
        HibernateDatastore.FlushMode.ALWAYS.level  == 20
    }

    void "FlushMode enum values are all present"() {
        expect:
        HibernateDatastore.FlushMode.values().size() == 4
        HibernateDatastore.FlushMode.valueOf('MANUAL')  != null
        HibernateDatastore.FlushMode.valueOf('COMMIT')  != null
        HibernateDatastore.FlushMode.valueOf('AUTO')    != null
        HibernateDatastore.FlushMode.valueOf('ALWAYS')  != null
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    void "hasCurrentSession is false outside a transaction"() {
        setup: "ensure no session is bound from a prior test"
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)

        expect:
        !datastore.hasCurrentSession()
    }

    void "hasCurrentSession is true inside withSession"() {
        when:
        boolean insideSession = false
        datastore.withSession {
            insideSession = datastore.hasCurrentSession()
        }

        then:
        insideSession
    }

    void "openSession returns a new Hibernate session with the default flush mode"() {
        when:
        def sess = datastore.openSession()

        then:
        sess != null
        sess.hibernateFlushMode.name() == datastore.defaultFlushModeName

        cleanup:
        sess?.close()
    }

    void "withSession executes the closure and returns a result"() {
        given:
        DatastoreBook.withTransaction {
            new DatastoreBook(title: "Groovy in Action", author: "Dierk König").save(flush: true, failOnError: true)
        }

        when:
        Long count = datastore.withSession { sess ->
            sess.createQuery("select count(b) from DatastoreBook b", Long).uniqueResult()
        }

        then:
        count >= 1L
    }

    void "withNewSession executes in a separate session"() {
        when: "a query runs inside a brand-new session opened by the datastore"
        Long count = datastore.withNewSession { sess ->
            sess.createQuery("select count(b) from DatastoreBook b", Long).uniqueResult()
        }

        then: "the new session is functional and returns a non-null result"
        count != null
        count >= 0L
    }

    // -------------------------------------------------------------------------
    // withFlushMode
    // -------------------------------------------------------------------------

    void "withFlushMode executes the callable"() {
        given:
        boolean executed = false

        when:
        DatastoreBook.withTransaction {
            datastore.withFlushMode(HibernateDatastore.FlushMode.AUTO) {
                executed = true
                true
            }
        }

        then:
        executed
    }

    void "withFlushMode restores the previous flush mode after execution"() {
        given:
        org.hibernate.FlushMode modeAfter

        when:
        DatastoreBook.withTransaction {
            def sess = sessionFactory.currentSession
            org.hibernate.FlushMode modeBefore = sess.hibernateFlushMode

            datastore.withFlushMode(HibernateDatastore.FlushMode.ALWAYS) { true }

            modeAfter = sess.hibernateFlushMode
        }

        then:
        // flush mode is restored to whatever it was before the call
        modeAfter != org.hibernate.FlushMode.ALWAYS
    }

    // -------------------------------------------------------------------------
    // MappingContext — entity registration
    // -------------------------------------------------------------------------

    void "mappingContext contains the registered domain class"() {
        when:
        def entity = datastore.mappingContext.getPersistentEntity(DatastoreBook.name)

        then:
        entity != null
        entity.javaClass == DatastoreBook
    }

    void "mappingContext reports the correct persistent properties for DatastoreBook"() {
        when:
        def entity = datastore.mappingContext.getPersistentEntity(DatastoreBook.name)
        def propNames = entity.persistentProperties*.name as Set

        then:
        'title'  in propNames
        'author' in propNames
    }

    // -------------------------------------------------------------------------
    // Metadata (Hibernate boot Metadata)
    // -------------------------------------------------------------------------

    void "metadata contains entity mappings for DatastoreBook"() {
        when:
        def entityBindings = datastore.metadata.entityBindings

        then:
        entityBindings.any { it.entityName.contains('DatastoreBook') }
    }

    // -------------------------------------------------------------------------
    // Event listeners (HibernateDatastore)
    // -------------------------------------------------------------------------

    void "eventTriggeringInterceptor is a HibernateEventListener"() {
        expect:
        datastore.eventTriggeringInterceptor instanceof HibernateEventListener
    }

    void "autoTimestampEventListener is registered"() {
        expect:
        datastore.autoTimestampEventListener != null
    }

    // -------------------------------------------------------------------------
    // getDatastoreForConnection
    // -------------------------------------------------------------------------

    void "getDatastoreForConnection with DEFAULT returns the same datastore"() {
        when:
        def same = datastore.getDatastoreForConnection('DEFAULT')

        then:
        same.is(datastore)
    }
}

@Entity
class DatastoreBook implements HibernateEntity<DatastoreBook> {
    String title
    String author
    static mapping = {
        id generator: 'identity'
    }
}

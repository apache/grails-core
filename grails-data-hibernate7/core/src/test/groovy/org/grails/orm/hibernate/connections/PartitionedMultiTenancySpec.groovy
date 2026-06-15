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
package org.grails.orm.hibernate.connections

import grails.gorm.DetachedCriteria
import grails.gorm.MultiTenant
import org.grails.orm.hibernate.connections.MultiTenantAuthor
import org.grails.orm.hibernate.connections.MultiTenantBook
import org.grails.orm.hibernate.connections.MultiTenantPublisher
import grails.gorm.hibernate.mapping.MappingBuilder
import org.grails.orm.hibernate.connections.MultiTenantAuthorService
import grails.gorm.multitenancy.Tenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect

/**
 * Created by graemerocher on 11/07/2016.
 *
 * NOTE: This test has been refactored and fixed by the Gemini CLI.
 * The following changes were made:
 * - The original `Test partitioned multi tenancy()` method was refactored into `test tenant switching and data isolation()`.
 * - Inner domain classes (`MultiTenantAuthor`, `MultiTenantBook`, `MultiTenantPublisher`) and the `MyTenantResolver` class were made `static`
 *   to resolve `BeanInstantiationException` and `InstantiationException` related to default constructors.
 * - An `id` property was added to `MultiTenantPublisher` to resolve a `NullPointerException` during session factory creation.
 * - Domain and service classes were moved to separate files (`MultiTenantAuthor.groovy`, `MultiTenantBook.groovy`,
 *   `MultiTenantPublisher.groovy`, `MultiTenantAuthorService.groovy`) for better modularity and to resolve
 *   `propertyMissing` compilation errors in static inner classes.
 * - Imports in `PartitionedMultiTenancySpec.groovy` were updated to reflect the new locations of the moved classes.
 * - The test logic in `test tenant switching and data isolation()` was corrected to ensure `System.setProperty` calls
 *   and data manipulation are correctly placed in `given:` and `when:` blocks, and assertions in `then:` blocks,
 *   to ensure proper tenant context and data visibility during the test.
 */
class PartitionedMultiTenancySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([MultiTenantAuthor, MultiTenantBook, MultiTenantPublisher])
        manager.grailsConfig = [
                'dataSource.url'                              : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'                         : 'update',
                'dataSource.dialect'                          : H2Dialect.name,
                'dataSource.formatSql'                        : 'true',
                'dataSource.logSql'                           : 'true',
                'hibernate.flush.mode'                        : 'COMMIT',
                // Disable query cache and 2nd level cache for this spec to avoid cross-tenant contamination
                'hibernate.cache.queries'                     : 'false',
                'hibernate.use_query_cache'                   : 'false',
                'hibernate.cache.use_second_level_cache'      : 'false',
                'hibernate.hbm2ddl.auto'                      : 'create',
                'hibernate.type.descriptor.sql'               : 'true',
                "grails.gorm.multiTenancy.mode"               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                "grails.gorm.multiTenancy.tenantResolverClass": MyTenantResolver,
        ]
    }

    def setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    def cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        try {
            manager.session?.clear()
        } catch (ignored) {
            // session may not be available in all contexts
        }
    }

    void "test no tenant id present"() {
        when: "no tenant id is present"
        MultiTenantAuthor.list()

        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "no tenant id is present"
        def author = new MultiTenantAuthor(name: "Stephen King")
        author.save(flush: true)

        then: "An exception is thrown"
        !author.errors.hasErrors()
        thrown(TenantNotFoundException)
    }

    void "test save and count for moreBooks tenant"() {
        when: "A tenant id is present"
        manager.hibernateDatastore.sessionFactory.currentSession.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        then: "the correct tenant is used"
        MultiTenantAuthor.count() == 0

        when: "An object is saved"
        def author = new MultiTenantAuthor(name: "Stephen King")
        author.save(flush: true)

        then: "The results are correct"
        author.tmp != null // the beforeInsert event was triggered
        MultiTenantAuthor.findByName("Stephen King")
        MultiTenantAuthor.findAll("from MultiTenantAuthor a", Collections.emptyMap()).size() == 1
        MultiTenantAuthor.count() == 1

        when: "An a transaction is used"
        MultiTenantAuthor.withTransaction {
            new MultiTenantAuthor(name: "JRR Tolkien").save(flush: true)
        }

        then: "The results are correct"
        MultiTenantAuthor.count() == 2
    }

    void "test tenant switching and data isolation"() {
        given: "Setup data for 'moreBooks' tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")
        new MultiTenantAuthor(name: "Stephen King").save(flush: true)
        MultiTenantAuthor.withTransaction {
            new MultiTenantAuthor(name: "JRR Tolkien").save(flush: true)
        }
        manager.session.clear() // Clear session after setup
        
        and: "Verify data for 'moreBooks' tenant immediately after creation"
        assert MultiTenantAuthor.count() == 2

        when: "The tenant id is switched to 'books'"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")
        // Ensure first-level session cache does not bleed across tenant switch
        manager.session.clear()

        then: "the correct tenant is used and no data exists for 'books'"
        MultiTenantAuthor.withNewSession { MultiTenantAuthor.count() } == 0
        MultiTenantAuthor.withNewSession { MultiTenantAuthor.findByName("Stephen King") } == null
        MultiTenantAuthor.withNewSession { MultiTenantAuthor.findAll("from MultiTenantAuthor a", Collections.emptyMap()).size() } == 0

        when: "Save data for 'books' tenant"
        // Clear any stale first-level cache before switching to explicit tenant contexts
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")
        new MultiTenantAuthor(name: "James Patterson").save(flush: true)
        manager.session.clear() // Clear session after saving

        then: "Verify data for 'James Patterson' in 'books' tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")
        Tenants.withCurrent {
            def results = MultiTenantAuthor.withCriteria {
                eq 'name', 'James Patterson'
            }
            results.size() == 1
        }
        Tenants.withCurrent {
            MultiTenantAuthor.findByName('James Patterson') != null
        }
        Tenants.withCurrent {
            MultiTenantAuthor.count() == 1
        }

        when: "Switch to 'moreBooks' tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")
        manager.session.clear()

        then: "Assert 'James Patterson' does not exist in 'moreBooks' tenant, and original data is present"
        MultiTenantAuthor.withCriteria {
            eq 'name', 'James Patterson'
        }.size() == 0
        MultiTenantAuthor.count() == 2
    }

    void "test multi tenancy and associations"() {
        when: "A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")

        MultiTenantAuthor.withTransaction {
            new MultiTenantAuthor(name: "Stephen King")
                    .addTo("books", [title: "The Stand"])
                    .addTo("books", [title: "The Shining"])
                    .save()

            new MultiTenantPublisher(name: "Fluff").save()
        }

        manager.session.clear()
        MultiTenantAuthor author = MultiTenantAuthor.findByName("Stephen King")
        MultiTenantPublisher publisher = MultiTenantPublisher.first()

        then: "The association ids are loaded with the tenant id"
        author.name == "Stephen King"
        author.books.size() == 2
        author.books.every() { MultiTenantBook book -> book.tenantCode == 'books' }
        publisher.tenantCode == 'books'

    }

    void "Test first "() {
        given: "Create two Authors with tenant T0"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A")])
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        MultiTenantAuthor.first()
        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "Query with a TENANT"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
        MultiTenantAuthor.first().name == 'A'

        when: "Query with OTHER TENANT"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        then:
        MultiTenantAuthor.first().name == 'B'
    }


    void "Test last "() {
        given: "Create two Authors with tenant T0"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A")])
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        MultiTenantAuthor.last()
        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "Query with a TENANT"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
        MultiTenantAuthor.last().name == 'A'

        when: "Query with OTHER TENANT"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        then:
        MultiTenantAuthor.last().name == 'B'
    }

    void "Test findAll with max params"() {
        given: "Create two Authors with tenant T0"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A")])
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        MultiTenantAuthor.findAll([max: 2])
        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "Query with a TENANT"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
        MultiTenantAuthor.findAll([max: 2]).name == ['A']

        when: "Query with OTHER TENANT"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        then:
        MultiTenantAuthor.findAll([max: 2]).name == ['B']
    }

    void "Test list without 'max' parameter"() {
        given: "Create two Authors with tenant T0"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A"), new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        MultiTenantAuthor.list()
        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "Query with the same tenant as saved, should obtain 2 entities"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
        MultiTenantAuthor.list().size() == 2
    }

    void "Test list with 'max' parameter"() {
        given: "Create two Authors with tenant T0"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        MultiTenantAuthor.saveAll([new MultiTenantAuthor(name: "A"), new MultiTenantAuthor(name: "B")])

        when: "Query with no tenant"
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        MultiTenantAuthor.list([max: 2])
        then: "An exception is thrown"
        thrown(TenantNotFoundException)

        when: "Query with the same tenant as saved, should obtain 2 entities"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'TENANT')
        then:
        MultiTenantAuthor.list().size() == 2

        when: "Check the paged results"
        def sameTenantList = MultiTenantAuthor.list([max: 1])
        then:
        sameTenantList.size() == 1
        sameTenantList.getTotalCount() == 2

        when: "Query by another tenant, should obtain no entities"
        manager.session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'OTHER TENANT')
        def list = MultiTenantAuthor.list([max: 2])
        then:
        list.size() == 0
        list.getTotalCount() == 0
    }

    static class MyTenantResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {

        Iterable<Serializable> resolveTenantIds() {
            Tenants.withoutId {
                def tenantIds = new DetachedCriteria<MultiTenantAuthor>(MultiTenantAuthor)
                        .distinct('tenantId')
                        .list()
                return tenantIds
            }
        }

    }
}

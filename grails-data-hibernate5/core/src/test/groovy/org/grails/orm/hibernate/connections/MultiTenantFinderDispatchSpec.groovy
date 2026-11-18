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

import grails.gorm.transactions.Rollback
import org.codehaus.groovy.runtime.InvokerHelper
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Regression coverage for dynamic-finder dispatch and tenant-scoped session passing on
 * multi-tenant entities.
 *
 * <p>When a dynamic finder such as {@code findByName} is invoked on a domain class through a
 * channel that resolves the name as a property before falling back to a method call (for example
 * Spock's {@code verifyMethodCondition} or any {@code InvokerHelper.invokeMethod} call), the
 * static API's {@code propertyMissing} handler must return a finder closure rather than a
 * connection-qualifier API. A qualifier API would be invoked via {@code call(arg)} and fail with a
 * {@code MissingMethodException} for {@code call}.</p>
 *
 * <p>The two-argument {@code withTenant} closure on a shared-connection (DISCRIMINATOR) entity must
 * also receive the datastore's own session, exposed as a native Hibernate session, rather than a
 * resolved child-datastore session.</p>
 */
@Rollback
@RestoreSystemProperties
class MultiTenantFinderDispatchSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore

    void setupSpec() {
        Map config = [
                "grails.gorm.multiTenancy.mode"               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                "grails.gorm.multiTenancy.tenantResolverClass": MyTenantResolver,
                'dataSource.url'                              : "jdbc:h2:mem:finderDispatchDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'                         : 'update',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
        ]
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), MultiTenantAuthor, MultiTenantBook, MultiTenantPublisher)
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")
    }

    void cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void "dynamic finder resolves through the property-then-call dispatch channel"() {
        given: "a saved author for the current tenant"
        new MultiTenantAuthor(name: "Stephen King").save(flush: true)

        when: "the finder is invoked via the property-fallback channel used by Spock conditions"
        def viaInvoker = InvokerHelper.invokeMethod(MultiTenantAuthor, "findByName", ["Stephen King"] as Object[])

        then: "the finder runs and returns the entity rather than throwing MissingMethodException('call')"
        viaInvoker != null
        viaInvoker.name == "Stephen King"

        and: "a Spock method condition (also a property-channel resolution) finds the entity"
        MultiTenantAuthor.findByName("Stephen King")
    }

    void "two-argument withTenant passes a native Hibernate session for shared-connection mode"() {
        given:
        new MultiTenantAuthor(name: "JRR Tolkien").save(flush: true)

        when: "withTenant is called with a closure typed against the native Hibernate session"
        Session captured = null
        MultiTenantAuthor.withTenant("books") { String tenantId, Session session ->
            captured = session
        }

        then: "the closure receives a usable native session"
        captured != null
        captured instanceof Session
    }
}

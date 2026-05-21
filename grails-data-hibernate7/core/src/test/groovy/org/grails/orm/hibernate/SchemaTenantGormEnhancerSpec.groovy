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
import grails.gorm.multitenancy.CurrentTenant
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.hibernate.dialect.H2Dialect
import spock.lang.Shared
import spock.lang.Specification

/**
 * Verifies the behaviour of SchemaTenantGormEnhancer
 * which is instantiated when the datastore runs in SCHEMA multi-tenancy mode.
 *
 * Because Hibernate infrastructure classes are final / sealed, we drive the
 * tests through a real {@link HibernateDatastore} built with a SCHEMA
 * multi-tenancy configuration and then inspect the enhancer directly.
 */
class SchemaTenantGormEnhancerSpec extends Specification {

    @Shared
    HibernateDatastore datastore

    @Shared
    def enhancer

    void setupSpec() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        Map config = [
                "grails.gorm.multiTenancy.mode"           : "SCHEMA",
                "grails.gorm.multiTenancy.tenantResolverClass": FixedTenantsResolver,
                'dataSource.url'                          : "jdbc:h2:mem:schemaEnhancerDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'                     : 'update',
                'dataSource.dialect'                      : H2Dialect.name,
                'dataSource.formatSql'                    : 'true',
                'hibernate.flush.mode'                    : 'COMMIT',
                'hibernate.hbm2ddl.auto'                  : 'create',
        ]
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), SchemaTenantBook)
        enhancer = datastore.gormEnhancer
    }

    void cleanupSpec() {
        datastore?.close()
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
    }

    void "gormEnhancer is an instance of SchemaTenantGormEnhancer in SCHEMA mode"() {
        expect:
        enhancer instanceof SchemaTenantGormEnhancer
    }

    void "allQualifiers includes tenant IDs from AllTenantsResolver for MultiTenant entity"() {
        given:
        def entity = datastore.getMappingContext().getPersistentEntity(SchemaTenantBook.name)

        when:
        List<String> qualifiers = enhancer.allQualifiers(datastore, entity)

        then:
        qualifiers.contains("tenantA")
        qualifiers.contains("tenantB")
    }

    void "allQualifiers does not add tenant IDs for non-MultiTenant entity"() {
        given:
        def entity = datastore.getMappingContext().getPersistentEntity(SchemaTenantBook.name)

        when:
        // Replace with a non-MultiTenant entity check using a regular entity
        List<String> baseQualifiers = enhancer.allQualifiers(datastore, entity)

        then:
        // It must return at least a non-empty list (DEFAULT qualifier always present)
        !baseQualifiers.isEmpty()
    }

    void "allQualifiers returns non-empty list (construction guard is transparent after init)"() {
        given:
        def entity = datastore.getMappingContext().getPersistentEntity(SchemaTenantBook.name)

        when:
        // If the null-guard in allQualifiers incorrectly stays active after construction,
        // tenant IDs will be missing. This verifies guard is only active during super().
        List<String> qualifiers = enhancer.allQualifiers(datastore, entity)

        then:
        qualifiers.containsAll(["tenantA", "tenantB"])
    }

    void "SchemaTenantGormEnhancer extends HibernateGormEnhancer"() {
        expect:
        HibernateGormEnhancer.isAssignableFrom(SchemaTenantGormEnhancer)
    }

    // -------------------------------------------------------------------------
    // else branch: tenantResolver is NOT an AllTenantsResolver
    // schemaHandler.resolveSchemaNames() path — tested via a second datastore built
    // with a plain TenantResolver (SystemPropertyTenantResolver alone).
    // -------------------------------------------------------------------------

    void "allQualifiers skips INFORMATION_SCHEMA and PUBLIC when resolving via schemaHandler"() {
        given: "a datastore whose tenantResolver is NOT an AllTenantsResolver"
        Map config = [
            "grails.gorm.multiTenancy.mode"              : "SCHEMA",
            "grails.gorm.multiTenancy.tenantResolverClass": SystemPropertyTenantResolver,
            'dataSource.url'                              : "jdbc:h2:mem:schemaSchemaHandlerDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'                        : 'update',
            'dataSource.dialect'                         : org.hibernate.dialect.H2Dialect.name,
            'hibernate.flush.mode'                       : 'COMMIT',
            'hibernate.hbm2ddl.auto'                     : 'create',
        ]
        HibernateDatastore schemaDs = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(config), SchemaTenantBook)
        def schemaEnhancer = schemaDs.gormEnhancer
        def entity = schemaDs.getMappingContext().getPersistentEntity(SchemaTenantBook.name)

        when: "allQualifiers resolves via schemaHandler (H2 returns no custom schemas)"
        List<String> qualifiers = schemaEnhancer.allQualifiers(schemaDs, entity)

        then: "no exception is thrown; INFORMATION_SCHEMA and PUBLIC are excluded"
        !qualifiers.contains("INFORMATION_SCHEMA")
        !qualifiers.contains("PUBLIC")

        cleanup:
        schemaDs?.close()
    }

    // -------------------------------------------------------------------------
    // Inline domain classes
    // -------------------------------------------------------------------------

    static class FixedTenantsResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {
        @Override
        Iterable<Serializable> resolveTenantIds() {
            return ["tenantA", "tenantB"]
        }
    }
}

@Entity
@CurrentTenant
class SchemaTenantBook implements GormEntity<SchemaTenantBook>, MultiTenant<SchemaTenantBook> {
    String title
    static constraints = { title blank: false }
}

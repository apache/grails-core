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
package grails.gorm.tests.generated

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Discriminator multi-tenancy over the generated mapping: the tenant filter that the generator declares keeps one tenant's rows
 * from another.
 */
@RestoreSystemProperties
class GeneratedDomainClassesMultiTenancySpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    def "a tenant sees only its own rows"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'ford')
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver.name,
                'dataSource.url'                              : 'jdbc:h2:mem:gdcTenants;LOCK_TIMEOUT=10000',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.cache.queries'                     : 'false',
                'hibernate.cache.use_query_cache'             : 'false',
                'hibernate.hbm2ddl.auto'                      : 'create',
                'hibernate.generatedDomainClasses'            : true,
        ]), GdcTenantItem)

        when:
        GdcTenantItem.withTransaction {
            new GdcTenantItem(name: 'mustang').save(flush: true)
        }
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tesla')
        GdcTenantItem.withTransaction {
            new GdcTenantItem(name: 'model s').save(flush: true)
            new GdcTenantItem(name: 'model 3').save(flush: true)
        }

        then: 'the entity is named after the domain class'
        datastore.sessionFactory.mappingMetamodel.getEntityDescriptor(GdcTenantItem).entityName == GdcTenantItem.name

        and:
        GdcTenantItem.withNewSession { GdcTenantItem.count() } == 2
        GdcTenantItem.withNewSession { GdcTenantItem.list()*.name.sort() } == ['model 3', 'model s']

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'ford')

        then:
        GdcTenantItem.withNewSession { GdcTenantItem.count() } == 1
        GdcTenantItem.withNewSession { GdcTenantItem.list()*.name } == ['mustang']
        GdcTenantItem.withNewSession { GdcTenantItem.findByName('model s') } == null
    }
}

@Entity
class GdcTenantItem implements MultiTenant<GdcTenantItem> {
    String name
    String tenantId
}

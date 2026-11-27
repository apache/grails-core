/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests.generated

import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A multi-tenant id that has a mapped type. The domain binder gives the column the type and builds the tenant filter from the bound
 * property, so a tenant still sees only its own rows; the generated mode does the same instead of refusing the mapping.
 */
@RestoreSystemProperties
class GeneratedDomainClassesTenantTypeSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(boolean generated) {
        datastore?.close()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'ford')
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver.name,
                'dataSource.url'                              : "jdbc:h2:mem:gdt${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'                         : 'create-drop',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.cache.queries'                     : 'false',
                'hibernate.cache.use_query_cache'             : 'false',
                'hibernate.generatedDomainClasses'            : generated,
        ]), GdtTypedTenantItem)
        return datastore
    }

    private Map<String, String> columns() {
        Map<String, String> result = new TreeMap<String, String>()
        for (Table table : datastore.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                table.columns.each { org.hibernate.mapping.Column column ->
                    result["${table.name}.${column.name}".toString()] = "${column.getSqlType(datastore.metadata)}${column.nullable ? '' : ' not null'}".toString()
                }
            }
        }
        return result
    }

    void "the tenant id column has the mapped type in both modes"() {
        when:
        boot(false)
        Map<String, String> binder = columns()
        boot(true)
        Map<String, String> generated = columns()

        then:
        generated == binder
        generated['gdt_typed_tenant_item.company_id'].startsWith('clob')
    }

    void "a tenant sees only its own rows in both modes"() {
        when:
        Map<Boolean, List> results = [false, true].collectEntries { boolean generated ->
            boot(generated)
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'ford')
            GdtTypedTenantItem.withTransaction { new GdtTypedTenantItem(name: 'mustang').save(failOnError: true, flush: true) }
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tesla')
            GdtTypedTenantItem.withTransaction {
                new GdtTypedTenantItem(name: 'model s').save(failOnError: true, flush: true)
                new GdtTypedTenantItem(name: 'model 3').save(failOnError: true, flush: true)
            }
            List tesla = GdtTypedTenantItem.withNewSession { [GdtTypedTenantItem.count(), GdtTypedTenantItem.list()*.name.sort()] }
            System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'ford')
            List ford = GdtTypedTenantItem.withNewSession { [GdtTypedTenantItem.count(), GdtTypedTenantItem.list()*.name, GdtTypedTenantItem.findByName('model s')] }
            [(generated): [tesla, ford]]
        }

        then:
        results[true] == results[false]
        results[true] == [[2, ['model 3', 'model s']], [1, ['mustang'], null]]
    }
}

@Entity
class GdtTypedTenantItem implements MultiTenant<GdtTypedTenantItem> {
    String name
    String companyId

    static mapping = {
        tenantId name: 'companyId'
        columns {
            companyId type: 'text'
        }
    }
}

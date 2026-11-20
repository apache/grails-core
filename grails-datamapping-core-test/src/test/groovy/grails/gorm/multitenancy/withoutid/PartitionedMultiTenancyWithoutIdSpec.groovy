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
package grails.gorm.multitenancy.withoutid

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

@RestoreSystemProperties
class PartitionedMultiTenancyWithoutIdSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()],
            getClass().getPackage()
    )

    void setup() {
        datastore.clearData()
        Voucher.withTenant('alice') {
            Voucher.withNewSession { new Voucher(number: 'A-1').save(flush: true) }
        }
    }

    void 'only withoutId runs without a tenant id'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'alice')

        expect: 'a resolved tenant id is a tenant id'
        !Tenants.isWithoutId(datastore)

        and: 'inside withoutId there is no tenant id, also inside withCurrent there'
        Tenants.withoutId(datastore) { Tenants.isWithoutId(datastore) }
        Tenants.withoutId(datastore) { Tenants.withCurrent(datastore.getClass()) { Tenants.isWithoutId(datastore) } }

        and: 'withId binds a tenant id, also inside withoutId, and withoutId applies again afterwards'
        !Tenants.withId(datastore, 'alice') { Tenants.isWithoutId(datastore) }
        Tenants.withoutId(datastore) {
            [Tenants.withId(datastore, 'alice') { Tenants.isWithoutId(datastore) }, Tenants.isWithoutId(datastore)]
        } == [false, true]

        and: 'the name of the default connection source is a tenant id like any other'
        !Tenants.withId(datastore, ConnectionSource.DEFAULT) { Tenants.isWithoutId(datastore) }
        !Tenants.withId(datastore, new String(ConnectionSource.DEFAULT)) { Tenants.isWithoutId(datastore) }
    }

    void 'inside withoutId the current tenant id is DEFAULT, and binding it again is a tenant id like any other'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'alice')

        when:
        Serializable captured = Tenants.withoutId(datastore) { Tenants.currentId(datastore) }

        then:
        captured == ConnectionSource.DEFAULT
        !Tenants.withId(datastore, captured) { Tenants.isWithoutId(datastore) }

        when: 'a resolver returns it later'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, captured as String)

        then:
        !Tenants.withCurrent(datastore.getClass()) { Tenants.isWithoutId(datastore) }
    }

    void 'an instance saved inside withoutId without a tenant id gets DEFAULT, and binding it again is a tenant id like any other'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'bob')

        when:
        final Voucher voucher = Tenants.withoutId(datastore) {
            Voucher.withNewSession { new Voucher(number: 'M-1').save(flush: true) }
        }

        then:
        voucher.tenantId == ConnectionSource.DEFAULT
        !Tenants.withId(datastore, voucher.tenantId) { Tenants.isWithoutId(datastore) }
    }

    void 'withCurrent inside withoutId stays without a tenant id and passes DEFAULT as the tenant id'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'alice')

        expect:
        Tenants.withoutId(datastore) {
            Tenants.withCurrent(datastore.getClass()) { Serializable tenantId -> [tenantId, Tenants.isWithoutId(datastore)] }
        } == [ConnectionSource.DEFAULT, true]
    }

    @Unroll
    void 'a resolved tenant id #description is a tenant id like any other'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, resolved)

        expect:
        !Tenants.isWithoutId(datastore)
        !Tenants.withCurrent(datastore.getClass()) { Tenants.isWithoutId(datastore) }

        where:
        description                                             | resolved
        'that is the name of the default connection source'     | ConnectionSource.DEFAULT
        'that equals the name of the default connection source' | new String(ConnectionSource.DEFAULT)
    }

    @Unroll
    void 'an instance saved for a resolved tenant id #description gets that tenant id, not the one it was given'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, resolved)

        when:
        Voucher.withNewSession { new Voucher(number: 'B-1', tenantId: 'alice').save(flush: true) }

        then:
        Voucher.withTenant('alice') { Voucher.withNewSession { Voucher.list()*.number } } == ['A-1']
        Voucher.withTenant(ConnectionSource.DEFAULT) { Voucher.withNewSession { Voucher.list()*.number } } == ['B-1']

        where:
        description                                             | resolved
        'that is the name of the default connection source'     | ConnectionSource.DEFAULT
        'that equals the name of the default connection source' | new String(ConnectionSource.DEFAULT)
    }

    void 'a service method with the current tenant assigns the resolved tenant id'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)

        when:
        new VoucherService().save('C-1', 'alice')

        then:
        Voucher.withTenant('alice') { Voucher.withNewSession { Voucher.list()*.number } } == ['A-1']
        Voucher.withTenant(ConnectionSource.DEFAULT) { Voucher.withNewSession { Voucher.list()*.number } } == ['C-1']
    }

    void 'inside withoutId an instance keeps the tenant id it was given'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'bob')

        when:
        Tenants.withoutId(datastore) {
            Voucher.withNewSession { new Voucher(number: 'D-1', tenantId: 'alice').save(flush: true) }
        }
        new VoucherService().saveWithoutTenant('D-2', 'alice')
        Tenants.withoutId(datastore) { new VoucherService().save('D-3', 'alice') }

        then: 'also a service method with the current tenant, called inside withoutId'
        Voucher.withTenant('alice') { Voucher.withNewSession { Voucher.list()*.number.sort() } } == ['A-1', 'D-1', 'D-2', 'D-3']
    }
}

@Entity
class Voucher implements MultiTenant<Voucher> {

    String number
    String tenantId
}

@CurrentTenant
@Transactional
class VoucherService {

    Voucher save(String number, String givenTenantId) {
        new Voucher(number: number, tenantId: givenTenantId).save(flush: true)
    }

    @WithoutTenant
    Voucher saveWithoutTenant(String number, String givenTenantId) {
        new Voucher(number: number, tenantId: givenTenantId).save(flush: true)
    }
}

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

import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

@Rollback
@RestoreSystemProperties
class PartitionedMultiTenancyWithoutIdSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore

    void setupSpec() {
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:withoutIdDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'update',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
        ]), Voucher)
    }

    Session getSession() {
        datastore.sessionFactory.currentSession
    }

    @Unroll
    void 'a query for a resolved tenant id #description finds the instances of that tenant only'() {
        given:
        final aliceId = save('alice', 'A-1')
        save('bob', 'B-1')
        save(ConnectionSource.DEFAULT, 'D-1')
        session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, resolved)

        expect:
        Voucher.count() == 1
        Voucher.list()*.number == ['D-1']
        Voucher.findAllByNumberLike('%')*.number == ['D-1']
        Voucher.get(aliceId) == null

        where:
        description                                             | resolved
        'that is the name of the default connection source'     | ConnectionSource.DEFAULT
        'that equals the name of the default connection source' | new String(ConnectionSource.DEFAULT)
    }

    @Unroll
    void 'an instance saved for a resolved tenant id #description gets that tenant id, not the one it was given'() {
        given:
        save('alice', 'A-1')
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, resolved)

        when:
        new Voucher(number: 'P-1', tenantId: 'alice').save(flush: true)

        then:
        storedTenantId('P-1') == ConnectionSource.DEFAULT
        Tenants.withId(datastore, 'alice') { Voucher.list()*.number } == ['A-1']

        where:
        description                                             | resolved
        'that is the name of the default connection source'     | ConnectionSource.DEFAULT
        'that equals the name of the default connection source' | new String(ConnectionSource.DEFAULT)
    }

    @Unroll
    void 'an instance updated for a resolved tenant id #description keeps that tenant id'() {
        given:
        final defaultId = save(ConnectionSource.DEFAULT, 'D-1')
        session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, resolved)

        when:
        final voucher = Voucher.get(defaultId)
        voucher.tenantId = 'bob'
        voucher.save(flush: true)

        then:
        storedTenantId('D-1') == ConnectionSource.DEFAULT

        where:
        description                                             | resolved
        'that is the name of the default connection source'     | ConnectionSource.DEFAULT
        'that equals the name of the default connection source' | new String(ConnectionSource.DEFAULT)
    }

    @Unroll
    void 'withId with a tenant id #description restricts queries and saves to that tenant'() {
        given:
        final aliceId = save('alice', 'A-1')
        save(ConnectionSource.DEFAULT, 'D-1')
        session.clear()

        when:
        final result = Tenants.withId(datastore, tenantId) {
            new Voucher(number: 'P-1', tenantId: 'alice').save(flush: true)
            [Voucher.list()*.number.sort(), Voucher.get(aliceId)]
        }

        then:
        result == [['D-1', 'P-1'], null]
        storedTenantId('P-1') == ConnectionSource.DEFAULT

        where:
        description                                             | tenantId
        'that is the name of the default connection source'     | ConnectionSource.DEFAULT
        'that equals the name of the default connection source' | new String(ConnectionSource.DEFAULT)
    }

    void 'the current tenant id taken inside withoutId restricts queries to the tenant DEFAULT when it is bound again'() {
        given:
        save('alice', 'A-1')
        save(ConnectionSource.DEFAULT, 'D-1')
        session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'alice')
        final Serializable captured = Tenants.withoutId(datastore) { Tenants.currentId(datastore) }

        expect:
        captured == ConnectionSource.DEFAULT
        Tenants.withId(datastore, captured) { Voucher.list()*.number } == ['D-1']

        when: 'a resolver returns it later'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, captured as String)

        then:
        Tenants.withCurrent(HibernateDatastore) { Voucher.list()*.number } == ['D-1']
    }

    void 'a service method with the current tenant is restricted to a resolved DEFAULT and runs without a tenant inside withoutId'() {
        given:
        save('alice', 'A-1')
        save(ConnectionSource.DEFAULT, 'D-1')
        session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)
        final service = new VoucherService()

        expect:
        service.numbers() == ['D-1']
        Tenants.withoutId(datastore) { service.numbers() } == ['A-1', 'D-1']
    }

    void 'inside withoutId a query finds the instances of every tenant and an instance keeps the tenant id it was given'() {
        given:
        final aliceId = save('alice', 'A-1')
        save('bob', 'B-1')
        session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'bob')

        when:
        final numbers = Tenants.withoutId(datastore) {
            new Voucher(number: 'W-1', tenantId: 'alice').save(flush: true)
            final voucher = Voucher.get(aliceId)
            voucher.tenantId = 'carol'
            voucher.save(flush: true)
            Voucher.list()*.number.sort()
        }

        then:
        numbers == ['A-1', 'B-1', 'W-1']
        storedTenantId('W-1') == 'alice'
        storedTenantId('A-1') == 'carol'
    }

    private Long save(Serializable tenantId, String number) {
        Tenants.withId(datastore, tenantId) {
            new Voucher(number: number).save(flush: true).id
        }
    }

    private Serializable storedTenantId(String number) {
        session.clear()
        Tenants.withoutId(datastore) {
            Voucher.findByNumber(number).tenantId
        }
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

    List<String> numbers() {
        Voucher.list()*.number.sort()
    }
}

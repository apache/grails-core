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
package grails.gorm.multitenancy.numerictenant

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

@RestoreSystemProperties
class PartitionedMultiTenancyNumericTenantSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()],
            getClass().getPackage()
    )

    void setup() {
        datastore.clearData()
        NumericVoucher.withTenant(0L) {
            NumericVoucher.withNewSession { new NumericVoucher(number: 'Z-1').save(flush: true) }
        }
        NumericVoucher.withTenant(7L) {
            NumericVoucher.withNewSession { new NumericVoucher(number: 'S-1').save(flush: true) }
        }
    }

    void 'a resolved default is not the numeric tenant 0'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, new String(ConnectionSource.DEFAULT))

        expect:
        NumericVoucher.withNewSession { NumericVoucher.list()*.number } == []

        when:
        NumericVoucher.withNewSession { new NumericVoucher(number: 'Z-2', tenantId: 7L).save(flush: true) }

        then:
        thrown(TenantException)
        NumericVoucher.withTenant(0L) { NumericVoucher.withNewSession { NumericVoucher.list()*.number } } == ['Z-1']
    }

    void 'inside withoutId a numeric tenant id is compared with 0 and an instance keeps the tenant id it was given'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, new String(ConnectionSource.DEFAULT))

        when:
        final numbers = Tenants.withoutId(datastore) {
            NumericVoucher.withNewSession {
                new NumericVoucher(number: 'S-2', tenantId: 7L).save(flush: true)
                NumericVoucher.list()*.number
            }
        }

        then:
        numbers == ['Z-1']
        NumericVoucher.withTenant(7L) { NumericVoucher.withNewSession { NumericVoucher.list()*.number.sort() } } == ['S-1', 'S-2']
    }
}

@Entity
class NumericVoucher implements MultiTenant<NumericVoucher> {

    String number
    Long tenantId
}

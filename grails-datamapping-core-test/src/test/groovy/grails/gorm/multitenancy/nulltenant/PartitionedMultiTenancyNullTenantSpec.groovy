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
package grails.gorm.multitenancy.nulltenant

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.simple.SimpleMapDatastore

class PartitionedMultiTenancyNullTenantSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new NullReturningTenantResolver()],
            getClass().getPackage()
    )

    void setup() {
        datastore.clearData()
        Ticket.withTenant('alice') {
            Ticket.withNewSession { new Ticket(number: 'A-1').save(flush: true) }
        }
    }

    void 'a resolver that returns null resolves no current tenant'() {
        when:
        Tenants.currentId(datastore)

        then:
        thrown(TenantNotFoundException)
    }

    void 'a query without a current tenant fails'() {
        when:
        Ticket.withNewSession { Ticket.list() }

        then:
        thrown(TenantNotFoundException)
    }

    void 'a save without a current tenant fails'() {
        when:
        Ticket.withNewSession { new Ticket(number: 'N-1', tenantId: 'alice').save(flush: true) }

        then:
        thrown(TenantNotFoundException)
        Ticket.withTenant('alice') { Ticket.withNewSession { Ticket.list()*.number } } == ['A-1']
    }

    void 'withoutId still runs without a tenant id'() {
        when:
        Tenants.withoutId(datastore) {
            Ticket.withNewSession { new Ticket(number: 'W-1', tenantId: 'alice').save(flush: true) }
        }

        then:
        Ticket.withTenant('alice') { Ticket.withNewSession { Ticket.list()*.number.sort() } } == ['A-1', 'W-1']
    }
}

class NullReturningTenantResolver implements TenantResolver {

    @Override
    Serializable resolveTenantIdentifier() {
        null
    }
}

@Entity
class Ticket implements MultiTenant<Ticket> {

    String number
    String tenantId
}

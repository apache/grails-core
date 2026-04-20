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
package grails.gorm.tests

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.TenantService
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.util.environment.RestoreSystemProperties
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 11/01/2017.
 */
@RestoreSystemProperties
class TenantServiceSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DATABASE,
                    (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
            ),
            [ConnectionSource.DEFAULT, 'two'],
            MultiTenantTeam
    )

    void "test multi tenancy with in-memory datastore"() {
        when:
        MultiTenantTeam.count()

        then:
        thrown TenantNotFoundException

        when:
        TenantService tenantService = datastore.getService(TenantService)
        def twoCount = tenantService.withId("two") {
            new MultiTenantTeam(name: "Arsenal").save(flush:true)
            MultiTenantTeam.count()
        }
        def defaultCount = tenantService.withId(ConnectionSource.DEFAULT) { MultiTenantTeam.count() }
        MultiTenantTeam.count()

        then:
        twoCount == 1
        defaultCount == 0
        thrown TenantNotFoundException

        when:"The current tenant is set"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "two")
        new MultiTenantTeam(name: "Chelsea").save(flush:true)
        twoCount == MultiTenantTeam.count()
        defaultCount = tenantService.withoutId {
            MultiTenantTeam.count()
        }

        then:
        tenantService.currentId() == "two"
        MultiTenantTeam.findByName("Chelsea") != null
        MultiTenantTeam.findByName("Arsenal") != null
        defaultCount == 0
        MultiTenantTeam.count() == 2


        when:"The current tenant is set"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)
        new MultiTenantTeam(name: "Manchester United").save(flush:true)


        then:
        tenantService.currentId() == ConnectionSource.DEFAULT
        MultiTenantTeam.findByName("Chelsea") == null
        MultiTenantTeam.findByName("Arsenal") == null
        MultiTenantTeam.count() == 1

    }
}

@Entity
class MultiTenantTeam implements MultiTenant<MultiTenantTeam> {
    String name
}



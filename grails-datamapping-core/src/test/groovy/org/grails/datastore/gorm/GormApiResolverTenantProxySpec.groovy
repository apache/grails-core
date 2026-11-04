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
package org.grails.datastore.gorm

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class GormApiResolverTenantProxySpec extends Specification {

    void "findEntity resolves the current tenant before an active session for #entity.simpleName with bound tenant #boundTenant"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantEntity = Stub(PersistentEntity)
        def tenantMappingContext = Stub(MappingContext) {
            getPersistentEntity(GormApiResolverTenantEntity.name) >> tenantEntity
        }
        def tenantDatastore = Stub(Datastore) {
            getMappingContext() >> tenantMappingContext
        }
        def activeEntity = Stub(PersistentEntity)
        def activeMappingContext = Stub(MappingContext) {
            getPersistentEntity(GormApiResolverTenantEntity.name) >> activeEntity
        }
        def activeDatastore = Stub(Datastore) {
            hasCurrentSession() >> true
            getMappingContext() >> activeMappingContext
        }
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> 'tenant1'
        }
        def defaultDatastore = Stub(MultiTenantCapableDatastore) {
            getTenantResolver() >> tenantResolver
        }
        registry.registerEntityDatastore(GormApiResolverTenantEntity.name, ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerEntityDatastore(GormApiResolverTenantEntity.name, 'tenant1', tenantDatastore)
        registry.registerEntityDatastore(GormApiResolverTenantEntity.name, 'tenant2', activeDatastore)
        TransactionSynchronizationManager.bindResource(activeDatastore, new Object())
        if (boundTenant) {
            CurrentTenantHolder.set(defaultDatastore, 'tenant1')
        }

        when:
        def result = resolver.findEntity(entity)

        then:
        result.is(tenantEntity)

        cleanup:
        CurrentTenantHolder.remove(defaultDatastore)
        TransactionSynchronizationManager.unbindResource(activeDatastore)

        where:
        [entity, boundTenant] << [[GormApiResolverTenantEntity,
                                  GormApiResolverTenantEntity$HibernateProxy,
                                  GormApiResolverTenantEntity$HibernateProxy$Wrapper], [false, true]].combinations()
    }
}

class GormApiResolverTenantEntity implements MultiTenant<GormApiResolverTenantEntity> {
    Long id
}

class GormApiResolverTenantEntity$HibernateProxy extends GormApiResolverTenantEntity {
}

class GormApiResolverTenantEntity$HibernateProxy$Wrapper extends GormApiResolverTenantEntity$HibernateProxy {
}

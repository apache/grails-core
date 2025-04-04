/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.services

import grails.gorm.multitenancy.TenantService
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.services.Service

/**
 * Default implementation of the {@link TenantService} interface
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class DefaultTenantService implements Service, TenantService {

    @Override
    void eachTenant(Closure callable) {
        MultiTenantCapableDatastore multiTenantCapableDatastore = multiTenantDatastore()
        Tenants.eachTenant(multiTenantCapableDatastore, callable)
    }

    @Override
    Serializable currentId() {
        MultiTenantCapableDatastore multiTenantCapableDatastore = multiTenantDatastore()
        def mode = multiTenantCapableDatastore.getMultiTenancyMode()
        if(mode != MultiTenancySettings.MultiTenancyMode.NONE) {
            return Tenants.currentId(multiTenantCapableDatastore)
        }
        else {
            throw new DatastoreConfigurationException("Current datastore [$datastore] is not configured for Multi-Tenancy")
        }
    }

    @Override
    def <T> T withoutId(Closure<T> callable) {
        MultiTenantCapableDatastore multiTenantCapableDatastore = multiTenantDatastore()
        def mode = multiTenantCapableDatastore.getMultiTenancyMode()
        if(mode != MultiTenancySettings.MultiTenancyMode.NONE) {
            return Tenants.withoutId(multiTenantCapableDatastore, callable)
        }
        else {
            throw new DatastoreConfigurationException("Current datastore [$datastore] is not configured for Multi-Tenancy")
        }
    }

    @Override
    def <T> T withCurrent(Closure<T> callable) {
        MultiTenantCapableDatastore multiTenantCapableDatastore = multiTenantDatastore()
        def mode = multiTenantCapableDatastore.getMultiTenancyMode()
        if(mode != MultiTenancySettings.MultiTenancyMode.NONE) {
            return Tenants.withId(multiTenantCapableDatastore, currentId(), callable)
        }
        else {
            throw new DatastoreConfigurationException("Current datastore [$datastore] is not configured for Multi-Tenancy")
        }
    }

    @Override
    def <T> T withId(Serializable tenantId, Closure<T> callable) {
        MultiTenantCapableDatastore multiTenantCapableDatastore = multiTenantDatastore()
        def mode = multiTenantCapableDatastore.getMultiTenancyMode()
        if(mode != MultiTenancySettings.MultiTenancyMode.NONE) {
            return Tenants.withId(multiTenantCapableDatastore, tenantId, callable)
        }
        else {
            throw new DatastoreConfigurationException("Current datastore [$datastore] is not configured for Multi-Tenancy")
        }
    }

    protected MultiTenantCapableDatastore multiTenantDatastore() {
        MultiTenantCapableDatastore multiTenantCapableDatastore
        if (datastore instanceof MultiTenantCapableDatastore) {
            multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
        } else {
            throw new DatastoreConfigurationException("Current datastore [$datastore] is not Multi-Tenant capable")
        }
        return multiTenantCapableDatastore
    }
}

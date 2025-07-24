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

package org.grails.datastore.mapping.multitenancy

import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

import org.springframework.beans.BeanUtils

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.resolvers.NoTenantResolver

/**
 * Represents the multi tenancy settings
 */
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class MultiTenancySettings {

    TenantResolver tenantResolver

    /**
     * The default multi tenancy mode
     */
    MultiTenancyMode mode = MultiTenancyMode.NONE

    /**
     * The tenant resolver class
     */

    Class<? extends TenantResolver> tenantResolverClass

    /**
     * @return The tenant resolver
     */
    TenantResolver getTenantResolver() {
        if(tenantResolver != null) {
            return tenantResolver
        }
        else if(tenantResolverClass != null) {
            return BeanUtils.instantiateClass(tenantResolverClass)
        }
        return new NoTenantResolver()
    }

    /**
     * Sets the tenant resolver to use
     *
     * @param tenantResolver The tenant resolver to use
     */
    void setTenantResolver(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver
    }

    /**
     * The multi-tenancy mode
     */
    static enum MultiTenancyMode {
        /**
         * No multi tenancy
         */
        NONE,
        /**
         * A single database per tenant
         */
        DATABASE,

        /**
         * A shared database amongst multiple tenants using a separate schema for each tenant
         */
        SCHEMA,
        /**
         * A shared database amongst multiple tenants using a discriminator column
         */
        DISCRIMINATOR

        /**
         * @return Whether a single shared connection is used
         */
        boolean isSharedConnection() {
            return this == DISCRIMINATOR || this == SCHEMA
        }
    }

    /**
     * Resolves the connection to use for the given tenant id based on the current mode
     *
     * @param mode The datastore
     * @param tenantId The tenant id
     * @return
     */
    static String resolveConnectionForTenantId(MultiTenancyMode mode, Serializable tenantId) {
        if(mode.isSharedConnection()) {
            return ConnectionSource.DEFAULT
        }
        else {
            return tenantId.toString()
        }
    }
}

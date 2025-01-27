package org.grails.datastore.mapping.multitenancy

import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.resolvers.NoTenantResolver
import org.springframework.beans.BeanUtils

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

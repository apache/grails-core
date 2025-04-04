package grails.gorm.rx.multitenancy

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.gorm.rx.api.RxGormEnhancer
/**
 * Tenants implementation for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class Tenants {
    /**
     * Execute the given closure for each tenant.
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static void eachTenant(@DelegatesTo(RxDatastoreClient) Closure callable) {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findSingleDatastoreClient()
        eachTenantInternal(datastoreClient, callable)
    }

    /**
     * Execute the given closure for each tenant.
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static void eachTenant(Class<? extends RxDatastoreClient> datastoreClass, @DelegatesTo(RxDatastoreClient) Closure callable) {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findDatastoreClientByType(datastoreClass)
        eachTenantInternal(datastoreClient, callable)
    }

    /**
     * @return The current tenant id
     */
    static Serializable currentId() {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findSingleDatastoreClient()
        def tenantId = grails.gorm.multitenancy.Tenants.CurrentTenant.get()
        if(tenantId != null) {
            return tenantId
        }
        else {
            return datastoreClient.getTenantResolver().resolveTenantIdentifier()
        }
    }

    /**
     * @return The current tenant id for the given datastore type
     */
    static Serializable currentId(Class<? extends RxDatastoreClient> datastoreClass) {
        RxDatastoreClient datastore = RxGormEnhancer.findDatastoreClientByType(datastoreClass)
        def tenantId = grails.gorm.multitenancy.Tenants.CurrentTenant.get()
        if(tenantId != null) {
            log.debug "Found tenant id [$tenantId] bound to thread local"
            return tenantId
        }
        else {
            def tenantResolver = datastore.getTenantResolver()
            def tenantIdentifier = tenantResolver.resolveTenantIdentifier()
            log.debug "Resolved tenant id [$tenantIdentifier] from resolver [${tenantResolver.getClass().simpleName}]"
            return tenantIdentifier
        }
    }

    /**
     * Execute the given closure with the current tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withCurrent(@DelegatesTo(RxDatastoreClient) Closure<T> callable) {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findSingleDatastoreClient()
        def tenantIdentifier = datastoreClient.getTenantResolver().resolveTenantIdentifier()
        return withTenantIdInternal(datastoreClient, tenantIdentifier, callable)
    }

    /**
     * Execute the given closure with the current tenant
     *
     * @param datastoreClass The datastore class
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withCurrent(Class<? extends RxDatastoreClient> datastoreClass, @DelegatesTo(RxDatastoreClient) Closure<T> callable) {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findDatastoreClientByType(datastoreClass)
        def tenantIdentifier = datastoreClient.getTenantResolver().resolveTenantIdentifier()
        return withTenantIdInternal(datastoreClient, tenantIdentifier, callable)

    }

    /**
     * Execute the given closure with given tenant id
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withId(Serializable tenantId, @DelegatesTo(RxDatastoreClient) Closure<T> callable) {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findSingleDatastoreClient()
        return withTenantIdInternal(datastoreClient, tenantId, callable)
    }
    /**
     * Execute the given closure with given tenant id
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withId(Class<? extends RxDatastoreClient> datastoreClass, Serializable tenantId, @DelegatesTo(RxDatastoreClient) Closure<T> callable) {
        RxDatastoreClient datastoreClient = RxGormEnhancer.findDatastoreClientByType(datastoreClass)
        return withTenantIdInternal(datastoreClient, tenantId, callable)
    }

    private static <T> T withTenantIdInternal(RxDatastoreClient datastoreClient, Serializable tenantIdentifier, Closure<T> callable) {
        return grails.gorm.multitenancy.Tenants.CurrentTenant.withTenant(tenantIdentifier) {
            callable.setDelegate(datastoreClient)
            def i = callable.parameterTypes.length
            switch (i) {
                case 0:
                    return callable.call()
                    break
                case 1:
                    return callable.call(tenantIdentifier)
                    break
                default:
                    throw new IllegalArgumentException("Provided closure accepts too many arguments")
            }
        }
    }

    protected static void eachTenantInternal(RxDatastoreClient datastoreClient, Closure callable) {
        MultiTenancySettings.MultiTenancyMode multiTenancyMode = datastoreClient.multiTenancyMode
        ConnectionSources connectionSources = datastoreClient.connectionSources
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            for (ConnectionSource connectionSource in connectionSources.allConnectionSources) {
                def tenantId = connectionSource.name
                if (tenantId != ConnectionSource.DEFAULT) {
                    withTenantIdInternal(datastoreClient, tenantId, callable)
                }
            }
        } else if (multiTenancyMode.isSharedConnection()) {
            TenantResolver tenantResolver = datastoreClient.tenantResolver
            if (tenantResolver instanceof AllTenantsResolver) {
                for (tenantId in ((AllTenantsResolver) tenantResolver).resolveTenantIds()) {
                    withTenantIdInternal(datastoreClient, tenantId, callable)
                }
            } else {
                throw new UnsupportedOperationException("Multi tenancy mode $multiTenancyMode is configured, but the configured TenantResolver does not implement the [org.grails.datastore.mapping.multitenancy.AllTenantsResolver] interface")
            }
        } else {
            throw new UnsupportedOperationException("Method not supported in multi tenancy mode $multiTenancyMode")
        }
    }
}

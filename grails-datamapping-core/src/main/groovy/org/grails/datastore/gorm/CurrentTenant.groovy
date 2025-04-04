package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Holds a reference to the current tenant for the thread
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@PackageScope
@CompileStatic
class CurrentTenant  {

    private static final ThreadLocal<Serializable> currentTenantThreadLocal = new ThreadLocal<>()

    /**
     * @return Obtain the current tenant
     */
    static Serializable get() {
        currentTenantThreadLocal.get()
    }

    /**
     * Set the current tenant
     *
     * @param tenantId The tenant id
     */
    private static void set(Serializable tenantId) {
        currentTenantThreadLocal.set(tenantId)
    }

    private static void remove() {
        currentTenantThreadLocal.remove()
    }

    /**
     * Execute with the current tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    public static <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        try {
            set(tenantId)
            callable.call()
        } finally {
            remove()
        }
    }
}

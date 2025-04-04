package grails.gorm

import grails.gorm.api.GormAllOperations
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.connections.ConnectionSource

/**
 * A trait for domain classes to implement that should be treated as multi tenant
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
trait MultiTenant<D> extends Entity {

    /**
     * Execute the closure with the given tenantId
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        GormEnhancer.findStaticApi(this).withTenant tenantId, callable
    }

    /**
     * Execute the closure for each tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <D> GormAllOperations eachTenant(Closure callable) {
        GormEnhancer.findStaticApi(this, ConnectionSource.DEFAULT).eachTenant callable
    }

    /**
     * Return the {@link GormAllOperations} for the given tenant id
     *
     * @param tenantId The tenant id
     * @return The operations
     */
    static <D> GormAllOperations<D> withTenant(Serializable tenantId) {
        (GormAllOperations<D>)GormEnhancer.findStaticApi(this).withTenant(tenantId)
    }
}
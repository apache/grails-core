package grails.gorm.rx

import grails.gorm.api.GormAllOperations
import grails.gorm.rx.api.RxGormAllOperations
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.gorm.rx.api.RxGormEnhancer

/**
 * A trait for domain classes that should be treated as multi tenant
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
trait MultiTenant<D> extends RxEntity<D> {
    /**
     * Execute the closure with the given tenantId
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Serializable tenantId, @DelegatesTo(RxGormAllOperations) Closure<T> callable) {
        RxGormEnhancer.findStaticApi(this).withTenant tenantId, callable
    }

    /**
     * Execute the closure for each tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static RxGormAllOperations<D> eachTenant( @DelegatesTo(RxGormAllOperations) Closure callable) {
        RxGormEnhancer.findStaticApi(this, ConnectionSource.DEFAULT).eachTenant callable
    }

    /**
     * Return the {@link GormAllOperations} for the given tenant id
     *
     * @param tenantId The tenant id
     * @return The operations
     */
    static RxGormAllOperations<D> withTenant(Serializable tenantId) {
        (RxGormAllOperations<D>)RxGormEnhancer.findStaticApi(this).withTenant(tenantId)
    }
}
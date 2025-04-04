package grails.gorm.async

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.async.GormAsyncStaticApi

/**
 * Adds Grails Async features to an entity that implements this trait, including the ability to run GORM tasks in a separate thread
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait AsyncEntity<D> extends GormEntity<D> {
    /**
     * @return The async version of the GORM static API
     */
    static GormAsyncStaticApi<D> getAsync() {
        return new GormAsyncStaticApi(GormEnhancer.findStaticApi(this))
    }
}
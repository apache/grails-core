package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.EntityFetchOptions

/**
 * A class to retrieve data from the environment source
 * with a closure.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ClosureDataFetcher implements DataFetcher<Object> {

    private Closure closure
    private Class domainType
    private boolean initialized
    private EntityFetchOptions fetchOptions

    ClosureDataFetcher(Closure closure, Class domainType = null) {
        this.closure = closure
        this.domainType = domainType
    }

    @Override
    Object get(DataFetchingEnvironment environment) {
        Object source = environment.source
        if (closure.maximumNumberOfParameters == 2) {
            closure.call(source, new ClosureDataFetchingEnvironment(environment, domainType))
        }
        else {
            closure.call(source)
        }
    }

    EntityFetchOptions buildFetchOptions() {
        if (initialized) {
            return fetchOptions
        }
        if (domainType != null && GormEntity.isAssignableFrom(domainType)) {
            fetchOptions = new EntityFetchOptions(domainType)
        }
        initialized = true
        fetchOptions
    }
}

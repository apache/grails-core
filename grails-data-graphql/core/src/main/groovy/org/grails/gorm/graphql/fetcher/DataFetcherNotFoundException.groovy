package org.grails.gorm.graphql.fetcher

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DataFetcherNotFoundException extends RuntimeException {

    DataFetcherNotFoundException(PersistentEntity entity, GraphQLDataFetcherType type) {
        this(entity.javaClass, type)
    }

    DataFetcherNotFoundException(Class clazz, GraphQLDataFetcherType type) {
        super("No ${type.name()} data fetcher could be found for ${clazz.name}")
    }
}

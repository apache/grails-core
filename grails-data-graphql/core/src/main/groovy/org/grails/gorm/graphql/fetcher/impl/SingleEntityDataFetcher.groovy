package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.gorm.graphql.fetcher.DefaultGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher

/**
 * A class for querying a single entity with GraphQL
 *
 * @param <T> The domain returnType to query
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class SingleEntityDataFetcher<T> extends DefaultGormDataFetcher<T> implements ReadingGormDataFetcher {

    @Override
    T get(DataFetchingEnvironment environment) {
        (T)withTransaction(true) {
            queryInstance(environment)
        }
    }

    @Override
    boolean supports(GraphQLDataFetcherType type) {
        type == GraphQLDataFetcherType.GET
    }
}

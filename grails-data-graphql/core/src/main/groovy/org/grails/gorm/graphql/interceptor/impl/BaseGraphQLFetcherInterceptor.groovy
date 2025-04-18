package org.grails.gorm.graphql.interceptor.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * Base class to extend from for custom data fetcher interceptors. Provides default
 * implementations of all methods.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class BaseGraphQLFetcherInterceptor implements GraphQLFetcherInterceptor {

    boolean onQuery(DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
        true
    }

    boolean onMutation(DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
        true
    }

    boolean onCustomQuery(String name, DataFetchingEnvironment environment) {
        true
    }

    boolean onCustomMutation(String name, DataFetchingEnvironment environment) {
        true
    }

}

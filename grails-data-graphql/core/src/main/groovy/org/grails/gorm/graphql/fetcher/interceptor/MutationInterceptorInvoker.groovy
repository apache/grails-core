package org.grails.gorm.graphql.fetcher.interceptor

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * Executes the onMutation method of an interceptor
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class MutationInterceptorInvoker extends ProvidedInterceptorInvoker {

    @Override
    boolean call(GraphQLFetcherInterceptor interceptor, DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
        interceptor.onMutation(environment, type)
    }
}

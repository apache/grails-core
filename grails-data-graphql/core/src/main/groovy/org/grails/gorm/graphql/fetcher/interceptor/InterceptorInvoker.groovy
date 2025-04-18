package org.grails.gorm.graphql.fetcher.interceptor

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * A generic interface for custom operations to separate which event
 * will be called based on the returnType of the operation.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
abstract class InterceptorInvoker {

    protected String getName(DataFetchingEnvironment environment) {
        environment.fields.empty ? 'UNKNOWN' : environment.fields[0].name
    }

    abstract boolean invoke(GraphQLFetcherInterceptor interceptor, DataFetchingEnvironment environment, GraphQLDataFetcherType type)
}

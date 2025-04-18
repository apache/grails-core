package org.grails.gorm.graphql.fetcher.interceptor

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * Executes the onCustomQuery method of an interceptor
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class CustomQueryInterceptorInvoker extends CustomInterceptorInvoker {

    @Override
    boolean invoke(GraphQLFetcherInterceptor interceptor, String name, DataFetchingEnvironment environment) {
        interceptor.onCustomQuery(name, environment)
    }
}

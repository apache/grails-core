package org.grails.gorm.graphql.fetcher.interceptor

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * Executes interceptors for provided operations
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@Slf4j
abstract class ProvidedInterceptorInvoker extends InterceptorInvoker {

    @Override
    final boolean invoke(GraphQLFetcherInterceptor interceptor, DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
        final String name = getName(environment)
        boolean result = call(interceptor, environment, type)
        if (!result) {
            log.info("Execution of ${name} was prevented by an interceptor")
        }
        result
    }

    abstract boolean call(GraphQLFetcherInterceptor interceptor, DataFetchingEnvironment environment, GraphQLDataFetcherType type)
}

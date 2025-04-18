package org.grails.gorm.graphql.interceptor.manager

import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.GraphQLSchemaInterceptor

/**
 * Describes a class that stores and retrieves fetcher interceptor
 * instances based on a class
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLInterceptorManager {

    /**
     * Registers the interceptor
     *
     * @param clazz The class operations should be intercepted for
     * @param interceptor The interceptor to register
     */
    void registerInterceptor(Class clazz, GraphQLFetcherInterceptor interceptor)

    /**
     * Registers the interceptor
     *
     * @param interceptor The interceptor to register
     */
    void registerInterceptor(GraphQLSchemaInterceptor interceptor)

    /**
     * @param clazz The class to search for
     * @return Interceptors that support the class
     */
    List<GraphQLFetcherInterceptor> getInterceptors(Class clazz)

    /**
     * @param clazz The class to search for
     * @return Interceptors of the schema
     */
    List<GraphQLSchemaInterceptor> getInterceptors()
}

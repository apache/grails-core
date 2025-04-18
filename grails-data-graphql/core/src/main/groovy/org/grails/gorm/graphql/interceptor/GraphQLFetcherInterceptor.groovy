package org.grails.gorm.graphql.interceptor

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

/**
 * Interface to describe a class that can intercept GraphQL data
 * fetchers and prevent the execution of their functionality.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLFetcherInterceptor {

    /**
     * This method will be executed before query operations provided by this library.
     *
     * @param environment The data fetching environment provided by GraphQL
     * @param type The data fetcher returnType. Either {@link GraphQLDataFetcherType#GET} or
     * {@link GraphQLDataFetcherType#LIST}
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onQuery(DataFetchingEnvironment environment, GraphQLDataFetcherType type)

    /**
     * This method will be executed before mutation operations provided by this library.
     *
     * @param environment The data fetching environment provided by GraphQL
     * @param type The data fetcher returnType. Either {@link GraphQLDataFetcherType#CREATE},
     * {@link GraphQLDataFetcherType#UPDATE}, or {@link GraphQLDataFetcherType#DELETE}
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onMutation(DataFetchingEnvironment environment, GraphQLDataFetcherType type)

    /**
     * This method will be executed before custom query operations provided by the user of
     * this library.
     *
     * @param name The name of the operation attempting to be executed
     * @param environment The data fetching environment provided by GraphQL
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onCustomQuery(String name, DataFetchingEnvironment environment)

    /**
     * This method will be executed before custom mutation operations provided by the user of
     * this library.
     *
     * @param name The name of the operation attempting to be executed
     * @param environment The data fetching environment provided by GraphQL
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onCustomMutation(String name, DataFetchingEnvironment environment)

}

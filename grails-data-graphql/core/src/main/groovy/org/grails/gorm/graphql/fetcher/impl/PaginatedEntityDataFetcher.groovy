package org.grails.gorm.graphql.fetcher.impl

import grails.gorm.PagedResultList
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.gorm.graphql.fetcher.PaginatingGormDataFetcher
import org.grails.gorm.graphql.response.pagination.GraphQLPaginationResponseHandler
import org.grails.gorm.graphql.response.pagination.PagedResultListPaginationResponse

/**
 * A class for retrieving a single page of entities with GraphQL
 *
 * @param <T> The collection return type
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class PaginatedEntityDataFetcher<T> extends EntityDataFetcher implements PaginatingGormDataFetcher {

    GraphQLPaginationResponseHandler responseHandler

    protected T executeQuery(DataFetchingEnvironment environment, Map queryArgs) {
        if (!queryArgs.containsKey('max')) {
            queryArgs.put('max', responseHandler.defaultMax)
        }
        if (!queryArgs.containsKey('offset')) {
            queryArgs.put('offset', responseHandler.defaultOffset)
        }
        PagedResultList results = (PagedResultList)buildCriteria(environment).list(queryArgs)
        (T)responseHandler.createResponse(environment, new PagedResultListPaginationResponse(results))
    }
}

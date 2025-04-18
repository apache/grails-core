package org.grails.gorm.graphql.fetcher

import org.grails.gorm.graphql.response.pagination.GraphQLPaginationResponseHandler

/**
 * An interface to describe data fetchers that return a page
 * of data at a time
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface PaginatingGormDataFetcher extends ReadingGormDataFetcher {

    void setResponseHandler(GraphQLPaginationResponseHandler dataBinder)

    GraphQLPaginationResponseHandler getResponseHandler()
}

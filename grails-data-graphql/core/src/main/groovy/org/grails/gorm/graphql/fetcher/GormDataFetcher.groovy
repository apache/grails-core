package org.grails.gorm.graphql.fetcher

import graphql.schema.DataFetcher

/**
 * A base interface to describe fetchers that
 * work with GORM
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GormDataFetcher extends DataFetcher {

    boolean supports(GraphQLDataFetcherType type)
}

package org.grails.gorm.graphql.fetcher

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.response.delete.GraphQLDeleteResponseHandler

/**
 * A trait to describe data fetchers that delete
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait DeletingGormDataFetcher implements GormDataFetcher {

    @Override
    boolean supports(GraphQLDataFetcherType type) {
        type == GraphQLDataFetcherType.DELETE
    }

    abstract void setResponseHandler(GraphQLDeleteResponseHandler responseHandler)

    abstract GraphQLDeleteResponseHandler getResponseHandler()
}

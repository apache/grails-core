package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.gorm.graphql.fetcher.DefaultGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher

/**
 * A class for retrieving how many entities exist in the datastore
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class CountEntityDataFetcher extends DefaultGormDataFetcher<Integer> implements ReadingGormDataFetcher {

    protected Integer queryCount() {
        staticApi.count()
    }

    @Override
    Integer get(DataFetchingEnvironment environment) {
        (Integer) withTransaction(true) {
            queryCount()
        }
    }

    @Override
    boolean supports(GraphQLDataFetcherType type) {
        type == GraphQLDataFetcherType.COUNT
    }
}

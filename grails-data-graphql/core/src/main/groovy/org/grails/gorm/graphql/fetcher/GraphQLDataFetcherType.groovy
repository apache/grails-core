package org.grails.gorm.graphql.fetcher

import groovy.transform.CompileStatic

/**
 * An enum defining the different data fetcher types and their
 * required interfaces
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
enum GraphQLDataFetcherType {

    CREATE(BindingGormDataFetcher),
    GET(ReadingGormDataFetcher),
    LIST(ReadingGormDataFetcher),
    COUNT(ReadingGormDataFetcher),
    UPDATE(BindingGormDataFetcher),
    DELETE(DeletingGormDataFetcher)

    final Class requiredClass

    GraphQLDataFetcherType(Class requiredClass) {
        this.requiredClass = requiredClass
    }
}

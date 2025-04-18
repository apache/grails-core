package org.grails.gorm.graphql.fetcher

import org.grails.gorm.graphql.binding.GraphQLDataBinder

/**
 * An interface to describe data fetchers that use data binding
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface BindingGormDataFetcher extends GormDataFetcher {

    void setDataBinder(GraphQLDataBinder dataBinder)

    GraphQLDataBinder getDataBinder()
}

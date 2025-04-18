package org.grails.gorm.graphql.binding

/**
 * An interface to bind data from GraphQL to a GORM entity
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDataBinder {

    /**
     * Binds data to a domain class instance
     *
     * @param object The domain class instance
     * @param data The data to bind
     */
    void bind(Object object, Map data)
}

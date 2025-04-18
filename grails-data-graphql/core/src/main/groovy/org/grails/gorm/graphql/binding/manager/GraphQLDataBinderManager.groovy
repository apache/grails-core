package org.grails.gorm.graphql.binding.manager

import org.grails.gorm.graphql.binding.GraphQLDataBinder

/**
 * An interface to describe a manager that will store
 * and return instances of data binders to be used
 * with GraphQL operations on GORM entities
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDataBinderManager {

    /**
     * Register a data binder for use with the provided class
     *
     * @param clazz The class to be bound
     * @param dataBinder The data binding instance to be used
     */
    void registerDataBinder(Class clazz, GraphQLDataBinder dataBinder)

    /**
     * Returns a data binder to be used for the provided class
     *
     * @param clazz The class to be bound
     * @return The data binding instance to be used
     */
    GraphQLDataBinder getDataBinder(Class clazz)
}

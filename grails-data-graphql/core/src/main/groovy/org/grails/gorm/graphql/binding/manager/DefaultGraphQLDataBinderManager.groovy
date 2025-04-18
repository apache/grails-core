package org.grails.gorm.graphql.binding.manager

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.types.KeyClassQuery
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * A default implementation of {@link GraphQLDataBinderManager} that
 * will also return a result if the class requested is a subclass
 * of a class that exists in the registry. The order of which binders
 * are registered is relevant to their resolution. The items added last
 * have priority when searching for subclass matches.
 *
 * Example:
 * register(Collection)
 * register(List)
 *
 * When the binder is searched for ArrayList, List will be returned.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLDataBinderManager implements GraphQLDataBinderManager, KeyClassQuery<GraphQLDataBinder> {

    protected final Map<Class, GraphQLDataBinder> dataBinders = Collections.synchronizedMap([:])

    /**
     * Registers a default data binder for the Object class
     */
    DefaultGraphQLDataBinderManager() {
        //Create the default data binder
        registerDataBinder(Object, new GraphQLDataBinder() {
            @Override
            void bind(Object object, Map data) {
                DataBinder dataBinder = new DataBinder(object)
                dataBinder.bind(new MutablePropertyValues(data))
            }
        })
    }

    /**
     * Registers a the data binder provided for the Object class
     */
    DefaultGraphQLDataBinderManager(GraphQLDataBinder defaultDataBinder) {
        registerDataBinder(Object, defaultDataBinder)
    }

    /**
     * @see GraphQLDataBinderManager#registerDataBinder
     */
    void registerDataBinder(Class clazz, GraphQLDataBinder dataBinder) {
        dataBinders.put(clazz, dataBinder)
    }

    /**
     * @see GraphQLDataBinderManager#getDataBinder
     *
     * @return NULL if no data binder found
     */
    GraphQLDataBinder getDataBinder(Class clazz) {
        searchMap(dataBinders, clazz)
    }
}

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DefaultGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

/**
 * A class for creating entities with GraphQL
 *
 * @param <T>  The domain returnType to create
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class CreateEntityDataFetcher<T> extends DefaultGormDataFetcher<T> implements BindingGormDataFetcher {

    GraphQLDataBinder dataBinder

    @Override
    T get(DataFetchingEnvironment environment) {
        (T) withTransaction(false) {
            GormEntity instance = newInstance
            dataBinder.bind(instance, getArgument(environment))
            if (!instance.hasErrors()) {
                instance.save()
            }
            instance
        }
    }

    protected GormEntity getNewInstance() {
        (GormEntity) entity.newInstance()
    }

    protected Map getArgument(DataFetchingEnvironment environment) {
        (Map) environment.getArgument(entity.decapitalizedName)
    }

    @Override
    boolean supports(GraphQLDataFetcherType type) {
        type == GraphQLDataFetcherType.CREATE
    }

}

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DefaultGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

import static org.grails.datastore.mapping.model.config.GormProperties.VERSION

/**
 * A class for updating an entity with GraphQL
 *
 * @param <T> The domain returnType to update
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class UpdateEntityDataFetcher<T> extends DefaultGormDataFetcher<T> implements BindingGormDataFetcher {

    GraphQLDataBinder dataBinder

    @Override
    T get(DataFetchingEnvironment environment) {
        (T)withTransaction(false) {
            GormEntity instance = getInstance(environment)
            Map dataToBind = getEntityArgument(environment)
            if (entity.versioned && dataToBind.containsKey(VERSION)) {
                Long entityVersion = (Long)entity.mappingContext.createEntityAccess(entity, instance).getProperty(VERSION)
                Long versionParam = (Long)dataToBind.get(VERSION)
                if (versionParam != null && versionParam < entityVersion) {
                    instance.errors.rejectValue(VERSION, 'default.optimistic.locking.failure', [entity.javaClass.simpleName] as Object[], 'Another user has updated this {0} while you were editing')
                    return instance
                }
            }
            dataBinder.bind(instance, dataToBind)
            if (!instance.hasErrors()) {
                instance.save()
            }
            instance
        }
    }

    protected GormEntity getInstance(DataFetchingEnvironment environment) {
        queryInstance(environment)
    }

    protected Map getEntityArgument(DataFetchingEnvironment environment) {
        (Map)environment.getArgument(entity.decapitalizedName)
    }

    @Override
    boolean supports(GraphQLDataFetcherType type) {
        type == GraphQLDataFetcherType.UPDATE
    }
}

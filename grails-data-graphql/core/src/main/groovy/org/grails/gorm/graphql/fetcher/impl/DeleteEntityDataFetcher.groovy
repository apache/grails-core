package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.fetcher.DefaultGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.response.delete.GraphQLDeleteResponseHandler

/**
 * A class for deleting entities with GraphQL
 *
 * @param <T> The domain returnType to delete
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class DeleteEntityDataFetcher<T> extends DefaultGormDataFetcher<T> implements DeletingGormDataFetcher {

    GraphQLDeleteResponseHandler responseHandler

    void delete(DataFetchingEnvironment environment) {
        withTransaction(false) {
            GormEntity instance = queryInstance(environment)
            deleteInstance(instance)
        }
    }

    protected void deleteInstance(GormEntity instance) {
        instance.delete(failOnError: true)
    }

    @Override
    T get(DataFetchingEnvironment environment) {
        boolean success = false
        Exception exception
        try {
            delete(environment)
            success = true
        } catch (Exception e) {
            exception = e
        }

        (T)responseHandler.createResponse(environment, success, exception)
    }
}

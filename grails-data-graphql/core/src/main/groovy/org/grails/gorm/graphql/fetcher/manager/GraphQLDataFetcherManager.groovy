package org.grails.gorm.graphql.fetcher.manager

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher

/**
 * An interface to register and retrieve data fetcher instances
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDataFetcherManager {

    /**
     * Register a fetcher instance to be used for CREATE or UPDATE for the
     * provided class.
     *
     * @param clazz The class to be updated or deleted
     * @param fetcher The fetcher instance to be used
     */
    void registerBindingDataFetcher(Class clazz, BindingGormDataFetcher fetcher)

    /**
     * Register a fetcher instance to be used for DELETE for the
     * provided class.
     *
     * @param clazz The class to be deleted
     * @param fetcher The fetcher instance to be used
     */
    void registerDeletingDataFetcher(Class clazz, DeletingGormDataFetcher fetcher)

    /**
     * Register a fetcher instance to be used for GET or LIST for the
     * provided class.
     *
     * @param clazz The class to be retrieved
     * @param fetcher The fetcher instance to be used
     */
    void registerReadingDataFetcher(Class clazz, ReadingGormDataFetcher fetcher)

    /**
     * Returns a data fetcher instance to be used in CREATE or UPDATE
     *
     * @param entity The entity representing the domain used in the fetcher
     * @param type Which returnType of fetcher to return (CREATE or UPDATE)
     * @return An optional data fetcher
     */
    Optional<BindingGormDataFetcher> getBindingFetcher(PersistentEntity entity, GraphQLDataFetcherType type)

    /**
     * Returns a data fetcher instance to be used in DELETE
     *
     * @param entity The entity representing the domain used in the fetcher
     * @return An optional data fetcher
     */
    Optional<DeletingGormDataFetcher> getDeletingFetcher(PersistentEntity entity)

    /**
     * Returns a data fetcher instance to be used in GET or LIST
     *
     * @param entity The entity representing the domain used in the fetcher
     * @param type Which returnType of fetcher to return (GET or LIST)
     * @return An optional data fetcher
     */
    Optional<ReadingGormDataFetcher> getReadingFetcher(PersistentEntity entity, GraphQLDataFetcherType type)

}

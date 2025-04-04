package org.grails.datastore.rx

import grails.gorm.rx.proxy.ObservableProxy
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.QueryCreator
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.gorm.rx.config.Settings
import rx.Observable

/**
 * Represents a client connection pool that can be used to interact with a backing implementation in RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 *
 * @param The native client interface
 */
interface RxDatastoreClient<T> extends Closeable, QueryCreator, Settings, ConnectionSourcesProvider<T, ConnectionSourceSettings> {

    /**
     * Obtains a single instance for the given type and id
     *
     * @param type The persistent type
     * @param id The identifier
     *
     * @return A single observable result
     */
    public <T1> Observable<T1> get(Class<T1> type, Serializable id)


    /**
     * Persist and instance and return the observable
     *
     * @param instance The instance
     * @param arguments The arguments
     * @return The observable
     */
    def <T1> Observable<T1> persist(T1 instance, Map<String, Object> arguments)

    /**
     * Force an insert of an instance and return the observable
     *
     * @param instance The instance
     * @param arguments The arguments
     * @return The observable
     */
    def <T1> Observable<T1> insert(T1 instance, Map<String, Object> arguments)

    /**
     * Persist and instance and return the observable
     *
     * @param instance The instance
     * @param arguments The arguments
     * @return The observable
     */
    def <T1> Observable<T1> persist(T1 instance)

    /**
     * Deletes an instance
     *
     * @param instance The object to delete
     * @return An observable that returns a boolean true if successful
     */
    Observable<Boolean> delete(Object instance)

    /**
     * Deletes a number of instances
     *
     * @param instances The objects to delete
     * @return An observable that returns the actual number of objects deleted
     */
    Observable<Number> deleteAll(Iterable instances)

    /**
     * Deletes an instance
     *
     * @param instance The object to delete
     * @return An observable that returns a boolean true if successful
     */
    Observable<Boolean> delete(Object instance, Map<String, Object> arguments)

    /**
     * Deletes a number of instances
     *
     * @param instances The objects to delete
     * @return An observable that returns the actual number of objects deleted
     */
    Observable<Number> deleteAll(Iterable instances, Map<String, Object> arguments)

    /**
     * Obtain an {@link ObservableProxy} for the given type and id
     *
     * @param type The type
     * @param id The id
     * @return An {@link ObservableProxy}
     */
    def <T1> ObservableProxy<T1> proxy(Class<T1> type, Serializable id)

    /**
     * Obtain an {@link ObservableProxy} that executes the given query to initialize
     *
     * @param query The query
     * @return An {@link ObservableProxy}
     */
    def ObservableProxy proxy(Query query)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> persistAll(Iterable objects)

    /**
     * Batch insert all all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> insertAll(Iterable objects)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> persistAll(Iterable objects, Map<String, Object> arguments)

    /**
     * Batch insert all all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> insertAll(Iterable objects, Map<String, Object> arguments)

    /**
     * Creates a query for the given type
     *
     * @param type The type
     * @return The query
     */
    Query createQuery(Class type)

    /**
     * Creates a query for the given type
     *
     * @param type The type
     * @return The query
     */
    Query createQuery(Class type, Map arguments)

    /**
     * @return The native interface to the datastore
     */
    T getNativeInterface()

    /**
     * @return The mapping context
     */
    MappingContext getMappingContext()

    /**
     * @return The event publisher
     */
    ConfigurableApplicationEventPublisher getEventPublisher()

    /**
     * @return Obtain the connection sources
     */
    ConnectionSources<T, ConnectionSourceSettings> getConnectionSources()

    /**
     * Obtain another {@link RxDatastoreClient} for the given {@link ConnectionSource} name
     *
     * @param connectionSourceName The name of the client
     * @return The {@link RxDatastoreClient} instance
     */
    RxDatastoreClient getDatastoreClient(String connectionSourceName)


    /**
     * @return The multi tenancy mode
     */
    MultiTenancySettings.MultiTenancyMode getMultiTenancyMode();

    /**
     * @return Obtain the tenant resolver
     */
    TenantResolver getTenantResolver();

    /**
     * Obtains the datastore for the given tenant id. In SINGLE mode
     * this will be a unique datastore for each tenant. For MULTI mode
     * a single datastore is used for all tenants
     *
     * @param tenantId The tenant id
     * @return The datastore
     */
    RxDatastoreClient getDatastoreClientForTenantId(Serializable tenantId);
}
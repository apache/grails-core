package org.grails.gorm.rx.api

import grails.gorm.rx.MultiTenant
import grails.gorm.rx.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor

import java.util.concurrent.ConcurrentHashMap
/**
 * Enhances {@link grails.gorm.rx.RxEntity} instances with behaviour necessary at runtime
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class RxGormEnhancer {

    private static final Map<String, Map<String,RxGormStaticApi>> STATIC_APIS = new ConcurrentHashMap<String, Map<String,RxGormStaticApi>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, RxGormStaticApi>
    }
    private static final Map<String, Map<String, RxGormInstanceApi>> INSTANCE_APIS = new ConcurrentHashMap<String, Map<String, RxGormInstanceApi>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, RxGormInstanceApi>
    }
    private static final Map<String, Map<String, RxGormValidationApi>> VALIDATION_APIS = new ConcurrentHashMap<String, Map<String, RxGormValidationApi>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, RxGormValidationApi>
    }
    private static final Map<Class<? extends RxDatastoreClient>, RxDatastoreClient> DATASTORE_CLIENTS = new ConcurrentHashMap<Class<? extends RxDatastoreClient>, RxDatastoreClient>()



    private RxGormEnhancer() {
    }

    static void close() {
        STATIC_APIS.clear()
        INSTANCE_APIS.clear()
        VALIDATION_APIS.clear()
        DATASTORE_CLIENTS.clear()
    }

    /**
     * Registers a new entity with the RxGormEnhancer
     *
     * @param entity The entity
     * @param client The client
     * @param tenantResolver The tenant resolver
     */
    static void registerEntity(PersistentEntity entity, RxDatastoreClient client) {
        List<String> connectionSourceNames = ConnectionSourcesSupport.getConnectionSourceNames(entity)
        String defaultConnectionSource = ConnectionSourcesSupport.getDefaultConnectionSourceName(entity)
        RxDatastoreClientImplementor rxDatastoreClientImplementor = (RxDatastoreClientImplementor) client

        if(!DATASTORE_CLIENTS.containsKey(client.getClass())) {
            DATASTORE_CLIENTS.put( (Class<? extends RxDatastoreClient>)client.getClass(), client)
        }

        if(MultiTenant.isAssignableFrom(entity.javaClass) || defaultConnectionSource == ConnectionSource.ALL) {
            for(ConnectionSource cs in client.getConnectionSources().getAllConnectionSources()) {
                registerEntityWithConnectionSource(entity, cs.name, cs.name, rxDatastoreClientImplementor)
            }
        }
        else {
            registerEntityWithConnectionSource(entity, ConnectionSource.DEFAULT, defaultConnectionSource, rxDatastoreClientImplementor)
            for(String connectionSourceName in connectionSourceNames) {
                if(connectionSourceName == ConnectionSource.DEFAULT) continue
                registerEntityWithConnectionSource(entity, connectionSourceName, connectionSourceName, rxDatastoreClientImplementor)
            }
        }
    }

    /**
     * Finds a datastore by type
     *
     * @param datastoreType The datastore type
     * @return The datastore
     *
     * @throws IllegalStateException If no datastore is found for the type
     */
    static RxDatastoreClient findDatastoreClientByType(Class<? extends RxDatastoreClient> datastoreType) {
        RxDatastoreClient client = DATASTORE_CLIENTS.get(datastoreType)
        if(client == null) {
            throw new IllegalStateException("No RxGORM implementation configured for type [$datastoreType]. Ensure RxGORM has been initialized correctly")
        }
        return client
    }

    /**
     * Finds a single datastore
     *
     * @throws IllegalStateException If no datastore is found or more than one is configured
     */
    static RxDatastoreClient findSingleDatastoreClient() {
        Collection<RxDatastoreClient> allDatastores = DATASTORE_CLIENTS.values()
        if(allDatastores.isEmpty()) {
            throw new IllegalStateException("No RxGORM implementations configured. Ensure RxGORM has been initialized correctly")
        }
        else if(allDatastores.size() > 1) {
            throw new IllegalStateException("More than one RxGORM implementation is configured. Specific the client type!")
        }
        else {
            return allDatastores.first()
        }
    }

    /**
     * Find the tenant id for the given entity
     *
     * @param entity
     * @return
     */
    static <T> String findTenantId(Class<T> entity) {
        if(MultiTenant.isAssignableFrom(entity)) {
            RxDatastoreClient datastoreClient = findStaticApi(entity, ConnectionSource.DEFAULT).datastoreClient
            if(datastoreClient.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
                return Tenants.currentId( (Class<? extends RxDatastoreClient>) datastoreClient.getClass() )
            }
            else {
                return ConnectionSource.DEFAULT
            }
        }
        else {
            log.debug "Returning default tenant id for non-multitenant class [$entity]"
            return ConnectionSource.DEFAULT
        }
    }

    /**
     * Find the static API for the given type
     *
     * @param type The type
     * @param qualifier The qualifier
     * @return The static api
     */
    static <T> RxGormStaticApi<T> findStaticApi(Class<T> type, String qualifier = findTenantId(type)) {
        def api = STATIC_APIS.get(qualifier).get(type.name)
        if(api == null) {
            throw stateException(type)
        }
        return api
    }


    /**
     * Find the instance API for the given type
     *
     * @param type The type
     * @param qualifier The qualifier
     * @return The static api
     */
    static <T> RxGormInstanceApi<T> findInstanceApi(Class<T> type, String qualifier = findTenantId(type)) {
        def api = INSTANCE_APIS.get(qualifier).get(type.name)
        if(api == null) {
            throw stateException(type)
        }
        return api
    }

    /**
     * Find the validation API for the given type
     *
     * @param type The type
     * @param qualifier The qualifier
     * @return The validation api
     */

    static <T> RxGormValidationApi<T> findValidationApi(Class<T> type, String qualifier = findTenantId(type)) {
        def api = VALIDATION_APIS.get(qualifier).get(type.name)
        if(api == null) {
            throw stateException(type)
        }
        return api
    }

    protected static void registerEntityWithConnectionSource(PersistentEntity entity, String qualifierName, String connectionSourceName, RxDatastoreClientImplementor rxDatastoreClientImplementor) {
        String entityName = entity.getName()
        STATIC_APIS.get(qualifierName).put(entityName, rxDatastoreClientImplementor.createStaticApi(entity, connectionSourceName))
        INSTANCE_APIS.get(qualifierName).put(entityName, rxDatastoreClientImplementor.createInstanceApi(entity, connectionSourceName))
        VALIDATION_APIS.get(qualifierName).put(entityName, rxDatastoreClientImplementor.createValidationApi(entity, connectionSourceName))
    }

    private static IllegalStateException stateException(Class entity) {
        new IllegalStateException("Either class [$entity.name] is not a domain class or GORM has not been initialized correctly or has already been shutdown. If you are unit testing your entities using the mocking APIs")
    }

}

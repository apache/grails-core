package org.grails.gorm.graphql.fetcher

import grails.gorm.DetachedCriteria
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.TransactionService
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute
import org.grails.gorm.graphql.entity.EntityFetchOptions

/**
 * A generic class to assist with querying entities with GraphQL
 *
 * @param <T> The domain returnType to query
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@Slf4j
abstract class DefaultGormDataFetcher<T> implements DataFetcher<T> {

    protected Map<String, Association> associations = [:]
    protected PersistentEntity entity
    protected String propertyName
    protected EntityFetchOptions entityFetchOptions

    DefaultGormDataFetcher(PersistentEntity entity) {
        this(entity, null)
    }

    DefaultGormDataFetcher(PersistentEntity entity, String projectionName) {
        this.entity = entity
        this.propertyName = projectionName
        this.entityFetchOptions = new EntityFetchOptions(entity, projectionName)
        initializeEntity(entity)
    }

    protected void initializeEntity(PersistentEntity entity) {
        this.associations = this.entityFetchOptions.associations
    }

    protected Map getFetchArguments(DataFetchingEnvironment environment, boolean skipCollections = false) {
        Set<String> joinProperties = entityFetchOptions.getJoinProperties(environment, skipCollections)

        if (propertyName) {
            joinProperties.add(propertyName)
        }

        entityFetchOptions.getFetchArgument(joinProperties)
    }

    protected Object loadEntity(PersistentEntity entity, Object argument) {
        GormEnhancer.findStaticApi(entity.javaClass).load((Serializable)argument)
    }

    protected Map<String, Object> getIdentifierValues(DataFetchingEnvironment environment) {
        Map<String, Object> idProperties = [:]

        PersistentProperty identity = entity.identity
        if (identity != null) {
            idProperties.put(identity.name, environment.getArgument(identity.name))
        }
        else if (entity.compositeIdentity != null) {
            for (PersistentProperty p: entity.compositeIdentity) {
                Object value
                Object argument = environment.getArgument(p.name)
                if (associations.containsKey(p.name)) {
                    PersistentEntity associatedEntity = associations.get(p.name).associatedEntity
                    value = loadEntity(associatedEntity, argument)
                } else {
                    value = argument
                }
                idProperties.put(p.name, value)
            }
        }

        idProperties
    }

    protected DetachedCriteria buildCriteria(DataFetchingEnvironment environment) {
        Map<String, Object> idProperties = getIdentifierValues(environment)
        new DetachedCriteria(entity.javaClass).build {
            for (Map.Entry<String, Object> prop: idProperties) {
                eq(prop.key, prop.value)
            }
        }
    }

    protected GormEntity queryInstance(DataFetchingEnvironment environment) {
        buildCriteria(environment).get(getFetchArguments(environment))
    }

    protected Object withTransaction(boolean readOnly, Closure closure) {
        Datastore datastore
        if (entity.multiTenant && this.datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore)this.datastore
            Serializable currentTenantId = Tenants.currentId(multiTenantCapableDatastore)
            datastore = multiTenantCapableDatastore.getDatastoreForTenantId(currentTenantId)
        }
        else {
            datastore = this.datastore
        }

        TransactionService txService = datastore.getService(TransactionService)
        CustomizableRollbackTransactionAttribute transactionAttribute = new CustomizableRollbackTransactionAttribute()
        transactionAttribute.setReadOnly(readOnly)
        txService.withTransaction(transactionAttribute, closure)
    }

    protected Datastore getDatastore() {
        staticApi.datastore
    }

    protected GormStaticApi getStaticApi() {
        GormEnhancer.findStaticApi(entity.javaClass)
    }

    abstract T get(DataFetchingEnvironment environment)
}

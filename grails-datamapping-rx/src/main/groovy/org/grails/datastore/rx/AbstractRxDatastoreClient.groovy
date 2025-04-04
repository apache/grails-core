package org.grails.datastore.rx

import grails.gorm.rx.proxy.ObservableProxy
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries
import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.IdentityGenerationException
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.*
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.grails.datastore.rx.batch.BatchOperation
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.proxy.ProxyFactory
import org.grails.datastore.rx.proxy.RxJavassistProxyFactory
import org.grails.datastore.rx.query.QueryState
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import org.grails.gorm.rx.api.RxGormValidationApi
import org.grails.gorm.rx.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.gorm.rx.events.DomainEventListener
import org.grails.gorm.rx.events.MultiTenantEventListener
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceAware
import org.springframework.context.support.StaticMessageSource
import rx.Observable

import jakarta.persistence.CascadeType
/**
 * Abstract implementation the {@link RxDatastoreClient} interface
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
abstract class AbstractRxDatastoreClient<T> implements RxDatastoreClient<T>, RxDatastoreClientImplementor<T>, MessageSourceAware {

    protected MappingContext mappingContext
    ConfigurableApplicationEventPublisher eventPublisher = new DefaultApplicationEventPublisher()
    final ProxyFactory proxyFactory
    final ConnectionSources<T, ? extends ConnectionSourceSettings> connectionSources
    final Map<String, RxDatastoreClient<T>> datastoreClients = [:]
    final MultiTenancySettings.MultiTenancyMode multiTenancyMode
    final TenantResolver tenantResolver

    AbstractRxDatastoreClient(ConnectionSources<T, ConnectionSourceSettings> connectionSources, MappingContext mappingContext) {
        this.mappingContext = mappingContext
        this.proxyFactory = new RxJavassistProxyFactory()
        this.connectionSources = connectionSources
        this.datastoreClients.put(ConnectionSource.DEFAULT, this)
        mappingContext.setProxyFactory(new RxJavassistProxyFactory())

        ConnectionSourceSettings connectionSourceSettings = connectionSources.defaultConnectionSource.settings
        MultiTenancySettings multiTenancySettings = connectionSourceSettings.multiTenancy
        this.multiTenancyMode = multiTenancySettings.getMode()
        this.tenantResolver = multiTenancySettings.getTenantResolver()
        if(this.tenantResolver instanceof RxDatastoreClientAware) {
            ((RxDatastoreClientAware)tenantResolver).setRxDatastoreClient(this)
        }
        this.mappingContext.setValidatorRegistry(
                createValidatorRegistry()
        )
    }

    @Override
    void setMessageSource(MessageSource messageSource) {
        ValidatorRegistry validatorRegistry = createValidatorRegistry(messageSource)
        this.mappingContext.setValidatorRegistry(
            validatorRegistry
        )
    }

    protected ValidatorRegistry createValidatorRegistry(MessageSource messageSource = new StaticMessageSource()) {
        return ValidatorRegistries.createValidatorRegistry(mappingContext, getConnectionSources().getDefaultConnectionSource().getSettings(), messageSource)
    }

    @Override
    MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return this.multiTenancyMode
    }

    @Override
    TenantResolver getTenantResolver() {
        return this.tenantResolver
    }

    @Override
    RxDatastoreClient getDatastoreClientForTenantId(Serializable tenantId) {
        if(multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            return getDatastoreClient(tenantId.toString())
        }
        else {
            return this
        }
    }

    ConfigurableApplicationEventPublisher getEventPublisher() {
        return eventPublisher
    }

    void setEventPublisher(ConfigurableApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher
        initDefaultEventListeners(eventPublisher)
    }

    protected void initDefaultEventListeners(ConfigurableApplicationEventPublisher configurableApplicationEventPublisher) {
        configurableApplicationEventPublisher.addApplicationListener(new AutoTimestampEventListener(this))
        configurableApplicationEventPublisher.addApplicationListener(new DomainEventListener(this))

        if(multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            configurableApplicationEventPublisher.addApplicationListener(new MultiTenantEventListener(this))
        }
    }

    MappingContext getMappingContext() {
        return mappingContext
    }

    @Override
    boolean isSchemaless() {
        return false
    }

    @Override
    def <T1> ObservableProxy<T1> proxy(Class<T1> type, Serializable id, QueryState queryState = new QueryState()) {
        (ObservableProxy)proxyFactory.createProxy(this, queryState, type, id)
    }

    @Override
    ObservableProxy proxy(Query query, QueryState queryState = new QueryState()) {
        (ObservableProxy)proxyFactory.createProxy(this, queryState, query)
    }

    @Override
    def <T> Observable get(Class<T> type, Serializable id, QueryState queryState) {
        return (Observable<T>)createQuery(type, queryState)
                .idEq(id)
                .max(1)
                .singleResult()
    }
    /**
     * Retrieve and instance of the given type and id
     * @param type The type
     * @param id The id
     * @return An observable
     */
    @Override
    final <T1> Observable<T1> get(Class<T1> type, Serializable id) {
        return get(type, id, new QueryState())
    }

    @Override
    final Query createQuery(Class type, QueryState queryState) {
        return createQuery(type, queryState, [:])
    }

    @Override
    final Query createQuery(Class type, QueryState queryState, Map arguments) {
        def entity = mappingContext.getPersistentEntity(type.name)
        if(entity == null) {
            throw new IllegalArgumentException("Type [$type.name] is not a persistent type")
        }

        return createEntityQuery(entity, queryState, arguments)
    }

    @Override
    final Query createQuery(Class type, Map arguments) {
        return createQuery(type, new QueryState())
    }

    @Override
    final Query createQuery(Class type) {
        return createQuery(type, [:])
    }

    @Override
    final Observable<Boolean> delete(Object instance) {
        delete(instance, Collections.emptyMap())
    }

    @Override
    Observable<Number> deleteAll(Iterable instances) {
        deleteAll(instances, Collections.emptyMap())
    }

    @Override
    Observable<Boolean> delete(Object instance, Map<String, Object> arguments) {
        deleteAll((Iterable)Arrays.asList(instance), arguments).map { Number deleteCount ->
            deleteCount > 0
        }
    }

    @Override
    Observable<Number> deleteAll(Iterable instances, Map<String, Object> arguments) {
        def ctx = this.mappingContext
        def proxyHandler = ctx.getProxyHandler()
        if(instances != null) {
            def batchOperation = new BatchOperation(arguments)
            List<ApplicationEvent> postEvents = []
            for(o in instances) {
                Class type = proxyHandler.getProxiedClass(o)
                def entity = ctx.getPersistentEntity(type.name)
                if(entity == null) {
                    throw new IllegalArgumentException("Type [$type.name] of instance [$o] is not a persistent type")
                }
                def reflector = mappingContext.getEntityReflector(entity)
                def id = proxyHandler.getIdentifier(o) ?: reflector.getIdentifier(o)
                if(id != null) {

                    def ea = ctx.createEntityAccess(entity, o)
                    def preDeleteEvent = new PreDeleteEvent(this, entity, ea)
                    eventPublisher?.publishEvent(preDeleteEvent)
                    if(!preDeleteEvent.isCancelled()) {
                        batchOperation.addDelete(entity, id, o)
                        postEvents.add new PostDeleteEvent(this, entity, ea)
                    }
                }
            }

            return batchDelete(batchOperation).map { Number deleteCount ->
                if(deleteCount > 0) {
                    if(eventPublisher != null) {
                        for(event in postEvents) {
                            eventPublisher.publishEvent(event)
                        }
                    }
                }
                return deleteCount
            }.defaultIfEmpty(0)
        }
        else {
            return Observable.just((Number)0)
        }
    }

    /**
     * Persist an instance
     * @param instance The instance
     *
     * @return An observable
     */
    @Override
    final <T1> Observable<T1> persist(T1 instance, Map<String, Object> arguments) {
        persistAll((Iterable<T1>)Collections.singletonList(instance), arguments).map { List<Serializable> identifiers ->
            if(!identifiers.isEmpty()) {
                return instance
            }
            else {
                return null
            }
        }
    }

    @Override
    final <T1> Observable<T1> insert(T1 instance, Map<String, Object> arguments) {
        insertAll((Iterable<T1>)Collections.singletonList(instance), arguments).map { List<Serializable> identifiers ->
            if(!identifiers.isEmpty()) {
                return instance
            }
            else {
                return null
            }
        }
    }

    @Override
    final <T1> Observable<T1> persist(T1 instance) {
        return persist(instance, Collections.<String,Object>emptyMap())
    }

    @Override
    Observable<List<Serializable>> insertAll(Iterable instances) {
        return persistAllInternal(instances, true, Collections.emptyMap())
    }

    @Override
    Observable<List<Serializable>> persistAll(Iterable instances) {
        return persistAllInternal(instances, false, Collections.emptyMap())
    }

    @Override
    Observable<List<Serializable>> persistAll(Iterable instances, Map<String, Object> arguments) {
        return persistAllInternal(instances, false, arguments)
    }

    @Override
    Observable<List<Serializable>> insertAll(Iterable instances, Map<String, Object> arguments) {
        return persistAllInternal(instances, true, arguments)
    }

    protected Observable<List<Serializable>> persistAllInternal(Iterable instances, boolean isInsert, Map<String, Object> arguments) {
        MappingContext ctx = this.mappingContext
        ApplicationEventPublisher eventPublisher = this.eventPublisher

        def proxyHandler = ctx.getProxyHandler()
        if (instances != null) {
            def batchOperation = new BatchOperation(arguments)
            List<Serializable> identifiers = []
            List<ApplicationEvent> postEvents = []
            for (o in instances) {
                Class type = proxyHandler.getProxiedClass(o)
                PersistentEntity entity = ctx.getPersistentEntity(type.name)
                EntityReflector entityReflector = ctx.getEntityReflector(entity)
                EntityAccess entityAccess = ctx.createEntityAccess(entity, o)
                if (entity == null) {
                    throw new IllegalArgumentException("Type [$type.name] of instance [$o] is not a persistent type")
                }
                def id = entityReflector.getIdentifier(o)

                boolean hasId = id != null
                if (hasId && !isInsert) {
                    def preUpdateEvent = new PreUpdateEvent(this, entity, entityAccess)
                    eventPublisher?.publishEvent(preUpdateEvent)
                    if (!preUpdateEvent.isCancelled()) {
                        processAssociations(entity, id, o, entityReflector, batchOperation, postEvents)
                        batchOperation.addUpdate(entity, id, o)
                        postEvents.add(new PostUpdateEvent(this, entity, entityAccess))
                    }
                } else {
                    if(!hasId) {
                        ValueGenerator valueGenerator = entity.getMapping().getIdentifier().generator
                        if(valueGenerator == ValueGenerator.NATIVE) {
                            // if the identifier is generated natively then use the hash code to identity the entity since the
                            // identifiers themselves will be generated from the insert operation
                            id = o.hashCode()
                        }
                        else if(valueGenerator == ValueGenerator.ASSIGNED) {
                            throw new IdentityGenerationException("Id generator is set to assigned but not identifier was provided for entity $o")
                        }
                        else {
                            id = generateIdentifier(entity, o, entityReflector)
                        }

                    }
                    def preInsertEvent = new PreInsertEvent(this, entity, entityAccess)
                    eventPublisher?.publishEvent(preInsertEvent)
                    if (!preInsertEvent.isCancelled()) {
                        processAssociations(entity, id, o, entityReflector, batchOperation, postEvents)
                        batchOperation.addInsert(entity, id, o)
                        postEvents.add(new PostInsertEvent(this, entity, entityAccess))
                    }
                }
                identifiers.add(id)
            }

            if (batchOperation.hasPendingOperations()) {

                return batchWrite(batchOperation).map({
                    if (eventPublisher != null) {
                        for (event in postEvents) {
                            eventPublisher.publishEvent(event)
                        }
                    }
                    return identifiers
                }) as Observable<List<Serializable>>
            } else {
                return Observable.just(identifiers)
            }
        } else {
            return Observable.just([])
        }
    }

    void processAssociations(PersistentEntity entity, Serializable id, Object instance, EntityReflector entityReflector, BatchOperation operation, List<ApplicationEvent> postEvents) {
        if(operation.isAlreadyPending(entity, id, instance)) {
            return
        }
        for(association in entity.associations) {
            if(association.doesCascade(CascadeType.PERSIST) && !association.isEmbedded() && !association.isBasic()) {
                def associatedEntity = association.associatedEntity

                if(association instanceof ToOne) {
                    DirtyCheckable associatedObject = (DirtyCheckable )entityReflector.getProperty(instance, association.name)
                    if(associatedObject != null && associatedObject.hasChanged()) {
                        scheduleInsertOrUpdate(associatedEntity, associatedObject, operation, postEvents)
                    }
                }
                else if(association instanceof ToMany) {
                    Iterable collection = (Iterable)entityReflector.getProperty(instance, association.name)
                    if(collection != null) {

                        if(collection instanceof PersistentCollection) {
                            if( !((PersistentCollection)collection).isInitialized() ) {
                                continue
                            }
                        }
                        if(collection instanceof DirtyCheckableCollection) {
                            if( !((DirtyCheckableCollection)collection).hasChanged() ) {
                                continue
                            }
                        }

                        for(obj in collection) {
                            scheduleInsertOrUpdate(associatedEntity, (DirtyCheckable)obj, operation, postEvents)
                        }
                    }
                }
            }
        }
    }

    protected void scheduleInsertOrUpdate(PersistentEntity associatedEntity, DirtyCheckable associatedObject, BatchOperation operation, List<ApplicationEvent> postEvents) {
        EntityReflector associationReflector = mappingContext.getEntityReflector(associatedEntity)
        def associationAccess = mappingContext.createEntityAccess(associatedEntity, associatedObject)
        def associatedId = associationReflector.getIdentifier(associatedObject)
        if (associatedId != null) {
            scheduleUpdate(associatedEntity, associationAccess, associatedId, associatedObject, associationReflector, operation, postEvents)
        } else {
            scheduleInsert(associatedEntity, associatedObject, associationReflector, associationAccess, operation, postEvents)
        }
        processAssociations(associatedEntity, associatedId, associatedObject, associationReflector, operation, postEvents)
    }

    protected void scheduleInsert(PersistentEntity associatedEntity, DirtyCheckable associatedObject, EntityReflector associationReflector, EntityAccess associationAccess, BatchOperation operation, List<ApplicationEvent> postEvents) {
        ValueGenerator valueGenerator = associatedEntity.getMapping().getIdentifier().generator
        def associatedId
        if(valueGenerator == ValueGenerator.NATIVE) {
            // if the identifier is generated natively then use the hash code to identity the entity since the
            // identifiers themselves will be generated from the insert operation
            associatedId = associatedObject.hashCode()
        }
        else {
            associatedId = generateIdentifier(associatedEntity, associatedObject, associationReflector)
        }

        def preInsertEvent = new PreInsertEvent(this, associatedEntity, associationAccess)
        eventPublisher?.publishEvent(preInsertEvent)
        if (!preInsertEvent.isCancelled()) {
            operation.addInsert(associatedEntity, associatedId, associatedObject)
            postEvents.add(new PostInsertEvent(this, associatedEntity, associationAccess))
            processAssociations(associatedEntity, associatedId, associatedObject, associationReflector, operation, postEvents)
        }
    }

    protected void scheduleUpdate(PersistentEntity associatedEntity, EntityAccess associationAccess, Serializable associatedId, DirtyCheckable associatedObject, EntityReflector associationReflector, BatchOperation operation, List<ApplicationEvent> postEvents) {
        def preUpdateEvent = new PreUpdateEvent(this, associatedEntity, associationAccess)
        eventPublisher?.publishEvent(preUpdateEvent)
        if (!preUpdateEvent.isCancelled()) {
            operation.addUpdate(associatedEntity, associatedId, associatedObject)
            postEvents.add(new PostUpdateEvent(this, associatedEntity, associationAccess))

            processAssociations(associatedEntity, associatedId, associatedObject, associationReflector, operation, postEvents)
        }
    }

    protected void activeDirtyChecking(object) {
        if (object instanceof DirtyCheckable) {
            def dirtyCheckable = (DirtyCheckable) object
            dirtyCheckable.trackChanges()
        }
    }

    protected boolean isIndexed(PersistentProperty property) {
        PropertyMapping<Property> pm = property.getMapping();
        final Property keyValue = pm.getMappedForm();
        return keyValue != null && keyValue.isIndex();
    }

    @Override
    final void close() throws IOException {
        try {
            RxGormEnhancer.close()
            def registry = GroovySystem.metaClassRegistry
            for(entity in mappingContext.persistentEntities) {
                registry.removeMetaClass(entity.javaClass)
            }
        } finally {
            doClose()
        }
    }

    /**
     * Close the client
     */
    abstract void doClose()

    @Override
    final RxGormStaticApi createStaticApi(PersistentEntity entity) {
        return createStaticApi(entity, ConnectionSourcesSupport.getDefaultConnectionSourceName(entity))
    }

    @Override
    final RxGormInstanceApi createInstanceApi(PersistentEntity entity) {
        return createInstanceApi(entity, ConnectionSourcesSupport.getDefaultConnectionSourceName(entity))
    }

    @Override
    final RxGormValidationApi createValidationApi(PersistentEntity entity) {
        return createValidationApi(entity, ConnectionSourcesSupport.getDefaultConnectionSourceName(entity))
    }

    @Override
    RxGormStaticApi createStaticApi(PersistentEntity entity, String connectionSourceName) {
        return new RxGormStaticApi(entity,  getDatastoreClient(connectionSourceName))
    }

    @Override
    RxGormInstanceApi createInstanceApi(PersistentEntity entity, String connectionSourceName) {
        return new RxGormInstanceApi(entity, getDatastoreClient(connectionSourceName))
    }

    @Override
    RxGormValidationApi createValidationApi(PersistentEntity entity, String connectionSourceName) {
        return new RxGormValidationApi(entity, getDatastoreClient(connectionSourceName))
    }

    /**
     * Executes a batch write operation
     *
     * @param operation The batch operation
     *
     * @return The total number of records updated, deleted or inserted
     */
    abstract Observable<Number> batchWrite(BatchOperation operation)

    /**
     * Executes a batch delete operation
     *
     * @param operation The batch operation
     *
     * @return The total number of records deleted
     */
    abstract Observable<Number> batchDelete(BatchOperation operation)

    /**
     * Generates an identifier for the given entity and instance
     *
     * @param entity
     * @param instance
     * @param reflector
     * @return The generated identifier
     */
    abstract Serializable generateIdentifier(PersistentEntity entity, Object instance, EntityReflector reflector)


    /**
     * Creates a query for the given entity
     *
     * @param entity The entity
     *
     * @return The query object
     */
    Query createEntityQuery(PersistentEntity entity, QueryState queryState) {
        return createEntityQuery(entity, queryState, [:])
    }

    /**
     * Creates a query for the given entity
     *
     * @param entity The entity
     *
     * @return The query object
     */
    abstract Query createEntityQuery(PersistentEntity entity, QueryState queryState, Map arguments)

    @Override
    RxDatastoreClient getDatastoreClient(String connectionSourceName) {
        def datastoreClient = this.datastoreClients.get(connectionSourceName)
        if(datastoreClient == null) {
            throw new ConfigurationException("No connection source configured for name [$connectionSourceName]. Check your configuration.")
        }
        return datastoreClient
    }
}

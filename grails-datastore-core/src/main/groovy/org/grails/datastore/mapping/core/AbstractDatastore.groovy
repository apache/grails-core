/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.core

import groovy.transform.CompileStatic
import jakarta.annotation.PreDestroy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.convert.converter.ConverterRegistry
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.cache.TPCacheAdapterRepository
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.types.BasicTypeConverterRegistrar
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import org.grails.datastore.mapping.services.DefaultServiceRegistry
import org.grails.datastore.mapping.services.Service
import org.grails.datastore.mapping.services.ServiceNotFoundException
import org.grails.datastore.mapping.services.ServiceRegistry
import org.grails.datastore.mapping.transactions.SessionHolder

/**
 * Abstract Datastore implementation that deals with binding the Session to thread locale upon creation.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings(['rawtypes', 'unchecked'])
abstract class AbstractDatastore implements Datastore, StatelessDatastore, ServiceRegistry {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractDatastore)

    private ApplicationContext applicationContext
    protected ApplicationEventPublisher applicationEventPublisher

    protected final MappingContext mappingContext
    protected final ServiceRegistry serviceRegistry
    protected final PropertyResolver connectionDetails
    protected final TPCacheAdapterRepository cacheAdapterRepository

    /**
     * Created lazily by {@link #getSessionResolver()} so that {@code this} does not escape the
     * constructor into the resolver while the datastore is still under construction.
     */
    private volatile SessionResolver sessionResolver

    /**
     * @return The session resolver for this datastore: a stateless view over the same
     * {@link SessionHolder}/{@link TransactionSynchronizationManager} state used elsewhere for
     * this datastore. The same instance is returned on every call.
     */
    SessionResolver getSessionResolver() {
        SessionResolver resolver = this.sessionResolver
        if (resolver == null) {
            synchronized (this) {
                resolver = this.sessionResolver
                if (resolver == null) {
                    resolver = new TransactionSynchronizationSessionResolver(this)
                    this.sessionResolver = resolver
                }
            }
        }
        return resolver
    }

    AbstractDatastore(MappingContext mappingContext) {
        this(mappingContext, (PropertyResolver) null, null)
    }

    AbstractDatastore(MappingContext mappingContext, Map<String, Object> connectionDetails,
              ConfigurableApplicationContext ctx) {
        this(mappingContext, connectionDetails, ctx, null)
    }

    AbstractDatastore(MappingContext mappingContext, PropertyResolver connectionDetails,
                      ConfigurableApplicationContext ctx) {
        this(mappingContext, connectionDetails, ctx, null)
    }

    AbstractDatastore(MappingContext mappingContext, PropertyResolver connectionDetails,
                      ConfigurableApplicationContext ctx, TPCacheAdapterRepository cacheAdapterRepository) {
        this.mappingContext = mappingContext
        this.connectionDetails = connectionDetails
        this.cacheAdapterRepository = cacheAdapterRepository
        setApplicationContext(ctx)
        DefaultServiceRegistry defaultServiceRegistry = new DefaultServiceRegistry(this)
        this.serviceRegistry = defaultServiceRegistry
        defaultServiceRegistry.initialize()
    }

    AbstractDatastore(MappingContext mappingContext, Map<String, Object> connectionDetails,
              ConfigurableApplicationContext ctx, TPCacheAdapterRepository cacheAdapterRepository) {
        this(mappingContext, mapToPropertyResolver(connectionDetails), ctx, cacheAdapterRepository)
    }

    protected static PropertyResolver mapToPropertyResolver(Map<String, Object> connectionDetails) {
        return DatastoreUtils.createPropertyResolver(connectionDetails)
    }

    @Override
    def <T> T getService(Class<T> interfaceType) throws ServiceNotFoundException {
        return serviceRegistry.getService(interfaceType)
    }

    @Override
    def <T extends Service> Iterable<T> getServices() {
        return serviceRegistry.getServices()
    }

    /**
     * Closes every session held by the current thread's {@link SessionHolder}, if any. Since
     * {@link TransactionSynchronizationManager} is thread-local, this only reaches the thread
     * invoking {@code @PreDestroy} - sessions bound on other threads are not visible here and must
     * be closed by their own owning thread. A holder that is synchronized with an active Spring
     * transaction is left alone: {@code DatastoreTransactionManager} still owns those sessions and
     * will operate on them during commit or rollback.
     */
    @PreDestroy
    void destroy() {
        Object resource = TransactionSynchronizationManager.hasResource(this) ?
                TransactionSynchronizationManager.getResource(this) : null
        if (resource instanceof SessionHolder && !((SessionHolder) resource).isSynchronizedWithTransaction()) {
            TransactionSynchronizationManager.unbindResource(this)
            for (Session session in new ArrayList<Session>(((SessionHolder) resource).getSessions())) {
                DatastoreUtils.closeSession(session)
            }
        }
        FieldEntityAccess.clearReflectors()
        final MetaClassRegistry registry = GroovySystem.getMetaClassRegistry()
        for (PersistentEntity persistentEntity in getMappingContext().getPersistentEntities()) {
            final Class cls = persistentEntity.getJavaClass()
            try {
                registry.removeMetaClass(cls)
            }
            catch (Exception e) {
                LOG.error('There was an error shutting down GORM for entity [' + cls.getName() + ']: ' + e.getMessage(), e)
            }
        }
    }

    void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx
        this.applicationEventPublisher = ctx
    }

    Session connect() {
        return connect(connectionDetails)
    }

    final Session connect(PropertyResolver connDetails) {
        Session session = createSession(connDetails)
        publishSessionCreationEvent(session)
        return session
    }

    private void publishSessionCreationEvent(Session session) {
        ApplicationEventPublisher eventPublisher = getApplicationEventPublisher()
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new SessionCreationEvent(session))
        }
    }

    @Override
    Session connectStateless() {
        Session session = createStatelessSession(connectionDetails)
        publishSessionCreationEvent(session)
        return session
    }

    /**
     * Creates the native session
     *
     * @param connectionDetails The session details
     * @return The session object
     */
    protected abstract Session createSession(PropertyResolver connectionDetails)

    /**
     * Creates the native stateless session
     *
     * @param connectionDetails The session details
     * @return The session object
     */
    protected Session createStatelessSession(PropertyResolver connectionDetails) {
        return createSession(connectionDetails)
    }

    Session getCurrentSession() throws ConnectionNotFoundException {
        return DatastoreUtils.doGetSession(this, false)
    }

    /**
     * @return Whether {@link #getCurrentSession()} would return an existing session rather than
     * opening a new one: a validated (still connected) session is bound to the current thread.
     * Delegates to {@link #getSessionResolver()} so both methods read the same state. Unlike
     * {@link #getCurrentSession()}, this is non-mutating - it never evicts a stale session or
     * unbinds an empty holder, so it is safe to call for routing/discovery purposes on a
     * datastore whose session state this call has no other reason to touch.
     */
    boolean hasCurrentSession() {
        return getSessionResolver().hasResolvedSession()
    }

    /**
     * Static way to retrieve the session
     * @return The session instance
     * @throws ConnectionNotFoundException If no session has been created
     */
    static Session retrieveSession() throws ConnectionNotFoundException {
        return retrieveSession(Datastore)
    }

    /**
     * Static way to retrieve the session
     * @param datastoreClass The type of datastore
     * @return The session instance
     * @throws ConnectionNotFoundException If no session has been created
     */
    static Session retrieveSession(Class datastoreClass) throws ConnectionNotFoundException {
        final Map<Object, Object> resourceMap = TransactionSynchronizationManager.getResourceMap()
        Session session = null

        if (resourceMap != null && !resourceMap.isEmpty()) {
            for (Object key in resourceMap.keySet()) {
                if (datastoreClass.isInstance(key)) {
                    SessionHolder sessionHolder = (SessionHolder) resourceMap.get(key)
                    if (sessionHolder != null) {
                        session = sessionHolder.getSession()
                    }
                }
            }
        }

        if (session == null) {
            throw new ConnectionNotFoundException('No datastore session found. Call Datastore.connect(..) before calling Datastore.getCurrentSession()')
        }
        return session
    }

    MappingContext getMappingContext() {
        return mappingContext
    }

    /**
     * @deprecated  Deprecated, will be removed in a future version of GORM
     */
    @Deprecated
    ConfigurableApplicationContext getApplicationContext() {
        return (ConfigurableApplicationContext) applicationContext
    }

    /**
     * @return The event publisher, or {@code null} if this datastore was constructed without an
     * {@link ApplicationContext} and no subclass provides its own publisher
     */
    ApplicationEventPublisher getApplicationEventPublisher() {
        return applicationEventPublisher
    }

    protected void initializeConverters(MappingContext mappingContext) {
        final ConverterRegistry conversionService = mappingContext.getConverterRegistry()
        BasicTypeConverterRegistrar registrar = new BasicTypeConverterRegistrar()
        registrar.register(conversionService)
    }

    protected boolean isIndexed(PersistentProperty property) {
        PropertyMapping<Property> pm = property.getMapping()
        final Property keyValue = pm.getMappedForm()
        return keyValue != null && keyValue.isIndex()
    }

    boolean isSchemaless() {
        return false
    }

    @Override
    def <T> T withSession(final Closure<T> callable) {
        return DatastoreUtils.execute(this, { Session session ->
            callable.call(session)
        } as SessionCallback<T>)
    }

}

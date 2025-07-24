/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.core;

import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.env.PropertyResolver;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.grails.datastore.mapping.cache.TPCacheAdapterRepository;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.model.types.BasicTypeConverterRegistrar;
import org.grails.datastore.mapping.reflect.FieldEntityAccess;
import org.grails.datastore.mapping.services.DefaultServiceRegistry;
import org.grails.datastore.mapping.services.Service;
import org.grails.datastore.mapping.services.ServiceNotFoundException;
import org.grails.datastore.mapping.services.ServiceRegistry;
import org.grails.datastore.mapping.transactions.SessionHolder;

/**
 * Abstract Datastore implementation that deals with binding the Session to thread locale upon creation.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractDatastore implements Datastore, StatelessDatastore, ServiceRegistry {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractDatastore.class);
    private ApplicationContext applicationContext;

    protected final MappingContext mappingContext;
    protected final ServiceRegistry serviceRegistry;
    protected final PropertyResolver connectionDetails;
    protected final TPCacheAdapterRepository cacheAdapterRepository;


    public AbstractDatastore(MappingContext mappingContext) {
        this(mappingContext,(PropertyResolver) null, null);
    }

    public AbstractDatastore(MappingContext mappingContext, Map<String, Object> connectionDetails,
              ConfigurableApplicationContext ctx) {
        this(mappingContext, connectionDetails,  ctx, null);
    }

    public AbstractDatastore(MappingContext mappingContext, PropertyResolver connectionDetails,
                             ConfigurableApplicationContext ctx) {
        this(mappingContext, connectionDetails,  ctx, null);
    }

    public AbstractDatastore(MappingContext mappingContext, PropertyResolver connectionDetails,
                             ConfigurableApplicationContext ctx, TPCacheAdapterRepository cacheAdapterRepository) {
        this.mappingContext = mappingContext;
        this.connectionDetails = connectionDetails;
        setApplicationContext(ctx);
        this.cacheAdapterRepository = cacheAdapterRepository;
        DefaultServiceRegistry defaultServiceRegistry = new DefaultServiceRegistry(this);
        this.serviceRegistry = defaultServiceRegistry;
        defaultServiceRegistry.initialize();
    }

    public AbstractDatastore(MappingContext mappingContext, Map<String, Object> connectionDetails,
              ConfigurableApplicationContext ctx, TPCacheAdapterRepository cacheAdapterRepository) {
        this(mappingContext, mapToPropertyResolver(connectionDetails), ctx, cacheAdapterRepository);
    }

    protected static PropertyResolver mapToPropertyResolver(Map<String,Object> connectionDetails) {
        return DatastoreUtils.createPropertyResolver(connectionDetails);
    }

    @Override
    public <T> T getService(Class<T> interfaceType) throws ServiceNotFoundException {
        return serviceRegistry.getService(interfaceType);
    }

    @Override
    public <T extends Service> Iterable<T> getServices() {
        return serviceRegistry.getServices();
    }

    @PreDestroy
    public void destroy() {
        FieldEntityAccess.clearReflectors();
        final MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        for (PersistentEntity persistentEntity : getMappingContext().getPersistentEntities()) {
            final Class cls = persistentEntity.getJavaClass();
            try {
                registry.removeMetaClass(cls);
            } catch (Exception e) {
                LOG.error("There was an error shutting down GORM for entity ["+cls.getName()+"]: " + e.getMessage(), e);
            }
        }
    }

    public void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    public Session connect() {
        return connect(connectionDetails);
    }

    public final Session connect(PropertyResolver connDetails) {
        Session session = createSession(connDetails);
        publishSessionCreationEvent(session);
        return session;
    }

    private void publishSessionCreationEvent(Session session) {
        ApplicationEventPublisher applicationEventPublisher = getApplicationEventPublisher();
        if(applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new SessionCreationEvent(session));
        }
    }

    @Override
    public Session connectStateless() {
        Session session = createStatelessSession(connectionDetails);
        publishSessionCreationEvent(session);
        return session;
    }


    /**
     * Creates the native session
     *
     * @param connectionDetails The session details
     * @return The session object
     */
    protected abstract Session createSession(PropertyResolver connectionDetails);

    /**
     * Creates the native stateless session
     *
     * @param connectionDetails The session details
     * @return The session object
     */
    protected Session createStatelessSession(PropertyResolver connectionDetails) {
        return createSession(connectionDetails);
    }


    public Session getCurrentSession() throws ConnectionNotFoundException {
        return DatastoreUtils.doGetSession(this, false);
    }

    public boolean hasCurrentSession() {
        return TransactionSynchronizationManager.hasResource(this);
    }

    /**
     * Static way to retrieve the session
     * @return The session instance
     * @throws ConnectionNotFoundException If no session has been created
     */
    public static Session retrieveSession() throws ConnectionNotFoundException {
        return retrieveSession(Datastore.class);
    }

    /**
     * Static way to retrieve the session
     * @param datastoreClass The type of datastore
     * @return The session instance
     * @throws ConnectionNotFoundException If no session has been created
     */
    public static Session retrieveSession(Class datastoreClass) throws ConnectionNotFoundException {
        final Map<Object, Object> resourceMap = TransactionSynchronizationManager.getResourceMap();
        Session session = null;

        if (resourceMap != null && !resourceMap.isEmpty()) {
            for (Object key : resourceMap.keySet()) {
                if (datastoreClass.isInstance(key)) {
                    SessionHolder sessionHolder = (SessionHolder) resourceMap.get(key);
                    if (sessionHolder != null) {
                        session = sessionHolder.getSession();
                    }
                }
            }
        }

        if (session == null) {
            throw new ConnectionNotFoundException("No datastore session found. Call Datastore.connect(..) before calling Datastore.getCurrentSession()");
        }
        return session;
    }

    public MappingContext getMappingContext() {
        return mappingContext;
    }

    /**
     * @deprecated  Deprecated, will be removed in a future version of GORM
     */
    @Deprecated
    public ConfigurableApplicationContext getApplicationContext() {
        return (ConfigurableApplicationContext)applicationContext;
    }


    public ApplicationEventPublisher getApplicationEventPublisher() {
        return getApplicationContext();
    }


    protected void initializeConverters(MappingContext mappingContext) {
        final ConverterRegistry conversionService = mappingContext.getConverterRegistry();
        BasicTypeConverterRegistrar registrar = new BasicTypeConverterRegistrar();
        registrar.register(conversionService);
    }

    protected boolean isIndexed(PersistentProperty property) {
        PropertyMapping<Property> pm = property.getMapping();
        final Property keyValue = pm.getMappedForm();
        return keyValue != null && keyValue.isIndex();
    }

    public boolean isSchemaless() {
        return false;
    }


    @Override
    public <T> T withSession(final Closure<T> callable) {
        return DatastoreUtils.execute(this, new SessionCallback<T>() {
            @Override
            public T doInSession(Session session) {
                return callable.call(session);
            }
        });
    }
}

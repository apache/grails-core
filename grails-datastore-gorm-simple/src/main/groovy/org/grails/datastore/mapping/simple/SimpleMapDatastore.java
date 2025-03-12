/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.simple;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.Closure;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormInstanceApi;
import org.grails.datastore.gorm.GormStaticApi;
import org.grails.datastore.gorm.GormValidationApi;
import org.grails.datastore.gorm.events.*;
import org.grails.datastore.gorm.multitenancy.MultiTenantEventListener;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.SchemaMultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.simple.connections.SimpleMapConnectionSourceFactory;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.core.Datastore} interface that backs onto an in-memory map.
 * Mainly used for mocking and testing scenarios.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class SimpleMapDatastore extends AbstractDatastore implements Closeable, TransactionCapableDatastore, MultipleConnectionSourceCapableDatastore, SchemaMultiTenantCapableDatastore<Map<String,Map>, ConnectionSourceSettings>, ConnectionSourcesProvider<Map<String,Map>, ConnectionSourceSettings> {
    private final Map<String, Map> inmemoryData;
    private final TenantResolver tenantResolver;
    protected final GormEnhancer gormEnhancer;
    private final ConfigurableApplicationEventPublisher eventPublisher;
    private Map indices = new ConcurrentHashMap();
    private final PlatformTransactionManager transactionManager;
    private final ConnectionSources<Map<String,Map>, ConnectionSourceSettings> connectionSources;
    private final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final Map<String, SimpleMapDatastore> datastoresByConnectionSource = new LinkedHashMap<>();
    protected final boolean failOnError;

    public SimpleMapDatastore(ConnectionSources<Map<String,Map>, ConnectionSourceSettings> connectionSources, MappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        super(mappingContext);
        this.connectionSources = connectionSources;
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        this.inmemoryData = defaultConnectionSource.getSource();
        DatastoreTransactionManager dtm = new DatastoreTransactionManager();
        dtm.setDatastore(this);
        this.transactionManager = dtm;
        MultiTenancySettings multiTenancy = defaultConnectionSource.getSettings().getMultiTenancy();
        this.multiTenancyMode = multiTenancy.getMode();
        this.tenantResolver = multiTenancy.getTenantResolver();
        PropertyResolver config = connectionSources.getBaseConfiguration();
        this.failOnError = config.getProperty(Settings.SETTING_FAIL_ON_ERROR, Boolean.class, false);
        if(!(connectionSources instanceof SingletonConnectionSources)) {

            Iterable<ConnectionSource<Map<String,Map>, ConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (ConnectionSource<Map<String,Map>, ConnectionSourceSettings> connectionSource : allConnectionSources) {
                SingletonConnectionSources singletonConnectionSources = new SingletonConnectionSources(connectionSource, connectionSources.getBaseConfiguration());
                SimpleMapDatastore childDatastore;

                if(ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
                    childDatastore = this;
                }
                else {
                    childDatastore = new SimpleMapDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
                        @Override
                        protected GormEnhancer initialize(ConnectionSourceSettings settings) {
                            return null;
                        }
                    };
                }
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }
        }
        this.eventPublisher = eventPublisher;
        this.gormEnhancer = initialize(defaultConnectionSource.getSettings());
    }

    public SimpleMapDatastore(ConnectionSources<Map<String,Map>, ConnectionSourceSettings> connectionSources, ConfigurableApplicationEventPublisher eventPublisher, Class... classes) {
        this(connectionSources, createMappingContext(connectionSources,classes), eventPublisher);
    }

    public SimpleMapDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(ConnectionSourcesInitializer.create(new SimpleMapConnectionSourceFactory(), configuration), eventPublisher, classes);
    }

    public SimpleMapDatastore() {
        this(DatastoreUtils.createPropertyResolver(null), new DefaultApplicationEventPublisher());
    }

    public SimpleMapDatastore(final Iterable<String> dataSourceNames, Class...classes) {
        this(createMultipleDataSources(dataSourceNames, DatastoreUtils.createPropertyResolver(null)),new DefaultApplicationEventPublisher(), classes);
    }

    public SimpleMapDatastore(Class...classes) {
        this(DatastoreUtils.createPropertyResolver(null),new DefaultApplicationEventPublisher(), classes);
    }


    public SimpleMapDatastore(PropertyResolver configuration, final Iterable<String> dataSourceNames, Class...classes) {
        this(createMultipleDataSources(dataSourceNames, configuration),new DefaultApplicationEventPublisher(), classes);
    }

    public SimpleMapDatastore(PropertyResolver configuration, final Iterable<String> dataSourceNames, Package...packages) {
        this(createMultipleDataSources(dataSourceNames, configuration),new DefaultApplicationEventPublisher(), new ClasspathEntityScanner().scan(packages));
    }


    public SimpleMapDatastore(Map configuration, final Iterable<String> dataSourceNames, Package...packages) {
        this(createMultipleDataSources(dataSourceNames, DatastoreUtils.createPropertyResolver(configuration)),new DefaultApplicationEventPublisher(), new ClasspathEntityScanner().scan(packages));
    }

    public SimpleMapDatastore(Map configuration, Package...packages) {
        this(DatastoreUtils.createPropertyResolver(configuration),new DefaultApplicationEventPublisher(), new ClasspathEntityScanner().scan(packages));
    }

    public SimpleMapDatastore(PropertyResolver configuration, final Iterable<String> dataSourceNames, Package packageToScan) {
        this(createMultipleDataSources(dataSourceNames, configuration),new DefaultApplicationEventPublisher(), new ClasspathEntityScanner().scan(packageToScan));
    }

    /**
     * Creates a map based datastore backing onto the specified map
     *
     * @param datastore The datastore to back on to
     * @param ctx the application context
     */
    @Deprecated
    public SimpleMapDatastore(Map<String, Map> datastore, ConfigurableApplicationContext ctx) {
        this(new SingletonConnectionSources<>(new DefaultConnectionSource<>(ConnectionSource.DEFAULT, datastore, new ConnectionSourceSettings()), DatastoreUtils.createPropertyResolver(null)), new ConfigurableApplicationContextEventPublisher(ctx));
        setApplicationContext(ctx);
    }

    private static PropertyResolver getConfiguration(ConfigurableApplicationContext ctx) {
        PropertyResolver propertyResolver;
        try {
            propertyResolver = ctx.getBean(PropertyResolver.class);
        } catch (Exception e) {
            propertyResolver = DatastoreUtils.createPropertyResolver(null);
        }
        return propertyResolver;
    }

    @Deprecated
    public SimpleMapDatastore(ConfigurableApplicationContext ctx) {
        this(getConfiguration(ctx), new ConfigurableApplicationContextEventPublisher(ctx));
        setApplicationContext(ctx);
    }

    /**
     * Creates a map based datastore for the specified mapping context
     *
     * @param mappingContext The mapping context
     */
    @Deprecated
    public SimpleMapDatastore(MappingContext mappingContext, ConfigurableApplicationContext ctx) {
        this(ConnectionSourcesInitializer.create(new SimpleMapConnectionSourceFactory(), DatastoreUtils.createPropertyResolver(null)), mappingContext, new ConfigurableApplicationContextEventPublisher(ctx));
    }

    protected static KeyValueMappingContext createMappingContext(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, Class... classes) {
        KeyValueMappingContext ctx = new KeyValueMappingContext("test", connectionSources.getDefaultConnectionSource().getSettings());
        ctx.addPersistentEntities(classes);
        return ctx;
    }

    protected static InMemoryConnectionSources<Map<String, Map>, ConnectionSourceSettings> createMultipleDataSources(final Iterable<String> dataSourceNames, PropertyResolver propertyResolver) {
        SimpleMapConnectionSourceFactory simpleMapConnectionSourceFactory = new SimpleMapConnectionSourceFactory();
        return new InMemoryConnectionSources<Map<String, Map>, ConnectionSourceSettings>(
                simpleMapConnectionSourceFactory.create(ConnectionSource.DEFAULT, propertyResolver),
                simpleMapConnectionSourceFactory,
                propertyResolver
        ) {
            @Override
            protected Iterable<String> getConnectionSourceNames(ConnectionSourceFactory<Map<String, Map>, ConnectionSourceSettings> connectionSourceFactory, PropertyResolver configuration) {
                return dataSourceNames;
            }
        };
    }

    protected GormEnhancer initialize(ConnectionSourceSettings settings) {
        registerEventListeners(this.eventPublisher);

        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            public void persistentEntityAdded(PersistentEntity entity) {
                gormEnhancer.registerEntity(entity);
            }
        });

        return new GormEnhancer(this, transactionManager, settings) {

            @Override
            protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, String qualifier) {
                SimpleMapDatastore datastore = getDatastoreForQualifier(cls, qualifier);
                return new GormStaticApi<>(cls, datastore, createDynamicFinders(datastore), datastore.getTransactionManager());
            }

            @Override
            protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier) {
                SimpleMapDatastore datastore = getDatastoreForQualifier(cls, qualifier);
                return new GormValidationApi<>(cls, datastore);
            }

            @Override
            protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier) {
                SimpleMapDatastore datastore = getDatastoreForQualifier(cls, qualifier);
                GormInstanceApi<D> instanceApi = new GormInstanceApi<>(cls, datastore);
                instanceApi.setFailOnError(failOnError);
                return instanceApi;
            }

            private <D> SimpleMapDatastore getDatastoreForQualifier(Class<D> cls, String qualifier) {
                String defaultConnectionSourceName = ConnectionSourcesSupport.getDefaultConnectionSourceName(getMappingContext().getPersistentEntity(cls.getName()));
                boolean isDefaultQualifier = qualifier.equals(ConnectionSource.DEFAULT);
                if(isDefaultQualifier && defaultConnectionSourceName.equals(ConnectionSource.DEFAULT)) {
                    return SimpleMapDatastore.this;
                }
                else {
                    if(isDefaultQualifier) {
                        qualifier = defaultConnectionSourceName;
                    }
                    ConnectionSource<Map<String,Map>, ConnectionSourceSettings> connectionSource = connectionSources.getConnectionSource(qualifier);
                    if(connectionSource == null) {
                        throw new ConfigurationException("Invalid connection ["+defaultConnectionSourceName+"] configured for class ["+cls+"]");
                    }
                    return SimpleMapDatastore.this.datastoresByConnectionSource.get(qualifier);
                }
            }
        };
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(new DomainEventListener(this));
        eventPublisher.addApplicationListener(new AutoTimestampEventListener(this));
        if(multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener(this));
        }
    }

    public Map getIndices() {
        return indices;
    }

    @Override
    protected Session createSession(PropertyResolver connectionDetails) {
        return new SimpleMapSession(this, getMappingContext(), eventPublisher);
    }

    @Override
    public ApplicationEventPublisher getApplicationEventPublisher() {
        return this.eventPublisher;
    }

    public Map<String, Map> getBackingMap() {
        return inmemoryData;
    }

    public void clearData() {
        inmemoryData.clear();
        indices.clear();
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    @Override
    public ConnectionSources<Map<String, Map>, ConnectionSourceSettings> getConnectionSources() {
        return this.connectionSources;
    }

    @Override
    public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA ? MultiTenancySettings.MultiTenancyMode.DATABASE : this.multiTenancyMode;
    }

    @Override
    public TenantResolver getTenantResolver() {
        return this.tenantResolver;
    }

    @Override
    public Datastore getDatastoreForTenantId(Serializable tenantId) {
        if(multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            return this;
        }
        if(tenantId != null) {
            return getDatastoreForConnection(tenantId.toString());
        }
        return this;
    }

    @Override
    public <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
        Datastore datastore = getDatastoreForTenantId(tenantId);
        org.grails.datastore.mapping.core.Session session = datastore.connect();
        try {
            DatastoreUtils.bindNewSession(session);
            return callable.call(session);
        }
        finally {
            DatastoreUtils.unbindSession(session);
        }
    }

    @Override
    public Datastore getDatastoreForConnection(String connectionName) {

        SimpleMapDatastore childDatastore = datastoresByConnectionSource.get(connectionName);
        if(childDatastore == null) {
            throw new ConfigurationException("No datastore found for connection named ["+connectionName+"]");
        }
        return childDatastore;
    }

    @Override
    public void close() throws IOException {
        try {
            destroy();
        } catch (Exception e) {
            throw new IOException(e);
        }
        gormEnhancer.close();
    }

    @Override
    public void addTenantForSchema(String schemaName) {
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> connectionSource = this.connectionSources.addConnectionSource(schemaName, Collections.<String, Object>emptyMap());
        SingletonConnectionSources singletonConnectionSources = new SingletonConnectionSources(connectionSource, connectionSources.getBaseConfiguration());
        SimpleMapDatastore childDatastore;

        if(ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
            childDatastore = this;
        }
        else {
            childDatastore = new SimpleMapDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
                @Override
                protected GormEnhancer initialize(ConnectionSourceSettings settings) {
                    return null;
                }
            };
        }
        datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);

        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            gormEnhancer.registerEntity(persistentEntity);
        }
    }
}

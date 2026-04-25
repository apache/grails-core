/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS licenses this file
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
package org.grails.datastore.mapping.simple;

import groovy.lang.Closure;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormRegistry;
import org.grails.datastore.gorm.multitenancy.MultiTenantEventListener;
import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.*;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.simple.connections.SimpleMapConnectionSourceFactory;
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.PropertyResolver;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.core.Datastore} interface that backs onto an in-memory map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SimpleMapDatastore extends AbstractDatastore implements MultiTenantCapableDatastore<Map<String, Map>, ConnectionSourceSettings>, TransactionCapableDatastore, MultipleConnectionSourceCapableDatastore, ConnectionSourcesProvider<Map<String, Map>, ConnectionSourceSettings>, Closeable {

    private static class SharedState {
        final Map<Serializable, Map> inmemoryData = new ConcurrentHashMap<>();
        final Map<String, Map> indices = new ConcurrentHashMap<>();
        final AtomicLong lastKey = new AtomicLong(0);
    }
    
    private static final Map<MappingContext, SharedState> stateCache = Collections.synchronizedMap(new WeakHashMap<MappingContext, SharedState>());
    
    private static final ApplicationEventPublisher NO_OP_PUBLISHER = new ApplicationEventPublisher() {
        @Override
        public void publishEvent(ApplicationEvent event) { }
        @Override
        public void publishEvent(Object event) { }
    };

    protected final PlatformTransactionManager transactionManager;
    protected final String connectionName;
    protected final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final TenantResolver tenantResolver;
    protected final boolean failOnError;
    protected final GormEnhancer gormEnhancer;
    protected final ApplicationEventPublisher eventPublisher;
    protected final ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources;
    protected final Map<String, SimpleMapDatastore> datastoresByConnectionSource = new ConcurrentHashMap<>();
    
    private final SharedState sharedState;

    public SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        super(mappingContext);
        this.connectionSources = connectionSources;
        this.eventPublisher = eventPublisher != null ? eventPublisher : NO_OP_PUBLISHER;
        
        SharedState state = stateCache.get(mappingContext);
        if (state == null) {
            state = new SharedState();
            stateCache.put(mappingContext, state);
        }
        this.sharedState = state;
        
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        this.connectionName = defaultConnectionSource.getName();
        
        DatastoreTransactionManager dtm = new DatastoreTransactionManager();
        dtm.setDatastore(this);
        this.transactionManager = dtm;
        this.multiTenancyMode = defaultConnectionSource.getSettings().getMultiTenancy().getMode();
        this.tenantResolver = defaultConnectionSource.getSettings().getMultiTenancy().getTenantResolver();
        PropertyResolver config = connectionSources.getBaseConfiguration();
        this.failOnError = config != null ? config.getProperty(Settings.SETTING_FAIL_ON_ERROR, Boolean.class, false) : false;
        
        if (this.eventPublisher instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext cac = (ConfigurableApplicationContext) this.eventPublisher;
            if (!cac.isActive()) {
                cac.refresh();
            }
        }
        
        if (ConnectionSource.DEFAULT.equals(connectionName)) {
            this.gormEnhancer = new GormEnhancer(this, transactionManager, defaultConnectionSource.getSettings());
        } else {
            this.gormEnhancer = null;
        }

        GormRegistry.getInstance().registerDatastore(connectionName, this);
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            GormRegistry.getInstance().registerEntityDatastore(persistentEntity.getName(), connectionName, this);
        }

        if (ConnectionSource.DEFAULT.equals(connectionName)) {
            GormEnhancer.setPreferredDatastore(this);
            Iterable<ConnectionSource<Map<String, Map>, ConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (ConnectionSource<Map<String, Map>, ConnectionSourceSettings> connectionSource : allConnectionSources) {
                String name = connectionSource.getName();
                if (!ConnectionSource.DEFAULT.equals(name)) {
                    getDatastoreForConnection(name);
                }
            }
        }

        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            public void persistentEntityAdded(PersistentEntity entity) {
                if (gormEnhancer != null) {
                    gormEnhancer.registerEntity(entity);
                }
                GormRegistry.getInstance().registerEntityDatastore(entity.getName(), connectionName, SimpleMapDatastore.this);
            }
        });

        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR && this.eventPublisher instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext)this.eventPublisher).addApplicationListener(new MultiTenantEventListener(this));
        }

        mappingContext.getConverterRegistry().addConverter(Long.class, UUID.class, new Converter<Long, UUID>() {
            @Override
            public UUID convert(Long source) {
                return new UUID(source, source);
            }
        });
        mappingContext.getConverterRegistry().addConverter(Long.class, Integer.class, new Converter<Long, Integer>() {
            @Override
            public Integer convert(Long source) {
                return source.intValue();
            }
        });
        mappingContext.getConverterRegistry().addConverter(Long.class, String.class, new Converter<Long, String>() {
            @Override
            public String convert(Long source) {
                return source.toString();
            }
        });
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Class... classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), (ApplicationEventPublisher)null, classes);
    }

    public SimpleMapDatastore(PropertyResolver configuration, ApplicationEventPublisher eventPublisher, Class... classes) {
        this(createConnectionSources(configuration), createMappingContext(classes), eventPublisher);
    }

    public SimpleMapDatastore(PropertyResolver configuration, List<String> connectionNames, Class... classes) {
        this(createConnectionSources(connectionNames, configuration), createMappingContext(classes), (ApplicationEventPublisher)null);
    }

    public SimpleMapDatastore(PropertyResolver configuration, Class... classes) {
        this(configuration, (ApplicationEventPublisher)null, classes);
    }

    public SimpleMapDatastore(ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource, ApplicationEventPublisher eventPublisher, Class... classes) {
        this(new SingletonConnectionSources(defaultConnectionSource, DatastoreUtils.createPropertyResolver(null)), createMappingContext(classes), eventPublisher);
    }

    public SimpleMapDatastore(Class... classes) {
        this(DatastoreUtils.createPropertyResolver(null), classes);
    }

    public SimpleMapDatastore(List<String> connectionNames, Class... classes) {
        this(createConnectionSources(connectionNames, DatastoreUtils.createPropertyResolver(null)), createMappingContext(classes), null);
    }

    public SimpleMapDatastore(Package... packages) {
        this(DatastoreUtils.createPropertyResolver(null), packages);
    }

    public SimpleMapDatastore(PropertyResolver configuration, Package... packages) {
        this(configuration, null, packages);
    }

    public SimpleMapDatastore(PropertyResolver configuration, ApplicationEventPublisher eventPublisher, Package... packages) {
        this(createConnectionSources(configuration), createMappingContext(packages), eventPublisher);
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Package... packages) {
        this(DatastoreUtils.createPropertyResolver(configuration), (ApplicationEventPublisher)null, packages);
    }

    public SimpleMapDatastore(ApplicationContext ctx, Class... classes) {
        this(DatastoreUtils.createPropertyResolver(null), ctx, classes);
    }

    private static ConnectionSources createConnectionSources(List<String> connectionNames, PropertyResolver configuration) {
        ConnectionSourceFactory factory = new SimpleMapConnectionSourceFactory();
        ConnectionSource defaultConnectionSource = factory.create(ConnectionSource.DEFAULT, configuration);
        InMemoryConnectionSources connectionSources = new InMemoryConnectionSources(defaultConnectionSource, factory, configuration);
        for (String name : connectionNames) {
            if (!ConnectionSource.DEFAULT.equals(name)) {
                connectionSources.addConnectionSource(name, Collections.emptyMap());
            }
        }
        return connectionSources;
    }

    private static ConnectionSources createConnectionSources(PropertyResolver configuration) {
        ConnectionSourceFactory factory = new SimpleMapConnectionSourceFactory();
        ConnectionSource defaultConnectionSource = factory.create(ConnectionSource.DEFAULT, configuration);
        return new InMemoryConnectionSources(defaultConnectionSource, factory, configuration);
    }

    private static MappingContext createMappingContext(Class... classes) {
        KeyValueMappingContext context = new KeyValueMappingContext("");
        for (Class cls : classes) {
            context.addPersistentEntity(cls);
        }
        return context;
    }

    private static MappingContext createMappingContext(Package... packages) {
        KeyValueMappingContext context = new KeyValueMappingContext("");
        // Package scanning not implemented here, but needed by some tests
        return context;
    }

    public long nextId() {
        return sharedState.lastKey.incrementAndGet();
    }

    @Override
    protected Session createSession(PropertyResolver settings) {
        return new SimpleMapSession(this, mappingContext, eventPublisher);
    }

    public Map<Serializable, Map> getBackingMap() {
        return sharedState.inmemoryData;
    }

    public Map<String, Map> getIndices() {
        return sharedState.indices;
    }

    public void clearData() {
        sharedState.inmemoryData.clear();
        sharedState.indices.clear();
        sharedState.lastKey.set(0);
    }

    @Override
    public void close() throws IOException {
        clearData();
        connectionSources.close();
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public ConnectionSources<Map<String, Map>, ConnectionSourceSettings> getConnectionSources() {
        return connectionSources;
    }

    @Override
    public Datastore getDatastoreForConnection(final String connectionName) {
        if (this.connectionName.equals(connectionName)) {
            return this;
        }
        if (!ConnectionSource.DEFAULT.equals(this.connectionName)) {
            // Child datastores should delegate to root if needed
            return GormRegistry.getInstance().getDatastore(null, connectionName);
        }
        SimpleMapDatastore childDatastore = datastoresByConnectionSource.get(connectionName);
        if (childDatastore == null) {
            final ConnectionSource<Map<String, Map>, ConnectionSourceSettings> connectionSource = connectionSources.getConnectionSource(connectionName);
            if (connectionSource == null) {
                connectionSources.addConnectionSource(connectionName, Collections.<String, Object>emptyMap());
                return getDatastoreForConnection(connectionName);
            }
            final ConnectionSources childSources = new InMemoryConnectionSources<Map<String, Map>, ConnectionSourceSettings>(connectionSource, new SimpleMapConnectionSourceFactory(), connectionSources.getBaseConfiguration()) {
                @Override
                public ConnectionSource<Map<String, Map>, ConnectionSourceSettings> getDefaultConnectionSource() {
                    return connectionSource;
                }
            };
            childDatastore = new SimpleMapDatastore(childSources, mappingContext, eventPublisher);
            datastoresByConnectionSource.put(connectionName, childDatastore);
        }
        return childDatastore;
    }

    public void addTenantForSchema(String schemaName) {
        if (!ConnectionSource.DEFAULT.equals(schemaName)) {
            this.connectionSources.addConnectionSource(schemaName, Collections.<String, Object>emptyMap());
            getDatastoreForConnection(schemaName);
        }
    }

    @Override
    public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return multiTenancyMode;
    }

    @Override
    public Datastore getDatastoreForTenantId(Serializable tenantId) {
        return getDatastoreForConnection(tenantId.toString());
    }

    public String getConnectionName() {
        return connectionName;
    }

    @Override
    public <T1> T1 withNewSession(Serializable tenantId, final Closure<T1> callable) {
        return grails.gorm.multitenancy.Tenants.withTenant(tenantId, new Closure<T1>(this) {
            public T1 doCall(Object... args) {
                final Datastore tenantDatastore = getDatastoreForTenantId(tenantId);
                final Datastore previous = GormEnhancer.getPreferredDatastore();
                GormEnhancer.setPreferredDatastore(tenantDatastore);
                try {
                    return DatastoreUtils.execute(tenantDatastore, new SessionCallback<T1>() {
                        @Override
                        public T1 doInSession(Session session) {
                            return (T1) callable.call(session);
                        }
                    });
                } finally {
                    if (previous != null) {
                        GormEnhancer.setPreferredDatastore(previous);
                    } else {
                        GormEnhancer.clearPreferredDatastore();
                    }
                }
            }
        });
    }

    @Override
    public TenantResolver getTenantResolver() {
        return tenantResolver;
    }
    
    public <T> T execute(SessionCallback<T> callback) {
        return DatastoreUtils.execute(this, callback);
    }

    public void execute(VoidSessionCallback callback) {
        DatastoreUtils.execute(this, callback);
    }
}

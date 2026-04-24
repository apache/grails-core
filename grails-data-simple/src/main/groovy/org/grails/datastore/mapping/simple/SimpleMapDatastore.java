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
package org.grails.datastore.mapping.simple;

import groovy.lang.Closure;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormRegistry;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.simple.connections.SimpleMapConnectionSourceFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.PropertyResolver;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.config.Settings;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.core.Datastore} interface that backs onto an in-memory map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SimpleMapDatastore extends AbstractDatastore implements MultiTenantCapableDatastore, TransactionCapableDatastore, MultipleConnectionSourceCapableDatastore, Closeable {

    protected Map<String, Map> inmemoryData;
    protected Map indices = new ConcurrentHashMap();
    protected final ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources;
    protected final Map<String, SimpleMapDatastore> datastoresByConnectionSource = new ConcurrentHashMap<>();
    protected final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final TenantResolver tenantResolver;
    protected final PlatformTransactionManager transactionManager;
    protected final GormEnhancer gormEnhancer;
    protected final ApplicationEventPublisher eventPublisher;
    protected final boolean failOnError;

    public SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
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
        this.eventPublisher = eventPublisher;
        this.gormEnhancer = initialize(defaultConnectionSource.getSettings());

        if (ConnectionSource.DEFAULT.equals(defaultConnectionSource.getName())) {
            GormEnhancer.setPreferredDatastore(this);
            Iterable<ConnectionSource<Map<String, Map>, ConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (ConnectionSource<Map<String, Map>, ConnectionSourceSettings> connectionSource : allConnectionSources) {
                String name = connectionSource.getName();
                if (!ConnectionSource.DEFAULT.equals(name)) {
                    getDatastoreForConnection(name);
                }
            }
        }
    }

    private SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher, Map<String, Map> sharedBackingMap, Map sharedIndices) {
        super(mappingContext);
        this.connectionSources = connectionSources;
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        
        this.inmemoryData = sharedBackingMap;
        this.indices = sharedIndices;
        DatastoreTransactionManager dtm = new DatastoreTransactionManager();
        dtm.setDatastore(this);
        this.transactionManager = dtm;
        MultiTenancySettings multiTenancy = defaultConnectionSource.getSettings().getMultiTenancy();
        this.multiTenancyMode = multiTenancy.getMode();
        this.tenantResolver = multiTenancy.getTenantResolver();
        PropertyResolver config = connectionSources.getBaseConfiguration();
        this.failOnError = config.getProperty(Settings.SETTING_FAIL_ON_ERROR, Boolean.class, false);
        this.eventPublisher = eventPublisher;
        this.gormEnhancer = null; 
        GormRegistry.getInstance().registerDatastore(defaultConnectionSource.getName(), this);
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            GormRegistry.getInstance().registerEntityDatastore(persistentEntity.getName(), defaultConnectionSource.getName(), this);
        }
    }

    public SimpleMapDatastore(MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        this(new SimpleMapConnectionSourceFactory().create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(null)), mappingContext, eventPublisher);
    }

    public SimpleMapDatastore(Map<String, Object> connectionDetails, MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        this(new SimpleMapConnectionSourceFactory().create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(connectionDetails)), mappingContext, eventPublisher);
    }

    public SimpleMapDatastore(ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource, MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        this(new SingletonConnectionSources<Map<String, Map>, ConnectionSourceSettings>(defaultConnectionSource, DatastoreUtils.createPropertyResolver(null)), mappingContext, eventPublisher);
    }

    public SimpleMapDatastore(PropertyResolver configuration, ApplicationEventPublisher eventPublisher, Class... classes) {
        this(new SimpleMapConnectionSourceFactory().create(ConnectionSource.DEFAULT, configuration), createMappingContext(classes), eventPublisher);
    }

    public SimpleMapDatastore(PropertyResolver configuration, Class... classes) {
        this(configuration, new GenericApplicationContext(), classes);
    }

    public SimpleMapDatastore(ApplicationContext ctx, Class... classes) {
        this(DatastoreUtils.createPropertyResolver(null), ctx, classes);
    }

    public SimpleMapDatastore(Class... classes) {
        this(DatastoreUtils.createPropertyResolver(null), classes);
    }

    public SimpleMapDatastore(final Iterable<String> dataSourceNames, Class... classes) {
        this(dataSourceNames, DatastoreUtils.createPropertyResolver(null), classes);
    }

    public SimpleMapDatastore(final Iterable<String> dataSourceNames, PropertyResolver propertyResolver, Class... classes) {
        this(createMultipleDataSources(dataSourceNames, propertyResolver), createMappingContext(classes), new GenericApplicationContext());
    }

    public SimpleMapDatastore(PropertyResolver propertyResolver, List<String> dataSourceNames, Class... classes) {
        this(dataSourceNames, propertyResolver, classes);
    }

    private static MappingContext createMappingContext(Class... classes) {
        KeyValueMappingContext context = new KeyValueMappingContext("");
        if (classes != null) {
            context.addPersistentEntities(classes);
        }
        return context;
    }

    protected static ConnectionSources<Map<String, Map>, ConnectionSourceSettings> createMultipleDataSources(final Iterable<String> dataSourceNames, PropertyResolver propertyResolver) {
        final SimpleMapConnectionSourceFactory simpleMapConnectionSourceFactory = new SimpleMapConnectionSourceFactory();
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
        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            public void persistentEntityAdded(PersistentEntity entity) {
                if (gormEnhancer != null) {
                    gormEnhancer.registerEntity(entity);
                }
            }
        });
        
        // Register AutoTimestampEventListener
        AutoTimestampEventListener autoTimestampEventListener = new AutoTimestampEventListener(this);
        if (eventPublisher instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext)eventPublisher).addApplicationListener(autoTimestampEventListener);
        }
        
        return new GormEnhancer(this, transactionManager, settings);
    }

    @Override
    protected Session createSession(PropertyResolver settings) {
        return new SimpleMapSession(this, mappingContext, eventPublisher);
    }

    public Map<String, Map> getBackingMap() {
        return inmemoryData;
    }

    public Map getIndices() {
        return indices;
    }

    public void clearData() {
        if (inmemoryData != null) inmemoryData.clear();
        if (indices != null) indices.clear();
        for (SimpleMapDatastore datastore : datastoresByConnectionSource.values()) {
            if (datastore != this) {
                datastore.clearData();
            }
        }
    }

    @Override
    public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return multiTenancyMode;
    }

    @Override
    public TenantResolver getTenantResolver() {
        return tenantResolver;
    }

    public Datastore getDatastoreForTenantId(Serializable tenantId) {
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            return this;
        }
        if (tenantId != null) {
            return getDatastoreForConnection(tenantId.toString());
        }
        return this;
    }

    @Override
    public Object withNewSession(Serializable tenantId, Closure callable) {
        Datastore tenantDatastore = getDatastoreForTenantId(tenantId);
        return DatastoreUtils.execute(tenantDatastore, (Session session) -> {
            return callable.call(session);
        });
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
    public void close() throws IOException {
        if (gormEnhancer != null) {
            gormEnhancer.close();
        }
        GormRegistry.getInstance().removeDatastore(this);
    }

    public Datastore getDatastoreForConnection(String connectionName) {
        if (ConnectionSource.DEFAULT.equals(connectionName)) {
            return this;
        }
        SimpleMapDatastore childDatastore = datastoresByConnectionSource.get(connectionName);
        if (childDatastore == null) {
            ConnectionSource<Map<String, Map>, ConnectionSourceSettings> connectionSource = connectionSources.getConnectionSource(connectionName);
            if (connectionSource == null) {
                connectionSource = connectionSources.addConnectionSource(connectionName, Collections.<String, Object>emptyMap());
            }
            SingletonConnectionSources singletonConnectionSources = new SingletonConnectionSources(connectionSource, connectionSources.getBaseConfiguration());
            childDatastore = new SimpleMapDatastore(singletonConnectionSources, mappingContext, eventPublisher, inmemoryData, indices);
            datastoresByConnectionSource.put(connectionName, childDatastore);
            GormRegistry registry = GormRegistry.getInstance();
            registry.registerDatastore(connectionName, childDatastore);
            for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
                registry.registerEntityDatastore(persistentEntity.getName(), connectionName, childDatastore);
            }
        }
        return childDatastore;
    }

    public void addTenantForSchema(String schemaName) {
        this.connectionSources.addConnectionSource(schemaName, Collections.<String, Object>emptyMap());
        getDatastoreForConnection(schemaName);
    }
}

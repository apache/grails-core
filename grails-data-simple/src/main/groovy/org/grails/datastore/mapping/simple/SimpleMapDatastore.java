/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the \"License\");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an \"AS IS\" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.simple;

import groovy.lang.Closure;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormRegistry;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DomainEventListener;
import org.grails.datastore.gorm.multitenancy.MultiTenantEventListener;
import org.grails.datastore.mapping.core.*;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.simple.connections.SimpleMapConnectionSourceFactory;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.config.Settings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.core.Datastore} interface that backs onto a Map and is designed for simple in-memory tests.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SimpleMapDatastore extends AbstractDatastore implements MultiTenantCapableDatastore, TransactionCapableDatastore, MultipleConnectionSourceCapableDatastore {
    private static final Map<MappingContext, SharedState> stateCache = new ConcurrentHashMap<>();

    public static class SharedState {
        public final Map<Serializable, Map> inmemoryData = new ConcurrentHashMap<>();
        public final Map<String, Map> indices = new ConcurrentHashMap<>();
        public final AtomicLong lastKey = new AtomicLong();
    }

    protected final DatastoreTransactionManager transactionManager;
    protected final String connectionName;
    protected final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final TenantResolver tenantResolver;
    protected final boolean failOnError;
    protected final GormEnhancer gormEnhancer;
    protected final ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources;
    protected final Map<String, SimpleMapDatastore> datastoresByConnectionSource = new ConcurrentHashMap<>();
    protected final DomainEventListener domainEventListener;
    protected final AutoTimestampEventListener autoTimestampEventListener;
    
    private final SharedState sharedState;

    public SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, KeyValueMappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        this(connectionSources, (MappingContext)mappingContext, eventPublisher, null);
    }

    public SimpleMapDatastore(ConnectionSources connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher, SharedState state) {
        this(connectionSources, mappingContext, eventPublisher, state, ConnectionSource.DEFAULT,
             ((ConnectionSource<Map<String, Map>, ConnectionSourceSettings>)connectionSources.getDefaultConnectionSource()).getSettings().getMultiTenancy().getMode(),
             ((ConnectionSource<Map<String, Map>, ConnectionSourceSettings>)connectionSources.getDefaultConnectionSource()).getSettings().getMultiTenancy().getTenantResolver());
    }

    protected SimpleMapDatastore(ConnectionSources connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher, SharedState state, String connectionName, MultiTenancySettings.MultiTenancyMode multiTenancyMode, TenantResolver tenantResolver) {
        super(mappingContext, connectionSources.getBaseConfiguration(), eventPublisher instanceof ConfigurableApplicationContext ? (ConfigurableApplicationContext) eventPublisher : null);
        this.multiTenancyMode = multiTenancyMode;
        mappingContext.setMultiTenancyMode(this.multiTenancyMode);
        this.connectionSources = (ConnectionSources<Map<String, Map>, ConnectionSourceSettings>) connectionSources;
        if (eventPublisher != null) {
            this.applicationEventPublisher = eventPublisher;
        }

        if (state == null) {
            state = stateCache.get(mappingContext);
            if (state == null) {
                state = new SharedState();
                stateCache.put(mappingContext, state);
            }
        }
        this.sharedState = state;
        this.connectionName = connectionName;
        this.tenantResolver = tenantResolver;
        mappingContext.initialize(this.connectionSources.getDefaultConnectionSource().getSettings());

        DatastoreTransactionManager dtm = new DatastoreTransactionManager();
        dtm.setDatastore(this);
        this.transactionManager = dtm;
        
        PropertyResolver config = connectionSources.getBaseConfiguration();
        this.failOnError = config != null ? config.getProperty(Settings.SETTING_FAIL_ON_ERROR, Boolean.class, false) : false;
        
        if (this.applicationEventPublisher instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext cac = (ConfigurableApplicationContext) this.applicationEventPublisher;
            if (!cac.isActive()) {
                cac.refresh();
            }
        }
        
        if (ConnectionSource.DEFAULT.equals(this.connectionName)) {
            ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = this.connectionSources.getDefaultConnectionSource();
            this.gormEnhancer = new GormEnhancer(this, transactionManager, defaultConnectionSource.getSettings());
        } else {
            this.gormEnhancer = null;
        }

        GormRegistry.getInstance().registerDatastore(this.connectionName, this);
        mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            public void persistentEntityAdded(PersistentEntity entity) {
                if (gormEnhancer != null) {
                    gormEnhancer.registerEntity(entity);
                }
                GormRegistry.getInstance().registerEntityDatastore(entity.getName(), SimpleMapDatastore.this.connectionName, SimpleMapDatastore.this);
            }
        });
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            if (gormEnhancer != null) {
                gormEnhancer.registerEntity(persistentEntity);
            }
            GormRegistry.getInstance().registerEntityDatastore(persistentEntity.getName(), this.connectionName, this);
        }

        this.domainEventListener = new DomainEventListener(this);
        this.autoTimestampEventListener = new AutoTimestampEventListener(this);
        ApplicationEventPublisher ep = getApplicationEventPublisher();
        if (ep instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext cac = (ConfigurableApplicationContext) ep;
            cac.addApplicationListener(domainEventListener);
            cac.addApplicationListener(autoTimestampEventListener);
            if (this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
                cac.addApplicationListener(new MultiTenantEventListener(this));
            }
        } else if (ep instanceof ConfigurableApplicationEventPublisher) {
            ConfigurableApplicationEventPublisher caep = (ConfigurableApplicationEventPublisher) ep;
            caep.addApplicationListener(domainEventListener);
            caep.addApplicationListener(autoTimestampEventListener);
            if (this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
                caep.addApplicationListener(new MultiTenantEventListener(this));
            }
        } else if (this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            this.addApplicationListener(new MultiTenantEventListener(this));
        }

        if (ConnectionSource.DEFAULT.equals(this.connectionName)) {
            GormEnhancer.setPreferredDatastore(this);
            Iterable<ConnectionSource<Map<String, Map>, ConnectionSourceSettings>> allConnectionSources = this.connectionSources.getAllConnectionSources();
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
                GormRegistry.getInstance().registerEntityDatastore(entity.getName(), SimpleMapDatastore.this.connectionName, SimpleMapDatastore.this);
            }
        });
    }

    public SimpleMapDatastore(ApplicationContext applicationContext, Class... classes) {
        this(createConnectionSources(applicationContext.getEnvironment()), (KeyValueMappingContext)createMappingContext(applicationContext.getEnvironment(), classes), applicationContext);
    }

    public SimpleMapDatastore(ApplicationContext applicationContext) {
        this(createConnectionSources(applicationContext.getEnvironment()), (KeyValueMappingContext)createMappingContext(applicationContext.getEnvironment(), new Class[0]), applicationContext);
    }

    public SimpleMapDatastore(ApplicationEventPublisher eventPublisher) {
        this(createConnectionSources(new StandardEnvironment()), new KeyValueMappingContext(""), eventPublisher);
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Class... classes) {
        this(ConnectionSourcesInitializer.create(new SimpleMapConnectionSourceFactory(), DatastoreUtils.createPropertyResolver(configuration)), (KeyValueMappingContext)createMappingContext(DatastoreUtils.createPropertyResolver(configuration), classes), null);
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Package... packages) {
        this(ConnectionSourcesInitializer.create(new SimpleMapConnectionSourceFactory(), DatastoreUtils.createPropertyResolver(configuration)), (KeyValueMappingContext)createMappingContext(DatastoreUtils.createPropertyResolver(configuration), packages), null);
    }

    public SimpleMapDatastore(Class... classes) {
        this(Collections.<String, Object>emptyMap(), classes);
    }

    public SimpleMapDatastore(PropertyResolver configuration, Class... classes) {
        this(createConnectionSources(configuration), (KeyValueMappingContext)createMappingContext(configuration, classes), null);
    }

    private static ConnectionSources<Map<String, Map>, ConnectionSourceSettings> createConnectionSources(PropertyResolver configuration, String... connectionNames) {
        ConnectionSourceFactory<Map<String, Map>, ConnectionSourceSettings> factory = new SimpleMapConnectionSourceFactory();
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = factory.create(ConnectionSource.DEFAULT, configuration);
        InMemoryConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources = new InMemoryConnectionSources<>(defaultConnectionSource, factory, configuration);
        for (String name : connectionNames) {
            if (!ConnectionSource.DEFAULT.equals(name)) {
                connectionSources.addConnectionSource(name, Collections.<String, Object>emptyMap());
            }
        }
        return connectionSources;
    }

    private static ConnectionSources<Map<String, Map>, ConnectionSourceSettings> createConnectionSources(PropertyResolver configuration) {
        ConnectionSourceFactory<Map<String, Map>, ConnectionSourceSettings> factory = new SimpleMapConnectionSourceFactory();
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = factory.create(ConnectionSource.DEFAULT, configuration);
        return new InMemoryConnectionSources<>(defaultConnectionSource, factory, configuration);
    }

    private static MappingContext createMappingContext(PropertyResolver configuration, Class... classes) {
        ConnectionSourceSettings settings = configuration != null ? new SimpleMapConnectionSourceFactory().createSettings(configuration) : new ConnectionSourceSettings();
        return createMappingContext(settings, classes);
    }

    private static MappingContext createMappingContext(ConnectionSourceSettings settings, Class... classes) {
        KeyValueMappingContext context = new KeyValueMappingContext("");
        context.initialize(settings);
        if (classes != null) {
            for (Class cls : classes) {
                context.addPersistentEntity(cls);
            }
        }
        return context;
    }

    private static MappingContext createMappingContext(PropertyResolver configuration, Package... packages) {
        KeyValueMappingContext context = new KeyValueMappingContext("");
        ConnectionSourceSettings settings = configuration != null ? new SimpleMapConnectionSourceFactory().createSettings(configuration) : new ConnectionSourceSettings();
        context.initialize(settings);
        return context;
    }

    public long nextId() {
        return sharedState.lastKey.incrementAndGet();
    }

    @Override
    protected Session createSession(PropertyResolver settings) {
        return new SimpleMapSession(this, mappingContext, getApplicationEventPublisher());
    }

    public Map<Serializable, Map> getBackingMap() {
        return getBackingMap(connectionName);
    }

    public Map<Serializable, Map> getBackingMap(String connectionName) {
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR || ConnectionSource.DEFAULT.equals(connectionName)) {
            return sharedState.inmemoryData;
        }
        return new ScopedMap<>(sharedState.inmemoryData, connectionName);
    }

    public Map<String, Map> getIndices() {
        return getIndices(connectionName);
    }

    public Map<String, Map> getIndices(String connectionName) {
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR || ConnectionSource.DEFAULT.equals(connectionName)) {
            return sharedState.indices;
        }
        return new ScopedMap<>(sharedState.indices, connectionName);
    }

    protected Datastore createDatastore(String connectionName, PropertyResolver configuration) {
        SimpleMapDatastore child = new SimpleMapDatastore(connectionSources, mappingContext, applicationEventPublisher, sharedState, connectionName, multiTenancyMode, tenantResolver);
        if (this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            ApplicationEventPublisher childEp = child.getApplicationEventPublisher();
            if (childEp instanceof ConfigurableApplicationEventPublisher) {
                ((ConfigurableApplicationEventPublisher) childEp).addApplicationListener(new MultiTenantEventListener(child));
            }
        }
        return child;
    }

    private static class ScopedMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> target;
        private final String prefix;

        ScopedMap(Map<K, V> target, String prefix) {
            this.target = target;
            this.prefix = prefix + ":";
        }

        @Override
        public V get(Object key) {
            return target.get(prefix + key);
        }

        @Override
        public V put(K key, V value) {
            return target.put((K) (prefix + key), value);
        }

        @Override
        public V remove(Object key) {
            return target.remove(prefix + key);
        }

        @Override
        public boolean containsKey(Object key) {
            return target.containsKey(prefix + key);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            Set<Entry<K, V>> entries = new LinkedHashSet<>();
            for (Entry<K, V> entry : target.entrySet()) {
                String key = entry.getKey().toString();
                if (key.startsWith(prefix)) {
                    entries.add(new SimpleEntry<>((K) key.substring(prefix.length()), entry.getValue()));
                }
            }
            return entries;
        }
    }

    public void clearData() {
        sharedState.inmemoryData.clear();
        sharedState.indices.clear();
        sharedState.lastKey.set(0);
    }

    public void close() throws IOException {
        clearData();
        connectionSources.close();
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public ConnectionSources<Map<String, Map>, ConnectionSourceSettings> getConnectionSources() {
        return connectionSources;
    }

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
            childDatastore = new SimpleMapDatastore(childSources, mappingContext, getApplicationEventPublisher(), sharedState, connectionName, this.multiTenancyMode, this.tenantResolver);
            if (this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
                childDatastore.addApplicationListener(new MultiTenantEventListener(childDatastore));
            }
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
        if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            return this;
        }
        return getDatastoreForConnection(tenantId.toString());
    }

    public String getConnectionName() {
        return connectionName;
    }

    @Override
    public Object withNewSession(Serializable tenantId, Closure callable) {
        return grails.gorm.multitenancy.Tenants.withTenant(tenantId, new Closure<Object>(this) {
            @Override
            public Object call(Object... args) {
                final Datastore tenantDatastore = getDatastoreForTenantId(tenantId);
                final Datastore previous = GormEnhancer.getPreferredDatastore();
                GormEnhancer.setPreferredDatastore(tenantDatastore);
                try {
                    return DatastoreUtils.execute(tenantDatastore, new SessionCallback<Object>() {
                        @Override
                        public Object doInSession(Session session) {
                            return callable.call(session);
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

    public DomainEventListener getDomainEventListener() {
        return domainEventListener;
    }

    public AutoTimestampEventListener getAutoTimestampEventListener() {
        return autoTimestampEventListener;
    }
    
    public <T> T execute(SessionCallback<T> callback) {
        return DatastoreUtils.execute(this, callback);
    }

    public void execute(VoidSessionCallback callback) {
        DatastoreUtils.execute(this, callback);
    }
}

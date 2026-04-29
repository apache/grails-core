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
package org.grails.datastore.mapping.simple;

import groovy.lang.Closure;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormRegistry;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.DomainEventListener;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.multitenancy.resolvers.NoTenantResolver;
import org.grails.datastore.mapping.simple.connections.SimpleMapConnectionSourceFactory;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.core.Datastore} interface that backs onto a Map
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SimpleMapDatastore extends AbstractDatastore implements TransactionCapableDatastore, MultiTenantCapableDatastore<Map<String, Map>, ConnectionSourceSettings>, Closeable {

    protected static final Map<MappingContext, SharedState> stateCache = new ConcurrentHashMap<>();

    public static class SharedState {
        public final Map<Serializable, Map> inmemoryData = new ConcurrentHashMap<>();
        public final Map<String, List> indices = new ConcurrentHashMap<>();
        public final Map<String, AtomicLong> lastKeys = new ConcurrentHashMap<>();
    }

    private SharedState state;
    protected final ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources;
    protected final DatastoreTransactionManager transactionManager;
    protected final String connectionName;
    protected final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final TenantResolver tenantResolver;
    protected final Map<String, SimpleMapDatastore> childDatastores = new ConcurrentHashMap<>();

    public SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        this(connectionSources, (KeyValueMappingContext)mappingContext, eventPublisher, null);
    }

    public SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, KeyValueMappingContext mappingContext, ApplicationEventPublisher eventPublisher, SharedState state) {
        this(connectionSources, mappingContext, eventPublisher, state, ConnectionSource.DEFAULT,
             ((ConnectionSource<Map<String, Map>, ConnectionSourceSettings>)connectionSources.getDefaultConnectionSource()).getSettings().getMultiTenancy().getMode(),
             ((ConnectionSource<Map<String, Map>, ConnectionSourceSettings>)connectionSources.getDefaultConnectionSource()).getSettings().getMultiTenancy().getTenantResolver());
    }

    protected SimpleMapDatastore(ConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources, MappingContext mappingContext, ApplicationEventPublisher eventPublisher, SharedState state, String connectionName, MultiTenancySettings.MultiTenancyMode multiTenancyMode, TenantResolver tenantResolver) {
        super(mappingContext, connectionSources.getBaseConfiguration(), (eventPublisher instanceof ConfigurableApplicationContext ? (ConfigurableApplicationContext) eventPublisher : null));
        if (eventPublisher != null) {
            this.applicationEventPublisher = eventPublisher;
        }
        this.connectionSources = connectionSources;
        this.connectionName = connectionName;
        this.multiTenancyMode = multiTenancyMode != null ? multiTenancyMode : MultiTenancySettings.MultiTenancyMode.NONE;
        this.tenantResolver = tenantResolver != null ? tenantResolver : new NoTenantResolver();
        this.state = state;
        this.transactionManager = new DatastoreTransactionManager();
        this.transactionManager.setDatastore(this);

        if (this.state == null) {
            this.state = stateCache.get(mappingContext);
            if (this.state == null) {
                this.state = new SharedState();
                stateCache.put(mappingContext, this.state);
            }
        }

        if (!(mappingContext instanceof KeyValueMappingContext)) {
            throw new IllegalArgumentException("MappingContext must be an instance of KeyValueMappingContext");
        }

        GormRegistry.getInstance().registerDatastore(this.connectionName, this);
        new GormEnhancer(this, this.transactionManager);
        addApplicationListener(new DomainEventListener(this));
        addApplicationListener(new AutoTimestampEventListener(this));

        if (ConnectionSource.DEFAULT.equals(this.connectionName)) {
            for (ConnectionSource<Map<String, Map>, ConnectionSourceSettings> connectionSource : connectionSources.getAllConnectionSources()) {
                String name = connectionSource.getName();
                if (!this.connectionName.equals(name)) {
                    getDatastoreForConnection(name);
                }
            }
        }

        if (this.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            ApplicationEventPublisher publisher = getApplicationEventPublisher();
            if (publisher instanceof ConfigurableApplicationContext) {
                ((ConfigurableApplicationContext) publisher).addApplicationListener(new org.grails.datastore.gorm.multitenancy.MultiTenantEventListener(this));
            } else {
                try {
                    Method addApplicationListener = publisher.getClass().getMethod("addApplicationListener", ApplicationListener.class);
                    addApplicationListener.setAccessible(true);
                    addApplicationListener.invoke(publisher, new org.grails.datastore.gorm.multitenancy.MultiTenantEventListener(this));
                } catch (Exception e) {
                    // fallback to just creating the listener, it might register itself in some other way or via the constructor
                    new org.grails.datastore.gorm.multitenancy.MultiTenantEventListener(this);
                }
            }
        }
    }

    public SimpleMapDatastore(ApplicationEventPublisher ctx) {
        this(new StandardEnvironment(), ctx);
    }

    public SimpleMapDatastore(PropertyResolver configuration, ApplicationEventPublisher ctx) {
        this(createConnectionSources(configuration), (KeyValueMappingContext)createMappingContext(configuration, new Class[0]), ctx, null);
    }

    public SimpleMapDatastore(PropertyResolver configuration, Class... classes) {
        this(configuration, Arrays.asList(classes));
    }

    public SimpleMapDatastore(Class... classes) {
        this(new StandardEnvironment(), Arrays.asList(classes));
    }

    public SimpleMapDatastore(PropertyResolver configuration, Collection classes) {
        this(configuration, classes, new Class[0]);
    }

    public SimpleMapDatastore(PropertyResolver configuration, Collection classes, Class... moreClasses) {
        this(createConnectionSourcesFromCollection(configuration, classes), (KeyValueMappingContext)createMappingContext(configuration, combine(classes, moreClasses)), null, null);
    }

    public SimpleMapDatastore(Collection classes, Class... moreClasses) {
        this(new StandardEnvironment(), classes, moreClasses);
    }

    private static ConnectionSources<Map<String, Map>, ConnectionSourceSettings> createConnectionSourcesFromCollection(PropertyResolver configuration, Collection collection) {
        List<String> names = new ArrayList<>();
        if (collection != null) {
            for (Object o : collection) {
                if (o instanceof CharSequence) {
                    names.add(o.toString());
                }
            }
        }
        return createConnectionSources(configuration, names.toArray(new String[0]));
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Class... classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), classes);
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Collection<Class> classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), classes, new Class[0]);
    }

    public SimpleMapDatastore(Map<String, Object> configuration, Package pkg) {
        this(DatastoreUtils.createPropertyResolver(configuration), new Class[0]);
        // Note: Package scanning not implemented here, but constructor needed for compatibility
    }

    private static Class[] combine(Collection classes, Class... moreClasses) {
        List<Class> all = new ArrayList<>();
        if (classes != null) {
            for (Object o : classes) {
                if (o instanceof Class) {
                    all.add((Class) o);
                }
            }
        }
        if (moreClasses != null) {
            for (Class c : moreClasses) {
                if (c != null) {
                    all.add(c);
                }
            }
        }
        return all.toArray(new Class[0]);
    }

    private static ConnectionSources<Map<String, Map>, ConnectionSourceSettings> createConnectionSources(PropertyResolver configuration, String... connectionNames) {
        ConnectionSourceFactory<Map<String, Map>, ConnectionSourceSettings> factory = new SimpleMapConnectionSourceFactory();
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> defaultConnectionSource = factory.create(ConnectionSource.DEFAULT, configuration);
        InMemoryConnectionSources<Map<String, Map>, ConnectionSourceSettings> connectionSources = new InMemoryConnectionSources<>(defaultConnectionSource, factory, configuration);
        for (String name : connectionNames) {
            connectionSources.addConnectionSource(name, configuration);
        }
        return connectionSources;
    }

    private static MappingContext createMappingContext(PropertyResolver configuration, Class... classes) {
        ConnectionSourceSettings settings = configuration != null ? new SimpleMapConnectionSourceFactory().createSettings(configuration) : new ConnectionSourceSettings();
        return createMappingContext(settings, classes);
    }

    private static KeyValueMappingContext createMappingContext(ConnectionSourceSettings settings, Class... classes) {
        KeyValueMappingContext context = new KeyValueMappingContext("");
        context.initialize(settings);
        if (classes != null) {
            for (Class cls : classes) {
                context.addPersistentEntity(cls);
            }
        }
        return context;
    }

    @Override
    public void close() throws IOException {
        for (SimpleMapDatastore child : childDatastores.values()) {
            child.close();
        }
        GormRegistry.getInstance().removeDatastore(this);
        connectionSources.close();
    }

    public SharedState getSharedState() {
        return state;
    }

    public void clearData() {
        state.inmemoryData.clear();
        state.indices.clear();
        state.lastKeys.clear();
    }

    @Override
    protected Session createSession(PropertyResolver connectionDetails) {
        SimpleMapSession session = new SimpleMapSession(this, getMappingContext(), getApplicationEventPublisher());
        System.err.println("SimpleMapDatastore.createSession: created session " + System.identityHashCode(session) + " for datastore " + System.identityHashCode(this));
        return session;
    }

    public Map<Serializable, Map> getBackingMap() {
        return getBackingMap(connectionName);
    }

    public Map<Serializable, Map> getBackingMap(String connectionName) {
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            return state.inmemoryData;
        }
        return new ScopedMap<>(state.inmemoryData, connectionName);
    }

    public Map<String, List> getIndices() {
        return getIndices(connectionName);
    }

    public Map<String, List> getIndices(String connectionName) {
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            return state.indices;
        }
        return new ScopedMap<>(state.indices, connectionName);
    }

    public long nextId(String family) {
        AtomicLong lastKey = state.lastKeys.get(family);
        if (lastKey == null) {
            lastKey = new AtomicLong(0);
            AtomicLong existing = state.lastKeys.putIfAbsent(family, lastKey);
            if (existing != null) {
                lastKey = existing;
            }
        }
        return lastKey.incrementAndGet();
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return multiTenancyMode;
    }

    @Override
    public TenantResolver getTenantResolver() {
        return tenantResolver;
    }

    @Override
    public Datastore getDatastoreForTenantId(Serializable tenantId) {
        return getDatastoreForConnection(tenantId.toString());
    }

    @Override
    public <T1> T1 withNewSession(Serializable tenantId, final Closure<T1> callable) {
        return org.grails.datastore.mapping.core.DatastoreUtils.execute(getDatastoreForTenantId(tenantId), new org.grails.datastore.mapping.core.SessionCallback<T1>() {
            @Override
            public T1 doInSession(Session s) {
                return callable.call(s);
            }
        });
    }

    @Override
    public ConnectionSources<Map<String, Map>, ConnectionSourceSettings> getConnectionSources() {
        return connectionSources;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void addTenantForSchema(String schemaName) {
        getDatastoreForConnection(schemaName);
    }

    public Datastore getDatastoreForConnection(String connectionName) {
        if (this.connectionName.equals(connectionName)) {
            return this;
        }
        SimpleMapDatastore child = childDatastores.get(connectionName);
        if (child == null) {
            child = new SimpleMapDatastore(connectionSources, (KeyValueMappingContext) mappingContext, getApplicationEventPublisher(), state, connectionName, multiTenancyMode, tenantResolver);
            childDatastores.put(connectionName, child);
        }
        return child;
    }

    private static class ScopedMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> proxy;
        private final String prefix;

        ScopedMap(Map<K, V> proxy, String prefix) {
            this.proxy = proxy;
            this.prefix = prefix + ":";
        }

        @Override
        public V get(Object key) {
            return proxy.get(prefix + key);
        }

        @Override
        public V put(K key, V value) {
            return proxy.put((K)(prefix + key), value);
        }

        @Override
        public V remove(Object key) {
            return proxy.remove(prefix + key);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            Set<Entry<K, V>> entries = new HashSet<>();
            for (Entry<K, V> entry : proxy.entrySet()) {
                if (entry.getKey().toString().startsWith(prefix)) {
                    entries.add(new SimpleEntry<>((K)entry.getKey().toString().substring(prefix.length()), entry.getValue()));
                }
            }
            return entries;
        }
    }
}

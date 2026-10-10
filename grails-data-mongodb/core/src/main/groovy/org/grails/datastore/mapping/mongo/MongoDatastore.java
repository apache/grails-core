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

package org.grails.datastore.mapping.mongo;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import groovy.lang.Closure;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.FlushModeType;

import com.mongodb.DuplicateKeyException;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.MongoNamespace;
import com.mongodb.MongoServerException;
import com.mongodb.MongoSocketException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.connection.ClusterType;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.transaction.PlatformTransactionManager;

import grails.gorm.multitenancy.Tenants;
import grails.util.GrailsMessageSourceUtils;
import org.grails.datastore.bson.codecs.CodecExtensions;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormInstanceApi;
import org.grails.datastore.gorm.GormValidationApi;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher;
import org.grails.datastore.gorm.events.DomainEventListener;
import org.grails.datastore.gorm.mongo.MongoGormEnhancer;
import org.grails.datastore.gorm.mongo.api.MongoStaticApi;
import org.grails.datastore.gorm.multitenancy.MultiTenantEventListener;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry;
import org.grails.datastore.gorm.validation.listener.ValidationEventListener;
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries;
import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.StatelessDatastore;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSourceFactory;
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettingsBuilder;
import org.grails.datastore.mapping.core.connections.ConnectionSources;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesListener;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource;
import org.grails.datastore.mapping.core.connections.InMemoryConnectionSources;
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore;
import org.grails.datastore.mapping.core.connections.SingletonConnectionSources;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.mongo.config.MongoAttribute;
import org.grails.datastore.mapping.mongo.config.MongoCollection;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.grails.datastore.mapping.mongo.config.MongoSettings;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSource;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettingsBuilder;
import org.grails.datastore.mapping.mongo.connections.RestartableMongoClient;
import org.grails.datastore.mapping.mongo.engine.codecs.PersistentEntityCodec;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.validation.ValidatorRegistry;

/**
 * A Datastore implementation for the Mongo document store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MongoDatastore extends AbstractDatastore implements MappingContext.Listener, Closeable, StatelessDatastore, MultipleConnectionSourceCapableDatastore, MultiTenantCapableDatastore<MongoClient, MongoConnectionSourceSettings>, TransactionCapableDatastore, SmartLifecycle {

    public static final String SETTING_DATABASE_NAME = MongoSettings.SETTING_DATABASE_NAME;
    public static final String SETTING_CONNECTION_STRING = MongoSettings.SETTING_CONNECTION_STRING;
    public static final String SETTING_URL = MongoSettings.SETTING_URL;
    public static final String SETTING_DEFAULT_MAPPING = MongoSettings.SETTING_DEFAULT_MAPPING;
    public static final String SETTING_OPTIONS = MongoSettings.SETTING_OPTIONS;
    public static final String SETTING_HOST = MongoSettings.SETTING_HOST;
    public static final String SETTING_PORT = MongoSettings.SETTING_PORT;
    public static final String SETTING_USERNAME = MongoSettings.SETTING_USERNAME;
    public static final String SETTING_PASSWORD = MongoSettings.SETTING_PASSWORD;
    public static final String SETTING_STATELESS = MongoSettings.SETTING_STATELESS;
    public static final String SETTING_ENGINE = MongoSettings.SETTING_ENGINE;
    public static final String INDEX_ATTRIBUTES = "indexAttributes";

    /**
     * TTL index attribute. MongoDB's {@link IndexOptions#expireAfter(Long, TimeUnit)} is the only
     * way to set a TTL, but it is a two-argument setter that {@link MongoConstants#mapToObject}
     * cannot reach (that helper only invokes single-argument setters). So {@code expireAfterSeconds}
     * is pulled out of the index attributes and applied explicitly. TTL indexes are single-field
     * only — MongoDB silently ignores the option on a compound index.
     */
    public static final String INDEX_EXPIRE_AFTER_SECONDS = "expireAfterSeconds";

    /**
     * Opt-in index attribute: when an index already exists on the same keys with conflicting
     * options that cannot be reconciled in place (i.e. anything other than a TTL change), or the
     * declared name is taken by an index on other keys, drop the existing index and recreate it with
     * the declared options instead of just logging the conflict.
     */
    public static final String INDEX_RECREATE_ON_CONFLICT = "recreateOnConflict";

    /** MongoDB server error code for {@code IndexOptionsConflict}. */
    private static final int INDEX_OPTIONS_CONFLICT_CODE = 85;

    /** MongoDB server error code for {@code IndexKeySpecsConflict}: the name is taken by an index on other keys. */
    private static final int INDEX_KEY_SPECS_CONFLICT_CODE = 86;

    /** MongoDB server error code for {@code IndexNotFound}. */
    private static final int INDEX_NOT_FOUND_CODE = 27;

    /** The name MongoDB gives the index every collection has on {@code _id}, which cannot be dropped. */
    private static final String ID_INDEX_NAME = "_id_";

    /**
     * The keys MongoDB lists a text index under in place of its text fields, which its {@code weights} name.
     */
    private static final String TEXT_INDEX_KEY = "_fts";
    private static final String TEXT_INDEX_TERM_KEY = "_ftsx";
    public static final String CODEC_ENGINE = MongoConstants.CODEC_ENGINE;

    private static final Logger LOG = LoggerFactory.getLogger(MongoDatastore.class);

    /**
     * Not final because {@link #start()} replaces it after a CRaC restore when it is not a
     * {@link RestartableMongoClient}, which is restarted in place instead. Everything other than
     * construction reaches it through {@link #getMongoClient()}, so a replacement is picked up
     * without anything else having to be told.
     */
    protected volatile MongoClient mongo;
    protected final String defaultDatabase;
    protected final Map<PersistentEntity, String> mongoCollections = new ConcurrentHashMap<>();
    protected final Map<PersistentEntity, String> mongoDatabases = new ConcurrentHashMap<>();
    protected final boolean stateless;
    protected final boolean codecEngine;
    protected final boolean transactionsEnabled;
    protected final boolean buildIndexes;
    protected final boolean buildIndexesAsync;

    /**
     * Runs the background index builds: every {@link #buildIndexAsync()}, and the startup build and each
     * {@link #buildIndex()} when {@code grails.mongodb.buildIndexesAsync} is enabled. A single thread,
     * so the indexes are still built one at a time per connection. The worker expires after one idle
     * second, releasing the worker while allowing subsequent builds.
     *
     * <p>Not final: a shut down executor cannot be restarted, so {@link #start()} replaces the one
     * {@link #stop()} shut down.
     */
    private volatile ExecutorService indexBuildExecutor;

    /**
     * Held for the whole of every build on this connection, on the background thread or a caller's, and while a
     * domain class registered after startup is indexed, so that no two apply declarations at the same time: two
     * {@code recreateOnConflict} drop-and-recreate sequences on one index would undo each other.
     */
    private final ReentrantLock indexBuildLock = new ReentrantLock();

    /**
     * Set when a build did not run to the end because the datastore was stopping, or was requested while it
     * was stopped, for {@link #start()} to run again. A build interrupted by {@link #stop()} sets it itself
     * as it exits, so a build that finished in the meantime is not run twice.
     */
    private volatile boolean indexBuildPending;

    /**
     * Set first thing in {@link #close()}. A connection added at runtime starts its index build only while
     * this is clear, and only after it is registered, so a close cannot miss a build that has started.
     */
    private volatile boolean closed;

    /**
     * How long {@link #start()} waits for a build that {@link #stop()} interrupted to finish exiting. One that is
     * going to exit does so within milliseconds of the interrupt.
     */
    private static final long INDEX_BUILD_EXIT_TIMEOUT_MILLIS = 1000;

    /** The summary for the current build, scoped to its thread so the protected index hook is preserved. */
    private final ThreadLocal<IndexBuildSummary> indexBuildSummary = new ThreadLocal<>();
    private volatile Boolean transactionsSupported;
    private volatile boolean warnedTransactionsUnsupported = false;
    // Volatile: an asynchronous index build reads it from its own thread while the @Autowired setters below
    // can still be replacing it.
    protected volatile CodecRegistry codecRegistry;
    protected final ConfigurableApplicationEventPublisher eventPublisher;
    protected final PlatformTransactionManager transactionManager;
    protected final GormEnhancer gormEnhancer;
    protected final ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources;
    protected final FlushModeType defaultFlushMode;
    /**
     * Concurrent because it is written by the connection sources listener, which can add a child for a
     * connection registered at runtime, while {@link #close()} may be iterating it.
     */
    protected final Map<String, MongoDatastore> datastoresByConnectionSource = new ConcurrentHashMap<>();
    protected final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final TenantResolver tenantResolver;
    protected final AutoTimestampEventListener autoTimestampEventListener;

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param connectionSources The {@link ConnectionSources} to use
     * @param eventPublisher The Spring ApplicationContext
     * @param mappingContext The mapping context
     */
    public MongoDatastore(final ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources, final MongoMappingContext mappingContext, final ConfigurableApplicationEventPublisher eventPublisher) {
        super(mappingContext, connectionSources != null ? connectionSources.getBaseConfiguration() : null, null);
        if (connectionSources == null) {
            throw new IllegalArgumentException("Argument [connectionSources] cannot be null");
        }
        if (mappingContext == null) {
            throw new IllegalArgumentException("Argument [mappingContext] cannot be null");
        }

        this.connectionSources = connectionSources;

        final ConnectionSource<MongoClient, MongoConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        MongoConnectionSourceSettings settings = defaultConnectionSource.getSettings();
        MultiTenancySettings multiTenancySettings = settings.getMultiTenancy();

        this.mongo = defaultConnectionSource.getSource();
        this.multiTenancyMode = multiTenancySettings.getMode();
        this.eventPublisher = eventPublisher;
        this.defaultDatabase = settings.getDatabase();
        this.defaultFlushMode = settings.getFlushMode();
        this.stateless = settings.isStateless();
        this.codecEngine = settings.getEngine().equals(MongoConstants.CODEC_ENGINE);
        if (!this.codecEngine) {
            LOG.warn("The '{}' persistence engine is deprecated and will be removed in a " +
                    "future release. Remove the {} setting to use the default codec engine.",
                    settings.getEngine(), MongoSettings.SETTING_ENGINE);
        }
        this.transactionsEnabled = settings.isTransactional();
        this.buildIndexes = settings.isBuildIndexes();
        this.buildIndexesAsync = settings.isBuildIndexesAsync();
        // Whatever the setting: buildIndexAsync() runs on it too. Until a build is submitted it holds no thread.
        this.indexBuildExecutor = newIndexBuildExecutor(defaultConnectionSource.getName());
        codecRegistry = CodecRegistries.fromRegistries(
                CodecRegistries.fromProviders(new CodecExtensions(), new PersistentEntityCodeRegistry()),
                mappingContext.getCodecRegistry(),
                MongoClientSettings.getDefaultCodecRegistry()
        );

        DatastoreTransactionManager datastoreTransactionManager = new DatastoreTransactionManager();
        datastoreTransactionManager.setDatastore(this);
        transactionManager = datastoreTransactionManager;
        for (PersistentEntity entity : mappingContext.getPersistentEntities()) {
            registerEntity(entity);
        }
        if (!(connectionSources instanceof SingletonConnectionSources)) {
            final MongoDatastore parent = this;
            Iterable<ConnectionSource<MongoClient, MongoConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (final ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource : allConnectionSources) {
                SingletonConnectionSources<MongoClient, MongoConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
                MongoDatastore childDatastore;

                if (ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
                    childDatastore = this;
                } else {
                    childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources);
                }
                // Its indexes are built when the datastore starts, along with this one's.
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }

            connectionSources.addListener(new ConnectionSourcesListener<>() {
                public void newConnectionSource(final ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource) {
                    final SingletonConnectionSources<MongoClient, MongoConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
                    MongoDatastore childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources);
                    datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
                    registerAllEntitiesWithEnhancer();
                    // Decided under the lifecycle monitor, so a connection registered while start() or stop() is
                    // under way waits for it and is then treated as the datastore now is.
                    synchronized (lifecycleMonitor) {
                        // Registered first and then checked: either close() has not started, and will find this
                        // child when it walks the map, or it has, and the build is never started.
                        if (closed) {
                            return;
                        }
                        if (running) {
                            // Unless the start() this waited for found it in the map, and has connected and
                            // built it already.
                            if (!childDatastore.startupBuildDone) {
                                childDatastore.startClient(connectionSources.getFactory(), false);
                                childDatastore.buildIndexAutomatically();
                                childDatastore.startupBuildDone = true;
                            }
                        }
                        else if (stopped) {
                            // As stop() left the others: a build requested on it waits for start(), which
                            // connects it and builds its indexes, and its client, if GORM's, refuses use.
                            childDatastore.stopIndexBuild();
                            if (childDatastore.ownsClient()) {
                                childDatastore.stopClient();
                            }
                        }
                        // Otherwise the datastore has not started, and start() connects and builds it with the
                        // others, including when start() itself is what registered it.
                    }
                }
            });
        }

        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            final TenantResolver baseResolver = multiTenancySettings.getTenantResolver();
            this.tenantResolver = new AllTenantsResolver() {
                @Override
                public Iterable<Serializable> resolveTenantIds() {
                    List<Serializable> ids = new ArrayList<>();
                    // Through the datastore rather than the connection source, which may still hold the client
                    // a checkpoint closed.
                    MongoIterable<String> databaseNames = MongoDatastore.this.getMongoClient().listDatabaseNames();
                    for (String databaseName : databaseNames) {
                        ids.add(databaseName);
                    }
                    return ids;
                }

                @Override
                public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
                    return baseResolver.resolveTenantIdentifier();
                }
            };
        } else {
            this.tenantResolver = multiTenancySettings.getTenantResolver();
        }

        this.autoTimestampEventListener = new AutoTimestampEventListener(this);
        registerEventListeners(this.eventPublisher);
        this.gormEnhancer = initialize(settings);
    }

    private MongoDatastore createChildDatastore(MongoMappingContext mappingContext,
                                                    ConfigurableApplicationEventPublisher eventPublisher,
                                                    final MongoDatastore parent,
                                                    SingletonConnectionSources<MongoClient, MongoConnectionSourceSettings> singletonConnectionSources) {
        return new MongoDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
            @Override
            protected MongoGormEnhancer initialize(final MongoConnectionSourceSettings settings) {
                // The parent starts the index build once this child is registered, where close() can reach it.
                return null;
            }

            @Override
            void ensureStarted() {
                // A child has no lifecycle of its own: starting the parent starts it.
                parent.ensureStarted();
            }

            @Override
            public MongoDatastore getDatastoreForConnection(String connectionName) {
                if (connectionName.equals(Settings.SETTING_DATASOURCE) || connectionName.equals(ConnectionSource.DEFAULT)) {
                    return parent;
                } else {
                    MongoDatastore mongoDatastore = parent.datastoresByConnectionSource.get(connectionName);
                    if (mongoDatastore == null) {
                        throw new ConfigurationException("DataSource not found for name [" + connectionName + "] in configuration. Please check your multiple data sources configuration and try again.");
                    }
                    return mongoDatastore;
                }
            }
        };
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param connectionSources The {@link ConnectionSources} to use
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public MongoDatastore(ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources, ConfigurableApplicationEventPublisher eventPublisher, Class... classes) {
        this(connectionSources, createMappingContext(connectionSources, classes), eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param eventPublisher The Spring ApplicationContext
     * @param mappingContext The mapping context
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, MongoMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        // The client is supplied by the caller, so GORM must not close it (closeable = false).
        this(createDefaultConnectionSources(mongoClient, configuration, mappingContext, false), mappingContext, eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class... classes) {
        this(mongoClient, configuration, createMappingContext(configuration, classes), eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} around the clients a supplier builds, which GORM owns: it builds one
     * the first time the client is needed, closes it when the datastore is stopped for a checkpoint, and builds the
     * one the restore needs from the same supplier. Use this where the client cannot be rebuilt from
     * {@code grails.mongodb} settings, such as one built from Spring Boot's own {@code MongoClientSettings}.
     *
     * @param clientSupplier Builds a {@link MongoClient}, whenever the datastore needs one
     * @param configuration The configuration
     * @param eventPublisher The Spring ApplicationContext
     * @param packages The packages to scan
     * @since 8.0
     */
    public MongoDatastore(Supplier<MongoClient> clientSupplier, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Package... packages) {
        this(clientSupplier, configuration, createMappingContext(configuration, new ClasspathEntityScanner().scan(packages)), eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} around the clients a supplier builds; see
     * {@link #MongoDatastore(Supplier, PropertyResolver, ConfigurableApplicationEventPublisher, Package...)}.
     *
     * @param clientSupplier Builds a {@link MongoClient}, whenever the datastore needs one
     * @param configuration The configuration
     * @param mappingContext The mapping context
     * @param eventPublisher The Spring ApplicationContext
     * @since 8.0
     */
    public MongoDatastore(Supplier<MongoClient> clientSupplier, PropertyResolver configuration, MongoMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        // GORM builds the client from the supplier, so it owns it and must close it (closeable = true).
        this(createDefaultConnectionSources(new RestartableMongoClient(ConnectionSource.DEFAULT, clientSupplier),
                configuration, mappingContext, true), mappingContext, eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param eventPublisher The Spring ApplicationContext
     * @param packages The packages to scan
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Package... packages) {
        this(mongoClient, configuration, createMappingContext(configuration, new ClasspathEntityScanner().scan(packages)), eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param classes The persistent classes
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, Class... classes) {
        this(mongoClient, configuration, createMappingContext(configuration, classes), new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param packages The packages to scan
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, Package... packages) {
        this(mongoClient, configuration, createMappingContext(configuration, new ClasspathEntityScanner().scan(packages)), new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param classes The persistent classes
     */
    public MongoDatastore(MongoClient mongoClient, Class... classes) {
        this(mongoClient, mapToPropertyResolver(null), createMappingContext(mapToPropertyResolver(null), classes), new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param clientOptions The {@link MongoClientSettings} instance
     * @param configuration The configuration
     * @param eventPublisher The Spring ApplicationContext
     * @param mappingContext The mapping context
     */
    public MongoDatastore(MongoClientSettings.Builder clientOptions, PropertyResolver configuration, MongoMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        // GORM builds the client from the supplied options, so it owns it and must close it (closeable = true).
        this(createDefaultConnectionSources(createMongoClient(configuration, clientOptions, mappingContext), configuration, mappingContext, true), mappingContext, eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param clientOptions The {@link MongoClientSettings} instance
     * @param configuration The configuration
     * @param mappingContext The mapping context
     */
    public MongoDatastore(MongoClientSettings.Builder clientOptions, PropertyResolver configuration, MongoMappingContext mappingContext) {
        // GORM builds the client from the supplied options, so it owns it and must close it (closeable = true).
        this(createDefaultConnectionSources(createMongoClient(configuration, clientOptions, mappingContext), configuration, mappingContext, true), mappingContext, new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param mappingContext The mapping context
     */
    public MongoDatastore(PropertyResolver configuration, MongoMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        this(ConnectionSourcesInitializer.create(new MongoConnectionSourceFactory(), configuration), mappingContext, eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param connectionSourceFactory The connection source factory to use
     * @param classes The persistent classes
     */
    public MongoDatastore(PropertyResolver configuration, MongoConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher, Class... classes) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, configuration), eventPublisher, classes);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public MongoDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class... classes) {
        this(configuration, new MongoConnectionSourceFactory(), eventPublisher, classes);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param mappingContext The mapping context
     */
    public MongoDatastore(PropertyResolver configuration, MongoMappingContext mappingContext) {
        this(configuration, mappingContext, new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param classes The persistent classes
     */
    public MongoDatastore(PropertyResolver configuration, Class... classes) {
        this(configuration, new DefaultApplicationEventPublisher(), classes);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param classes The persistent classes
     */
    public MongoDatastore(Map<String, Object> configuration, ConfigurableApplicationEventPublisher eventPublisher, Class... classes) {
        this(mapToPropertyResolver(configuration), eventPublisher, classes);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public MongoDatastore(Map<String, Object> configuration, Class... classes) {
        this(mapToPropertyResolver(configuration), new DefaultApplicationEventPublisher(), classes);
    }

    /**
     * Creates a MongoDatastore with the given configuration
     *
     * @param configuration The configuration
     */
    public MongoDatastore(Map<String, Object> configuration) {
        this(configuration, new Class[0]);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param mappingContext The {@link MongoMappingContext}
     */

    public MongoDatastore(Map<String, Object> configuration, MongoMappingContext mappingContext) {
        this(mapToPropertyResolver(configuration), mappingContext, new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mappingContext The {@link MongoMappingContext}
     */
    public MongoDatastore(MongoMappingContext mappingContext) {
        this(mapToPropertyResolver(null), mappingContext, new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param classes The persistent classes
     */
    public MongoDatastore(Class... classes) {
        this(mapToPropertyResolver(null), classes);
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(Package... packagesToScan) {
        this(new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Mongo datastore scanning the given package
     *
     * @param packageToScan The packages to scan
     */
    public MongoDatastore(Package packageToScan) {
        this(new ClasspathEntityScanner().scan(packageToScan));
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(PropertyResolver configuration, Package... packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(Map<String, Object> configuration, Package... packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Package... packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * @return The {@link ConnectionSources} for this datastore
     */
    public ConnectionSources<MongoClient, MongoConnectionSourceSettings> getConnectionSources() {
        return connectionSources;
    }

    /**
     * Creates and reconciles the indexes declared by the domain classes mapped to this datastore's connection.
     *
     * <p>GORM calls this itself when the datastore starts, unless {@code grails.mongodb.buildIndexes} is
     * {@code false}; that setting only stops GORM building by itself, so an application that leaves indexes
     * alone at startup can call this when it chooses to build them. Each named connection builds its own
     * domain classes: call it on {@link #getDatastoreForConnection(String)} for those.
     *
     * <p>Each index is created by a command that the server answers only once the index has been built,
     * so this blocks the calling thread for as long as MongoDB takes to build every declared index. With
     * {@code grails.mongodb.buildIndexesAsync} enabled the work goes to a background thread and this
     * returns immediately instead. {@link #buildIndexAsync()} runs it in the background whatever the setting,
     * and reports the outcome to its caller. Builds on a connection run one at a time, wherever they run: a
     * build on the calling thread first waits for one running in the background, and the other way round, and a
     * domain class registered after startup is indexed once neither is running.
     */
    public void buildIndex() {
        String connection = connectionName();
        // Before either mode: a build on the calling thread would otherwise run against the client close() is closing.
        if (closed) {
            LOG.warn("An index build was requested for connection [{}] after the datastore was closed, so it was not started.",
                    connection);
            return;
        }
        if (!buildIndexesAsync) {
            runIndexBuild(null, LOG.isInfoEnabled(), null);
            return;
        }
        if (submitIndexBuild(null)) {
            return;
        }
        if (closed) {
            LOG.warn("An index build was requested for connection [{}] after the datastore was closed, so it was not started.",
                    connection);
        }
        else {
            indexBuildPending = true;
            LOG.info("An index build was requested for connection [{}] while the datastore is stopped; it will run when " +
                    "the datastore is restarted.", connection);
        }
    }

    /**
     * Runs the same build as {@link #buildIndex()} on this connection's background index build thread, whatever
     * {@code grails.mongodb.buildIndexesAsync} says, and returns at once with a handle on its outcome.
     *
     * <p>The future completes with the build's {@link IndexBuildResult} once every declaration has been applied,
     * including when some of them failed: those are counted in {@link IndexBuildResult#failures()} and logged
     * as they happen. It completes exceptionally with the exception that stopped the build partway, such as a
     * lost connection or a write concern the server could not satisfy. Created and already-present indexes are always told apart, whatever the log level.
     *
     * <p>Builds on a connection run one at a time, so a build requested while another is running waits for it,
     * including one running on a caller's thread with {@link #buildIndex()}, or one indexing a domain class
     * registered after startup.
     * Cancelling the future does not stop the build. If the datastore is stopped or closed, the future completes
     * exceptionally at once, and a build that stopping or closing the datastore cuts short completes
     * exceptionally too; a restart runs the cut-short build again, without a future. Each named connection
     * builds its own domain classes: call this on {@link #getDatastoreForConnection(String)} for those.
     *
     * @return the outcome of the build
     */
    public CompletableFuture<IndexBuildResult> buildIndexAsync() {
        CompletableFuture<IndexBuildResult> result = new CompletableFuture<>();
        if (closed || !submitIndexBuild(result)) {
            result.completeExceptionally(new IllegalStateException("The index build for connection [" +
                    connectionName() + "] was not started: the datastore is " + (closed ? "closed" : "stopped") + "."));
        }
        return result;
    }

    /**
     * Hands a build to this connection's background index build thread.
     *
     * @param result the future to complete with the build's outcome, or {@code null} if nothing is waiting on it
     * @return false if the executor has been shut down, by {@link #stop()} or {@link #close()}
     */
    private boolean submitIndexBuild(CompletableFuture<IndexBuildResult> result) {
        ExecutorService executor = this.indexBuildExecutor;
        if (executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(new IndexBuildTask(result, () -> {
                // The first thing the build does: said only of a build that is under way, and ahead of
                // everything it logs, which it would not be if the submitting thread said it.
                if (result == null) {
                    LOG.info("Building the indexes declared by the domain classes for connection [{}] on a " +
                            "background thread. Startup does not wait for them, so a query issued before its index " +
                            "exists is served without it.", connectionName());
                }
                else {
                    LOG.info("Building the indexes declared by the domain classes for connection [{}] on a " +
                            "background thread, for a caller waiting on the result.", connectionName());
                }
                // Classified whenever a caller is waiting for the counts, which are then the product.
                runIndexBuild(executor, result != null || LOG.isInfoEnabled(), result);
            }));
            return true;
        }
        catch (RejectedExecutionException e) {
            // Shut down between the check and the submission.
            return false;
        }
    }

    /**
     * A build on the background thread, carrying the future its caller holds so that one shut down before it
     * runs, or ended by an {@link Error}, still completes it.
     */
    private record IndexBuildTask(CompletableFuture<IndexBuildResult> result, Runnable build) implements Runnable {

        @Override
        public void run() {
            try {
                build.run();
            }
            catch (RuntimeException | Error e) {
                if (result != null) {
                    result.completeExceptionally(e);
                }
                throw e;
            }
        }

        private void notRun(String reason) {
            if (result != null) {
                result.completeExceptionally(new CancellationException(reason));
            }
        }
    }

    /**
     * The builds GORM starts by itself - at startup, and for a connection added at runtime - which
     * {@code grails.mongodb.buildIndexes = false} turns off. An explicit {@link #buildIndex()} is not affected.
     */
    private void buildIndexAutomatically() {
        if (!buildIndexes) {
            LOG.info("Index creation on startup is disabled for connection [{}] by [{} = false]. The indexes already " +
                    "present on the server are left untouched; call buildIndex() to create the declared ones.",
                    connectionName(), buildIndexesSettingName());
            return;
        }
        buildIndex();
    }

    private String connectionName() {
        return connectionSources.getDefaultConnectionSource().getName();
    }

    /**
     * The setting that turned index building off, as an operator would look for it: a named connection takes
     * its own value if it declares one and inherits the top level one otherwise, so both are named.
     */
    private String buildIndexesSettingName() {
        String connection = connectionName();
        if (ConnectionSource.DEFAULT.equals(connection)) {
            return MongoSettings.SETTING_BUILD_INDEXES;
        }
        return MongoSettings.SETTING_CONNECTIONS + "." + connection + ".buildIndexes (or " +
                MongoSettings.SETTING_BUILD_INDEXES + ")";
    }

    /**
     * Runs one build and reports it, whether it finished or not.
     *
     * @param executor the executor running it, or {@code null} for a build on the caller's thread, whose
     *                 failure is left to propagate to the caller
     * @param classify whether to tell created indexes from ones already present. It costs a listIndexes per
     *                 indexed collection, and its only product is the counts, so it is skipped when nothing
     *                 would report them
     * @param result   the future to complete with the outcome, or {@code null} if nothing is waiting on it
     */
    private void runIndexBuild(ExecutorService executor, boolean classify, CompletableFuture<IndexBuildResult> result) {
        long startedAt = System.nanoTime();
        IndexBuildSummary summary = new IndexBuildSummary(classify);
        Exception failure = null;
        boolean locked = false;
        try {
            lockIndexBuild();
            locked = true;
            // Timed from here: waiting for another build is not this one's cost.
            startedAt = System.nanoTime();
            buildDeclaredIndexes(summary);
        }
        catch (Exception e) {
            // An Error is left to propagate as it is, without the summary.
            failure = e;
            if (e instanceof InterruptedException) {
                // Caught here rather than by whoever interrupted, so the flag it cleared is put back.
                Thread.currentThread().interrupt();
            }
        }
        finally {
            if (locked) {
                indexBuildLock.unlock();
            }
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (failure == null) {
            logFinishedIndexBuild(summary, elapsedMillis);
            if (result != null) {
                result.complete(summary.toResult(defaultDatabase, elapsedMillis));
            }
            return;
        }
        if (executor == null) {
            logUnfinishedIndexBuild(summary, elapsedMillis, false);
            // Unchanged, checked or not: an initializeIndices override may throw one without declaring it.
            MongoDatastore.<RuntimeException>rethrow(failure);
            return;
        }
        // Nothing is waiting on this thread, so an error that would have failed startup has to be
        // reported here or it is lost entirely.
        boolean abandoned = executor.isShutdown() && explainedByShutdown(failure);
        if (abandoned) {
            indexBuildPending = true;
            // toString rather than the message: an interrupted driver call can arrive wrapped in
            // an exception that carries no message of its own.
            LOG.debug("The background index build was abandoned because the datastore is shutting down: {}",
                    failure.toString(), failure);
        }
        else {
            LOG.error("The background index build failed: {}. The application is running without the " +
                    "indexes that were not created.", failure.getMessage(), failure);
        }
        logUnfinishedIndexBuild(summary, elapsedMillis, abandoned);
        if (result != null) {
            result.completeExceptionally(failure);
        }
    }

    /**
     * Waits for any build already running on this connection. Interruptible, so that {@link #stop()} and
     * {@link #close()} can end a background build that is still waiting; a caller interrupted while it waits gets
     * the driver's unchecked {@link MongoInterruptedException}, as an interrupted driver call would.
     */
    private void lockIndexBuild() {
        try {
            indexBuildLock.lockInterruptibly();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MongoInterruptedException("Interrupted while waiting for the index build already running on " +
                    "connection [" + connectionName() + "]", e);
        }
    }

    /**
     * Whether a failure is one that shutting the datastore down produces: an interruption, the driver refusing a
     * client that has been closed, or a socket closed under a call in progress. Anything else - a duplicate key
     * on a unique index, say - is a genuine failure that merely coincided with the shutdown, and is not to be
     * reported as an orderly one.
     */
    private static boolean explainedByShutdown(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof MongoInterruptedException ||
                    cause instanceof IllegalStateException || cause instanceof MongoSocketException) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void rethrow(Exception failure) throws E {
        throw (E) failure;
    }

    private void logFinishedIndexBuild(IndexBuildSummary summary, long elapsedMillis) {
        if (summary.applied() == 0 && summary.recreated == 0 && summary.failures == 0) {
            LOG.debug("No indexes are declared by the {} domain class(es) mapped to database [{}]",
                    summary.entities, defaultDatabase);
            return;
        }
        if (summary.failures == 0) {
            LOG.info("Index build for database [{}] finished in {}ms: {}, from {} domain class(es)",
                    defaultDatabase, elapsedMillis, summary.describe(), summary.entities);
        }
        else {
            LOG.warn("Index build for database [{}] finished in {}ms: {}, from {} domain class(es). " +
                    "The failures are reported above.",
                    defaultDatabase, elapsedMillis, summary.describe(), summary.entities);
        }
    }

    /**
     * Reports how far a build got before it stopped, which is what an operator needs when a background
     * build fails partway: the error names the cause, this says what was applied before it.
     */
    private void logUnfinishedIndexBuild(IndexBuildSummary summary, long elapsedMillis, boolean abandoned) {
        String message = "Index build for database [{}] did not finish, stopping after {}ms at {} of {} domain class(es): {}";
        if (abandoned) {
            LOG.debug(message, defaultDatabase, elapsedMillis, summary.entities, summary.entitiesTotal, summary.describe());
        }
        else {
            LOG.warn(message, defaultDatabase, elapsedMillis, summary.entities, summary.entitiesTotal, summary.describe());
        }
    }

    /**
     * Creates and reconciles the indexes declared by every entity mapped to this datastore. MongoDB answers
     * each {@code createIndex} only once the index exists, so the time this takes is the time the caller —
     * startup, or the background build thread — actually spends waiting.
     */
    private void buildDeclaredIndexes(IndexBuildSummary summary) {
        List<PersistentEntity> entities = indexedEntities();
        summary.entitiesTotal = entities.size();
        IndexBuildSummary previousSummary = indexBuildSummary.get();
        indexBuildSummary.set(summary);
        try {
            for (PersistentEntity entity : entities) {
                initializeIndices(entity);
                summary.entities++;
            }
        }
        finally {
            if (previousSummary == null) {
                indexBuildSummary.remove();
            }
            else {
                indexBuildSummary.set(previousSummary);
            }
        }
    }

    /**
     * The entities whose declared indexes this datastore builds.
     */
    private List<PersistentEntity> indexedEntities() {
        List<PersistentEntity> entities = new ArrayList<>();
        for (PersistentEntity entity : this.mappingContext.getPersistentEntities()) {
            if (isIndexedHere(entity)) {
                entities.add(entity);
            }
        }
        return entities;
    }

    /**
     * Whether this datastore builds an entity's declared indexes: an entity mapped with Mongo, outside schema
     * multi-tenancy, to this datastore's connection. Every connection shares one mapping context, so an entity
     * mapped only to another connection is in it too.
     */
    private boolean isIndexedHere(PersistentEntity entity) {
        return !entity.isExternal() &&
                !(entity.isMultiTenant() && multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) &&
                ConnectionSourcesSupport.usesConnectionSource(entity, connectionName());
    }

    /**
     * The collections whose declared indexes this datastore builds, each with the declarations of every class
     * mapped to it: every class in an inheritance hierarchy maps to its root's collection, and several classes
     * can name the same one.
     */
    private Map<MongoNamespace, CollectionDeclarations> declarationsByCollection() {
        Map<MongoNamespace, CollectionDeclarations> collections = new LinkedHashMap<>();
        for (PersistentEntity entity : indexedEntities()) {
            com.mongodb.client.MongoCollection<Document> collection = getCollection(entity);
            CollectionDeclarations declarations = collections.computeIfAbsent(collection.getNamespace(),
                    namespace -> new CollectionDeclarations(collection, new ArrayList<>()));
            for (IndexDeclaration declaration : declaredIndexes(entity)) {
                declarations.declarations().add(new EntityDeclaration(entity, declaration));
            }
        }
        return collections;
    }

    private record CollectionDeclarations(com.mongodb.client.MongoCollection<Document> collection,
                                          List<EntityDeclaration> declarations) {
    }

    private record EntityDeclaration(PersistentEntity entity, IndexDeclaration declaration) {
    }

    /**
     * Lists the indexes the domain classes declare that their collections do not have, on the collections
     * whose declared indexes {@link #buildIndex()} builds for this datastore's connection.
     *
     * <p>A declared index is present when its collection has an index on the same key pattern, the same fields
     * in the same order, whatever its name and options: an option that differs is the build's to reconcile.
     * A text index is present when one indexes the same text fields, in any order, with the same keys before and
     * after them, whatever its weights. Keys that several classes mapped to one collection declare are reported
     * once. Nothing is changed:
     * {@link #buildIndex()} or {@link #buildIndexAsync()} creates them. Each named connection has its own
     * domain classes, so call this on {@link #getDatastoreForConnection(String)} for those.
     *
     * @return the missing indexes, collection by collection, in the order the domain classes declare them
     */
    public List<MissingIndex> findMissingIndexes() {
        List<MissingIndex> missing = new ArrayList<>();
        for (Map.Entry<MongoNamespace, CollectionDeclarations> entry : declarationsByCollection().entrySet()) {
            if (entry.getValue().declarations().isEmpty()) {
                continue;
            }
            MongoNamespace namespace = entry.getKey();
            List<Document> existing = entry.getValue().collection().listIndexes().into(new ArrayList<>());
            List<Document> reported = new ArrayList<>();
            for (EntityDeclaration declared : entry.getValue().declarations()) {
                Document keys = declared.declaration().keys();
                if (isListed(keys, existing) || isAmong(keys, reported)) {
                    continue;
                }
                reported.add(keys);
                Map<String, Object> options = declared.declaration().options();
                missing.add(new MissingIndex(namespace.getDatabaseName(), namespace.getCollectionName(),
                        declared.entity().getName(), keys, options != null ? new Document(options) : new Document()));
            }
        }
        return missing;
    }

    /**
     * Lists the indexes that no domain class declares, on the collections whose declared indexes
     * {@link #buildIndex()} builds for this datastore's connection.
     *
     * <p>An index is declared when a domain class mapped to the same collection declares its key pattern, the
     * same fields in the same order, with {@code compoundIndex}, {@code index} or a property's
     * {@code index: true}. Its name and options do not matter. A text index is declared when a class declares
     * text on the same fields, in any order, with the same keys before and after them; its weights do not
     * matter. The {@code _id} index is never reported, and
     * neither is any index on a collection that no domain class maps. An index that an
     * {@link #initializeIndices(PersistentEntity)} override creates by itself is not a declaration, so it is
     * reported.
     *
     * <p>Nothing is changed: see {@link #dropUndeclaredIndexes()}. Each named connection has its own domain
     * classes, so call this on {@link #getDatastoreForConnection(String)} for those.
     *
     * @return the undeclared indexes, collection by collection, in the order the server lists them
     */
    public List<UndeclaredIndex> findUndeclaredIndexes() {
        List<UndeclaredIndex> undeclared = new ArrayList<>();
        for (Map.Entry<MongoNamespace, CollectionDeclarations> entry : declarationsByCollection().entrySet()) {
            MongoNamespace namespace = entry.getKey();
            for (Document index : entry.getValue().collection().listIndexes()) {
                if (ID_INDEX_NAME.equals(index.getString("name"))) {
                    continue;
                }
                if (index.get("key") instanceof Document key && !isDeclared(index, entry.getValue())) {
                    undeclared.add(new UndeclaredIndex(namespace.getDatabaseName(), namespace.getCollectionName(),
                            index.getString("name"), key, index));
                }
            }
        }
        return undeclared;
    }

    /**
     * Drops every index that {@link #findUndeclaredIndexes()} reports.
     *
     * <p>Run this deliberately, once every instance of the application runs the release whose domain classes
     * declare the indexes to keep. An instance still on an earlier release does not declare what a later
     * release added, nor an index created by hand ahead of a deployment, so to it those indexes are
     * undeclared and this drops them.
     *
     * @return the indexes dropped
     */
    public List<UndeclaredIndex> dropUndeclaredIndexes() {
        return dropUndeclaredIndexes(findUndeclaredIndexes());
    }

    /**
     * Drops the given indexes: those {@link #findUndeclaredIndexes()} reported, once they have been reviewed.
     *
     * <p>Each index is checked again before it is dropped, since the reviewed list can be older than what is on
     * the server: one is dropped only while the index of that name is on the keys reviewed, its collection is
     * mapped on this datastore's connection, and no domain class mapped to it declares those keys. An index that
     * no longer exists, or whose collection no longer does, is skipped, and so is one that fails a check, which
     * is logged at {@code WARN}.
     *
     * @param indexes the indexes to drop
     * @return the indexes dropped
     */
    public List<UndeclaredIndex> dropUndeclaredIndexes(List<UndeclaredIndex> indexes) {
        List<UndeclaredIndex> dropped = new ArrayList<>();
        Map<MongoNamespace, CollectionDeclarations> mapped = declarationsByCollection();
        // Listed first, once per collection, rather than inferred from the drop: the driver answers a dropIndex on
        // a collection that no longer exists as a success, and a name can have been given to another index since.
        Map<MongoNamespace, List<Document>> listed = new HashMap<>();
        for (UndeclaredIndex index : indexes) {
            MongoNamespace namespace = new MongoNamespace(index.database(), index.collection());
            CollectionDeclarations declarations = mapped.get(namespace);
            if (declarations == null) {
                LOG.warn("Index [{}] on collection [{}] of database [{}] was not dropped: no domain class is mapped to " +
                        "that collection on connection [{}]", index.name(), index.collection(), index.database(),
                        connectionName());
                continue;
            }
            List<Document> current = listed.computeIfAbsent(namespace,
                    ignored -> declarations.collection().listIndexes().into(new ArrayList<>()));
            Document existing = findIndexByName(current, index.name());
            if (existing == null) {
                LOG.debug("Index [{}] on collection [{}] of database [{}] was not dropped: it no longer exists",
                        index.name(), index.collection(), index.database());
                continue;
            }
            if (!sameIndexKeys(existing, index.definition())) {
                LOG.warn("Index [{}] on collection [{}] of database [{}] was not dropped: it is on {} now, not on the " +
                        "keys reviewed, {}", index.name(), index.collection(), index.database(),
                        existing.get("key", Document.class).toJson(), index.key().toJson());
                continue;
            }
            if (isDeclared(existing, declarations)) {
                LOG.warn("Index [{}] on collection [{}] of database [{}] was not dropped: a domain class declares its " +
                        "keys {}", index.name(), index.collection(), index.database(), index.key().toJson());
                continue;
            }
            if (!dropIndex(declarations.collection(), index.name())) {
                LOG.debug("Index [{}] on collection [{}] of database [{}] was not dropped: it no longer exists",
                        index.name(), index.collection(), index.database());
                continue;
            }
            LOG.info("Dropped index [{}] on collection [{}] of database [{}]: no domain class declares its keys {}",
                    index.name(), index.collection(), index.database(), index.key().toJson());
            dropped.add(index);
        }
        return dropped;
    }

    /**
     * Whether a domain class mapped to a collection declares the key pattern of an index it has.
     */
    private static boolean isDeclared(Document index, CollectionDeclarations declarations) {
        for (EntityDeclaration declaration : declarations.declarations()) {
            if (describes(declaration.declaration().keys(), index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether two indexes the server listed are on the same keys. Every text index has the key pattern
     * {@code {_fts: 'text', _ftsx: 1}} wherever its text is, so for those the fields its {@code weights} name are
     * compared too.
     */
    private static boolean sameIndexKeys(Document index, Document other) {
        if (!(index.get("key") instanceof Document key) || !(other.get("key") instanceof Document otherKey) ||
                !sameKeyPattern(key, otherKey)) {
            return false;
        }
        Document weights = index.get("weights", Document.class);
        Document otherWeights = other.get("weights", Document.class);
        if (weights == null || otherWeights == null) {
            return weights == otherWeights;
        }
        return weights.keySet().equals(otherWeights.keySet());
    }

    /**
     * @return whether the index was dropped, false if it had gone since the collection was listed
     */
    private static boolean dropIndex(com.mongodb.client.MongoCollection<Document> collection, String name) {
        try {
            collection.dropIndex(name);
            return true;
        }
        catch (MongoCommandException e) {
            if (e.getErrorCode() == INDEX_NOT_FOUND_CODE) {
                return false;
            }
            throw e;
        }
    }

    /**
     * The indexes a collection already had when the build reached it, listed once on first use and then
     * reused. {@code createIndex} is idempotent and answers the same way whether or not it had to build
     * anything — the driver hands back only the index name, discarding the {@code numIndexesBefore} /
     * {@code numIndexesAfter} the server reports — so what was there beforehand is what distinguishes an
     * index this build created from one it merely confirmed.
     *
     * <p>Listed lazily so that an entity declaring no indexes costs no round trip, and not at all when the
     * summary is not being classified. Successful changes are recorded so later declarations on the same
     * keys see the current name and TTL, and are not counted as new indexes. A conflict re-lists instead
     * of trusting the snapshot: whatever the server reports as conflicting may have appeared since.
     */
    private static final class ExistingIndexes {

        private final com.mongodb.client.MongoCollection<Document> collection;

        private final IndexBuildSummary summary;

        private List<Document> indexes;

        private boolean listed;

        /** Why the last listing failed, for the conflict it leaves unreconciled to report. */
        private RuntimeException listingFailure;

        /**
         * False once a listing of this collection has failed, whether its first or a re-listing after a conflict.
         * Its declarations are then applied without being classified; other collections keep their breakdown.
         */
        private boolean readable = true;

        private ExistingIndexes(com.mongodb.client.MongoCollection<Document> collection, IndexBuildSummary summary) {
            this.collection = collection;
            this.summary = summary;
        }

        /**
         * @return the known current indexes, or {@code null} if they could not be listed
         */
        private List<Document> get() {
            return listed ? indexes : refresh();
        }

        /**
         * Lists the indexes from the server now, replacing what was known.
         *
         * @return the current indexes, or {@code null} if they could not be listed
         */
        private List<Document> refresh() {
            listed = true;
            try {
                indexes = collection.listIndexes().into(new ArrayList<>());
                listingFailure = null;
                readable = true;
            } catch (RuntimeException e) {
                // Not fatal: the build can still create indexes, it just cannot report which of them
                // were new. Losing the breakdown is not worth failing a startup over.
                LOG.debug("Could not list the existing indexes of collection [{}]: {}",
                        collection.getNamespace().getCollectionName(), e.getMessage(), e);
                indexes = null;
                listingFailure = e;
                readable = false;
            }
            return indexes;
        }

        private void record(Document keys, String name, Long expireAfterSeconds) {
            if (indexes == null) {
                return;
            }
            Document existing = findIndexByKeyPattern(indexes, keys);
            if (existing != null) {
                indexes.remove(existing);
            }
            Document index = new Document("key", new Document(keys)).append("name", name);
            if (expireAfterSeconds != null) {
                index.append(INDEX_EXPIRE_AFTER_SECONDS, expireAfterSeconds);
            }
            indexes.add(index);
        }

        /**
         * @return whether an index on these keys was already there, or {@code null} if that is not known: the
         *         build is not classifying, or this collection's indexes could not be listed
         */
        private Boolean contains(Document keys) {
            if (!summary.classifying || !readable) {
                return null;
            }
            List<Document> existing = get();
            if (existing == null) {
                return null;
            }
            return findIndexByKeyPattern(existing, keys) != null;
        }
    }

    /**
     * Counts the work one index build did, so that it can be summarised once at the end rather than a line
     * per index.
     */
    private static final class IndexBuildSummary {

        /** Domain classes whose indexes have been applied. */
        private int entities;

        /** Domain classes the build set out to cover. */
        private int entitiesTotal;

        private int created;

        /** Dropped and built again for {@code recreateOnConflict}, which costs a full build. */
        private int recreated;

        private int alreadyPresent;

        private int failures;

        /** Applied on a collection whose existing indexes could not be listed, so neither created nor present. */
        private int unclassified;

        /**
         * Whether created and already-present indexes are to be told apart at all, which costs a listing per
         * collection and is only worth it when the summary will be logged.
         */
        private final boolean classifying;

        /**
         * One listing per collection rather than per entity: every class in an inheritance hierarchy maps
         * to its root's collection, and several classes can name the same one. Sharing it also keeps them
         * consistent, so a second class declaring keys the first has just created sees them as present.
         */
        private final Map<MongoNamespace, ExistingIndexes> existingIndexes = new HashMap<>();

        private IndexBuildSummary(boolean classifying) {
            this.classifying = classifying;
        }

        private ExistingIndexes existingIndexesOf(com.mongodb.client.MongoCollection<Document> collection) {
            return existingIndexes.computeIfAbsent(collection.getNamespace(), namespace -> new ExistingIndexes(collection, this));
        }

        /** Declarations applied other than by a drop and recreate. */
        private int applied() {
            return created + alreadyPresent + unclassified;
        }

        private IndexBuildResult toResult(String database, long elapsedMillis) {
            return new IndexBuildResult(database, entities, created, recreated, alreadyPresent, unclassified,
                    failures, elapsedMillis);
        }

        private String describe() {
            StringBuilder outcome = new StringBuilder();
            if (created + alreadyPresent > 0 || unclassified == 0) {
                outcome.append(created).append(" created, ");
                if (recreated > 0) {
                    outcome.append(recreated).append(" recreated, ");
                }
                outcome.append(alreadyPresent).append(" already present");
                if (unclassified > 0) {
                    // Partial: what the collections that could be listed established still stands.
                    outcome.append(", ").append(unclassified).append(" applied without a listing");
                }
            }
            else {
                // Nothing was classified, whether because it was not asked for or no collection could be listed.
                outcome.append(unclassified).append(" index declaration(s) applied");
                if (recreated > 0) {
                    outcome.append(", ").append(recreated).append(" recreated");
                }
            }
            if (failures > 0) {
                outcome.append(", ").append(failures).append(" failed");
            }
            return outcome.toString();
        }
    }

    /** How a conflicting declaration was resolved. */
    private enum Reconciliation {
        /** The existing index was changed in place, with no rebuild. */
        UPDATED,
        /** The existing index was dropped and the declared one built from scratch. */
        RECREATED,
        /** The existing index was left as it was. */
        FAILED
    }

    /**
     * @return The default flush mode
     */
    public FlushModeType getDefaultFlushMode() {
        return defaultFlushMode;
    }

    /**
     * @return The default database name
     */
    public String getDefaultDatabase() {
        return defaultDatabase;
    }

    /**
     * Sets any additional codec registries
     *
     * @param codecRegistries The {@link CodecRegistry} instances
     */
    @Autowired(required = false)
    public void setCodecRegistries(List<CodecRegistry> codecRegistries) {
        this.codecRegistry = CodecRegistries.fromRegistries(
                this.codecRegistry,
                CodecRegistries.fromRegistries(codecRegistries));
    }

    /**
     * Sets any additional codec providers
     *
     * @param codecProviders The {@link CodecProvider} instances
     */
    @Autowired(required = false)
    public void setCodecProviders(List<CodecProvider> codecProviders) {
        this.codecRegistry = CodecRegistries.fromRegistries(
                this.codecRegistry,
                CodecRegistries.fromProviders(codecProviders));
    }

    /**
     * Sets any additional codecs
     *
     * @param codecs The {@link Codec} instances
     */
    @Autowired(required = false)
    public void setCodecs(List<Codec<?>> codecs) {
        this.codecRegistry = CodecRegistries.fromRegistries(
                this.codecRegistry,
                CodecRegistries.fromCodecs(codecs));
    }

    /**
     * The message source used for validation messages
     *
     * @param messageSources The message source
     */
    @Autowired(required = false)
    public void setMessageSource(List<MessageSource> messageSources) {
        setMessageSource(GrailsMessageSourceUtils.findPreferredMessageSource(messageSources));
    }

    public void setMessageSource(MessageSource messageSource) {
        if (messageSource != null) {
            configureValidatorRegistry(connectionSources.getDefaultConnectionSource().getSettings(), (MongoMappingContext) mappingContext, messageSource);
        }
    }

    /**
     * @return The transaction manager
     */
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * @return The {@link CodecRegistry}
     */
    public CodecRegistry getCodecRegistry() {
        return codecRegistry;
    }

    /**
     * Obtains a {@link PersistentEntityCodec} for the given entity
     *
     * @param entity The entity
     * @return The {@link PersistentEntityCodec}
     */
    public PersistentEntityCodec getPersistentEntityCodec(PersistentEntity entity) {
        if (entity instanceof EmbeddedPersistentEntity) {
            return new PersistentEntityCodec(codecRegistry, entity);
        } else {
            return getPersistentEntityCodec(entity.getJavaClass());
        }
    }

    /**
     * Obtains a {@link PersistentEntityCodec} for the given entity
     *
     * @param entityClass The entity class
     * @return The {@link PersistentEntityCodec}
     */
    public PersistentEntityCodec getPersistentEntityCodec(Class entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("Argument [entityClass] cannot be null");
        }

        final PersistentEntity entity = getMappingContext().getPersistentEntity(entityClass.getName());
        if (entity == null) {
            throw new IllegalArgumentException("Argument [" + entityClass + "] is not an entity");
        }

        return (PersistentEntityCodec) getCodecRegistry().get(entity.getJavaClass());
    }

    /**
     * @return The {@link ConfigurableApplicationEventPublisher} instance used by this datastore
     */
    @Override
    public ConfigurableApplicationEventPublisher getApplicationEventPublisher() {
        return this.eventPublisher;
    }

    /**
     * @return The {@link MongoClient} instance
     */
    public MongoClient getMongoClient() {
        return mongo;
    }

    /**
     * Whether GORM should use real MongoDB multi-document transactions (a server-side
     * {@code ClientSession}) for transactional operations. This is opt-in via
     * {@code grails.mongodb.transactional} and additionally requires a replica set or sharded
     * cluster; if a standalone topology is positively detected the feature is disabled (with a
     * one-time warning) and GORM falls back to the legacy client-side flush behavior.
     *
     * @return {@code true} if server-side transactions should be used
     * @since 8.0
     */
    public boolean isTransactionsEnabled() {
        if (!transactionsEnabled) {
            return false;
        }
        // Once the topology is positively known it does not change at runtime, so latch the result to
        // avoid recomputing (and possibly flipping) on every transaction begin.
        Boolean supported = transactionsSupported;
        if (supported != null) {
            return supported;
        }
        try {
            ClusterType clusterType = mongo.getClusterDescription().getType();
            switch (clusterType) {
                case STANDALONE:
                    transactionsSupported = Boolean.FALSE;
                    if (!warnedTransactionsUnsupported) {
                        warnedTransactionsUnsupported = true;
                        LOG.warn("grails.mongodb.transactional is enabled but the connected MongoDB topology is standalone, " +
                                "which does not support multi-document transactions. Falling back to flush-only transaction behavior.");
                    }
                    return false;
                case REPLICA_SET:
                case SHARDED:
                case LOAD_BALANCED:
                    transactionsSupported = Boolean.TRUE;
                    return true;
                default:
                    // UNKNOWN: topology not discovered yet. Assume transactions are available for this
                    // attempt without latching, so a later definitive determination can still apply.
                    return true;
            }
        }
        catch (RuntimeException e) {
            LOG.debug("Could not determine MongoDB cluster topology for transaction support; assuming transactions are available: {}", e.getMessage(), e);
            return true;
        }
    }

    /**
     * Whether GORM creates and reconciles the indexes declared in the domain class mapping blocks by itself:
     * when the datastore starts, and for connections and domain classes added later. Disabled with
     * {@code grails.mongodb.buildIndexes = false}, which leaves the indexes on the server as they are until
     * {@link #buildIndex()} is called.
     *
     * @return {@code true} if GORM builds the declared indexes by itself
     * @since 8.0
     */
    public boolean isBuildIndexes() {
        return buildIndexes;
    }

    /**
     * Whether index builds run on a background thread instead of blocking the thread that starts them - the
     * one GORM starts on startup, and any {@link #buildIndex()} the application calls. Enabled with
     * {@code grails.mongodb.buildIndexesAsync = true}.
     *
     * @return {@code true} if declared indexes are built asynchronously
     * @since 8.0
     */
    public boolean isBuildIndexesAsync() {
        return buildIndexesAsync;
    }

    public String getDatabaseName(PersistentEntity entity) {
        if (entity.isMultiTenant() && multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return Tenants.currentId(getClass()).toString();
        }
        else {
            final String databaseName = mongoDatabases.get(entity);
            if (databaseName == null) {
                mongoDatabases.put(entity, defaultDatabase);
                return defaultDatabase;
            }
            return databaseName;
        }
    }

    /**
     * Gets the default collection name for the given entity
     *
     * @param entity The entity
     * @return The collection name
     */
    public String getCollectionName(PersistentEntity entity) {
        final String collectionName = mongoCollections.get(entity);
        if (collectionName == null) {
            final String decapitalizedName = entity.isRoot() ? entity.getDecapitalizedName() : entity.getRootEntity().getDecapitalizedName();
            mongoCollections.put(entity, decapitalizedName);
            return decapitalizedName;
        }
        return collectionName;
    }

    /**
     * Obtain the raw {@link com.mongodb.client.MongoCollection} for the given entity
     *
     * @param entity The entity
     * @return The Mongo collection
     */
    public com.mongodb.client.MongoCollection<Document> getCollection(PersistentEntity entity) {
        return getMongoClient()
                .getDatabase(getDatabaseName(entity))
                .getCollection(getCollectionName(entity))
                .withCodecRegistry(codecRegistry);
    }

    /**
     * @return The mapping context
     */
    @Override
    public MongoMappingContext getMappingContext() {
        return (MongoMappingContext) super.getMappingContext();
    }

    @Override
    public boolean isSchemaless() {
        return true;
    }

    protected void registerAllEntitiesWithEnhancer() {
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            gormEnhancer.registerEntity(persistentEntity);
        }
    }

    @Override
    protected Session createSession(PropertyResolver connDetails) {
        ensureStarted();
        if (stateless) {
            return createStatelessSession(connDetails);
        } else {
            if (codecEngine) {
                return new MongoCodecSession(this, getMappingContext(), getApplicationEventPublisher(), false);
            } else {
                return new MongoSession(this, getMappingContext(), getApplicationEventPublisher(), false);
            }
        }
    }

    /**
     * Runs the initialization sequence
     * @param settings
     */
    protected MongoGormEnhancer initialize(final MongoConnectionSourceSettings settings) {
        initializeConverters(this.mappingContext);

        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            public void persistentEntityAdded(PersistentEntity entity) {
                gormEnhancer.registerEntity(entity);
                registerEntity(entity);
            }
        });
        // Listeners are called in the order they were added, and indexing an entity needs the collection and
        // database registerEntity resolves from its mapping: indexed first, it was indexed on its default
        // collection, and getCollectionName cached that name.
        getMappingContext().addMappingContextListener(this);

        return new MongoGormEnhancer(this, transactionManager, settings) {
            @Override
            protected <D> MongoStaticApi<D> getStaticApi(Class<D> cls, String qualifier) {
                MongoDatastore mongoDatastore = getDatastoreForQualifier(cls, qualifier);
                return new MongoStaticApi<>(cls, mongoDatastore, createDynamicFinders(mongoDatastore), transactionManager);
            }

            @Override
            protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier) {
                MongoDatastore mongoDatastore = getDatastoreForQualifier(cls, qualifier);

                GormInstanceApi<D> instanceApi = new GormInstanceApi<>(cls, mongoDatastore);
                instanceApi.setFailOnError(getFailOnError());
                instanceApi.setMarkDirty(getMarkDirty());
                return instanceApi;
            }

            @Override
            protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier) {
                MongoDatastore mongoDatastore = getDatastoreForQualifier(cls, qualifier);
                return new GormValidationApi<>(cls, mongoDatastore);
            }

            private <D> MongoDatastore getDatastoreForQualifier(Class<D> cls, String qualifier) {
                String defaultConnectionSourceName = ConnectionSourcesSupport.getDefaultConnectionSourceName(getMappingContext().getPersistentEntity(cls.getName()));
                if (defaultConnectionSourceName.equals(ConnectionSource.ALL)) {
                    defaultConnectionSourceName = ConnectionSource.DEFAULT;
                }

                boolean isDefaultQualifier = qualifier.equals(ConnectionSource.DEFAULT);
                if (isDefaultQualifier && defaultConnectionSourceName.equals(ConnectionSource.DEFAULT)) {
                    return MongoDatastore.this;
                }
                else {
                    if (isDefaultQualifier) {
                        qualifier = defaultConnectionSourceName;
                    }
                    ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource = connectionSources.getConnectionSource(qualifier);
                    if (connectionSource == null) {
                        throw new ConfigurationException("Invalid connection [" + defaultConnectionSourceName + "] configured for class [" + cls + "]");
                    }

                    return datastoresByConnectionSource.get(qualifier);
                }
            }
        };

    }

    @Override
    protected Session createStatelessSession(PropertyResolver connectionDetails) {
        ensureStarted();
        if (codecEngine) {
            return new MongoCodecSession(this, getMappingContext(), getApplicationEventPublisher(), true);
        } else {
            return new MongoSession(this, getMappingContext(), getApplicationEventPublisher(), true);
        }
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(new DomainEventListener(this));
        eventPublisher.addApplicationListener(autoTimestampEventListener);
        eventPublisher.addApplicationListener(new ValidationEventListener(this));

        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener(this));
        }
    }

    /**
     * Indexes any properties that are mapped with index:true. Called for both startup builds and
     * entities registered later, so subclasses can customise index creation on either path.
     *
     * <p>With {@code grails.mongodb.buildIndexesAsync} enabled the startup build calls this on a background
     * thread, and it can do so before the constructor of a subclass has finished. An override must not
     * depend on state that its own constructor or field initializers set up.
     *
     * @param entity The entity
     */
    protected void initializeIndices(final PersistentEntity entity) {
        IndexBuildSummary summary = indexBuildSummary.get();
        // Outside a build nothing reports the counts, so there is nothing to classify for.
        initializeIndices(entity, summary != null ? summary : new IndexBuildSummary(false));
    }

    private void initializeIndices(final PersistentEntity entity, final IndexBuildSummary summary) {
        final com.mongodb.client.MongoCollection<Document> collection = getCollection(entity);
        final ExistingIndexes existingIndexes = summary.existingIndexesOf(collection);
        for (IndexDeclaration declaration : declaredIndexes(entity)) {
            createOrUpdateIndex(entity, collection, declaration.keys(), declaration.options(),
                    declaration.descriptor(), summary, existingIndexes);
        }
    }

    /**
     * An index a domain class declares: its key pattern, the options it is built with and how the build's
     * log lines name it.
     */
    private record IndexDeclaration(Document keys, Map<String, Object> options, String descriptor) {
    }

    /**
     * The indexes an entity's mapping declares, in the order the build applies them: its {@code index}
     * entries, its {@code compoundIndex} entries, then each property mapped with {@code index: true}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<IndexDeclaration> declaredIndexes(final PersistentEntity entity) {
        List<IndexDeclaration> declarations = new ArrayList<>();
        final ClassMapping<MongoCollection> classMapping = entity.getMapping();
        if (classMapping != null) {
            final MongoCollection mappedForm = classMapping.getMappedForm();
            if (mappedForm != null) {
                List<MongoCollection.Index> indices = mappedForm.getIndices();
                for (MongoCollection.Index index : indices) {
                    declarations.add(new IndexDeclaration(new Document(index.getDefinition()), index.getOptions(),
                            "with definition [" + index.getDefinition() + "]"));
                }

                for (Map compoundIndex : mappedForm.getCompoundIndices()) {
                    // A copy, because the declaration is shared: every connection builds from the same
                    // mapping, and taking the attributes out of the original would leave the next build
                    // an index with none.
                    Map declaration = new LinkedHashMap(compoundIndex);
                    Object attributes = declaration.remove(INDEX_ATTRIBUTES);
                    Map indexAttributes = attributes instanceof Map ? (Map) attributes : null;
                    Document indexDef = new Document(declaration);
                    declarations.add(new IndexDeclaration(indexDef, indexAttributes,
                            "compound index with definition [" + indexDef + "]"));
                }
            }
        }

        for (PersistentProperty<MongoAttribute> property : entity.getPersistentProperties()) {
            final boolean indexed = isIndexed(property);

            if (indexed) {
                final MongoAttribute mongoAttributeMapping = property.getMapping().getMappedForm();
                Document dbObject = new Document();
                final String fieldName = getMongoFieldNameForProperty(property);
                dbObject.put(fieldName, 1);
                Document options = new Document();
                if (mongoAttributeMapping != null) {
                    Map attributes = mongoAttributeMapping.getIndexAttributes();
                    if (attributes != null) {
                        attributes = new HashMap(attributes);
                        if (attributes.containsKey(MongoAttribute.INDEX_TYPE)) {
                            dbObject.put(fieldName, attributes.remove(MongoAttribute.INDEX_TYPE));
                        }
                        options.putAll(attributes);
                    }
                }
                declarations.add(new IndexDeclaration(dbObject, options, "on property [" + property.getName() + "]"));
            }
        }
        return declarations;
    }

    /**
     * Create an index, reconciling option conflicts with any pre-existing index on the same keys.
     *
     * <p>Two things this does beyond a raw {@code createIndex}:</p>
     * <ol>
     *   <li>Applies {@code expireAfterSeconds} (TTL) — the one option {@link MongoConstants#mapToObject}
     *       cannot set, because the driver only exposes the two-argument {@link IndexOptions#expireAfter}.</li>
     *   <li>On {@code IndexOptionsConflict} (an index already exists on these keys with different
     *       options), reconciles instead of only logging: a TTL change is applied in place with
     *       {@code collMod} (no drop, no rebuild, no gap); any other conflict is dropped and
     *       recreated only when {@code recreateOnConflict:true} was declared, else logged with guidance.</li>
     * </ol>
     *
     * <p>Any other refusal from the server, a duplicate key under a {@code unique} index included, is logged and
     * counted as a failure, and the build goes on. An error that is not the server's answer, such as a lost
     * connection, is left to stop the build.
     */
    private void createOrUpdateIndex(PersistentEntity entity,
                                     com.mongodb.client.MongoCollection<Document> collection,
                                     Document keys, Map<String, Object> rawOptions, String descriptor,
                                     IndexBuildSummary summary, ExistingIndexes existingIndexes) {
        Map<String, Object> options = rawOptions != null ? new HashMap<>(rawOptions) : new HashMap<>();

        // Control flag — not a Mongo index option.
        boolean recreateOnConflict = Boolean.TRUE.equals(options.remove(INDEX_RECREATE_ON_CONFLICT));

        Long expireAfterSeconds = null;
        Object ttl = options.remove(INDEX_EXPIRE_AFTER_SECONDS);
        if (ttl instanceof Number) {
            expireAfterSeconds = ((Number) ttl).longValue();
        }

        final IndexOptions indexOptions = MongoConstants.mapToObject(IndexOptions.class, options);
        if (expireAfterSeconds != null) {
            indexOptions.expireAfter(expireAfterSeconds, TimeUnit.SECONDS);
        }

        // Asked before the index is created, while the answer still means something.
        Boolean present = existingIndexes.contains(keys);
        long startedAt = System.nanoTime();
        try {
            String indexName = collection.createIndex(keys, indexOptions);
            existingIndexes.record(keys, indexName, expireAfterSeconds);
            if (present == null) {
                summary.unclassified++;
            }
            else if (present) {
                summary.alreadyPresent++;
            }
            else {
                summary.created++;
            }
            // Unclassified, nothing says whether the index was new, so the line does not claim either.
            String applied = present == null ? "Applied" : present ? "Confirmed" : "Created";
            LOG.debug("{} index for entity [{}] {} in {}ms", applied,
                    entity.getName(), descriptor, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == INDEX_OPTIONS_CONFLICT_CODE || e.getErrorCode() == INDEX_KEY_SPECS_CONFLICT_CODE) {
                Reconciliation reconciliation = reconcileIndexConflict(entity, collection, existingIndexes, keys,
                        indexOptions, expireAfterSeconds, recreateOnConflict, descriptor, e);
                if (reconciliation == Reconciliation.UPDATED) {
                    // An index was already on these keys and was changed in place rather than added.
                    summary.alreadyPresent++;
                }
                else if (reconciliation == Reconciliation.RECREATED) {
                    // Counted apart: a rebuild costs as much as a new index, whatever was there before.
                    summary.recreated++;
                }
                else {
                    summary.failures++;
                }
            } else {
                summary.failures++;
                LOG.error("Failed to create index for entity [{}] {}: {}",
                    entity.getName(), descriptor, e.getMessage(), e);
            }
        } catch (MongoServerException e) {
            // A unique index over documents that already share a value, which the driver reports as a
            // DuplicateKeyException rather than a command error, is the server refusing this one declaration.
            // Anything else here, such as a write concern the server could not satisfy, says nothing about the
            // declaration and leaves unknown whether it was applied, so it stops the build.
            if (!isDuplicateKey(e)) {
                throw e;
            }
            summary.failures++;
            LOG.error("Failed to create index for entity [{}] {}: {}",
                entity.getName(), descriptor, e.getMessage(), e);
        }
    }

    private static boolean isDuplicateKey(MongoServerException e) {
        return e instanceof DuplicateKeyException || ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY;
    }

    /**
     * Reconcile an {@code IndexOptionsConflict}, where an index already exists on the same keys with
     * different options, or an {@code IndexKeySpecsConflict}, where the declared name is taken by an
     * index on other keys. A TTL difference is the common, safe case (e.g. a configurable retention
     * changed between restarts) and is updated in place via {@code collMod}; anything else needs an
     * explicit {@code recreateOnConflict:true} to authorise the drop-and-recreate.
     *
     * @return how the conflict was resolved
     */
    private Reconciliation reconcileIndexConflict(PersistentEntity entity,
                                        com.mongodb.client.MongoCollection<Document> collection,
                                        ExistingIndexes existingIndexes,
                                        Document keys, IndexOptions desired, Long expireAfterSeconds,
                                        boolean recreateOnConflict, String descriptor, MongoCommandException original) {
        // Listed afresh: the index the server just reported may not have existed when this collection was
        // first listed - another instance or another connection can have created it since.
        List<Document> indexes = existingIndexes.refresh();
        if (indexes == null) {
            // The reason the listing failed, which is what an operator can act on - often a missing
            // listIndexes privilege - with the conflict that made it matter attached.
            LOG.error("Failed to create index for entity [{}] {} and could not inspect existing indexes: {}",
                entity.getName(), descriptor, existingIndexes.listingFailure.getMessage(), original);
            return Reconciliation.FAILED;
        }
        Document existing = findIndexByKeyPattern(indexes, keys);
        // An IndexKeySpecsConflict names no index on these keys: the declared name belongs to one on other keys.
        boolean nameTaken = false;
        if (existing == null && desired.getName() != null) {
            existing = findIndexByName(indexes, desired.getName());
            nameTaken = existing != null;
        }
        if (existing == null) {
            LOG.error("Failed to create index for entity [{}] {}: {}",
                entity.getName(), descriptor, original.getMessage(), original);
            return Reconciliation.FAILED;
        }

        String existingName = existing.getString("name");
        Object existingTtl = existing.get(INDEX_EXPIRE_AFTER_SECONDS);
        Long existingTtlSeconds = existingTtl instanceof Number ? ((Number) existingTtl).longValue() : null;

        // TTL change on an existing index — update in place, no rebuild, no gap. An index that merely holds the
        // name is a different index, so its expiry is not the declared one to update.
        boolean ttlChange = !nameTaken && expireAfterSeconds != null && !expireAfterSeconds.equals(existingTtlSeconds);
        if (ttlChange) {
            try {
                getMongoClient().getDatabase(getDatabaseName(entity))
                        .runCommand(new Document("collMod", getCollectionName(entity))
                                .append("index", new Document("name", existingName)
                                        .append(INDEX_EXPIRE_AFTER_SECONDS, expireAfterSeconds)));
                existingIndexes.record(keys, existingName, expireAfterSeconds);
                LOG.info("Updated TTL of index [{}] on entity [{}] to {}s",
                    existingName, entity.getName(), expireAfterSeconds);
                return Reconciliation.UPDATED;
            } catch (MongoCommandException collModError) {
                // collMod can't make every change (e.g. add a TTL to a non-TTL index on older
                // servers) — fall through to recreate (if authorised) rather than fail outright.
                LOG.warn("collMod TTL update failed for index [{}] on entity [{}]: {}{}",
                    existingName, entity.getName(), collModError.getMessage(), recreateOnConflict ? " — recreating" : "");
            }
        }

        if (recreateOnConflict) {
            boolean dropped = false;
            try {
                collection.dropIndex(existingName);
                dropped = true;
                String indexName = collection.createIndex(keys, desired);
                existingIndexes.record(keys, indexName, expireAfterSeconds);
                LOG.info("Recreated index [{}] on entity [{}] {}", existingName, entity.getName(), descriptor);
                return Reconciliation.RECREATED;
            } catch (MongoServerException recreateError) {
                if (!(recreateError instanceof MongoCommandException) && !isDuplicateKey(recreateError)) {
                    if (dropped) {
                        LOG.warn("Dropped index [{}] on entity [{}] to recreate it {}, but the server did not confirm " +
                                "the new one, so whether it was built is not known: {}",
                            existingName, entity.getName(), descriptor, recreateError.getMessage());
                    }
                    throw recreateError;
                }
                if (!dropped) {
                    LOG.error("Failed to recreate index [{}] on entity [{}] {}: {}",
                        existingName, entity.getName(), descriptor, recreateError.getMessage(), recreateError);
                    return Reconciliation.FAILED;
                }
                // Listed again, so that a later declaration on these keys is not counted as already present.
                existingIndexes.refresh();
                LOG.error("Dropped index [{}] on entity [{}] to recreate it {}, but it could not be built again: {}. " +
                        "The collection has no index on these keys until the cause is fixed and the index built.",
                    existingName, entity.getName(), descriptor, recreateError.getMessage(), recreateError);
                return Reconciliation.FAILED;
            }
        }

        if (nameTaken) {
            LOG.error(
                "Index conflict for entity [{}] {}: the name [{}] is taken by an index on different keys. " +
                    "Declare indexAttributes:[recreateOnConflict:true] to drop and recreate it, or declare another " +
                    "name. Original error: {}",
                entity.getName(), descriptor, existingName, original.getMessage());
        }
        else {
            LOG.error(
                "Index conflict for entity [{}] {}: an index [{}] already exists on the same keys with different options. " +
                    "Declare indexAttributes:[recreateOnConflict:true] to drop and recreate it. Original error: {}",
                entity.getName(), descriptor, existingName, original.getMessage());
        }
        return Reconciliation.FAILED;
    }

    /**
     * Find an existing index by name, or {@code null} if none has it.
     */
    private static Document findIndexByName(Iterable<Document> indexes, String name) {
        for (Document index : indexes) {
            if (name.equals(index.getString("name"))) {
                return index;
            }
        }
        return null;
    }

    /**
     * Find an existing index whose key pattern matches the given keys, or {@code null} if none.
     */
    private static Document findIndexByKeyPattern(Iterable<Document> indexes, Document keys) {
        for (Document idx : indexes) {
            if (idx.get("key") instanceof Document key && matchesDeclaration(key, keys)) {
                return idx;
            }
        }
        return null;
    }

    /**
     * Whether any of the indexes the server listed is the one a declaration describes, as the reports compare them.
     */
    private static boolean isListed(Document declaredKeys, List<Document> indexes) {
        for (Document index : indexes) {
            if (describes(declaredKeys, index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a declaration describes the same index as one of the others, as the reports compare them.
     */
    private static boolean isAmong(Document declaredKeys, List<Document> others) {
        for (Document other : others) {
            if (sameDeclaredIndex(declaredKeys, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameDeclaredIndex(Document declaredKeys, Document other) {
        if (isTextIndex(declaredKeys) && isTextIndex(other)) {
            return declaredTextKeys(declaredKeys).matches(declaredTextKeys(other));
        }
        return sameKeyPattern(declaredKeys, other);
    }

    /**
     * Whether an index the server listed is the one a declaration describes, as the missing and undeclared
     * reports compare them: on the same key pattern, whatever its name and options. Unlike
     * {@link #matchesDeclaration(Document, Document)}, a text index is only the one a text declaration describes
     * when it indexes the same text fields, which its {@code weights} name, with the same keys before and after
     * them. Their weights are options, so they are not compared.
     */
    private static boolean describes(Document declaredKeys, Document index) {
        if (!(index.get("key") instanceof Document key)) {
            return false;
        }
        if (isTextIndex(declaredKeys) && key.containsKey(TEXT_INDEX_KEY)) {
            return index.get("weights") instanceof Document weights &&
                    declaredTextKeys(declaredKeys).matches(listedTextKeys(key, weights));
        }
        return sameKeyPattern(key, declaredKeys);
    }

    /**
     * A text index's keys as the reports compare them: the keys before its text fields, the text fields
     * themselves, in any order, and the keys after them.
     */
    private record TextKeys(Document before, Set<String> fields, Document after) {

        boolean matches(TextKeys other) {
            return fields.equals(other.fields) && sameKeyPattern(before, other.before) &&
                    sameKeyPattern(after, other.after);
        }
    }

    private static TextKeys declaredTextKeys(Document keys) {
        Document before = new Document();
        Document after = new Document();
        Set<String> fields = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            if ("text".equals(entry.getValue())) {
                fields.add(entry.getKey());
            }
            else {
                (fields.isEmpty() ? before : after).append(entry.getKey(), entry.getValue());
            }
        }
        return new TextKeys(before, fields, after);
    }

    /**
     * The same of an index the server listed, whose key holds {@code _fts} and {@code _ftsx} where the text
     * fields were declared and whose {@code weights} name them.
     */
    private static TextKeys listedTextKeys(Document key, Document weights) {
        Document before = new Document();
        Document after = new Document();
        boolean pastText = false;
        for (Map.Entry<String, Object> entry : key.entrySet()) {
            if (TEXT_INDEX_KEY.equals(entry.getKey()) || TEXT_INDEX_TERM_KEY.equals(entry.getKey())) {
                pastText = true;
            }
            else {
                (pastText ? after : before).append(entry.getKey(), entry.getValue());
            }
        }
        return new TextKeys(before, new LinkedHashSet<>(weights.keySet()), after);
    }

    /**
     * Whether an existing index's key pattern is the one a declaration describes, for creating and reconciling
     * it. Directions/types are compared numerically (1 vs 1.0) so driver-returned values match.
     *
     * <p>Text indexes are special-cased: a declared text index has key {@code {field: 'text'}}, but
     * MongoDB reports an existing one with a synthetic {@code {_fts: 'text', _ftsx: 1}} key, so the
     * two never match by pattern. Since MongoDB allows at most one text index per collection, an
     * existing text index is unambiguously the one a declared text index describes or conflicts with —
     * match it regardless of its key shape or name so {@code recreateOnConflict} can absorb it. The reports
     * compare text indexes field by field instead: see {@link #describes(Document, Document)}.</p>
     */
    private static boolean matchesDeclaration(Document existingKey, Document declaredKeys) {
        if (isTextIndex(declaredKeys) && isTextIndex(existingKey)) {
            return true;
        }
        return sameKeyPattern(existingKey, declaredKeys);
    }

    /**
     * True for a text index in either representation: a declaration ({@code {field: 'text'}}) or the
     * synthetic key MongoDB reports for an existing one ({@code {_fts: 'text', _ftsx: 1}}).
     */
    private static boolean isTextIndex(Document key) {
        if (key.containsKey(TEXT_INDEX_KEY)) {
            return true;
        }
        for (Object v : key.values()) {
            if ("text".equals(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether two key patterns describe the same index. Order counts: {@code {a: 1, b: 1}} and
     * {@code {b: 1, a: 1}} are two indexes to MongoDB, and one cannot serve the other's sort.
     */
    private static boolean sameKeyPattern(Document existingKey, Document desiredKey) {
        if (existingKey.size() != desiredKey.size()) {
            return false;
        }
        Iterator<Map.Entry<String, Object>> existing = existingKey.entrySet().iterator();
        for (Map.Entry<String, Object> desired : desiredKey.entrySet()) {
            Map.Entry<String, Object> actual = existing.next();
            if (!actual.getKey().equals(desired.getKey())) {
                return false;
            }
            Object a = actual.getValue();
            Object b = desired.getValue();
            if (a instanceof Number && b instanceof Number) {
                if (((Number) a).doubleValue() != ((Number) b).doubleValue()) {
                    return false;
                }
            } else if (!Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    String getMongoFieldNameForProperty(PersistentProperty<MongoAttribute> property) {
        PropertyMapping<MongoAttribute> pm = property.getMapping();
        String propKey = null;
        if (pm.getMappedForm() != null) {
            propKey = pm.getMappedForm().getField();
        }
        if (propKey == null) {
            propKey = property.getName();
        }
        return propKey;
    }

    public void persistentEntityAdded(PersistentEntity entity) {
        // GORM indexing a domain class registered after startup by itself, so it follows the setting.
        if (!buildIndexes) {
            LOG.debug("Index creation is disabled for connection [{}] by [{} = false]. Skipping the indexes declared " +
                    "by entity [{}].", connectionName(), buildIndexesSettingName(), entity.getName());
            return;
        }
        if (!isIndexedHere(entity)) {
            return;
        }
        synchronized (lifecycleMonitor) {
            if (!running) {
                // Not now, which would connect a datastore that has not started, or one stopped for a checkpoint.
                // start() builds the indexes of every entity registered by then, this one included; a datastore that
                // has started before builds this connection's again when it is started.
                if (started) {
                    startupBuildDone = false;
                }
                return;
            }
            // After any build running on this connection, as every build on it is: two at once could each drop
            // and recreate the same index. Taken inside the lifecycle monitor, as start() takes it for the build
            // on its own thread.
            lockIndexBuild();
            try {
                initializeIndices(entity);
            }
            finally {
                indexBuildLock.unlock();
            }
        }
    }

    /**
     * Below the web server's phase so the client outlives the requests using it, and above
     * {@code EmbeddedMongoLifecycle.PHASE} so an embedded server outlives this client:
     * Spring starts in ascending phase order and stops in descending.
     */
    public static final int LIFECYCLE_PHASE = -1000;

    private volatile boolean running;

    /**
     * Set once {@link #start()} has connected the datastore and built its indexes for the first time, after which
     * using the datastore no longer starts it.
     */
    private volatile boolean started;

    /**
     * Set by {@link #stop()} and cleared by {@link #start()}. A stopped datastore is started again only by a call to
     * {@link #start()}, never by being used, and a connection added while it is stopped is stopped too.
     */
    private volatile boolean stopped;

    /**
     * Set while the first start is under way, so that a session it opens does not start it again.
     */
    private boolean starting;

    private final Object lifecycleMonitor = new Object();

    /**
     * Set on each datastore whose client {@link #stop()} stopped or closed, so that {@link #start()} brings back
     * exactly those.
     */
    private volatile boolean clientStopped;

    /**
     * Set on each datastore, this one and the one for each connection, once the index build {@link #start()} runs for
     * it has finished, or been handed to its thread. A connection added while the datastore is stopped, or while it
     * starts, has not had one, and the next pass of {@link #start()} runs it.
     */
    private volatile boolean startupBuildDone;

    /**
     * Stops the {@link MongoClient} of every connection, so the process can be checkpointed.
     *
     * <p>CRaC refuses to checkpoint a process holding open sockets, and a connected driver
     * holds one per pooled connection plus its server monitors. Closing the driver client shuts
     * the monitor threads down and releases every socket, which nothing else in the driver
     * offers: draining the pool leaves the monitors connected. Each connection declared under
     * {@code grails.mongodb.connections}, or added at runtime, has a client of its own, and
     * each is stopped.
     *
     * <p>A {@link RestartableMongoClient}, which is what GORM creates, closes its driver client and
     * stays the client the datastore hands out, so whatever holds it - the {@code mongo} bean among
     * them - works again once {@link #start()} has restarted it. Any other client GORM owns is
     * closed, and {@link #start()} builds a replacement.
     *
     * <p>Every client is stopped whether or not the datastore has started: one used before it was started, or
     * connected by a {@link #start()} that then failed, holds sockets as much as one the datastore started. Until
     * {@link #start()} is called again the clients refuse to be used, and using the datastore does not start it.
     *
     * <p>A client the application supplied is left alone. Its lifecycle belongs to whoever
     * created it, and {@link #start()} does not replace it. A datastore that owns none of its
     * clients stays {@link #isRunning() running}.
     *
     * <p>A background index build still running, on this connection or any other, is interrupted first
     * rather than left to fail against a closed client, and {@link #start()} runs it again.
     */
    @Override
    public void stop() {
        synchronized (this.lifecycleMonitor) {
            this.stopped = true;
            List<MongoDatastore> datastores = datastoresAndChildren();
            List<MongoDatastore> owningTheirClient = new ArrayList<>();
            for (MongoDatastore datastore : datastores) {
                if (datastore.ownsClient()) {
                    owningTheirClient.add(datastore);
                }
            }
            if (owningTheirClient.isEmpty()) {
                return;
            }
            for (MongoDatastore datastore : datastores) {
                datastore.stopIndexBuild();
            }
            for (MongoDatastore datastore : owningTheirClient) {
                datastore.stopClient();
            }
            this.running = false;
        }
    }

    /**
     * Connects the datastore, which is the first point at which it opens a socket.
     *
     * <p>The first start connects the client of every connection GORM owns and builds the indexes the domain
     * classes declare, unless {@code grails.mongodb.buildIndexes} is {@code false}. Nothing before it connects: the
     * clients GORM creates are {@link RestartableMongoClient}s, which connect when first used, and building the
     * datastore builds no index. So a datastore created while an application context refreshes holds no socket
     * until Spring starts it in {@link #LIFECYCLE_PHASE}, which is what lets the process be checkpointed with CRaC as
     * the context refreshes ({@code spring.context.checkpoint=onRefresh}). Spring starts it before it publishes
     * {@code ContextRefreshedEvent}, so the indexes are in place before {@code BootStrap} runs and before the web
     * server accepts a request. A datastore that nothing starts - one built outside an application context - starts
     * itself the first time a session is opened on it.
     *
     * <p>Starting after {@link #stop()} brings back each {@link MongoClient} it stopped: a
     * {@link RestartableMongoClient} builds a new driver client and stays the one handed out, and any other client is
     * replaced by one built by the same factory the original was built with, so settings applied at startup still
     * apply. A replacement is handed out by the connection's {@link ConnectionSource} as well as by this datastore,
     * when that is a {@link MongoConnectionSource}. The indexes are not built again, since they outlive a checkpoint
     * on the server, but a background index build that {@link #stop()} cut short, or that was requested while
     * stopped, runs again on a fresh executor, on every connection. A connection added while the datastore was
     * stopped is connected and has its indexes built here.
     *
     * <p>A connection registered while this runs is not left out. One registered by another thread waits for it to
     * finish and is then connected and built as the running datastore's connections are; one registered by this
     * thread, from an index build hook, is taken up by another pass before this returns.
     */
    @Override
    public void start() {
        synchronized (this.lifecycleMonitor) {
            if (this.running) {
                return;
            }
            this.stopped = false;
            this.starting = true;
            try {
                startConnections();
                // Only once every build has finished, or been handed to its thread: one that failed is tried again
                // by the next start, or by the next use of a datastore that has never started.
                this.started = true;
                this.running = true;
            }
            finally {
                this.starting = false;
            }
        }
    }

    /**
     * Starts a datastore that nothing has started, the first time it is used. A datastore that has been stopped is
     * not started here: whatever stopped it starts it again, and until then its clients refuse to be used.
     */
    void ensureStarted() {
        if (this.started || this.closed || this.stopped) {
            return;
        }
        synchronized (this.lifecycleMonitor) {
            if (!this.started && !this.closed && !this.stopped && !this.starting) {
                start();
            }
        }
    }

    /**
     * Connects each client and runs each startup index build that is due, in passes, until a pass finds no
     * datastore it has not already seen: an index build hook can register a connection while this runs.
     */
    private void startConnections() {
        ConnectionSourceFactory<MongoClient, MongoConnectionSourceSettings> factory = connectionSources.getFactory();
        Set<MongoDatastore> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<MongoDatastore> pass = datastoresAndChildren();
        while (!pass.isEmpty()) {
            seen.addAll(pass);
            for (MongoDatastore datastore : pass) {
                datastore.startClient(factory, datastore == this);
            }
            for (MongoDatastore datastore : pass) {
                // Before either build: one submitted to the executor stop() shut down would be put off as
                // though the datastore were still stopped.
                datastore.replaceStoppedIndexBuildExecutor();
                if (!datastore.startupBuildDone) {
                    if (datastore.buildIndexes) {
                        // The full build does whatever a build cut short, or requested while stopped, would have.
                        datastore.indexBuildPending = false;
                    }
                    datastore.buildIndexAutomatically();
                    datastore.startupBuildDone = true;
                }
                datastore.runPendingIndexBuild();
            }
            List<MongoDatastore> next = new ArrayList<>();
            for (MongoDatastore datastore : datastoresAndChildren()) {
                if (!seen.contains(datastore)) {
                    next.add(datastore);
                }
            }
            pass = next;
        }
    }

    /**
     * Brings back a client {@link #stop()} stopped, or connects one GORM owns that has not connected yet.
     */
    private void startClient(ConnectionSourceFactory<MongoClient, MongoConnectionSourceSettings> factory, boolean defaultConnection) {
        if (this.clientStopped) {
            if (this.mongo instanceof RestartableMongoClient restartable) {
                restartable.start();
            }
            else {
                replaceClient(factory, defaultConnection);
            }
            this.clientStopped = false;
        }
        else if (ownsClient() && this.mongo instanceof RestartableMongoClient restartable) {
            restartable.start();
        }
    }

    /**
     * Stops a client GORM owns: a {@link RestartableMongoClient} closes its driver client and refuses to be used
     * until it is started again, and any other is closed, for {@link #start()} to replace.
     */
    private void stopClient() {
        if (this.mongo instanceof RestartableMongoClient restartable) {
            restartable.stop();
        }
        else {
            this.mongo.close();
        }
        this.clientStopped = true;
    }

    /**
     * Builds a replacement for a client {@link #stop()} closed. The default connection's is built from the
     * configuration again; any other reuses its settings, since a connection added at runtime was never part of
     * the configuration.
     */
    private void replaceClient(ConnectionSourceFactory<MongoClient, MongoConnectionSourceSettings> factory, boolean defaultConnection) {
        ConnectionSource<MongoClient, MongoConnectionSourceSettings> own = connectionSources.getDefaultConnectionSource();
        MongoClient replacement = defaultConnection ?
                factory.create(ConnectionSource.DEFAULT, connectionSources.getBaseConfiguration()).getSource() :
                factory.create(own.getName(), own.getSettings()).getSource();
        if (own instanceof MongoConnectionSource) {
            ((MongoConnectionSource) own).replaceSource(replacement);
        }
        else {
            // Only the connection source the factory creates can be given the replacement. A custom factory's
            // own kind cannot, so whatever reads the client from it, rather than from the datastore, would go on
            // using the one that was closed.
            LOG.warn("The connection source for [{}] is a {}, which cannot be given the client built for the " +
                    "restore, so it still hands out the one that was closed. A connection source factory whose " +
                    "clients outlive a restore should wrap each in a {}, or return a {}.", own.getName(),
                    own.getClass().getSimpleName(), RestartableMongoClient.class.getSimpleName(),
                    MongoConnectionSource.class.getSimpleName());
        }
        this.mongo = replacement;
    }

    /**
     * This datastore followed by the per-connection children, which have no lifecycle of their own.
     */
    private List<MongoDatastore> datastoresAndChildren() {
        List<MongoDatastore> datastores = new ArrayList<>();
        datastores.add(this);
        for (MongoDatastore datastore : datastoresByConnectionSource.values()) {
            if (datastore != this) {
                datastores.add(datastore);
            }
        }
        return datastores;
    }

    private void stopIndexBuild() {
        if (shutDownIndexBuild()) {
            indexBuildPending = true;
        }
    }

    /**
     * Replaces the executor {@link #stop()} shut down, so that a build submitted from here on runs. A build that
     * was running when it was interrupted records that it was cut short as it exits, so this waits for it to
     * have exited first.
     */
    private void replaceStoppedIndexBuildExecutor() {
        ExecutorService stopped = this.indexBuildExecutor;
        if (!stopped.isShutdown()) {
            // Never shut down: the datastore has not been stopped since it was last started.
            return;
        }
        try {
            if (!stopped.awaitTermination(INDEX_BUILD_EXIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                // Still running: it may yet finish, but assuming it will not costs only a repeat of work
                // that is idempotent.
                indexBuildPending = true;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            indexBuildPending = true;
        }
        this.indexBuildExecutor = newIndexBuildExecutor(connectionSources.getDefaultConnectionSource().getName());
    }

    /**
     * Runs a build {@link #stop()} cut short, or one requested while the datastore was stopped.
     */
    private void runPendingIndexBuild() {
        if (indexBuildPending) {
            indexBuildPending = false;
            LOG.info("Resuming the index build for connection [{}] that was pending while the datastore was stopped.",
                    connectionName());
            // On the background thread whatever the setting: only a background build can be cut short, and
            // with the setting off that is one buildIndexAsync() started.
            submitIndexBuild(null);
        }
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    /**
     * Whether GORM created the client and may close it, as opposed to it having been handed
     * in by the application.
     */
    private boolean ownsClient() {
        ConnectionSource<MongoClient, MongoConnectionSourceSettings> source = connectionSources.getDefaultConnectionSource();
        return !(source instanceof DefaultConnectionSource) || ((DefaultConnectionSource<?, ?>) source).isCloseable();
    }

    @Override
    @PreDestroy
    public void close() {
        List<MongoClient> inUse = new ArrayList<>();
        for (MongoDatastore datastore : datastoresAndChildren()) {
            if (datastore.ownsClient()) {
                inUse.add(datastore.mongo);
            }
        }
        // Set before the children are walked; see the connection sources listener.
        closed = true;
        for (MongoDatastore datastore : datastoresAndChildren()) {
            datastore.closed = true;
            datastore.shutDownIndexBuild();
        }
        try {
            super.destroy();
        } catch (Exception e) {
            // ignore
        }
        try {
            if (connectionSources != null) {
                connectionSources.close();
            }
            // A connection source other than a MongoConnectionSource closes the client it was built with,
            // which is no longer the one in use once a restore has replaced it. Closing one twice is harmless.
            for (MongoClient client : inUse) {
                client.close();
            }
        } catch (IOException e) {
            LOG.error("There was an error shutting down GORM for an entity: " + e.getMessage(), e);
        } finally {

            if (gormEnhancer != null) {
                try {
                    gormEnhancer.close();
                } catch (Throwable e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Interrupts the background index build, if there is one. A build can run for minutes, so shutdown
     * must not wait for it; the server carries on building what it was asked for.
     *
     * @return whether a build was still queued, and so never ran
     */
    private boolean shutDownIndexBuild() {
        // A build already running reports for itself whether it was cut short; one still queued never runs.
        List<Runnable> queued = this.indexBuildExecutor.shutdownNow();
        for (Runnable task : queued) {
            if (task instanceof IndexBuildTask build) {
                build.notRun("The datastore was stopped or closed before the index build for connection [" +
                        connectionName() + "] started.");
            }
        }
        return !queued.isEmpty();
    }

    /**
     * One worker, named after the connection it serves so that a log line or a thread dump says which
     * datastore is building indexes, and released after a second without work. The worker is a daemon: an
     * index build in flight must not hold the JVM open, and abandoning the wait does not abandon the build —
     * the server finishes an index it has been asked for whether or not a client is still listening.
     */
    private static ExecutorService newIndexBuildExecutor(String connectionName) {
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("gorm-mongo-index-build-" + connectionName + "-");
        threadFactory.setDaemon(true);
        return new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
    }

    /**
     * Creates the connection sources for a {@link MongoClient}.
     *
     * @param mongoClient The {@link MongoClient}
     * @param configuration The configuration
     * @param mappingContext The {@link MongoMappingContext}
     * @param closeable whether GORM owns the client and should close it on shutdown. Pass
     *                  {@code false} for an externally-supplied client (its lifecycle is owned by the
     *                  caller, e.g. a Spring-managed bean) and {@code true} for a client GORM created
     *                  itself, so it is not leaked.
     * @return The {@link ConnectionSources}
     */
    protected static ConnectionSources<MongoClient, MongoConnectionSourceSettings> createDefaultConnectionSources(MongoClient mongoClient, PropertyResolver configuration, MongoMappingContext mappingContext, boolean closeable) {
        // Bound from the configuration rather than left at the defaults: the client is supplied here, but
        // the settings that describe how the datastore behaves (multiTenancy, stateless, transactional,
        // buildIndexes, engine, flush mode) still come from grails.mongodb with grails.gorm fallbacks,
        // exactly as they do when GORM creates the client itself. The connection details in them are
        // unused - this client is already connected - so a configured URL is dropped: its database would
        // otherwise take precedence over the mapping context's, which is the one this path always used.
        MongoConnectionSourceSettings settings = buildConnectionSourceSettings(configuration);
        settings.url(null);
        settings.setDatabaseName(mappingContext.getDefaultDatabaseName());
        // One that GORM owns can be replaced after a restore; see start().
        ConnectionSource<MongoClient, MongoConnectionSourceSettings> defaultConnectionSource = closeable ?
                new MongoConnectionSource(ConnectionSource.DEFAULT, mongoClient, settings) :
                new DefaultConnectionSource<>(ConnectionSource.DEFAULT, mongoClient, settings, false);
        return new InMemoryConnectionSources<>(defaultConnectionSource, new MongoConnectionSourceFactory(), configuration);
    }

    protected static MongoClient createMongoClient(PropertyResolver configuration, MongoClientSettings.Builder mongoOptions, MongoMappingContext mappingContext) {
        MongoConnectionSourceFactory mongoConnectionSourceFactory = new MongoConnectionSourceFactory();
        mongoConnectionSourceFactory.setClientOptionsBuilder(mongoOptions);
        return mongoConnectionSourceFactory.create(ConnectionSource.DEFAULT, configuration).getSource();
    }

    protected static MongoMappingContext createMappingContext(ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources, Class... classes) {
        ConnectionSource<MongoClient, MongoConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        MongoMappingContext mongoMappingContext = new MongoMappingContext(defaultConnectionSource.getSettings(), classes);
        configureValidationRegistry(connectionSources.getDefaultConnectionSource().getSettings(), mongoMappingContext);
        return mongoMappingContext;
    }

    protected static MongoMappingContext createMappingContext(PropertyResolver configuration, Class... classes) {
        MongoConnectionSourceSettings mongoConnectionSourceSettings = buildConnectionSourceSettings(configuration);
        MongoMappingContext mongoMappingContext = new MongoMappingContext(mongoConnectionSourceSettings, classes);;
        configureValidationRegistry(mongoConnectionSourceSettings, mongoMappingContext);
        return mongoMappingContext;
    }

    private static MongoConnectionSourceSettings buildConnectionSourceSettings(PropertyResolver configuration) {
        return new MongoConnectionSourceSettingsBuilder(configuration, MongoSettings.PREFIX,
                new ConnectionSourceSettingsBuilder(configuration).build()).build();
    }

    protected void registerEntity(PersistentEntity entity) {
        String collectionName = entity.isRoot() ? entity.getDecapitalizedName() : entity.getRootEntity().getDecapitalizedName();
        String databaseName = this.defaultDatabase;

        MongoCollection collectionMapping = (MongoCollection) entity.getMapping().getMappedForm();
        if (collectionMapping.getCollection() != null) {
            collectionName = collectionMapping.getCollection();
        }
        if (collectionMapping.getDatabase() != null) {
            databaseName = collectionMapping.getDatabase();
        }

        mongoCollections.put(entity, collectionName);
        mongoDatabases.put(entity, databaseName);
    }

    private static void configureValidationRegistry(MongoConnectionSourceSettings settings, MongoMappingContext mongoMappingContext) {
        MessageSource messageSource = new StaticMessageSource();
        configureValidatorRegistry(settings, mongoMappingContext, messageSource);
    }

    private static void configureValidatorRegistry(MongoConnectionSourceSettings settings, MongoMappingContext mongoMappingContext, MessageSource messageSource) {
        ValidatorRegistry validatorRegistry = ValidatorRegistries.createValidatorRegistry(mongoMappingContext, settings, messageSource);
        if (validatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry) validatorRegistry).addConstraintFactory(
                    new MappingContextAwareConstraintFactory(UniqueConstraint.class, messageSource, mongoMappingContext)
            );
        }
        mongoMappingContext.setValidatorRegistry(
                validatorRegistry
        );
    }

    @Override
    public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return this.multiTenancyMode;
    }

    @Override
    public TenantResolver getTenantResolver() {
        return this.tenantResolver;
    }

    @Override
    public MongoDatastore getDatastoreForTenantId(Serializable tenantId) {
        if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            return this.datastoresByConnectionSource.get(tenantId.toString());
        }
        return this;
    }

    @Override
    public Datastore getDatastoreForConnection(String connectionName) {
        if (connectionName.equals(Settings.SETTING_DATASOURCE) || connectionName.equals(ConnectionSource.DEFAULT)) {
            return this;
        } else {
            MongoDatastore mongoDatastore = this.datastoresByConnectionSource.get(connectionName);
            if (mongoDatastore == null) {
                throw new ConfigurationException("DataSource not found for name [" + connectionName + "] in configuration. Please check your multiple data sources configuration and try again.");
            }
            return mongoDatastore;
        }
    }

    @Override
    public <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
        MongoDatastore mongoDatastore = getDatastoreForTenantId(tenantId);
        Session session = mongoDatastore.connect();
        try {
            DatastoreUtils.bindNewSession(session);
            return callable.call(session);
        }
        finally {
            DatastoreUtils.unbindSession(session);
        }
    }

    class PersistentEntityCodeRegistry implements CodecProvider {

        Map<String, PersistentEntityCodec> codecs = new HashMap<>();

        @Override
        public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
            final String entityName = clazz.getName();
            PersistentEntityCodec codec = codecs.get(entityName);
            if (codec == null) {
                final PersistentEntity entity = getMappingContext().getPersistentEntity(entityName);
                if (entity != null) {
                    codec = new PersistentEntityCodec(codecRegistry, entity);
                    codecs.put(entityName, codec);
                }
            }
            return codec;
        }
    }

    public AutoTimestampEventListener getAutoTimestampEventListener() {
        return this.autoTimestampEventListener;
    }
}

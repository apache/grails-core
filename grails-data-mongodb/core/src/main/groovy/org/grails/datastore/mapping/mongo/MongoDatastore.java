/* Copyright (C) 2010-2025 the original author or authors.
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

package org.grails.datastore.mapping.mongo;


import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.IndexOptions;
import grails.gorm.multitenancy.Tenants;
import grails.util.GrailsMessageSourceUtils;
import groovy.lang.Closure;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
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
import org.grails.datastore.mapping.core.*;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.mongo.config.MongoAttribute;
import org.grails.datastore.mapping.mongo.config.MongoCollection;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.grails.datastore.mapping.mongo.config.MongoSettings;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings;
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettingsBuilder;
import org.grails.datastore.mapping.mongo.engine.codecs.PersistentEntityCodec;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.FlushModeType;
import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Datastore implementation for the Mongo document store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MongoDatastore extends AbstractDatastore implements MappingContext.Listener, Closeable, StatelessDatastore, MultipleConnectionSourceCapableDatastore, MultiTenantCapableDatastore<MongoClient, MongoConnectionSourceSettings>, TransactionCapableDatastore {

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
    public static final String CODEC_ENGINE = MongoConstants.CODEC_ENGINE;

    protected final MongoClient mongo;
    protected final String defaultDatabase;
    protected final Map<PersistentEntity, String> mongoCollections = new ConcurrentHashMap<>();
    protected final Map<PersistentEntity, String> mongoDatabases = new ConcurrentHashMap<>();
    protected final boolean stateless;
    protected final boolean codecEngine;
    protected CodecRegistry codecRegistry;
    protected final ConfigurableApplicationEventPublisher eventPublisher;
    protected final PlatformTransactionManager transactionManager;
    protected final GormEnhancer gormEnhancer;
    protected final ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources;
    protected final FlushModeType defaultFlushMode;
    protected final Map<String, MongoDatastore> datastoresByConnectionSource = new LinkedHashMap<>();
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
        if(connectionSources == null) {
            throw new IllegalArgumentException("Argument [connectionSources] cannot be null");
        }
        if(mappingContext == null) {
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
        codecRegistry = CodecRegistries.fromRegistries(
                CodecRegistries.fromProviders(new CodecExtensions(), new PersistentEntityCodeRegistry()),
                mappingContext.getCodecRegistry(),
                MongoClientSettings.getDefaultCodecRegistry()
        );

        DatastoreTransactionManager datastoreTransactionManager = new DatastoreTransactionManager();
        datastoreTransactionManager.setDatastore(this);
        transactionManager = datastoreTransactionManager;
        for(PersistentEntity entity : mappingContext.getPersistentEntities()) {
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
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }

            connectionSources.addListener(new ConnectionSourcesListener<MongoClient, MongoConnectionSourceSettings>() {
                public void newConnectionSource(final ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource) {
                    final SingletonConnectionSources<MongoClient, MongoConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
                    MongoDatastore childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources);
                    datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
                    registerAllEntitiesWithEnhancer();
                }
            });
        }

        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            final TenantResolver baseResolver = multiTenancySettings.getTenantResolver();
            this.tenantResolver = new AllTenantsResolver() {
                @Override
                public Iterable<Serializable> resolveTenantIds() {
                    List<Serializable> ids = new ArrayList<>();
                    MongoIterable<String> databaseNames = defaultConnectionSource.getSource().listDatabaseNames();
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
                super.buildIndex();
                return null;
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
    public MongoDatastore(ConnectionSources<MongoClient, MongoConnectionSourceSettings> connectionSources, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
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
        this(createDefaultConnectionSources(mongoClient, configuration, mappingContext), mappingContext, eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(mongoClient, configuration, createMappingContext(configuration, classes), eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param eventPublisher The Spring ApplicationContext
     * @param packages The packages to scan
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Package...packages) {
        this(mongoClient, configuration, createMappingContext(configuration, new ClasspathEntityScanner().scan(packages)), eventPublisher);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param classes The persistent classes
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, Class...classes) {
        this(mongoClient, configuration, createMappingContext(configuration, classes), new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param packages The packages to scan
     */
    public MongoDatastore(MongoClient mongoClient, PropertyResolver configuration, Package...packages) {
        this(mongoClient, configuration, createMappingContext(configuration, new ClasspathEntityScanner().scan(packages)), new DefaultApplicationEventPublisher());
    }


    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param mongoClient The {@link MongoClient} instance
     * @param classes The persistent classes
     */
    public MongoDatastore(MongoClient mongoClient, Class...classes) {
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
        this(createMongoClient(configuration, clientOptions, mappingContext),  configuration, mappingContext,  eventPublisher);
    }


    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param clientOptions The {@link MongoClientSettings} instance
     * @param configuration The configuration
     * @param mappingContext The mapping context
     */
    public MongoDatastore(MongoClientSettings.Builder clientOptions, PropertyResolver configuration, MongoMappingContext mappingContext) {
        this(createMongoClient(configuration, clientOptions, mappingContext),  configuration, mappingContext,  new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param mappingContext The mapping context
     */
    public MongoDatastore(PropertyResolver configuration, MongoMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        this(ConnectionSourcesInitializer.create(new MongoConnectionSourceFactory(), configuration), mappingContext,  eventPublisher);
    }


    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param connectionSourceFactory The connection source factory to use
     * @param classes The persistent classes
     */
    public MongoDatastore(PropertyResolver configuration, MongoConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, configuration), eventPublisher, classes);
    }
    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public MongoDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
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
    public MongoDatastore(PropertyResolver configuration, Class...classes) {
        this(configuration, new DefaultApplicationEventPublisher(), classes);
    }


    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param classes The persistent classes
     */
    public MongoDatastore(Map<String, Object> configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(mapToPropertyResolver(configuration),eventPublisher, classes);
    }

    /**
     * Configures a new {@link MongoDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public MongoDatastore(Map<String, Object> configuration, Class...classes) {
        this(mapToPropertyResolver(configuration),new DefaultApplicationEventPublisher(), classes);
    }

    /**
     * Creates a MongoDatastore with the given configuration
     *
     * @param configuration The configuration
     */
    public MongoDatastore(Map<String, Object> configuration ) {
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
    public MongoDatastore(Class...classes) {
        this(mapToPropertyResolver(null), classes);
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(Package...packagesToScan) {
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
    public MongoDatastore(PropertyResolver configuration, Package...packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(Map<String,Object> configuration, Package...packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public MongoDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher,  Package...packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * @return The {@link ConnectionSources} for this datastore
     */
    public ConnectionSources<MongoClient, MongoConnectionSourceSettings> getConnectionSources() {
        return connectionSources;
    }

    /**
     * Builds the MongoDB index for this datastore
     */
    public void buildIndex() {
        for (PersistentEntity entity : this.mappingContext.getPersistentEntities()) {
            // Only create Mongo templates for entities that are mapped with Mongo
            if (!entity.isExternal()) {
                if(entity.isMultiTenant() && multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) continue;


                initializeIndices(entity);
            }
        }
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
        if(messageSource != null) {
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

    public String getDatabaseName(PersistentEntity entity) {
        if(entity.isMultiTenant() && multiTenancyMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return Tenants.currentId(getClass()).toString();
        }
        else {
            final String databaseName = mongoDatabases.get(entity);
            if(databaseName == null) {
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
        if(collectionName == null) {
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
        getMappingContext().addMappingContextListener(this);
        initializeConverters(this.mappingContext);

        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            public void persistentEntityAdded(PersistentEntity entity) {
                gormEnhancer.registerEntity(entity);
                registerEntity(entity);
            }
        });

        buildIndex();

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
                if(defaultConnectionSourceName.equals(ConnectionSource.ALL)) {
                    defaultConnectionSourceName = ConnectionSource.DEFAULT;
                }

                boolean isDefaultQualifier = qualifier.equals(ConnectionSource.DEFAULT);
                if(isDefaultQualifier && defaultConnectionSourceName.equals(ConnectionSource.DEFAULT)) {
                    return MongoDatastore.this;
                }
                else {
                    if(isDefaultQualifier) {
                        qualifier = defaultConnectionSourceName;
                    }
                    ConnectionSource<MongoClient, MongoConnectionSourceSettings> connectionSource = connectionSources.getConnectionSource(qualifier);
                    if(connectionSource == null) {
                        throw new ConfigurationException("Invalid connection ["+defaultConnectionSourceName+"] configured for class ["+cls+"]");
                    }

                    return datastoresByConnectionSource.get(qualifier);
                }
            }
        };


    }

    @Override
    protected Session createStatelessSession(PropertyResolver connectionDetails) {
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

        if(multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener(this));
        }
    }

    /**
     * Indexes any properties that are mapped with index:true
     *
     * @param entity The entity
     */
    protected void initializeIndices(final PersistentEntity entity) {
        final com.mongodb.client.MongoCollection<Document> collection = getCollection(entity);
        final ClassMapping<MongoCollection> classMapping = entity.getMapping();
        if (classMapping != null) {
            final MongoCollection mappedForm = classMapping.getMappedForm();
            if (mappedForm != null) {
                List<MongoCollection.Index> indices = mappedForm.getIndices();
                for (MongoCollection.Index index : indices) {
                    final Map<String, Object> options = index.getOptions();
                    final IndexOptions indexOptions = MongoConstants.mapToObject(IndexOptions.class, options);
                    collection.createIndex(new Document(index.getDefinition()), indexOptions);
                }

                for (Map compoundIndex : mappedForm.getCompoundIndices()) {

                    Map indexAttributes = null;
                    if (compoundIndex.containsKey(INDEX_ATTRIBUTES)) {
                        Object o = compoundIndex.remove(INDEX_ATTRIBUTES);
                        if (o instanceof Map) {
                            indexAttributes = (Map) o;
                        }
                    }
                    Document indexDef = new Document(compoundIndex);
                    if (indexAttributes != null) {
                        final IndexOptions indexOptions = MongoConstants.mapToObject(IndexOptions.class, indexAttributes);
                        collection.createIndex(indexDef, indexOptions);
                    } else {
                        collection.createIndex(indexDef);
                    }
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
                // continue using deprecated method to support older versions of MongoDB
                if (options.isEmpty()) {
                    collection.createIndex(dbObject);
                } else {
                    final IndexOptions indexOptions = MongoConstants.mapToObject(IndexOptions.class, options);
                    collection.createIndex(dbObject, indexOptions);
                }
            }
        }


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
        initializeIndices(entity);
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            super.destroy();
        } catch (Exception e) {
            // ignore
        }
        try {
            if (connectionSources != null) {
                connectionSources.close();
            }
        } catch(IOException e) {
            LOG.error("There was an error shutting down GORM for an entity: " + e.getMessage(), e);
        } finally {

            if(gormEnhancer != null) {
                try {
                    gormEnhancer.close();
                } catch (Throwable e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Creates the connection sources for an existing {@link MongoClient}
     *
     * @param mongoClient The {@link MongoClient}
     * @param configuration The configuration
     * @param mappingContext The {@link MongoMappingContext}
     * @return The {@link ConnectionSources}
     */
    protected static ConnectionSources<MongoClient, MongoConnectionSourceSettings> createDefaultConnectionSources(MongoClient mongoClient, PropertyResolver configuration, MongoMappingContext mappingContext) {
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings();
        settings.setDatabaseName(mappingContext.getDefaultDatabaseName());
        ConnectionSource<MongoClient, MongoConnectionSourceSettings> defaultConnectionSource = new DefaultConnectionSource<>(ConnectionSource.DEFAULT, mongoClient, settings);
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
        MongoConnectionSourceSettingsBuilder builder = new MongoConnectionSourceSettingsBuilder(configuration);
        MongoConnectionSourceSettings mongoConnectionSourceSettings = builder.build();
        MongoMappingContext mongoMappingContext = new MongoMappingContext(mongoConnectionSourceSettings, classes);;
        configureValidationRegistry(mongoConnectionSourceSettings, mongoMappingContext);
        return mongoMappingContext;
    }

    protected void registerEntity(PersistentEntity entity) {
        String collectionName = entity.isRoot() ? entity.getDecapitalizedName() : entity.getRootEntity().getDecapitalizedName();
        String databaseName = this.defaultDatabase;

        MongoCollection collectionMapping = (MongoCollection)entity.getMapping().getMappedForm();
        if(collectionMapping.getCollection() != null) {
            collectionName = collectionMapping.getCollection();
        }
        if(collectionMapping.getDatabase() != null) {
            databaseName = collectionMapping.getDatabase();
        }

        mongoCollections.put(entity, collectionName);
        mongoDatabases.put(entity,databaseName);
    }

    private static void configureValidationRegistry(MongoConnectionSourceSettings settings, MongoMappingContext mongoMappingContext) {
        MessageSource messageSource = new StaticMessageSource();
        configureValidatorRegistry(settings, mongoMappingContext, messageSource);
    }

    private static void configureValidatorRegistry(MongoConnectionSourceSettings settings, MongoMappingContext mongoMappingContext, MessageSource messageSource) {
        ValidatorRegistry validatorRegistry = ValidatorRegistries.createValidatorRegistry(mongoMappingContext, settings, messageSource);
        if(validatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry)validatorRegistry).addConstraintFactory(
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
        if(getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            return this.datastoresByConnectionSource.get(tenantId.toString());
        }
        return this;
    }

    @Override
    public Datastore getDatastoreForConnection(String connectionName) {
        if(connectionName.equals(Settings.SETTING_DATASOURCE) || connectionName.equals(ConnectionSource.DEFAULT)) {
            return this;
        } else {
            MongoDatastore mongoDatastore = this.datastoresByConnectionSource.get(connectionName);
            if(mongoDatastore == null) {
                throw new ConfigurationException("DataSource not found for name ["+connectionName+"] in configuration. Please check your multiple data sources configuration and try again.");
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

        Map<String, PersistentEntityCodec> codecs = new HashMap<String, PersistentEntityCodec>();

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

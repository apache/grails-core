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
package org.grails.datastore.gorm.neo4j;

import grails.neo4j.Relationship;
import groovy.lang.Closure;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.gorm.GormInstanceApi;
import org.grails.datastore.gorm.GormStaticApi;
import org.grails.datastore.gorm.GormValidationApi;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher;
import org.grails.datastore.gorm.events.DomainEventListener;
import org.grails.datastore.gorm.multitenancy.MultiTenantEventListener;
import org.grails.datastore.gorm.neo4j.api.Neo4jGormStaticApi;
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceFactory;
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings;
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettingsBuilder;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry;
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.StatelessDatastore;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.graph.GraphDatastore;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Simple;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;

import javax.annotation.PreDestroy;
import javax.persistence.FlushModeType;
import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.grails.datastore.gorm.neo4j.config.Settings.DATABASE_TYPE_EMBEDDED;
import static org.grails.datastore.gorm.neo4j.config.Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL;
import static org.grails.datastore.gorm.neo4j.config.Settings.SETTING_NEO4J_TYPE;

/**
 * Datastore implementation for Neo4j backend
 *
 * @author Stefan Armbruster (stefan@armbruster-it.de)
 * @author Graeme Rocher
 *
 * @since 1.0
 */
public class Neo4jDatastore extends AbstractDatastore implements Closeable, StatelessDatastore, GraphDatastore, Settings, MultipleConnectionSourceCapableDatastore, MultiTenantCapableDatastore<Driver, Neo4jConnectionSourceSettings>, MessageSourceAware, TransactionCapableDatastore {

    private static Logger log = LoggerFactory.getLogger(Neo4jDatastore.class);

    protected boolean skipIndexSetup = false;
    protected final Driver boltDriver;
    protected final FlushModeType defaultFlushMode;
    protected final ConfigurableApplicationEventPublisher eventPublisher;
    protected final Neo4jDatastoreTransactionManager transactionManager;
    protected final GormEnhancer gormEnhancer;
    protected final ConnectionSources<Driver, Neo4jConnectionSourceSettings> connectionSources;
    protected final Map<String, Neo4jDatastore> datastoresByConnectionSource = new LinkedHashMap<>();
    protected final TenantResolver tenantResolver;
    protected final MultiTenancySettings.MultiTenancyMode multiTenancyMode;
    protected final AutoTimestampEventListener autoTimestampEventListener;

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param mappingContext The {@link MappingContext} which contains information about the mapped classes
     * @param eventPublisher The Spring ApplicationContext
     */
    public Neo4jDatastore(ConnectionSources<Driver, Neo4jConnectionSourceSettings> connectionSources, Neo4jMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        super(mappingContext, connectionSources.getBaseConfiguration(), null);
        this.connectionSources = connectionSources;
        ConnectionSource<Driver, Neo4jConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        Neo4jConnectionSourceSettings settings = defaultConnectionSource.getSettings();
        MultiTenancySettings multiTenancySettings = settings.getMultiTenancy();

        this.boltDriver = defaultConnectionSource.getSource();
        this.eventPublisher = eventPublisher;
        this.defaultFlushMode = settings.getFlushMode();
        this.skipIndexSetup = !settings.isBuildIndex();
        this.multiTenancyMode = multiTenancySettings.getMode();
        this.tenantResolver = multiTenancySettings.getTenantResolver();
        this.autoTimestampEventListener = new AutoTimestampEventListener(this);

        if(!skipIndexSetup) {
            setupIndexing();
        }

        transactionManager = new Neo4jDatastoreTransactionManager(this);
        if(!(connectionSources instanceof SingletonConnectionSources)) {

            Iterable<ConnectionSource<Driver, Neo4jConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (ConnectionSource<Driver, Neo4jConnectionSourceSettings> connectionSource : allConnectionSources) {
                SingletonConnectionSources singletonConnectionSources = new SingletonConnectionSources(connectionSource, connectionSources.getBaseConfiguration());
                Neo4jDatastore childDatastore;

                if(ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
                    childDatastore = this;
                }
                else {
                    childDatastore = new Neo4jDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
                        @Override
                        protected GormEnhancer initialize(Neo4jConnectionSourceSettings settings) {
                            return null;
                        }
                    };
                }
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }
        }

        this.gormEnhancer = initialize(settings);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public Neo4jDatastore(ConnectionSources<Driver, Neo4jConnectionSourceSettings> connectionSources,  ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(connectionSources, createMappingContext(connectionSources,classes), eventPublisher);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param classes The persistent classes
     */
    public Neo4jDatastore(ConnectionSources<Driver, Neo4jConnectionSourceSettings> connectionSources, Class...classes) {
        this(connectionSources, createMappingContext(connectionSources,classes), new DefaultApplicationEventPublisher());
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param boltDriver The driver
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Driver boltDriver, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(createDefaultConnectionSources(boltDriver, configuration), eventPublisher, classes);
    }


    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param boltDriver The driver
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Driver boltDriver, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(createDefaultConnectionSources(boltDriver, DatastoreUtils.createPropertyResolver(null)), eventPublisher, classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param boltDriver The driver
     * @param configuration The configuration for the datastore
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Driver boltDriver, PropertyResolver configuration, Class...classes) {
        this(boltDriver, configuration, new DefaultApplicationEventPublisher(), classes);
    }
    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param boltDriver The driver
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Driver boltDriver, Class...classes) {
        this(boltDriver, DatastoreUtils.createPropertyResolver(null), new DefaultApplicationEventPublisher(), classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param connectionSourceFactory The {@link Neo4jConnectionSourceFactory} to use to configure Neo4j
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public Neo4jDatastore(PropertyResolver configuration, Neo4jConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, configuration), eventPublisher, classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param packagesToScan The packages to scan
     */
    public Neo4jDatastore(PropertyResolver configuration, Neo4jConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher, Package...packagesToScan) {
        this(configuration,connectionSourceFactory, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param classes The persistent classes
     */
    public Neo4jDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(ConnectionSourcesInitializer.create(new Neo4jConnectionSourceFactory(), configuration), eventPublisher, classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param eventPublisher The Spring ApplicationContext
     * @param packagesToScan The packages to scan
     */
    public Neo4jDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Package...packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }


    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration for the datastore
     * @param classes The persistent classes
     */
    public Neo4jDatastore(PropertyResolver configuration, Class...classes) {
        this(configuration, new DefaultApplicationEventPublisher(), classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments. This constructor is mainly used for testing, since no configuration is supplied
     * it will be default try to create an embedded server
     *
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Class...classes) {
        this(resolveEmbeddedConfiguration(), classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Map<String, Object> configuration, ConfigurableApplicationEventPublisher eventPublisher,Class...classes) {
        this(mapToPropertyResolver(configuration),eventPublisher, classes);
    }

    /**
     * Configures a new {@link Neo4jDatastore} for the given arguments
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public Neo4jDatastore(Map<String, Object> configuration, Class...classes) {
        this(configuration, new DefaultApplicationEventPublisher(), classes);
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param packagesToScan The packages to scan
     */
    public Neo4jDatastore(Package...packagesToScan) {
        this(new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param packageToScan The packages to scan
     */
    public Neo4jDatastore(Package packageToScan) {
        this(new ClasspathEntityScanner().scan(packageToScan));
    }


    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public Neo4jDatastore(PropertyResolver configuration, Package...packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Mongo datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public Neo4jDatastore(Map<String,Object> configuration, Package...packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
    }

    /**
     * @return The transaction manager
     */
    @Override
    public Neo4jDatastoreTransactionManager getTransactionManager() {
        return transactionManager;
    }


    @Override
    public ConfigurableApplicationEventPublisher getApplicationEventPublisher() {
        return this.eventPublisher;
    }

    @Override
    public Datastore getDatastoreForConnection(String connectionName) {
        return datastoresByConnectionSource.get(connectionName);
    }

    @Override
    public Neo4jMappingContext getMappingContext() {
        return (Neo4jMappingContext)super.getMappingContext();
    }

    /**
     * Creates the connection sources for an existing {@link Driver}
     *
     * @param driver The {@link Driver}
     * @param configuration The configuration
     * @return The {@link ConnectionSources}
     */
    protected static ConnectionSources<Driver, Neo4jConnectionSourceSettings> createDefaultConnectionSources(Driver driver, PropertyResolver configuration) {
        Neo4jConnectionSourceSettingsBuilder builder = new Neo4jConnectionSourceSettingsBuilder(configuration);
        Neo4jConnectionSourceSettings settings = builder.build();
        ConnectionSource<Driver, Neo4jConnectionSourceSettings> defaultConnectionSource = new DefaultConnectionSource<>(ConnectionSource.DEFAULT, driver, settings);
        return new InMemoryConnectionSources<>(defaultConnectionSource, new Neo4jConnectionSourceFactory(), configuration);
    }

    protected static Neo4jMappingContext createMappingContext(ConnectionSources<Driver, Neo4jConnectionSourceSettings> connectionSources, Class... classes) {
        ConnectionSource<Driver, Neo4jConnectionSourceSettings> defaultConnectionSource = connectionSources.getDefaultConnectionSource();
        Neo4jConnectionSourceSettings settings = defaultConnectionSource.getSettings();
        Neo4jMappingContext neo4jMappingContext = new Neo4jMappingContext(settings, classes);
        MessageSource messageSource = new StaticMessageSource();
        ValidatorRegistry defaultValidatorRegistry = createValidatorRegistry(settings, neo4jMappingContext, messageSource);
        neo4jMappingContext.setValidatorRegistry(
                defaultValidatorRegistry
        );
        return neo4jMappingContext;
    }

    private static PropertyResolver resolveEmbeddedConfiguration() {
        if(Neo4jConnectionSourceFactory.isEmbeddedAvailable()) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put(SETTING_NEO4J_TYPE, DATABASE_TYPE_EMBEDDED);
            config.put(SETTING_NEO4J_EMBEDDED_EPHEMERAL, true );
            return mapToPropertyResolver(config);
        }
        else {
            return mapToPropertyResolver(null);
        }
    }

    private static ValidatorRegistry createValidatorRegistry(Neo4jConnectionSourceSettings settings, Neo4jMappingContext neo4jMappingContext, MessageSource messageSource) {

        ValidatorRegistry defaultValidatorRegistry = ValidatorRegistries.createValidatorRegistry(neo4jMappingContext, settings, messageSource);
        configureValidatorRegistry(neo4jMappingContext, messageSource, defaultValidatorRegistry);
        return defaultValidatorRegistry;
    }

    private static void configureValidatorRegistry(Neo4jMappingContext neo4jMappingContext, MessageSource messageSource, ValidatorRegistry defaultValidatorRegistry) {
        if(defaultValidatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry)defaultValidatorRegistry).addConstraintFactory(
                    new MappingContextAwareConstraintFactory(UniqueConstraint.class, messageSource, neo4jMappingContext)
            );
        }
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(new DomainEventListener(this));
        eventPublisher.addApplicationListener(autoTimestampEventListener);
        if(multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener(this));
        }
    }


    protected GormEnhancer initialize(Neo4jConnectionSourceSettings settings) {
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
                Neo4jDatastore neo4jDatastore = getDatastoreForQualifier(cls, qualifier);
                return new Neo4jGormStaticApi<D>(cls, neo4jDatastore, createDynamicFinders(neo4jDatastore), neo4jDatastore.getTransactionManager());
            }

            @Override
            protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier) {
                Neo4jDatastore neo4jDatastore = getDatastoreForQualifier(cls, qualifier);
                return new GormValidationApi<>(cls, neo4jDatastore);
            }

            @Override
            protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier) {
                Neo4jDatastore neo4jDatastore = getDatastoreForQualifier(cls, qualifier);
                GormInstanceApi<D> instanceApi =  new GormInstanceApi<>(cls,neo4jDatastore);
                instanceApi.setFailOnError(getFailOnError());
                instanceApi.setMarkDirty(getMarkDirty());
                return instanceApi;
            }

            private <D> Neo4jDatastore getDatastoreForQualifier(Class<D> cls, String qualifier) {
                String defaultConnectionSourceName = ConnectionSourcesSupport.getDefaultConnectionSourceName(getMappingContext().getPersistentEntity(cls.getName()));
                boolean isDefaultQualifier = qualifier.equals(ConnectionSource.DEFAULT);
                if(isDefaultQualifier && defaultConnectionSourceName.equals(ConnectionSource.DEFAULT)) {
                    return Neo4jDatastore.this;
                }
                else {
                    if(isDefaultQualifier) {
                        qualifier = defaultConnectionSourceName;
                    }
                    ConnectionSource<Driver, Neo4jConnectionSourceSettings> connectionSource = connectionSources.getConnectionSource(qualifier);
                    if(connectionSource == null) {
                        throw new ConfigurationException("Invalid connection ["+defaultConnectionSourceName+"] configured for class ["+cls+"]");
                    }
                    return Neo4jDatastore.this.datastoresByConnectionSource.get(qualifier);
                }
            }
        };
    }

    public void setSkipIndexSetup(boolean skipIndexSetup) {
        this.skipIndexSetup = skipIndexSetup;
    }

    @Override
    protected org.grails.datastore.mapping.core.Session createSession(PropertyResolver connectionDetails) {
        final Neo4jSession neo4jSession = new Neo4jSession(this, mappingContext, eventPublisher, false, boltDriver);
        neo4jSession.setFlushMode(defaultFlushMode);
        return neo4jSession;
    }


    public void setupIndexing() {
        if(skipIndexSetup) return;
        List<String> schemaStrings = new ArrayList<String>(); // using set to avoid duplicate index creation

        for (PersistentEntity persistentEntity:  mappingContext.getPersistentEntities()) {
            if(persistentEntity.isExternal() || Relationship.class.isAssignableFrom(persistentEntity.getJavaClass())) continue;

            if(log.isDebugEnabled()) {
                log.debug("Setting up indexing for entity " + persistentEntity.getName());
            }
            final GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity) persistentEntity;
            for (String label: graphPersistentEntity.getLabels()) {
                StringBuilder sb = new StringBuilder();
                IdGenerator.Type idGeneratorType = graphPersistentEntity.getIdGeneratorType();
                if(graphPersistentEntity.getIdGenerator() != null && idGeneratorType.equals(IdGenerator.Type.SNOWFLAKE)) {
                    sb.append("CREATE CONSTRAINT ON (n:").append(label).append(") ASSERT n.").append(CypherBuilder.IDENTIFIER).append(" IS UNIQUE");
                    schemaStrings.add(sb.toString());
                }
                else if(!graphPersistentEntity.isNativeId()) {
                    createUniqueConstraintOnProperty(label, graphPersistentEntity.getIdentity(), schemaStrings);
                }


                for (PersistentProperty persistentProperty : persistentEntity.getPersistentProperties()) {
                    Property mappedForm = persistentProperty.getMapping().getMappedForm();
                    if ((persistentProperty instanceof Simple) && (mappedForm != null) ) {

                        if(mappedForm.isUnique()) {
                            createUniqueConstraintOnProperty(label, persistentProperty, schemaStrings);
                        }
                        else if(mappedForm.isIndex()) {
                            sb = new StringBuilder();
                            sb.append("CREATE INDEX ON :").append(label).append("(").append(persistentProperty.getName()).append(")");
                            schemaStrings.add(sb.toString());
                            if(log.isDebugEnabled()) {
                                log.debug("setting up indexing for " + label + " property " + persistentProperty.getName());
                            }
                        }
                    }
                }
            }
        }

        org.neo4j.driver.Session boltSession = boltDriver.session();

        final Transaction transaction = boltSession.beginTransaction();;
        try {
            for (String cypher: schemaStrings) {
                if(log.isDebugEnabled()) {
                    log.debug("CREATE INDEX Cypher [{}]", cypher);
                }
                transaction.run(cypher);
            }
            transaction.commit();
        } catch(Throwable e) {
            log.error("Error creating Neo4j index: " + e.getMessage(), e);
            transaction.rollback();
            throw new DatastoreConfigurationException("Error creating Neo4j index: " + e.getMessage(), e);
        }
        finally {
            boltSession.close();
        }
        if(log.isDebugEnabled()) {
            log.debug("done setting up indexes");
        }
    }

    private void createUniqueConstraintOnProperty(String label, PersistentProperty persistentProperty, List<String> schemaStrings) {
        StringBuilder sb;
        sb = new StringBuilder();

        sb.append("CREATE CONSTRAINT ON (n:").append(label).append(") ASSERT n.").append(persistentProperty.getName()).append(" IS UNIQUE");
        schemaStrings.add(sb.toString());
    }

    /**
     * @return The {@link Driver} used by this datastore
     */
    public Driver getBoltDriver() {
        return boltDriver;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        try {
            try {
                gormEnhancer.close();
            } catch (Throwable e) {
                // ignore
            }
            try {
                connectionSources.close();
            } catch (Neo4jException e) {
                log.error("Error shutting down Bolt driver: " + e.getMessage(), e);
            }
            super.destroy();
        } catch (Exception e) {
            throw new IOException("Error shutting down Neo4j datastore", e);
        }
    }

    @Override
    public ConnectionSources<Driver, Neo4jConnectionSourceSettings> getConnectionSources() {
        return this.connectionSources;
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
    public Neo4jDatastore getDatastoreForTenantId(Serializable tenantId) {
        if(getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            return this.datastoresByConnectionSource.get(tenantId.toString());
        }
        return this;
    }

    @Override
    public <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
        Neo4jDatastore neo4jDatastore = getDatastoreForTenantId(tenantId);
        org.grails.datastore.mapping.core.Session session = neo4jDatastore.connect();
        try {
            DatastoreUtils.bindNewSession(session);
            return callable.call(session);
        }
        finally {
            DatastoreUtils.unbindSession(session);
        }
    }


    @Override
    public void setMessageSource(MessageSource messageSource) {
        if (messageSource != null) {
            Neo4jMappingContext mappingContext = getMappingContext();
            ValidatorRegistry validatorRegistry = createValidatorRegistry(connectionSources.getDefaultConnectionSource().getSettings(), mappingContext, messageSource);
            configureValidatorRegistry(mappingContext, messageSource, validatorRegistry);
            mappingContext.setValidatorRegistry(validatorRegistry);
        }
    }

    public AutoTimestampEventListener getAutoTimestampEventListener() {
        return this.autoTimestampEventListener;
    }
}
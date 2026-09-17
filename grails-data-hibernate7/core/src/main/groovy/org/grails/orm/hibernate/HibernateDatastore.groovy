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
package org.grails.orm.hibernate

// Multi-datasource support currently uses a parent-child datastore map and child datastore instances.
// A future major release can replace this with a single CompositeDatastore approach.

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Callable

import javax.sql.DataSource

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import jakarta.annotation.PreDestroy
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.cfg.Environment
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.integrator.spi.Integrator
import org.hibernate.integrator.spi.IntegratorService
import org.hibernate.service.ServiceRegistry
import org.hibernate.tool.schema.Action
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.beans.BeanUtils
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceAware
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.env.PropertyResolver
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSource
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler
import org.grails.datastore.gorm.utils.ClasspathEntityScanner
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.ConnectionNotFoundException
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreAware
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.core.connections.SingletonConnectionSources
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.SchemaMultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.connections.HibernateConnectionSource
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.grails.orm.hibernate.event.listener.HibernateEventListener
import org.grails.orm.hibernate.multitenancy.MultiTenantEventListener
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider
import org.grails.orm.hibernate.query.HibernateQueryArgument
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor

/**
 * Datastore implementation that uses a Hibernate SessionFactory underneath.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
@SuppressWarnings([
    'PMD.CloseResource',
    'PMD.DataflowAnomalyAnalysis',
    'PMD.ConstructorCallsOverridableMethod',
    'PMD.AvoidFieldNameMatchingMethodName'
])
class HibernateDatastore extends AbstractDatastore
        implements ApplicationContextAware,
                Settings,
                SchemaMultiTenantCapableDatastore<SessionFactory, HibernateConnectionSourceSettings>,
                Closeable,
                MessageSourceAware,
                TransactionCapableDatastore,
                MultipleConnectionSourceCapableDatastore {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateDatastore)

    /** @deprecated Use {@link HibernateQueryArgument#CONFIG_CACHE_QUERIES} */
    @Deprecated(since = '8.0', forRemoval = true)
    public static final String CONFIG_PROPERTY_CACHE_QUERIES = HibernateQueryArgument.CONFIG_CACHE_QUERIES.value()

    /** @deprecated Use {@link HibernateQueryArgument#CONFIG_OSIV_READONLY} */
    @Deprecated(since = '8.0', forRemoval = true)
    public static final String CONFIG_PROPERTY_OSIV_READONLY = HibernateQueryArgument.CONFIG_OSIV_READONLY.value()

    /** @deprecated Use {@link HibernateQueryArgument#CONFIG_PASS_READONLY} */
    @Deprecated(since = '8.0', forRemoval = true)
    public static final String CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE =
            HibernateQueryArgument.CONFIG_PASS_READONLY.value()

    /** The session factory. */
    public static final String INFORMATION_SCHEMA = 'INFORMATION_SCHEMA'
    public static final String PUBLIC_SCHEMA = 'PUBLIC'

    protected SessionFactory sessionFactory

    /** The hibernate template. */
    protected IHibernateTemplate hibernateTemplate

    /** The instance API helper. */
    protected InstanceApiHelper instanceApiHelper

    /** The connection sources. */
    protected final ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources

    /** The default flush mode. */
    protected final FlushMode defaultFlushMode

    /** The multi tenant mode. */
    protected final MultiTenancySettings.MultiTenancyMode multiTenantMode

    /** The schema handler. */
    protected final SchemaHandler schemaHandler

    /** The event triggering interceptor. */
    protected final HibernateEventListener eventTriggeringInterceptor

    /** The auto timestamp event listener. */
    protected final AutoTimestampEventListener autoTimestampEventListener

    /** The osiv read only. */
    protected final boolean osivReadOnly

    /** The pass read only to hibernate. */
    protected final boolean passReadOnlyToHibernate

    /** The is cache queries. */
    protected final boolean isCacheQueries

    /** The fail on error. */
    protected final boolean failOnError

    /** The mark dirty. */
    protected final boolean markDirty

    /** The data source name. */
    protected final String dataSourceName

    /** The tenant resolver. */
    protected final TenantResolver tenantResolver

    protected boolean destroyed

    protected final GrailsHibernateTransactionManager transactionManager
    protected final ConfigurableApplicationEventPublisher eventPublisher
    protected final HibernateGormEnhancer gormEnhancer
    protected final Map<String, HibernateDatastore> datastoresByConnectionSource = Collections.synchronizedMap(new LinkedHashMap<>())
    protected final Metadata metadata
    protected final GrailsBytecodeProvider bytecodeProvider

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param connectionSources The {@link ConnectionSources} instance
     * @param mappingContext The {@link MappingContext} instance
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     */
    HibernateDatastore(
            ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources,
            HibernateMappingContext mappingContext,
            ConfigurableApplicationEventPublisher eventPublisher) {
        this(connectionSources, mappingContext, eventPublisher, (SessionFactory) null)
    }

    protected HibernateDatastore(
            ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources,
            HibernateMappingContext mappingContext,
            ConfigurableApplicationEventPublisher eventPublisher,
            SessionFactory sessionFactory) {
        super(mappingContext, connectionSources.baseConfiguration, null)
        this.connectionSources = connectionSources
        HibernateConnectionSource defaultConnectionSource =
                (HibernateConnectionSource) connectionSources.defaultConnectionSource

        ConnectionSourceFactory<SessionFactory, HibernateConnectionSourceSettings> factory = connectionSources.factory
        if (factory instanceof HibernateConnectionSourceFactory) {
            this.bytecodeProvider = ((HibernateConnectionSourceFactory) factory).bytecodeProvider
        } else {
            this.bytecodeProvider = new GrailsBytecodeProvider()
        }

        this.dataSourceName = ConnectionSource.DEFAULT
        this.sessionFactory = sessionFactory != null ? sessionFactory : defaultConnectionSource.source

        HibernateConnectionSourceSettings settings = defaultConnectionSource.settings
        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.hibernate
        this.osivReadOnly = hibernateSettings.osiv.readonly
        this.passReadOnlyToHibernate = hibernateSettings.isReadOnly()
        this.isCacheQueries = hibernateSettings.cache.queries
        this.failOnError = settings.isFailOnError()
        Boolean markDirty = settings.markDirty
        this.markDirty = markDirty != null && markDirty
        this.defaultFlushMode = FlushMode.valueOf(hibernateSettings.flush.mode.name())

        MultiTenancySettings multiTenancySettings = settings.multiTenancy
        TenantResolver multiTenantResolver = multiTenancySettings.tenantResolver
        this.multiTenantMode = multiTenancySettings.mode

        Class<? extends SchemaHandler> schemaHandlerClass = settings.dataSource.schemaHandler
        this.schemaHandler = BeanUtils.instantiateClass(schemaHandlerClass)
        this.tenantResolver = multiTenantResolver
        if (multiTenantResolver instanceof DatastoreAware) {
            ((DatastoreAware) multiTenantResolver).setDatastore(this)
        }

        this.metadata = getMetadataInternal()

        this.transactionManager = new GrailsHibernateTransactionManager(
                defaultConnectionSource.source, defaultConnectionSource.dataSource, defaultFlushMode)
        this.eventPublisher = eventPublisher
        this.eventTriggeringInterceptor = new HibernateEventListener(this)
        this.autoTimestampEventListener = new AutoTimestampEventListener(this)

        ClosureEventTriggeringInterceptor interceptor = hibernateSettings.eventTriggeringInterceptor
        interceptor.setDatastore(this)
        interceptor.setEventPublisher(eventPublisher)
        registerEventListeners(this.eventPublisher)
        configureValidatorRegistry(mappingContext)
        this.@mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            void persistentEntityAdded(PersistentEntity entity) {
                if (gormEnhancer != null) {
                    gormEnhancer.registerEntity(entity)
                }
            }
        })
        initializeConverters(this.@mappingContext)

        if (!(connectionSources instanceof SingletonConnectionSources)) {
            HibernateDatastore parent = this
            Iterable<ConnectionSource<SessionFactory, HibernateConnectionSourceSettings>> allConnectionSources =
                    connectionSources.allConnectionSources
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource :
                    allConnectionSources) {
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(
                                connectionSource, connectionSources.baseConfiguration)
                HibernateDatastore childDatastore

                if (ConnectionSource.DEFAULT == connectionSource.name) {
                    childDatastore = this
                } else {
                    childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources)
                }
                datastoresByConnectionSource.put(connectionSource.name, childDatastore)
            }

            connectionSources.addListener({ ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource ->
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(
                                connectionSource, connectionSources.baseConfiguration)
                HibernateDatastore childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources)
                datastoresByConnectionSource.put(connectionSource.name, childDatastore)
                registerAllEntitiesWithEnhancer()
            })

            if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                if (this.tenantResolver instanceof AllTenantsResolver) {
                    AllTenantsResolver allTenantsResolver = (AllTenantsResolver) this.tenantResolver
                    Iterable<Serializable> tenantIds = allTenantsResolver.resolveTenantIds()
                    for (Serializable tenantId : tenantIds) {
                        addTenantForSchemaInternal(tenantId.toString())
                    }
                } else {
                    Collection<String> allSchemas = schemaHandler.resolveSchemaNames(defaultConnectionSource.dataSource)
                    for (String schema : allSchemas) {
                        addTenantForSchemaInternal(schema)
                    }
                }
            }
        }

        this.gormEnhancer = initialize()
    }

    private HibernateDatastore createChildDatastore(
            HibernateMappingContext mappingContext,
            ConfigurableApplicationEventPublisher eventPublisher,
            HibernateDatastore parent,
            SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources) {
        return new ChildHibernateDatastore(parent, singletonConnectionSources, mappingContext, eventPublisher)
    }

    HibernateDatastore(
            PropertyResolver configuration,
            HibernateConnectionSourceFactory connectionSourceFactory,
            ConfigurableApplicationEventPublisher eventPublisher) {
        this(
                ConnectionSourcesInitializer.create(
                        connectionSourceFactory,
                        DatastoreUtils.preparePropertyResolver(configuration, 'dataSource', 'hibernate', 'grails')),
                connectionSourceFactory.mappingContext,
                eventPublisher)
    }

    HibernateDatastore(
            PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory) {
        this(
                ConnectionSourcesInitializer.create(
                        connectionSourceFactory,
                        DatastoreUtils.preparePropertyResolver(configuration, 'dataSource', 'hibernate', 'grails')),
                connectionSourceFactory.mappingContext,
                new DefaultApplicationEventPublisher())
    }

    HibernateDatastore(
            PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class<?>... classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes), eventPublisher)
    }

    HibernateDatastore(
            DataSource dataSource,
            PropertyResolver configuration,
            ConfigurableApplicationEventPublisher eventPublisher,
            Class<?>... classes) {
        this(configuration, createConnectionFactoryForDataSource(dataSource, classes), eventPublisher)
    }

    HibernateDatastore(
            PropertyResolver configuration,
            ConfigurableApplicationEventPublisher eventPublisher,
            Package... packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan))
    }

    HibernateDatastore(
            DataSource dataSource,
            PropertyResolver configuration,
            ConfigurableApplicationEventPublisher eventPublisher,
            Package... packagesToScan) {
        this(dataSource, configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan))
    }

    HibernateDatastore(PropertyResolver configuration, Class<?>... classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes))
    }

    HibernateDatastore(PropertyResolver configuration, Package... packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan))
    }

    HibernateDatastore(Map<String, Object> configuration, Class<?>... classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), new HibernateConnectionSourceFactory(classes))
    }

    HibernateDatastore(Map<String, Object> configuration, Package... packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan)
    }

    HibernateDatastore(Class<?>... classes) {
        this(
                DatastoreUtils.createPropertyResolver(
                        (Map<String, Object>) Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop')),
                new HibernateConnectionSourceFactory(classes))
    }

    HibernateDatastore(Package... packagesToScan) {
        this(new ClasspathEntityScanner().scan(packagesToScan))
    }

    HibernateDatastore(Package packageToScan) {
        this(new ClasspathEntityScanner().scan(packageToScan))
    }

    @SuppressWarnings('PMD.NullAssignment')
    protected HibernateDatastore(
            MappingContext mappingContext,
            SessionFactory sessionFactory,
            PropertyResolver config,
            ApplicationContext applicationContext,
            String dataSourceName) {
        super(mappingContext, config, (ConfigurableApplicationContext) applicationContext)
        this.connectionSources = new SingletonConnectionSources<>(
                new HibernateConnectionSource(dataSourceName, sessionFactory, null, null), config)
        this.sessionFactory = sessionFactory
        this.dataSourceName = dataSourceName
        this.bytecodeProvider = new GrailsBytecodeProvider()
        initializeConverters(mappingContext)
        if (applicationContext != null) {
            setApplicationContext(applicationContext)
        }

        this.osivReadOnly =
                config.getProperty(HibernateQueryArgument.CONFIG_OSIV_READONLY.value(), Boolean, false)
        this.passReadOnlyToHibernate =
                config.getProperty(HibernateQueryArgument.CONFIG_PASS_READONLY.value(), Boolean, false)
        this.isCacheQueries =
                config.getProperty(HibernateQueryArgument.CONFIG_CACHE_QUERIES.value(), Boolean, false)

        if (config.getProperty(SETTING_AUTO_FLUSH, Boolean, false)) {
            this.defaultFlushMode = FlushMode.AUTO
        } else {
            this.defaultFlushMode = config.getProperty(SETTING_FLUSH_MODE, FlushMode, FlushMode.COMMIT)
        }
        this.failOnError = config.getProperty(SETTING_FAIL_ON_ERROR, Boolean, false)
        this.markDirty = config.getProperty(SETTING_MARK_DIRTY, Boolean, false)
        this.tenantResolver = new FixedTenantResolver()
        this.multiTenantMode = MultiTenancySettings.MultiTenancyMode.NONE
        this.schemaHandler = new DefaultSchemaHandler()
        this.transactionManager = null
        this.eventPublisher = null
        this.eventTriggeringInterceptor = null
        this.autoTimestampEventListener = null
        this.gormEnhancer = null
        this.metadata = null
    }

    HibernateDatastore(MappingContext mappingContext, SessionFactory sessionFactory, PropertyResolver config) {
        this(mappingContext, sessionFactory, config, null, ConnectionSource.DEFAULT)
    }

    @Override
    ApplicationEventPublisher getApplicationEventPublisher() {
        return this.eventPublisher
    }

    /**
     * @return The {@link PlatformTransactionManager} instance
     */
    @Override
    PlatformTransactionManager getTransactionManager() {
        return transactionManager
    }

    @Override
    HibernateDatastore getDatastoreForConnection(String connectionName) {
        if (Settings.SETTING_DATASOURCE == connectionName ||
                ConnectionSource.DEFAULT == connectionName ||
                ConnectionSource.OLD_DEFAULT == connectionName) {
            return this
        } else {
            HibernateDatastore hibernateDatastore = this.datastoresByConnectionSource.get(connectionName)
            if (hibernateDatastore == null) {
                throw new ConfigurationException("DataSource not found for name [${connectionName}] in configuration. Please check your multiple data sources configuration and try again.".toString())
            }
            return hibernateDatastore
        }
    }

    @Override
    String toString() {
        return "HibernateDatastore: ${dataSourceName}".toString()
    }

    @Override
    HibernateMappingContext getMappingContext() {
        return (HibernateMappingContext) super.mappingContext
    }

    @Override
    void setMessageSource(@Nullable MessageSource messageSource) {
        HibernateMappingContext mappingContext = getMappingContext()
        ValidatorRegistry validatorRegistry = createValidatorRegistry(messageSource)
        configureValidatorRegistry(mappingContext, validatorRegistry, messageSource)
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        if (autoTimestampEventListener != null) {
            eventPublisher.addApplicationListener(autoTimestampEventListener)
        }
        if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener())
        }
        if (eventTriggeringInterceptor != null) {
            eventPublisher.addApplicationListener(eventTriggeringInterceptor)
        }
    }

    protected void configureValidatorRegistry(HibernateMappingContext mappingContext) {
        StaticMessageSource messageSource = new StaticMessageSource()
        ValidatorRegistry defaultValidatorRegistry = createValidatorRegistry(messageSource)
        configureValidatorRegistry(mappingContext, defaultValidatorRegistry, messageSource)
    }

    protected void configureValidatorRegistry(
            HibernateMappingContext mappingContext, ValidatorRegistry validatorRegistry, MessageSource messageSource) {
        if (validatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry) validatorRegistry)
                    .addConstraintFactory(new MappingContextAwareConstraintFactory(
                            UniqueConstraint, messageSource, mappingContext))
        }
        mappingContext.validatorRegistry = validatorRegistry
    }

    protected HibernateGormEnhancer initialize() {
        HibernateConnectionSource defaultConnectionSource =
                (HibernateConnectionSource) connectionSources.defaultConnectionSource
        if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return new SchemaTenantGormEnhancer(
                    this,
                    transactionManager,
                    defaultConnectionSource,
                    tenantResolver,
                    schemaHandler,
                    datastoresByConnectionSource
            )
        } else {
            return new HibernateGormEnhancer(this, transactionManager, defaultConnectionSource.settings)
        }
    }

    @Override
    boolean hasCurrentSession() {
        return TransactionSynchronizationManager.getResource(sessionFactory) != null
    }

    @Override
    protected Session createSession(PropertyResolver connectionDetails) {
        return new HibernateSession(this, sessionFactory)
    }

    @Override
    void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            super.setApplicationContext(applicationContext)

            for (HibernateDatastore hibernateDatastore : datastoresByConnectionSource.values()) {
                if (!Objects.equals(hibernateDatastore, this)) {
                    hibernateDatastore.setApplicationContext(applicationContext)
                }
            }
            ConfigurableApplicationContextEventPublisher publisher = new ConfigurableApplicationContextEventPublisher((ConfigurableApplicationContext) applicationContext)

            HibernateConnectionSourceSettings settings = connectionSources.defaultConnectionSource.settings
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.hibernate
            ClosureEventTriggeringInterceptor interceptor = hibernateSettings.eventTriggeringInterceptor
            interceptor.setDatastore(this)
            interceptor.setEventPublisher(publisher)

            HibernateMappingContext mappingContext = getMappingContext()
            ValidatorRegistry validatorRegistry = createValidatorRegistry(applicationContext)
            configureValidatorRegistry(mappingContext, validatorRegistry, applicationContext)
            mappingContext.validatorRegistry = validatorRegistry

            registerEventListeners(publisher)
            publisher.publishEvent(new DatastoreInitializedEvent(this))
        }
    }

    IHibernateTemplate getHibernateTemplate(int flushMode) {
        return new GrailsHibernateTemplate(sessionFactory, this, flushMode)
    }

    void withFlushMode(FlushMode flushMode, Callable<Boolean> callable) {
        org.hibernate.Session session = sessionFactory.currentSession
        org.hibernate.FlushMode previousMode = null
        Boolean reset = true
        try {
            if (session != null) {
                previousMode = session.hibernateFlushMode
                session.setHibernateFlushMode(flushMode)
            }
            try {
                reset = callable.call()
            } catch (Exception e) {
                reset = false
            }
        } finally {
            if (session != null && previousMode != null && reset) {
                session.setHibernateFlushMode(previousMode)
            }
        }
    }

    org.hibernate.Session openSession() {
        org.hibernate.Session session = this.sessionFactory.openSession()
        session.setHibernateFlushMode(defaultFlushMode)
        return session
    }

    @Override
    Session getCurrentSession() throws ConnectionNotFoundException {
        return new HibernateSession(this, sessionFactory)
    }

    @Override
    void destroy() {
        if (!this.destroyed) {
            try {
                for (HibernateDatastore childDatastore : datastoresByConnectionSource.values()) {
                    if (!childDatastore.is(this) && !childDatastore.mappingContext.is(mappingContext)) {
                        childDatastore.destroy()
                    }
                }
                super.destroy()
                HibernateGormInstanceApi.resetInsertActive()
                try {
                    closeConnectionSources()
                } catch (IOException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error('There was an error shutting down GORM for an entity: {}', e.message, e)
                    }
                }
            } finally {
                getMappingContext().mappingCacheHolder.clear()
                try {
                    closeGormEnhancer()
                } catch (IOException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error('There was an error shutting down GORM enhancer', e)
                    }
                }
                destroyed = true
            }
        }
    }

    @Override
    void addTenantForSchema(String schemaName) {
        if (!datastoresByConnectionSource.containsKey(schemaName)) {
            addTenantForSchemaInternal(schemaName)
            registerAllEntitiesWithEnhancer()
        }
        HibernateConnectionSource defaultConnectionSource =
                (HibernateConnectionSource) connectionSources.defaultConnectionSource
        DataSource dataSource = defaultConnectionSource.dataSource
        if (dataSource instanceof TransactionAwareDataSourceProxy) {
            dataSource = ((TransactionAwareDataSourceProxy) dataSource).targetDataSource
        }
        if (dataSource == null) {
            return
        }
        Object existing = TransactionSynchronizationManager.getResource(dataSource)
        if (existing instanceof ConnectionHolder) {
            ConnectionHolder connectionHolder = (ConnectionHolder) existing
            Connection connection = connectionHolder.connection
            try {
                if (!connection.isClosed() && !connection.isReadOnly()) {
                    schemaHandler.useDefaultSchema(connection)
                }
            } catch (SQLException e) {
                throw new DatastoreConfigurationException("Failed to reset to default schema: ${e.message}".toString(), e)
            }
        }
    }

    final Metadata getMetadata() {
        return metadata
    }

    protected void registerAllEntitiesWithEnhancer() {
        for (PersistentEntity persistentEntity : this.@mappingContext.persistentEntities) {
            gormEnhancer.registerEntity(persistentEntity)
        }
    }

    protected void closeConnectionSources() throws IOException {
        connectionSources.close()
    }

    protected void closeGormEnhancer() throws IOException {
        if (this.gormEnhancer != null) {
            this.gormEnhancer.close()
        }
    }

    private void addTenantForSchemaInternal(String schemaName) {
        if (multiTenantMode != MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            throw new ConfigurationException(
                    "The method [addTenantForSchema] can only be called with multi-tenancy mode SCHEMA. Current mode is: ${multiTenantMode}".toString())
        }
        HibernateConnectionSourceFactory factory = (HibernateConnectionSourceFactory) connectionSources.factory
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.defaultConnectionSource
        HibernateConnectionSourceSettings settings = connectionSources.defaultConnectionSource.settings
        HibernateConnectionSourceSettings tenantSettings
        try {
            tenantSettings = (HibernateConnectionSourceSettings) settings.clone()
        } catch (CloneNotSupportedException e) {
            throw new ConfigurationException("Couldn't clone default Hibernate settings! ${e.message}".toString(), e)
        }
        tenantSettings.hibernate.put(Environment.DEFAULT_SCHEMA, schemaName)

        String dbCreate = tenantSettings.dataSource.dbCreate

        Action schemaAutoTooling = Action.interpretHbm2ddlSetting(dbCreate)
        if (schemaAutoTooling != Action.VALIDATE && schemaAutoTooling != Action.NONE) {
            Connection connection = null
            try {
                connection = defaultConnectionSource.dataSource.connection
                try {
                    schemaHandler.useSchema(connection, schemaName)
                } catch (Exception e) {
                    schemaHandler.createSchema(connection, schemaName)
                }
                schemaHandler.useDefaultSchema(connection)
            } catch (SQLException e) {
                throw new DatastoreConfigurationException(
                        String.format('Failed to create schema for name [%s]', schemaName), e)
            } finally {
                connection?.close()
            }
        }

        DataSource dataSource = defaultConnectionSource.dataSource
        dataSource = new SchemaTenantDataSource(dataSource, schemaName, schemaHandler)
        DefaultConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource =
                new DefaultConnectionSource<>(schemaName, dataSource, tenantSettings.dataSource)
        try {
            ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource =
                    factory.create(schemaName, dataSourceConnectionSource, tenantSettings)
            HibernateDatastore childDatastore = getChildDatastore(connectionSource)
            datastoresByConnectionSource.put(connectionSource.name, childDatastore)
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(dataSource)
        }
    }

    private HibernateDatastore getChildDatastore(
            ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource) {
        SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources =
                new SingletonConnectionSources<>(connectionSource, connectionSources.baseConfiguration)
        return createChildDatastore((HibernateMappingContext) this.@mappingContext, eventPublisher, this, singletonConnectionSources)
    }

    private Metadata getMetadataInternal() {
        Metadata m = null
        if (sessionFactory instanceof SessionFactoryImplementor) {
            SessionFactoryImplementor sfi = (SessionFactoryImplementor) sessionFactory
            ServiceRegistry bootstrapServiceRegistry = sfi.serviceRegistry.parentServiceRegistry
            if (bootstrapServiceRegistry != null) {
                IntegratorService integratorService = bootstrapServiceRegistry.getService(IntegratorService)
                if (integratorService != null) {
                    for (Integrator integrator : integratorService.integrators) {
                        if (integrator instanceof MetadataIntegrator) {
                            m = ((MetadataIntegrator) integrator).metadata
                        }
                    }
                }
            }
        }
        return m
    }

    private static HibernateConnectionSourceFactory createConnectionFactoryForDataSource(
            DataSource dataSource, Class<?>... classes) {
        HibernateConnectionSourceFactory hibernateConnectionSourceFactory =
                new HibernateConnectionSourceFactory(classes)
        hibernateConnectionSourceFactory.dataSourceConnectionSourceFactory = new DataSourceConnectionSourceFactory() {
            @Override
            ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {
                if (ConnectionSource.DEFAULT == name) {
                    return new DataSourceConnectionSource(ConnectionSource.DEFAULT, dataSource, settings)
                } else {
                    return super.create(name, settings)
                }
            }
        }
        return hibernateConnectionSourceFactory
    }

    protected ValidatorRegistry createValidatorRegistry(MessageSource messageSource) {
        return ValidatorRegistries.createValidatorRegistry(
                this.@mappingContext,
                connectionSources.defaultConnectionSource.settings,
                messageSource)
    }

    @Override
    MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return this.multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA ?
                MultiTenancySettings.MultiTenancyMode.DATABASE :
                this.multiTenantMode
    }

    @Override
    Datastore getDatastoreForTenantId(Serializable tenantId) {
        if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            return getDatastoreForConnection(tenantId.toString())
        } else {
            return this
        }
    }

    @Override
    TenantResolver getTenantResolver() {
        return this.tenantResolver
    }

    @Override
    ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> getConnectionSources() {
        return this.connectionSources
    }

    Iterable<Serializable> resolveTenantIds() {
        if (this.tenantResolver instanceof AllTenantsResolver) {
            return ((AllTenantsResolver) this.tenantResolver).resolveTenantIds()
        } else if (this.multiTenantMode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            List<Serializable> tenantIds = []
            for (ConnectionSource<?, ?> connectionSource : this.connectionSources.allConnectionSources) {
                if (ConnectionSource.DEFAULT != connectionSource.name) {
                    tenantIds.add(connectionSource.name)
                }
            }
            return tenantIds
        } else {
            return Collections.emptyList()
        }
    }

    Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        return Tenants.currentId(this)
    }

    boolean isAutoFlush() {
        return defaultFlushMode == FlushMode.AUTO
    }

    FlushMode getDefaultFlushMode() {
        return defaultFlushMode
    }

    String getDefaultFlushModeName() {
        return defaultFlushMode.name()
    }

    boolean isFailOnError() {
        return failOnError
    }

    boolean isOsivReadOnly() {
        return osivReadOnly
    }

    boolean isPassReadOnlyToHibernate() {
        return passReadOnlyToHibernate
    }

    boolean isCacheQueries() {
        return isCacheQueries
    }

    SessionFactory getSessionFactory() {
        return sessionFactory
    }

    /**
     * @param connectionName The connection name
     * @return The {@link SessionFactory} being used by this datastore instance
     */
    SessionFactory getSessionFactory(String connectionName) {
        return getDatastoreForConnection(connectionName).sessionFactory
    }

    DataSource getDataSource() {
        return ((HibernateConnectionSource) this.connectionSources.defaultConnectionSource).dataSource
    }

    DataSource getDataSource(String connectionName) {
        return getDatastoreForConnection(connectionName).dataSource
    }

    PlatformTransactionManager getTransactionManager(String connectionName) {
        return getDatastoreForConnection(connectionName).transactionManager
    }

    HibernateEventListener getEventTriggeringInterceptor() {
        return eventTriggeringInterceptor
    }

    AutoTimestampEventListener getAutoTimestampEventListener() {
        return autoTimestampEventListener
    }

    String getDataSourceName() {
        return this.dataSourceName
    }

    IHibernateTemplate getHibernateTemplate() {
        if (this.hibernateTemplate == null) {
            this.hibernateTemplate = new GrailsHibernateTemplate(sessionFactory, this)
        }
        return this.hibernateTemplate
    }

    InstanceApiHelper getInstanceApiHelper() {
        if (this.instanceApiHelper == null) {
            this.instanceApiHelper = new InstanceApiHelper((GrailsHibernateTemplate) getHibernateTemplate())
        }
        return this.instanceApiHelper
    }

    @Override
    <T> T withSession(Closure<T> callable) {
        Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable)
        return getHibernateTemplate().execute(multiTenantCallable)
    }

    <T> T withSession(String connectionName, Closure<T> callable) {
        HibernateDatastore datastore = getDatastoreForConnection(connectionName)
        Closure<T> multiTenantCallable = datastore.prepareMultiTenantClosure(callable)
        return datastore.getHibernateTemplate().execute(multiTenantCallable)
    }

    <T> T withNewSession(String connectionName, Closure<T> callable) {
        HibernateDatastore datastore = getDatastoreForConnection(connectionName)
        Closure<T> multiTenantCallable = datastore.prepareMultiTenantClosure(callable)
        return datastore.getHibernateTemplate().executeWithNewSession(multiTenantCallable)
    }

    <T> T withNewSession(Closure<T> callable) {
        Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable)
        return getHibernateTemplate().executeWithNewSession(multiTenantCallable)
    }

    @Override
    <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
        if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            HibernateDatastore datastore = getDatastoreForConnection(tenantId.toString())
            SessionFactory sf = datastore.sessionFactory
            return datastore.getHibernateTemplate().executeWithExistingOrCreateNewSession(sf, callable)
        } else {
            return withNewSession(callable)
        }
    }

    void enableMultiTenancyFilter() {
        Serializable currentId = Tenants.currentId(this)
        if (ConnectionSource.DEFAULT == currentId) {
            disableMultiTenancyFilter()
        } else {
            getHibernateTemplate()
                    .sessionFactory
                    .currentSession
                    .enableFilter(GormProperties.TENANT_IDENTITY)
                    .setParameter(GormProperties.TENANT_IDENTITY, currentId)
        }
    }

    void disableMultiTenancyFilter() {
        getHibernateTemplate().sessionFactory.currentSession.disableFilter(GormProperties.TENANT_IDENTITY)
    }

    protected <T> Closure<T> prepareMultiTenantClosure(Closure<T> callable) {
        boolean isMultiTenant = getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        if (isMultiTenant) {
            return { Object... args ->
                enableMultiTenancyFilter()
                try {
                    return callable.call(*args)
                } finally {
                    disableMultiTenancyFilter()
                }
            }
        }
        return callable
    }

    @Override
    @PreDestroy
    void close() {
        try {
            destroy()
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error('Error closing hibernate datastore: {}', e.message, e)
            }
        }
    }

}

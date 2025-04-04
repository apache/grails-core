/* Copyright (C) 2011 SpringSource
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
package org.grails.orm.hibernate;

import grails.gorm.MultiTenant;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher;
import org.grails.datastore.gorm.jdbc.MultiTenantConnection;
import org.grails.datastore.gorm.jdbc.MultiTenantDataSource;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSource;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.event.listener.HibernateEventListener;
import org.grails.orm.hibernate.multitenancy.MultiTenantEventListener;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.SchemaAutoTooling;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.integrator.spi.IntegratorService;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.*;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Datastore implementation that uses a Hibernate SessionFactory underneath.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class HibernateDatastore extends AbstractHibernateDatastore implements MessageSourceAware {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateDatastore.class);

    protected final GrailsHibernateTransactionManager transactionManager;
    protected ConfigurableApplicationEventPublisher eventPublisher;
    protected final HibernateGormEnhancer gormEnhancer;
    protected final Map<String, HibernateDatastore> datastoresByConnectionSource = new LinkedHashMap<>();
    protected final Metadata metadata;

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param connectionSources The {@link ConnectionSources} instance
     * @param mappingContext The {@link MappingContext} instance
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     */
    public HibernateDatastore(final ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources, final HibernateMappingContext mappingContext, final ConfigurableApplicationEventPublisher eventPublisher) {
        super(connectionSources, mappingContext);

        this.metadata = getMetadataInternal();

        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        this.transactionManager = new GrailsHibernateTransactionManager(
                                                                        defaultConnectionSource.getSource(),
                                                                        defaultConnectionSource.getDataSource(),
                                                                        org.hibernate.FlushMode.valueOf(defaultFlushModeName));
        this.eventPublisher = eventPublisher;
        this.eventTriggeringInterceptor = new HibernateEventListener(this);
        this.autoTimestampEventListener = new AutoTimestampEventListener(this);

        HibernateConnectionSourceSettings settings = defaultConnectionSource.getSettings();
        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();

        ClosureEventTriggeringInterceptor interceptor = (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
        interceptor.setDatastore(this);
        interceptor.setEventPublisher(eventPublisher);
        registerEventListeners(this.eventPublisher);
        configureValidatorRegistry(settings, mappingContext);
        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            public void persistentEntityAdded(PersistentEntity entity) {
                gormEnhancer.registerEntity(entity);
            }
        });
        initializeConverters(this.mappingContext);



        if(!(connectionSources instanceof SingletonConnectionSources)) {

            final HibernateDatastore parent = this;
            Iterable<ConnectionSource<SessionFactory, HibernateConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource : allConnectionSources) {
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
                HibernateDatastore childDatastore;

                if (ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
                    childDatastore = this;
                } else {
                    childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources);
                }
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }

            // register a listener to update the datastore each time a connection source is added at runtime
            connectionSources.addListener(connectionSource -> {
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
                HibernateDatastore childDatastore = createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources);
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
                registerAllEntitiesWithEnhancer();
            });

            if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                if(this.tenantResolver instanceof AllTenantsResolver) {
                    AllTenantsResolver allTenantsResolver = (AllTenantsResolver) tenantResolver;
                    Iterable<Serializable> tenantIds = allTenantsResolver.resolveTenantIds();

                    for (Serializable tenantId : tenantIds) {
                        addTenantForSchemaInternal(tenantId.toString());
                    }
                }
                else {
                    Collection<String> allSchemas = schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                    for (String schema : allSchemas) {
                        addTenantForSchemaInternal(schema);
                    }
                }
            }
        }


        this.gormEnhancer = initialize();
    }

    private HibernateDatastore createChildDatastore(HibernateMappingContext mappingContext,
                                                    ConfigurableApplicationEventPublisher eventPublisher,
                                                    HibernateDatastore parent,
                                                    SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources) {
        return new HibernateDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
            @Override
            protected HibernateGormEnhancer initialize() {
                return null;
            }

            @Override
            public HibernateDatastore getDatastoreForConnection(String connectionName) {
                if (connectionName.equals(Settings.SETTING_DATASOURCE) || connectionName.equals(ConnectionSource.DEFAULT)) {
                    return parent;
                } else {
                    HibernateDatastore hibernateDatastore = parent.datastoresByConnectionSource.get(connectionName);
                    if (hibernateDatastore == null) {
                        throw new ConfigurationException("DataSource not found for name [" + connectionName + "] in configuration. Please check your multiple data sources configuration and try again.");
                    }
                    return hibernateDatastore;
                }
            }
        };
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param connectionSourceFactory The {@link HibernateConnectionSourceFactory} instance
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     */
    public HibernateDatastore(PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, DatastoreUtils.preparePropertyResolver(configuration, "dataSource", "hibernate", "grails")), connectionSourceFactory.getMappingContext(), eventPublisher);
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param connectionSourceFactory The {@link HibernateConnectionSourceFactory} instance
     */
    public HibernateDatastore(PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, DatastoreUtils.preparePropertyResolver(configuration, "dataSource", "hibernate", "grails")), connectionSourceFactory.getMappingContext(), new DefaultApplicationEventPublisher());
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     * @param classes The persistent classes
     */
    public HibernateDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes), eventPublisher);
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     * @param classes The persistent classes
     */
    public HibernateDatastore(DataSource dataSource, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(configuration, createConnectionFactoryForDataSource(dataSource, classes), eventPublisher);
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher,  Package...packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages for the given datasource
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(DataSource dataSource, PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher,  Package...packagesToScan) {
        this(dataSource, configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public HibernateDatastore(PropertyResolver configuration, Class...classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes));
    }
    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(PropertyResolver configuration, Package...packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Map<String,Object> configuration, Class...classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), new HibernateConnectionSourceFactory(classes));
    }
    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(Map<String,Object> configuration, Package...packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
    }

    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Class...classes) {
        this(DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")), new HibernateConnectionSourceFactory(classes));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(Package...packagesToScan) {
        this(new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param packageToScan The package to scan
     */
    public HibernateDatastore(Package packageToScan) {
        this(new ClasspathEntityScanner().scan(packageToScan));
    }

    @Override
    public ApplicationEventPublisher getApplicationEventPublisher() {
        return this.eventPublisher;
    }

    /**
     * @return The {@link org.springframework.transaction.PlatformTransactionManager} instance
     */
    public GrailsHibernateTransactionManager getTransactionManager() {
        return transactionManager;
    }


    /**
     * Obtain a child {@link HibernateDatastore} by connection name
     *
     * @param connectionName The connection name
     *
     * @return The {@link HibernateDatastore}
     */
    public HibernateDatastore getDatastoreForConnection(String connectionName) {
        if(connectionName.equals(Settings.SETTING_DATASOURCE) || connectionName.equals(ConnectionSource.DEFAULT)) {
            return this;
        } else {
            HibernateDatastore hibernateDatastore = this.datastoresByConnectionSource.get(connectionName);
            if(hibernateDatastore == null) {
                throw new ConfigurationException("DataSource not found for name ["+connectionName+"] in configuration. Please check your multiple data sources configuration and try again.");
            }
            return hibernateDatastore;
        }
    }

    @Override
    public String toString() {
        return "HibernateDatastore: " + getDataSourceName();
    }

    @Override
    public HibernateMappingContext getMappingContext() {
        return (HibernateMappingContext) super.getMappingContext();
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        HibernateMappingContext mappingContext = getMappingContext();
        ValidatorRegistry validatorRegistry = createValidatorRegistry(messageSource);
        HibernateConnectionSourceSettings settings = getConnectionSources().getDefaultConnectionSource().getSettings();
        configureValidatorRegistry(settings, mappingContext, validatorRegistry, messageSource);
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(autoTimestampEventListener);
        if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener());
        }
        eventPublisher.addApplicationListener(eventTriggeringInterceptor);
    }

    protected void configureValidatorRegistry(HibernateConnectionSourceSettings settings, HibernateMappingContext mappingContext) {
        StaticMessageSource messageSource = new StaticMessageSource();
        ValidatorRegistry defaultValidatorRegistry = createValidatorRegistry(messageSource);
        configureValidatorRegistry(settings, mappingContext, defaultValidatorRegistry, messageSource);
    }

    protected void configureValidatorRegistry(HibernateConnectionSourceSettings settings, HibernateMappingContext mappingContext, ValidatorRegistry validatorRegistry, MessageSource messageSource) {
        if(validatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry)validatorRegistry).addConstraintFactory(
                    new MappingContextAwareConstraintFactory(UniqueConstraint.class, messageSource, mappingContext)
            );
        }
        mappingContext.setValidatorRegistry(
                validatorRegistry
        );
    }

    protected HibernateGormEnhancer initialize() {
        final HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) getConnectionSources().getDefaultConnectionSource();
        if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return new HibernateGormEnhancer(this, transactionManager, defaultConnectionSource.getSettings()) {
                @Override
                public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
                    List<String> allQualifiers = super.allQualifiers(datastore, entity);
                    if( MultiTenant.class.isAssignableFrom(entity.getJavaClass()) ) {
                        if(tenantResolver instanceof AllTenantsResolver) {
                            Iterable<Serializable> tenantIds = ((AllTenantsResolver) tenantResolver).resolveTenantIds();
                            for(Serializable id : tenantIds) {
                                allQualifiers.add(id.toString());
                            }
                        }
                        else {
                            Collection<String> schemaNames = schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                            for (String schemaName : schemaNames) {
                                // skip common internal schemas
                                if(schemaName.equals("INFORMATION_SCHEMA") || schemaName.equals("PUBLIC")) continue;
                                for (String connectionName : datastoresByConnectionSource.keySet()) {
                                    if(schemaName.equalsIgnoreCase(connectionName)) {
                                        allQualifiers.add(connectionName);
                                    }
                                }
                            }
                        }
                    }

                    return allQualifiers;
                }
            };
        }
        else {
            return new HibernateGormEnhancer(this, transactionManager, defaultConnectionSource.getSettings());
        }
    }

    @Override
    public boolean hasCurrentSession() {
        return TransactionSynchronizationManager.getResource(sessionFactory) != null;
    }

    @Override
    protected Session createSession(PropertyResolver connectionDetails) {
        return new HibernateSession(this, sessionFactory);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(applicationContext instanceof ConfigurableApplicationContext) {
            super.setApplicationContext(applicationContext);

            for (HibernateDatastore hibernateDatastore : datastoresByConnectionSource.values()) {
                if(hibernateDatastore != this) {
                    hibernateDatastore.setApplicationContext(applicationContext);
                }
            }
            this.eventPublisher = new ConfigurableApplicationContextEventPublisher((ConfigurableApplicationContext) applicationContext);
            HibernateConnectionSourceSettings settings = getConnectionSources().getDefaultConnectionSource().getSettings();
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
            ClosureEventTriggeringInterceptor interceptor = (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
            interceptor.setDatastore(this);
            interceptor.setEventPublisher(eventPublisher);
            MappingContext mappingContext = getMappingContext();
            // make messages from the application context available to validation
            ValidatorRegistry validatorRegistry = createValidatorRegistry(applicationContext);
            configureValidatorRegistry(settings, (HibernateMappingContext) mappingContext, validatorRegistry, applicationContext);
            mappingContext.setValidatorRegistry(
                    validatorRegistry
            );

            registerEventListeners(eventPublisher);
            this.eventPublisher.publishEvent( new DatastoreInitializedEvent(this) );
        }
    }

    @Override
    public IHibernateTemplate getHibernateTemplate(int flushMode) {
        return new GrailsHibernateTemplate(getSessionFactory(), this, flushMode);
    }

    @Override
    public void withFlushMode(FlushMode flushMode, Callable<Boolean> callable) {
        final org.hibernate.Session session = sessionFactory.getCurrentSession();
        org.hibernate.FlushMode previousMode = null;
        Boolean reset = true;
        try {
            if (session != null) {
                previousMode = session.getHibernateFlushMode();
                session.setHibernateFlushMode(org.hibernate.FlushMode.valueOf(flushMode.name()));
            }
            try {
                reset = callable.call();
            } catch (Exception e) {
                reset = false;
            }
        }
        finally {
            if (session != null && previousMode != null && reset) {
                session.setHibernateFlushMode(previousMode);
            }
        }
    }

    @Override
    public org.hibernate.Session openSession() {
        org.hibernate.Session session = this.sessionFactory.openSession();
        session.setHibernateFlushMode(org.hibernate.FlushMode.valueOf(defaultFlushModeName));
        return session;
    }

    @Override
    public Session getCurrentSession() throws ConnectionNotFoundException {
        // HibernateSession, just a thin wrapper around default session handling so simply return a new instance here
        return new HibernateSession(this, sessionFactory, getDefaultFlushMode());
    }

    @Override
    public void destroy() {
        try {
            super.destroy();
        } finally {
            GrailsDomainBinder.clearMappingCache();
            try {
                this.gormEnhancer.close();
            } catch (IOException e) {
                LOG.error("There was an error shutting down GORM enhancer", e);
            }
        }
    }

    @Override
    public void addTenantForSchema(String schemaName) {
        addTenantForSchemaInternal(schemaName);
        registerAllEntitiesWithEnhancer();
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        DataSource dataSource = defaultConnectionSource.getDataSource();
        if(dataSource instanceof TransactionAwareDataSourceProxy) {
            dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
        }
        Object existing = TransactionSynchronizationManager.getResource(dataSource);
        if(existing instanceof ConnectionHolder) {
            ConnectionHolder connectionHolder = (ConnectionHolder) existing;
            Connection connection = connectionHolder.getConnection();
            try {
                if(!connection.isClosed() && !connection.isReadOnly()) {
                    schemaHandler.useDefaultSchema(connection);
                }
            } catch (SQLException e) {
                throw new DatastoreConfigurationException("Failed to reset to default schema: " + e.getMessage(), e);
            }
        }

    }

    public Metadata getMetadata() {
        return metadata;
    }

    protected void registerAllEntitiesWithEnhancer() {
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            gormEnhancer.registerEntity(persistentEntity);
        }
    }

    private void addTenantForSchemaInternal(final String schemaName) {
        if( multiTenantMode != MultiTenancySettings.MultiTenancyMode.SCHEMA ) {
            throw new ConfigurationException("The method [addTenantForSchema] can only be called with multi-tenancy mode SCHEMA. Current mode is: " + multiTenantMode);
        }
        HibernateConnectionSourceFactory factory = (HibernateConnectionSourceFactory) connectionSources.getFactory();
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        HibernateConnectionSourceSettings tenantSettings;
        try {
            tenantSettings = (HibernateConnectionSourceSettings)connectionSources.getDefaultConnectionSource().getSettings().clone();
        } catch (CloneNotSupportedException e) {
            throw new ConfigurationException("Couldn't clone default Hibernate settings! " + e.getMessage(), e);
        }
        tenantSettings.getHibernate().put(Environment.DEFAULT_SCHEMA, schemaName);

        String dbCreate = tenantSettings.getDataSource().getDbCreate();

        SchemaAutoTooling schemaAutoTooling = dbCreate != null ?  SchemaAutoTooling.interpret(dbCreate) : null;
        if(schemaAutoTooling != null && schemaAutoTooling != SchemaAutoTooling.VALIDATE && schemaAutoTooling != SchemaAutoTooling.NONE) {

            Connection connection = null;
            try {
                connection = defaultConnectionSource.getDataSource().getConnection();
                try {
                    schemaHandler.useSchema(connection, schemaName);
                } catch (Exception e) {
                    // schema doesn't exist
                    schemaHandler.createSchema(connection, schemaName);
                }

            } catch (SQLException e) {
                throw new DatastoreConfigurationException(String.format("Failed to create schema for name [%s]", schemaName));
            }
            finally {
                if(connection != null) {
                    try {
                        schemaHandler.useDefaultSchema(connection);
                        connection.close();
                    } catch (SQLException e) {
                        //ignore
                    }
                }
            }
        }

        DataSource dataSource = defaultConnectionSource.getDataSource();
        dataSource = new MultiTenantDataSource(dataSource, schemaName) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                schemaHandler.useSchema(connection, schemaName);
                return new MultiTenantConnection(connection, schemaHandler);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                Connection connection = super.getConnection(username, password);
                schemaHandler.useSchema(connection, schemaName);
                return new MultiTenantConnection(connection, schemaHandler);
            }
        };
        DefaultConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource = new DefaultConnectionSource<>(schemaName, dataSource, tenantSettings.getDataSource());
        ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource = factory.create(schemaName, dataSourceConnectionSource, tenantSettings);
        SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
        HibernateDatastore childDatastore = new HibernateDatastore(singletonConnectionSources, (HibernateMappingContext) mappingContext, eventPublisher) {
            @Override
            protected HibernateGormEnhancer initialize() {
                return null;
            }
        };
        datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
    }

    private Metadata getMetadataInternal() {
        Metadata metadata = null;
        ServiceRegistry bootstrapServiceRegistry = ((SessionFactoryImplementor) sessionFactory).getServiceRegistry().getParentServiceRegistry();
        Iterable<Integrator> integrators = bootstrapServiceRegistry.getService(IntegratorService.class).getIntegrators();
        for (Integrator integrator : integrators) {
            if (integrator instanceof MetadataIntegrator) {
                metadata = ((MetadataIntegrator) integrator).getMetadata();
            }
        }
        return metadata;
    }

    private static HibernateConnectionSourceFactory createConnectionFactoryForDataSource(final DataSource dataSource, Class... classes) {
        HibernateConnectionSourceFactory hibernateConnectionSourceFactory = new HibernateConnectionSourceFactory(classes);
        hibernateConnectionSourceFactory.setDataSourceConnectionSourceFactory(
                new DataSourceConnectionSourceFactory() {
                    @Override
                    public ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {
                        if(ConnectionSource.DEFAULT.equals(name)) {
                            return new DataSourceConnectionSource(ConnectionSource.DEFAULT, dataSource, settings);
                        }
                        else {
                            return super.create(name, settings);
                        }
                    }
                }
        );
        return hibernateConnectionSourceFactory;
    }
}

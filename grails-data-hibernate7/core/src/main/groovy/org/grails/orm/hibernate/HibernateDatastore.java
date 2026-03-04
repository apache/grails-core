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
package org.grails.orm.hibernate;

import grails.gorm.MultiTenant;
import grails.gorm.multitenancy.Tenants;
import groovy.lang.Closure;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher;
import org.grails.datastore.gorm.jdbc.MultiTenantConnection;
import org.grails.datastore.gorm.jdbc.MultiTenantDataSource;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSource;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler;
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry;
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreAware;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.SchemaMultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException;
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver;
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.event.listener.AbstractHibernateEventListener;
import org.grails.orm.hibernate.event.listener.HibernateEventListener;
import org.grails.orm.hibernate.multitenancy.MultiTenantEventListener;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.integrator.spi.IntegratorService;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.*;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import grails.gorm.MultiTenant;
import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSource;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler;
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry;
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreAware;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.multitenancy.SchemaMultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException;
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.event.listener.HibernateEventListener;
import org.grails.orm.hibernate.multitenancy.MultiTenantEventListener;
import org.grails.orm.hibernate.query.HibernateQueryArgument;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;

/**
 * Datastore implementation that uses a Hibernate SessionFactory underneath.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@SuppressWarnings({
  "PMD.CloseResource",
  "PMD.DataflowAnomalyAnalysis",
  "PMD.ConstructorCallsOverridableMethod",
  "PMD.AvoidFieldNameMatchingMethodName"
})
public class HibernateDatastore extends AbstractDatastore
    implements ApplicationContextAware,
        Settings,
        SchemaMultiTenantCapableDatastore<SessionFactory, HibernateConnectionSourceSettings>,
        TransactionCapableDatastore,
        Closeable,
        MessageSourceAware,
        MultipleConnectionSourceCapableDatastore {
  private static final Logger LOG = LoggerFactory.getLogger(HibernateDatastore.class);

  /** The config property cache queries. */
  public static final String CONFIG_PROPERTY_CACHE_QUERIES = "grails.hibernate.cache.queries";

  /** The config property osiv readonly. */
  public static final String CONFIG_PROPERTY_OSIV_READONLY = "grails.hibernate.osiv.readonly";

  /** The config property pass readonly to hibernate. */
  public static final String CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE =
      "grails.hibernate.pass.readonly";

  /** The session factory. */
  protected final SessionFactory sessionFactory;

  /** The connection sources. */
  protected final ConnectionSources<SessionFactory, HibernateConnectionSourceSettings>
      connectionSources;

  /** The default flush mode name. */
  protected final String defaultFlushModeName;

  /** The multi tenant mode. */
  protected final MultiTenancySettings.MultiTenancyMode multiTenantMode;

  /** The schema handler. */
  protected final SchemaHandler schemaHandler;

  /** The event triggering interceptor. */
  protected AbstractHibernateEventListener eventTriggeringInterceptor;

  /** The auto timestamp event listener. */
  protected AutoTimestampEventListener autoTimestampEventListener;

  /** The osiv read only. */
  protected final boolean osivReadOnly;

  /** The pass read only to hibernate. */
  protected final boolean passReadOnlyToHibernate;

  /** The is cache queries. */
  protected final boolean isCacheQueries;

  /** The default flush mode. */
  protected final int defaultFlushMode;

  /** The fail on error. */
  protected final boolean failOnError;

  /** The mark dirty. */
  protected final boolean markDirty;

  /** The data source name. */
  protected final String dataSourceName;

  /** The tenant resolver. */
  protected final TenantResolver tenantResolver;

  private boolean destroyed;

  protected final GrailsHibernateTransactionManager transactionManager;
  protected ConfigurableApplicationEventPublisher eventPublisher;
  protected final HibernateGormEnhancer gormEnhancer;
  protected final Map<String, HibernateDatastore> datastoresByConnectionSource =
      new LinkedHashMap<>();
  protected final Metadata metadata;

  /**
   * Create a new HibernateDatastore for the given connection sources and mapping context
   *
   * @param connectionSources The {@link ConnectionSources} instance
   * @param mappingContext The {@link MappingContext} instance
   * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
   */
  public HibernateDatastore(
      final ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources,
      final HibernateMappingContext mappingContext,
      final ConfigurableApplicationEventPublisher eventPublisher) {
    super(mappingContext, connectionSources.getBaseConfiguration(), null);
    this.connectionSources = connectionSources;
    final HibernateConnectionSource defaultConnectionSource =
        (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
    this.dataSourceName = defaultConnectionSource.getName();
    this.sessionFactory = defaultConnectionSource.getSource();
    HibernateConnectionSourceSettings settings = defaultConnectionSource.getSettings();
    HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
    this.osivReadOnly = hibernateSettings.getOsiv().isReadonly();
    this.passReadOnlyToHibernate = hibernateSettings.isReadOnly();
    this.isCacheQueries = hibernateSettings.getCache().isQueries();
    this.failOnError = settings.isFailOnError();
    Boolean markDirty = settings.getMarkDirty();
    this.markDirty = markDirty == null ? false : markDirty;
    FlushMode flushMode = FlushMode.valueOf(hibernateSettings.getFlush().getMode().name());
    this.defaultFlushModeName = flushMode.name();
    this.defaultFlushMode = flushMode.getLevel();
    MultiTenancySettings multiTenancySettings = settings.getMultiTenancy();
    final TenantResolver multiTenantResolver = multiTenancySettings.getTenantResolver();
    this.multiTenantMode = multiTenancySettings.getMode();
    Class<? extends SchemaHandler> schemaHandlerClass = settings.getDataSource().getSchemaHandler();
    this.schemaHandler = BeanUtils.instantiateClass(schemaHandlerClass);
    this.tenantResolver = multiTenantResolver;
    if (multiTenantResolver instanceof DatastoreAware) {
      ((DatastoreAware) multiTenantResolver).setDatastore(this);
    }

    /** @deprecated Use {@link HibernateQueryArgument#CONFIG_PASS_READONLY} */
    @Deprecated(since = "8.0", forRemoval = true)
    public static final String CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE =
            HibernateQueryArgument.CONFIG_PASS_READONLY.value();

    this.transactionManager =
        new GrailsHibernateTransactionManager(
            defaultConnectionSource.getSource(),
            defaultConnectionSource.getDataSource(),
            org.hibernate.FlushMode.valueOf(defaultFlushModeName));
    this.eventPublisher = eventPublisher;
    this.eventTriggeringInterceptor = new HibernateEventListener(this);
    this.autoTimestampEventListener = new AutoTimestampEventListener(this);

    ClosureEventTriggeringInterceptor interceptor =
        (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
    interceptor.setDatastore(this);
    interceptor.setEventPublisher(eventPublisher);
    registerEventListeners(this.eventPublisher);
    configureValidatorRegistry(settings, mappingContext);
    this.mappingContext.addMappingContextListener(
        new MappingContext.Listener() {
          @Override
          public void persistentEntityAdded(PersistentEntity entity) {
            gormEnhancer.registerEntity(entity);
          }
        });
        initializeConverters(this.mappingContext);

        if (!(connectionSources instanceof SingletonConnectionSources)) {

            final HibernateDatastore parent = this;
            Iterable<ConnectionSource<SessionFactory, HibernateConnectionSourceSettings>> allConnectionSources =
                    connectionSources.getAllConnectionSources();
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource :
                    allConnectionSources) {
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings>
                        singletonConnectionSources = new SingletonConnectionSources<>(
                                connectionSource, connectionSources.getBaseConfiguration());
                HibernateDatastore childDatastore;

        if (ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
          childDatastore = this;
        } else {
          childDatastore =
              createChildDatastore(
                  mappingContext, eventPublisher, parent, singletonConnectionSources);
        }
        datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
      }

      // register a listener to update the datastore each time a connection source is added at
      // runtime
      connectionSources.addListener(
          connectionSource -> {
            SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings>
                singletonConnectionSources =
                    new SingletonConnectionSources<>(
                        connectionSource, connectionSources.getBaseConfiguration());
            HibernateDatastore childDatastore =
                createChildDatastore(
                    mappingContext, eventPublisher, parent, singletonConnectionSources);
            datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            registerAllEntitiesWithEnhancer();
          });

      if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
        if (this.tenantResolver instanceof AllTenantsResolver) {
          AllTenantsResolver allTenantsResolver = (AllTenantsResolver) tenantResolver;
          Iterable<Serializable> tenantIds = allTenantsResolver.resolveTenantIds();

          for (Serializable tenantId : tenantIds) {
            addTenantForSchemaInternal(tenantId.toString());
          }
        } else {
          Collection<String> allSchemas =
              schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
          for (String schema : allSchemas) {
            addTenantForSchemaInternal(schema);
          }
        }
      }
    }

    this.gormEnhancer = initialize();
  }

  private HibernateDatastore createChildDatastore(
      HibernateMappingContext mappingContext,
      ConfigurableApplicationEventPublisher eventPublisher,
      HibernateDatastore parent,
      SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings>
          singletonConnectionSources) {
    return new HibernateDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
      @Override
      protected HibernateGormEnhancer initialize() {
        return null;
      }

      @Override
      public HibernateDatastore getDatastoreForConnection(String connectionName) {
        if (connectionName.equals(Settings.SETTING_DATASOURCE)
            || connectionName.equals(ConnectionSource.DEFAULT)) {
          return parent;
        } else {
          HibernateDatastore hibernateDatastore =
              parent.datastoresByConnectionSource.get(connectionName);
          if (hibernateDatastore == null) {
            throw new ConfigurationException(
                "DataSource not found for name ["
                    + connectionName
                    + "] in configuration. Please check your multiple data sources configuration and try again.");
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
  public HibernateDatastore(
      PropertyResolver configuration,
      HibernateConnectionSourceFactory connectionSourceFactory,
      ConfigurableApplicationEventPublisher eventPublisher) {
    this(
        ConnectionSourcesInitializer.create(
            connectionSourceFactory,
            DatastoreUtils.preparePropertyResolver(
                configuration, "dataSource", "hibernate", "grails")),
        connectionSourceFactory.getMappingContext(),
        eventPublisher);
  }

  /**
   * Create a new HibernateDatastore for the given connection sources and mapping context
   *
   * @param configuration The configuration
   * @param connectionSourceFactory The {@link HibernateConnectionSourceFactory} instance
   */
  public HibernateDatastore(
      PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory) {
    this(
        ConnectionSourcesInitializer.create(
            connectionSourceFactory,
            DatastoreUtils.preparePropertyResolver(
                configuration, "dataSource", "hibernate", "grails")),
        connectionSourceFactory.getMappingContext(),
        new DefaultApplicationEventPublisher());
  }

  /**
   * Create a new HibernateDatastore for the given connection sources and mapping context
   *
   * @param configuration The configuration
   * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
   * @param classes The persistent classes
   */
  public HibernateDatastore(
      PropertyResolver configuration,
      ConfigurableApplicationEventPublisher eventPublisher,
      Class... classes) {
    this(configuration, new HibernateConnectionSourceFactory(classes), eventPublisher);
  }

  /**
   * Create a new HibernateDatastore for the given connection sources and mapping context
   *
   * @param configuration The configuration
   * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
   * @param classes The persistent classes
   */
  public HibernateDatastore(
      DataSource dataSource,
      PropertyResolver configuration,
      ConfigurableApplicationEventPublisher eventPublisher,
      Class... classes) {
    this(configuration, createConnectionFactoryForDataSource(dataSource, classes), eventPublisher);
  }

  /**
   * Construct a Hibernate datastore scanning the given packages
   *
   * @param configuration The configuration
   * @param eventPublisher The event publisher
   * @param packagesToScan The packages to scan
   */
  public HibernateDatastore(
      PropertyResolver configuration,
      ConfigurableApplicationEventPublisher eventPublisher,
      Package... packagesToScan) {
    this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
  }

  /**
   * Construct a Hibernate datastore scanning the given packages for the given datasource
   *
   * @param configuration The configuration
   * @param eventPublisher The event publisher
   * @param packagesToScan The packages to scan
   */
  public HibernateDatastore(
      DataSource dataSource,
      PropertyResolver configuration,
      ConfigurableApplicationEventPublisher eventPublisher,
      Package... packagesToScan) {
    this(
        dataSource,
        configuration,
        eventPublisher,
        new ClasspathEntityScanner().scan(packagesToScan));
  }

  /**
   * Create a new HibernateDatastore for the given connection sources and mapping context
   *
   * @param configuration The configuration
   * @param classes The persistent classes
   */
  public HibernateDatastore(PropertyResolver configuration, Class... classes) {
    this(configuration, new HibernateConnectionSourceFactory(classes));
  }

  /**
   * Construct a Hibernate datastore scanning the given packages
   *
   * @param configuration The configuration
   * @param packagesToScan The packages to scan
   */
  public HibernateDatastore(PropertyResolver configuration, Package... packagesToScan) {
    this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
  }

  /**
   * Constructor used purely for testing purposes. Creates a datastore with an in-memory database
   * and dbCreate set to 'create-drop'
   *
   * @param classes The classes
   */
  public HibernateDatastore(Map<String, Object> configuration, Class... classes) {
    this(
        DatastoreUtils.createPropertyResolver(configuration),
        new HibernateConnectionSourceFactory(classes));
  }

  /**
   * Construct a Hibernate datastore scanning the given packages
   *
   * @param configuration The configuration
   * @param packagesToScan The packages to scan
   */
  public HibernateDatastore(Map<String, Object> configuration, Package... packagesToScan) {
    this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
  }

  /**
   * Constructor used purely for testing purposes. Creates a datastore with an in-memory database
   * and dbCreate set to 'create-drop'
   *
   * @param classes The classes
   */
  public HibernateDatastore(Class... classes) {
    this(
        DatastoreUtils.createPropertyResolver(
            Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")),
        new HibernateConnectionSourceFactory(classes));
  }

  /**
   * Construct a Hibernate datastore scanning the given packages
   *
   * @param packagesToScan The packages to scan
   */
  public HibernateDatastore(Package... packagesToScan) {
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

  /**
   * Legacy constructor used by {@code HibernateDatastoreFactoryBean} and similar factory helpers.
   *
   * @param mappingContext The mapping context
   * @param sessionFactory The session factory
   * @param config The property resolver configuration
   * @param applicationContext The application context (may be null)
   * @param dataSourceName The data source name
   */
  protected HibernateDatastore(
      MappingContext mappingContext,
      SessionFactory sessionFactory,
      PropertyResolver config,
      ApplicationContext applicationContext,
      String dataSourceName) {
    super(mappingContext, config, (ConfigurableApplicationContext) applicationContext);
    this.connectionSources =
        new SingletonConnectionSources<>(
            new HibernateConnectionSource(dataSourceName, sessionFactory, null, null), config);
    this.sessionFactory = sessionFactory;
    this.dataSourceName = dataSourceName;
    initializeConverters(mappingContext);
    if (applicationContext != null) {
      setApplicationContext(applicationContext);
    }

    this.osivReadOnly = config.getProperty(CONFIG_PROPERTY_OSIV_READONLY, Boolean.class, false);
    this.passReadOnlyToHibernate =
        config.getProperty(CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE, Boolean.class, false);
    this.isCacheQueries =
        config.getProperty(CONFIG_PROPERTY_CACHE_QUERIES, Boolean.class, false);

    if (config.getProperty(SETTING_AUTO_FLUSH, Boolean.class, false)) {
      this.defaultFlushModeName = FlushMode.AUTO.name();
      this.defaultFlushMode = FlushMode.AUTO.level;
    } else {
      FlushMode fm = config.getProperty(SETTING_FLUSH_MODE, FlushMode.class, FlushMode.COMMIT);
      this.defaultFlushModeName = fm.name();
      this.defaultFlushMode = fm.level;
    }
    this.failOnError = config.getProperty(SETTING_FAIL_ON_ERROR, Boolean.class, false);
    this.markDirty = config.getProperty(SETTING_MARK_DIRTY, Boolean.class, false);
    this.tenantResolver = new FixedTenantResolver();
    this.multiTenantMode = MultiTenancySettings.MultiTenancyMode.NONE;
    this.schemaHandler = new DefaultSchemaHandler();
    this.transactionManager = null;
    this.gormEnhancer = null;
    this.metadata = null;
  }

  /**
   * Legacy three-argument constructor delegating to the five-argument constructor.
   *
   * @param mappingContext The mapping context
   * @param sessionFactory The session factory
   * @param config The property resolver configuration
   */
  public HibernateDatastore(
      MappingContext mappingContext, SessionFactory sessionFactory, PropertyResolver config) {
    this(mappingContext, sessionFactory, config, null, ConnectionSource.DEFAULT);
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
   * @return The {@link HibernateDatastore}
   */
  public HibernateDatastore getDatastoreForConnection(String connectionName) {
    if (connectionName.equals(Settings.SETTING_DATASOURCE)
        || connectionName.equals(ConnectionSource.DEFAULT)) {
      return this;
    } else {
      HibernateDatastore hibernateDatastore = this.datastoresByConnectionSource.get(connectionName);
      if (hibernateDatastore == null) {
        throw new ConfigurationException(
            "DataSource not found for name ["
                + connectionName
                + "] in configuration. Please check your multiple data sources configuration and try again.");
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
    HibernateConnectionSourceSettings settings =
        getConnectionSources().getDefaultConnectionSource().getSettings();
    configureValidatorRegistry(settings, mappingContext, validatorRegistry, messageSource);
  }

  protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
    eventPublisher.addApplicationListener(autoTimestampEventListener);
    if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
      eventPublisher.addApplicationListener(new MultiTenantEventListener());
    }
    eventPublisher.addApplicationListener(eventTriggeringInterceptor);
  }

  protected void configureValidatorRegistry(
      HibernateConnectionSourceSettings settings, HibernateMappingContext mappingContext) {
    StaticMessageSource messageSource = new StaticMessageSource();
    ValidatorRegistry defaultValidatorRegistry = createValidatorRegistry(messageSource);
    configureValidatorRegistry(settings, mappingContext, defaultValidatorRegistry, messageSource);
  }

  protected void configureValidatorRegistry(
      HibernateConnectionSourceSettings settings,
      HibernateMappingContext mappingContext,
      ValidatorRegistry validatorRegistry,
      MessageSource messageSource) {
    if (validatorRegistry instanceof ConstraintRegistry) {
      ((ConstraintRegistry) validatorRegistry)
          .addConstraintFactory(
              new MappingContextAwareConstraintFactory(
                  UniqueConstraint.class, messageSource, mappingContext));
    }
    mappingContext.setValidatorRegistry(validatorRegistry);
  }

  protected HibernateGormEnhancer initialize() {
    final HibernateConnectionSource defaultConnectionSource =
        (HibernateConnectionSource) getConnectionSources().getDefaultConnectionSource();
    if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
      return new HibernateGormEnhancer(
          this, transactionManager, defaultConnectionSource.getSettings()) {
        @Override
        public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
          List<String> allQualifiers = super.allQualifiers(datastore, entity);
          if (MultiTenant.class.isAssignableFrom(entity.getJavaClass())) {
            if (tenantResolver instanceof AllTenantsResolver) {
              Iterable<Serializable> tenantIds =
                  ((AllTenantsResolver) tenantResolver).resolveTenantIds();
              for (Serializable id : tenantIds) {
                allQualifiers.add(id.toString());
              }
            } else {
              Collection<String> schemaNames =
                  schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
              for (String schemaName : schemaNames) {
                // skip common internal schemas
                if (schemaName.equals("INFORMATION_SCHEMA") || schemaName.equals("PUBLIC"))
                  continue;
                for (String connectionName : datastoresByConnectionSource.keySet()) {
                  if (schemaName.equalsIgnoreCase(connectionName)) {
                    allQualifiers.add(connectionName);
                  }
                }
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }

            // register a listener to update the datastore each time a connection source is added at
            // runtime
            connectionSources.addListener(connectionSource -> {
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings>
                        singletonConnectionSources = new SingletonConnectionSources<>(
                                connectionSource, connectionSources.getBaseConfiguration());
                HibernateDatastore childDatastore =
                        createChildDatastore(mappingContext, eventPublisher, parent, singletonConnectionSources);
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
                registerAllEntitiesWithEnhancer();
            });

            if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                if (this.tenantResolver instanceof AllTenantsResolver) {
                    AllTenantsResolver allTenantsResolver = (AllTenantsResolver) tenantResolver;
                    Iterable<Serializable> tenantIds = allTenantsResolver.resolveTenantIds();

                    for (Serializable tenantId : tenantIds) {
                        addTenantForSchemaInternal(tenantId.toString());
                    }
                } else {
                    Collection<String> allSchemas =
                            schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                    for (String schema : allSchemas) {
                        addTenantForSchemaInternal(schema);
                    }
                }
            }
        }

        this.gormEnhancer = initialize();
    }

    private HibernateDatastore createChildDatastore(
            HibernateMappingContext mappingContext,
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
                if (connectionName.equals(Settings.SETTING_DATASOURCE)
                        || connectionName.equals(ConnectionSource.DEFAULT)) {
                    return parent;
                } else {
                    HibernateDatastore hibernateDatastore = parent.datastoresByConnectionSource.get(connectionName);
                    if (hibernateDatastore == null) {
                        throw new ConfigurationException(
                                "DataSource not found for name ["
                                        + connectionName
                                        + "] in configuration. Please check your multiple data sources configuration and try again.");
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
    public HibernateDatastore(
            PropertyResolver configuration,
            HibernateConnectionSourceFactory connectionSourceFactory,
            ConfigurableApplicationEventPublisher eventPublisher) {
        this(
                ConnectionSourcesInitializer.create(
                        connectionSourceFactory,
                        DatastoreUtils.preparePropertyResolver(configuration, "dataSource", "hibernate", "grails")),
                connectionSourceFactory.getMappingContext(),
                eventPublisher);
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param connectionSourceFactory The {@link HibernateConnectionSourceFactory} instance
     */
    public HibernateDatastore(
            PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory) {
        this(
                ConnectionSourcesInitializer.create(
                        connectionSourceFactory,
                        DatastoreUtils.preparePropertyResolver(configuration, "dataSource", "hibernate", "grails")),
                connectionSourceFactory.getMappingContext(),
                new DefaultApplicationEventPublisher());
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     * @param classes The persistent classes
     */
    public HibernateDatastore(
            PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class<?>... classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes), eventPublisher);
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     * @param classes The persistent classes
     */
    public HibernateDatastore(
            DataSource dataSource,
            PropertyResolver configuration,
            ConfigurableApplicationEventPublisher eventPublisher,
            Class<?>... classes) {
        this(configuration, createConnectionFactoryForDataSource(dataSource, classes), eventPublisher);
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(
            PropertyResolver configuration,
            ConfigurableApplicationEventPublisher eventPublisher,
            Package... packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages for the given datasource
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(
            DataSource dataSource,
            PropertyResolver configuration,
            ConfigurableApplicationEventPublisher eventPublisher,
            Package... packagesToScan) {
        this(dataSource, configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public HibernateDatastore(PropertyResolver configuration, Class<?>... classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(PropertyResolver configuration, Package... packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
    }

    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database
     * and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Map<String, Object> configuration, Class<?>... classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), new HibernateConnectionSourceFactory(classes));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(Map<String, Object> configuration, Package... packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
    }

    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database
     * and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Class<?>... classes) {
        this(
                DatastoreUtils.createPropertyResolver(
                        Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")),
                new HibernateConnectionSourceFactory(classes));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(Package... packagesToScan) {
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

    /**
     * Legacy constructor used by {@code HibernateDatastoreFactoryBean} and similar factory helpers.
     *
     * @param mappingContext The mapping context
     * @param sessionFactory The session factory
     * @param config The property resolver configuration
     * @param applicationContext The application context (may be null)
     * @param dataSourceName The data source name
     */
    @SuppressWarnings("PMD.NullAssignment")
    protected HibernateDatastore(
            MappingContext mappingContext,
            SessionFactory sessionFactory,
            PropertyResolver config,
            ApplicationContext applicationContext,
            String dataSourceName) {
        super(mappingContext, config, (ConfigurableApplicationContext) applicationContext);
        this.connectionSources = new SingletonConnectionSources<>(
                new HibernateConnectionSource(dataSourceName, sessionFactory, null, null), config);
        this.sessionFactory = sessionFactory;
        this.dataSourceName = dataSourceName;
        initializeConverters(mappingContext);
        if (applicationContext != null) {
            setApplicationContext(applicationContext);
        }

        this.osivReadOnly =
                config.getProperty(HibernateQueryArgument.CONFIG_OSIV_READONLY.value(), Boolean.class, false);
        this.passReadOnlyToHibernate =
                config.getProperty(HibernateQueryArgument.CONFIG_PASS_READONLY.value(), Boolean.class, false);
        this.isCacheQueries =
                config.getProperty(HibernateQueryArgument.CONFIG_CACHE_QUERIES.value(), Boolean.class, false);

  public IHibernateTemplate getHibernateTemplate(int flushMode) {
    return new GrailsHibernateTemplate(getSessionFactory(), this, flushMode);
  }

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
    } finally {
      if (session != null && previousMode != null && reset) {
        session.setHibernateFlushMode(previousMode);
      }
    }
  }

  public org.hibernate.Session openSession() {
    org.hibernate.Session session = this.sessionFactory.openSession();
    session.setHibernateFlushMode(org.hibernate.FlushMode.valueOf(defaultFlushModeName));
    return session;
  }

  @Override
  public Session getCurrentSession() throws ConnectionNotFoundException {
    // HibernateSession, just a thin wrapper around default session handling so simply return a new
    // instance here
    return new HibernateSession(this, sessionFactory, getDefaultFlushMode());
  }

  @Override
  public void destroy() {
    if (!this.destroyed) {
      try {
        super.destroy();
        HibernateGormInstanceApi.resetInsertActive();
        try {
          connectionSources.close();
        } catch (IOException e) {
          LOG.error("There was an error shutting down GORM for an entity: " + e.getMessage(), e);
        }
      } finally {
        MappingCacheHolder.getInstance().clear();
        try {
          if (this.gormEnhancer != null) {
            this.gormEnhancer.close();
          }
        } catch (IOException e) {
          LOG.error("There was an error shutting down GORM enhancer", e);
        }
        destroyed = true;
      }
    }
  }

  @Override
  public void addTenantForSchema(String schemaName) {
    addTenantForSchemaInternal(schemaName);
    registerAllEntitiesWithEnhancer();
    HibernateConnectionSource defaultConnectionSource =
        (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
    DataSource dataSource = defaultConnectionSource.getDataSource();
    if (dataSource instanceof TransactionAwareDataSourceProxy) {
      dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
    }
    Object existing = TransactionSynchronizationManager.getResource(dataSource);
    if (existing instanceof ConnectionHolder) {
      ConnectionHolder connectionHolder = (ConnectionHolder) existing;
      Connection connection = connectionHolder.getConnection();
      try {
        if (!connection.isClosed() && !connection.isReadOnly()) {
          schemaHandler.useDefaultSchema(connection);
        }
        this.failOnError = config.getProperty(SETTING_FAIL_ON_ERROR, Boolean.class, false);
        this.markDirty = config.getProperty(SETTING_MARK_DIRTY, Boolean.class, false);
        this.tenantResolver = new FixedTenantResolver();
        this.multiTenantMode = MultiTenancySettings.MultiTenancyMode.NONE;
        this.schemaHandler = new DefaultSchemaHandler();
        this.transactionManager = null;
        this.gormEnhancer = null;
        this.metadata = null;
    }

    /**
     * Legacy three-argument constructor delegating to the five-argument constructor.
     *
     * @param mappingContext The mapping context
     * @param sessionFactory The session factory
     * @param config The property resolver configuration
     */
    public HibernateDatastore(MappingContext mappingContext, SessionFactory sessionFactory, PropertyResolver config) {
        this(mappingContext, sessionFactory, config, null, ConnectionSource.DEFAULT);
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
     * @return The {@link HibernateDatastore}
     */
    @Override
    public HibernateDatastore getDatastoreForConnection(String connectionName) {
        if (Settings.SETTING_DATASOURCE.equals(connectionName) || ConnectionSource.DEFAULT.equals(connectionName)) {
            return this;
        } else {
            HibernateDatastore hibernateDatastore = this.datastoresByConnectionSource.get(connectionName);
            if (hibernateDatastore == null) {
                throw new ConfigurationException("DataSource not found for name ["
                        + connectionName
                        + "] in configuration. Please check your multiple data sources configuration and try again.");
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
    public void setMessageSource(@Nullable MessageSource messageSource) {
        HibernateMappingContext mappingContext = getMappingContext();
        ValidatorRegistry validatorRegistry = createValidatorRegistry(messageSource);
        configureValidatorRegistry(mappingContext, validatorRegistry, messageSource);
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(autoTimestampEventListener);
        if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener());
        }
        eventPublisher.addApplicationListener(eventTriggeringInterceptor);
    }

    protected void configureValidatorRegistry(HibernateMappingContext mappingContext) {
        StaticMessageSource messageSource = new StaticMessageSource();
        ValidatorRegistry defaultValidatorRegistry = createValidatorRegistry(messageSource);
        configureValidatorRegistry(mappingContext, defaultValidatorRegistry, messageSource);
    }

    protected void configureValidatorRegistry(
            HibernateMappingContext mappingContext, ValidatorRegistry validatorRegistry, MessageSource messageSource) {
        if (validatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry) validatorRegistry)
                    .addConstraintFactory(new MappingContextAwareConstraintFactory(
                            UniqueConstraint.class, messageSource, mappingContext));
        }
        mappingContext.setValidatorRegistry(validatorRegistry);
    }

    protected HibernateGormEnhancer initialize() {
        final HibernateConnectionSource defaultConnectionSource =
                (HibernateConnectionSource) getConnectionSources().getDefaultConnectionSource();
        if (multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return new HibernateGormEnhancer(this, transactionManager, defaultConnectionSource.getSettings()) {
                @Override
                public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
                    List<String> allQualifiers = super.allQualifiers(datastore, entity);
                    if (MultiTenant.class.isAssignableFrom(entity.getJavaClass())) {
                        if (tenantResolver instanceof AllTenantsResolver) {
                            Iterable<Serializable> tenantIds = ((AllTenantsResolver) tenantResolver).resolveTenantIds();
                            for (Serializable id : tenantIds) {
                                allQualifiers.add(id.toString());
                            }
                        } else {
                            Collection<String> schemaNames =
                                    schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                            for (String schemaName : schemaNames) {
                                // skip common internal schemas
                                if (schemaName.equals("INFORMATION_SCHEMA") || schemaName.equals("PUBLIC")) continue;
                                for (String connectionName : datastoresByConnectionSource.keySet()) {
                                    if (schemaName.equalsIgnoreCase(connectionName)) {
                                        allQualifiers.add(connectionName);
                                    }
                                }
                            }
                        }
                    }

                    return allQualifiers;
                }
            };
        } else {
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

    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            super.setApplicationContext(applicationContext);

            for (HibernateDatastore hibernateDatastore : datastoresByConnectionSource.values()) {
                if (!Objects.equals(hibernateDatastore, this)) {
                    hibernateDatastore.setApplicationContext(applicationContext);
                }
            }
            this.eventPublisher = new ConfigurableApplicationContextEventPublisher(
                    (ConfigurableApplicationContext) applicationContext);
            HibernateConnectionSourceSettings settings =
                    getConnectionSources().getDefaultConnectionSource().getSettings();
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
            ClosureEventTriggeringInterceptor interceptor = hibernateSettings.getEventTriggeringInterceptor();
            interceptor.setDatastore(this);
            interceptor.setEventPublisher(eventPublisher);
            HibernateMappingContext mappingContext = getMappingContext();
            // make messages from the application context available to validation
            ValidatorRegistry validatorRegistry = createValidatorRegistry(applicationContext);
            configureValidatorRegistry(mappingContext, validatorRegistry, applicationContext);
            mappingContext.setValidatorRegistry(validatorRegistry);

            registerEventListeners(eventPublisher);
            this.eventPublisher.publishEvent(new DatastoreInitializedEvent(this));
        }
    }

    public IHibernateTemplate getHibernateTemplate(int flushMode) {
        return new GrailsHibernateTemplate(getSessionFactory(), this, flushMode);
    }

    public void withFlushMode(FlushMode flushMode, Callable<Boolean> callable) {
        final org.hibernate.Session session = sessionFactory.getCurrentSession();
        org.hibernate.FlushMode previousMode = null;
        Boolean reset = true;
        try {
            if (session != null) {
                previousMode = session.getHibernateFlushMode();
                session.setHibernateFlushMode(flushMode);
            }
            try {
                reset = callable.call();
            } catch (Exception e) {
                reset = false;
            }
        } finally {
            if (session != null && previousMode != null && reset) {
                session.setHibernateFlushMode(previousMode);
            }
        }
    }

    public org.hibernate.Session openSession() {
        org.hibernate.Session session = this.sessionFactory.openSession();
        session.setHibernateFlushMode(defaultFlushMode);
        return session;
    }

    @Override
    public Session getCurrentSession() throws ConnectionNotFoundException {
        // HibernateSession, just a thin wrapper around default session handling so simply return a new
        // instance here
        return new HibernateSession(this, sessionFactory);
    }

    @Override
    public void destroy() {
        if (!this.destroyed) {
            try {
                super.destroy();
                HibernateGormInstanceApi.resetInsertActive();
                try {
                    connectionSources.close();
                } catch (IOException e) {
                    LOG.error("There was an error shutting down GORM for an entity: {}", e.getMessage(), e);
                }
            } finally {
                MappingCacheHolder.getInstance().clear();
                try {
                    if (this.gormEnhancer != null) {
                        this.gormEnhancer.close();
                    }
                } catch (IOException e) {
                    LOG.error("There was an error shutting down GORM enhancer", e);
                }
                destroyed = true;
            }
        }
    }

    @Override
    public void addTenantForSchema(String schemaName) {
        addTenantForSchemaInternal(schemaName);
        registerAllEntitiesWithEnhancer();
        HibernateConnectionSource defaultConnectionSource =
                (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        DataSource dataSource = defaultConnectionSource.getDataSource();
        if (dataSource instanceof TransactionAwareDataSourceProxy) {
            dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
        }
        if (dataSource == null) return;
        Object existing = TransactionSynchronizationManager.getResource(dataSource);
        if (existing instanceof ConnectionHolder connectionHolder) {
            Connection connection = connectionHolder.getConnection();
            try {
                if (!connection.isClosed() && !connection.isReadOnly()) {
                    schemaHandler.useDefaultSchema(connection);
                }
            } catch (SQLException e) {
                throw new DatastoreConfigurationException("Failed to reset to default schema: " + e.getMessage(), e);
            }
        }
    }

    public final Metadata getMetadata() {
        return metadata;
    }

    protected void registerAllEntitiesWithEnhancer() {
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            gormEnhancer.registerEntity(persistentEntity);
        }
    }

    private void addTenantForSchemaInternal(final String schemaName) {
        if (multiTenantMode != MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            throw new ConfigurationException(
                    "The method [addTenantForSchema] can only be called with multi-tenancy mode SCHEMA. Current mode is: "
                            + multiTenantMode);
        }
        HibernateConnectionSourceFactory factory = (HibernateConnectionSourceFactory) connectionSources.getFactory();
        HibernateConnectionSource defaultConnectionSource =
                (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        HibernateConnectionSourceSettings tenantSettings;
        try {
            tenantSettings = (HibernateConnectionSourceSettings)
                    connectionSources.getDefaultConnectionSource().getSettings().clone();
        } catch (CloneNotSupportedException e) {
            throw new ConfigurationException("Couldn't clone default Hibernate settings! " + e.getMessage(), e);
        }
        tenantSettings.getHibernate().put(Environment.DEFAULT_SCHEMA, schemaName);

        String dbCreate = tenantSettings.getDataSource().getDbCreate();

        Action schemaAutoTooling = Action.interpretHbm2ddlSetting(dbCreate);
        if (schemaAutoTooling != Action.VALIDATE && schemaAutoTooling != Action.NONE) {

            try (Connection connection = defaultConnectionSource.getDataSource().getConnection()) {
                try {
                    schemaHandler.useSchema(connection, schemaName);
                } catch (Exception e) {
                    // schema doesn't exist
                    schemaHandler.createSchema(connection, schemaName);
                }
                schemaHandler.useDefaultSchema(connection);
            } catch (SQLException e) {
                throw new DatastoreConfigurationException(
                        String.format("Failed to create schema for name [%s]", schemaName), e);
            }
        }

        DataSource dataSource = defaultConnectionSource.getDataSource();
        dataSource = new SchemaTenantDataSource(dataSource, schemaName, schemaHandler);
        DefaultConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource =
                new DefaultConnectionSource<>(schemaName, dataSource, tenantSettings.getDataSource());
        ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource =
                factory.create(schemaName, dataSourceConnectionSource, tenantSettings);
        HibernateDatastore childDatastore = getChildDatastore(connectionSource);
        datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
    }

    private @NonNull HibernateDatastore getChildDatastore(
            ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource) {
        SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources =
                new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
        return new HibernateDatastore(
                singletonConnectionSources,
                (HibernateMappingContext) HibernateDatastore.this.mappingContext,
                HibernateDatastore.this.eventPublisher) {
            @Override
            protected HibernateGormEnhancer initialize() {
                return null;
            }
        };
    }

    private Metadata getMetadataInternal() {
        Metadata metadata = null;
        ServiceRegistry bootstrapServiceRegistry = ((SessionFactoryImplementor) sessionFactory)
                .getServiceRegistry()
                .getParentServiceRegistry();
        if (bootstrapServiceRegistry == null) return null;
        Iterable<Integrator> integrators;
        IntegratorService integratorService = bootstrapServiceRegistry.getService(IntegratorService.class);
        if (integratorService == null) return null;
        integrators = integratorService.getIntegrators();
        for (Integrator integrator : integrators) {
            if (integrator instanceof MetadataIntegrator) {
                metadata = ((MetadataIntegrator) integrator).getMetadata();
            }
        }
        return metadata;
    }

    private static HibernateConnectionSourceFactory createConnectionFactoryForDataSource(
            final DataSource dataSource, Class<?>... classes) {
        HibernateConnectionSourceFactory hibernateConnectionSourceFactory =
                new HibernateConnectionSourceFactory(classes);
        hibernateConnectionSourceFactory.setDataSourceConnectionSourceFactory(new DataSourceConnectionSourceFactory() {
            @Override
            public ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {
                if (ConnectionSource.DEFAULT.equals(name)) {
                    return new DataSourceConnectionSource(ConnectionSource.DEFAULT, dataSource, settings);
                } else {
                    return super.create(name, settings);
                }
            }
        });
    return hibernateConnectionSourceFactory;
  }

  /** Create validator registry. */
  protected ValidatorRegistry createValidatorRegistry(MessageSource messageSource) {
    return ValidatorRegistries.createValidatorRegistry(
        mappingContext,
        getConnectionSources().getDefaultConnectionSource().getSettings(),
        messageSource);
  }

  @Override
  public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
    return this.multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA
        ? MultiTenancySettings.MultiTenancyMode.DATABASE
        : this.multiTenantMode;
  }

  @Override
  public Datastore getDatastoreForTenantId(Serializable tenantId) {
    if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
      return getDatastoreForConnection(tenantId.toString());
    } else {
      return this;
    }
  }

  @Override
  public TenantResolver getTenantResolver() {
    return this.tenantResolver;
  }

  @Override
  public ConnectionSources<SessionFactory, HibernateConnectionSourceSettings>
      getConnectionSources() {
    return this.connectionSources;
  }

  /** Resolve tenant ids. */
  public Iterable<Serializable> resolveTenantIds() {
    if (this.tenantResolver instanceof AllTenantsResolver) {
      return ((AllTenantsResolver) tenantResolver).resolveTenantIds();
    } else if (this.multiTenantMode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
      List<Serializable> tenantIds = new ArrayList<>();
      for (ConnectionSource connectionSource : this.connectionSources.getAllConnectionSources()) {
        if (!ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
          tenantIds.add(connectionSource.getName());
        }
      }
      return tenantIds;
    } else {
      return Collections.emptyList();
    }
  }

  /** Resolve tenant identifier. */
  public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
    return Tenants.currentId(this);
  }

  /** Returns whether auto flush. */
  public boolean isAutoFlush() {
    return defaultFlushMode == FlushMode.AUTO.level;
  }

  /**
   * @return Obtains the default flush mode level
   */
  public int getDefaultFlushMode() {
    return defaultFlushMode;
  }

  /**
   * @return The name of the default flush mode
   */
  public String getDefaultFlushModeName() {
    return defaultFlushModeName;
  }

  /** Returns whether fail on error. */
  public boolean isFailOnError() {
    return failOnError;
  }

  /** Returns whether osiv read only. */
  public boolean isOsivReadOnly() {
    return osivReadOnly;
  }

  /** Returns whether pass read only to hibernate. */
  public boolean isPassReadOnlyToHibernate() {
    return passReadOnlyToHibernate;
  }

  /** Returns whether cache queries. */
  public boolean isCacheQueries() {
    return isCacheQueries;
  }

  /**
   * @return The Hibernate {@link SessionFactory} being used by this datastore instance
   */
  public SessionFactory getSessionFactory() {
    return sessionFactory;
  }

  /**
   * @return The {@link DataSource} being used by this datastore instance
   */
  public DataSource getDataSource() {
    return ((HibernateConnectionSource) this.connectionSources.getDefaultConnectionSource())
        .getDataSource();
  }

  /** For testing: returns the event triggering interceptor. */
  public AbstractHibernateEventListener getEventTriggeringInterceptor() {
    return eventTriggeringInterceptor;
  }

  /**
   * @return The event listener that populates lastUpdated and dateCreated
   */
  public AutoTimestampEventListener getAutoTimestampEventListener() {
    return autoTimestampEventListener;
  }

  /**
   * @return The data source name being used
   */
  public String getDataSourceName() {
    return this.dataSourceName;
  }

  /** Gets the hibernate template using the default flush mode. */
  public IHibernateTemplate getHibernateTemplate() {
    return getHibernateTemplate(defaultFlushMode);
  }

  @Override
  public <T> T withSession(final Closure<T> callable) {
    Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable);
    return getHibernateTemplate().execute(multiTenantCallable);
  }

  /** Execute the given closure in a new session. */
  public <T> T withNewSession(final Closure<T> callable) {
    Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable);
    return getHibernateTemplate().executeWithNewSession(multiTenantCallable);
  }

  @Override
  public <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
    if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
      HibernateDatastore datastore = getDatastoreForConnection(tenantId.toString());
      SessionFactory sf = datastore.getSessionFactory();
      return datastore
          .getHibernateTemplate()
          .executeWithExistingOrCreateNewSession(sf, callable);
    } else {
      return withNewSession(callable);
    }
  }

  /** Enable the tenant id filter for the given datastore and entity */
  public void enableMultiTenancyFilter() {
    Serializable currentId = Tenants.currentId(this);
    if (ConnectionSource.DEFAULT.equals(currentId)) {
      disableMultiTenancyFilter();
    } else {
      getHibernateTemplate()
          .getSessionFactory()
          .getCurrentSession()
          .enableFilter(GormProperties.TENANT_IDENTITY)
          .setParameter(GormProperties.TENANT_IDENTITY, currentId);
    }
  }

  /** Disable the tenant id filter for the given datastore and entity */
  public void disableMultiTenancyFilter() {
    getHibernateTemplate()
        .getSessionFactory()
        .getCurrentSession()
        .disableFilter(GormProperties.TENANT_IDENTITY);
  }

  /** Prepare multi tenant closure. */
  protected <T> Closure<T> prepareMultiTenantClosure(final Closure<T> callable) {
    final boolean isMultiTenant =
        getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR;
    return isMultiTenant
        ? new Closure<T>(this) {
          @Override
          public T call(Object... args) {
            enableMultiTenancyFilter();
            try {
              return callable.call(args);
            } finally {
              disableMultiTenancyFilter();
            }
          }
        }
        : callable;
  }

  @Override
  @PreDestroy
  public void close() {
    try {
      destroy();
    } catch (Exception e) {
      LOG.error("Error closing hibernate datastore: " + e.getMessage(), e);
    }
  }

  /**
   * We use a separate enum here because the classes differ between Hibernate 3 and 4
   *
   * @see org.hibernate.FlushMode
   */
  public enum FlushMode {
    /** The manual constant. */
    MANUAL(0),
    /** The commit constant. */
    COMMIT(5),
    /** The auto constant. */
    AUTO(10),
    /** The always constant. */
    ALWAYS(20);

    private final int level;

    FlushMode(int level) {
      this.level = level;
    }

    /** Gets the level. */
    public int getLevel() {
      return level;
    }
  }
}

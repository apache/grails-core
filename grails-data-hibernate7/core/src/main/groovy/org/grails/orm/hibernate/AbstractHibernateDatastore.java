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

import grails.gorm.multitenancy.Tenants;
import groovy.lang.Closure;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler;
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler;
import org.grails.datastore.gorm.validation.registry.support.ValidatorRegistries;
import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreAware;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.multitenancy.*;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException;
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver;
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.event.listener.AbstractHibernateEventListener;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.*;
import org.springframework.core.env.PropertyResolver;

/**
 * Datastore implementation that uses a Hibernate SessionFactory underneath.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public abstract class AbstractHibernateDatastore extends AbstractDatastore
    implements ApplicationContextAware,
        Settings,
        SchemaMultiTenantCapableDatastore<SessionFactory, HibernateConnectionSourceSettings>,
        TransactionCapableDatastore,
        Closeable,
        MessageSourceAware,
        MultipleConnectionSourceCapableDatastore {

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

  /**
   * Creates a new {@link AbstractHibernateDatastore} instance.
   */
  protected AbstractHibernateDatastore(
      ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources,
      HibernateMappingContext mappingContext) {
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
  }

  /**
   * Creates a new {@link AbstractHibernateDatastore} instance.
   */
  protected AbstractHibernateDatastore(
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

    osivReadOnly = config.getProperty(CONFIG_PROPERTY_OSIV_READONLY, Boolean.class, false);
    passReadOnlyToHibernate =
        config.getProperty(CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE, Boolean.class, false);
    isCacheQueries = config.getProperty(CONFIG_PROPERTY_CACHE_QUERIES, Boolean.class, false);

    if (config.getProperty(SETTING_AUTO_FLUSH, Boolean.class, false)) {
      this.defaultFlushModeName = FlushMode.AUTO.name();
      defaultFlushMode = FlushMode.AUTO.level;
    } else {
      FlushMode flushMode =
          config.getProperty(SETTING_FLUSH_MODE, FlushMode.class, FlushMode.COMMIT);
      this.defaultFlushModeName = flushMode.name();
      defaultFlushMode = flushMode.level;
    }
    failOnError = config.getProperty(SETTING_FAIL_ON_ERROR, Boolean.class, false);
    markDirty = config.getProperty(SETTING_MARK_DIRTY, Boolean.class, false);
    this.tenantResolver = new FixedTenantResolver();
    this.multiTenantMode = MultiTenancySettings.MultiTenancyMode.NONE;
    this.schemaHandler = new DefaultSchemaHandler();
  }

  /**
   * Creates a new {@link AbstractHibernateDatastore} instance.
   */
  public AbstractHibernateDatastore(
      MappingContext mappingContext, SessionFactory sessionFactory, PropertyResolver config) {
    this(mappingContext, sessionFactory, config, null, ConnectionSource.DEFAULT);
  }

  @Override
  public void setMessageSource(MessageSource messageSource) {
    ValidatorRegistry validatorRegistry = createValidatorRegistry(messageSource);
    this.mappingContext.setValidatorRegistry(validatorRegistry);
  }

  /**
   * Create validator registry.
   */
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

  /**
   * Obtain a child datastore for the given connection name
   *
   * @param connectionName The name of the connection
   * @return The child data store
   */
  public abstract AbstractHibernateDatastore getDatastoreForConnection(String connectionName);

  /**
   * Resolve tenant ids.
   */
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

  /**
   * Resolve tenant identifier.
   */
  public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
    return Tenants.currentId(this);
  }

  /**
   * Returns whether auto flush.
   */
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
   * @return The name of the default value flush
   */
  public String getDefaultFlushModeName() {
    return defaultFlushModeName;
  }

  /**
   * Returns whether fail on error.
   */
  public boolean isFailOnError() {
    return failOnError;
  }

  /**
   * Returns whether osiv read only.
   */
  public boolean isOsivReadOnly() {
    return osivReadOnly;
  }

  /**
   * Returns whether pass read only to hibernate.
   */
  public boolean isPassReadOnlyToHibernate() {
    return passReadOnlyToHibernate;
  }

  /**
   * Returns whether cache queries.
   */
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

  /** TODO: Add description. */
  // for testing
  public AbstractHibernateEventListener getEventTriggeringInterceptor() {
    return eventTriggeringInterceptor;
  }

  /**
   * @return The event listener that populates lastUpdated and dateCreated
   */
  public AutoTimestampEventListener getAutoTimestampEventListener() {
    return autoTimestampEventListener;
  }

  @Override
  public boolean hasCurrentSession() {
    // Consider a session present only if a bound session exists and is connected
    org.grails.datastore.mapping.transactions.SessionHolder sessionHolder =
        (org.grails.datastore.mapping.transactions.SessionHolder)
            org.springframework.transaction.support.TransactionSynchronizationManager.getResource(
                this);
    if (sessionHolder == null) {
      return false;
    }
    return sessionHolder.getValidatedSession() != null;
  }

  /**
   * @return The data source name being used
   */
  public String getDataSourceName() {
    return this.dataSourceName;
  }

  /**
   * Execute the given operation with the given flush mode
   *
   * @param flushMode the flush mode
   * @param callable The callable
   */
  public abstract void withFlushMode(FlushMode flushMode, Callable<Boolean> callable);

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

    /**
     * Gets the level.
     */
    public int getLevel() {
      return level;
    }
  }

  @Override
  public void destroy() {
    if (!this.destroyed) {
      super.destroy();
      HibernateGormInstanceApi.resetInsertActive();
      try {
        connectionSources.close();
      } catch (IOException e) {
        LOG.error("There was an error shutting down GORM for an entity: " + e.getMessage(), e);
      }
      destroyed = true;
    }
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
   * Obtains a hibernate template for the given flush mode
   *
   * @param flushMode The flush mode
   * @return The IHibernateTemplate
   */
  public abstract IHibernateTemplate getHibernateTemplate(int flushMode);

  /**
   * Gets the hibernate template.
   */
  public IHibernateTemplate getHibernateTemplate() {
    return getHibernateTemplate(defaultFlushMode);
  }

  /**
   * @return Opens a session
   */
  public abstract Session openSession();

  @Override
  public <T> T withSession(final Closure<T> callable) {
    Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable);
    return getHibernateTemplate().execute(multiTenantCallable);
  }

  /**
   * With new session.
   */
  public <T> T withNewSession(final Closure<T> callable) {
    Closure<T> multiTenantCallable = prepareMultiTenantClosure(callable);
    return getHibernateTemplate().executeWithNewSession(multiTenantCallable);
  }

  @Override
  public <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
    if (getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
      AbstractHibernateDatastore datastore = getDatastoreForConnection(tenantId.toString());
      SessionFactory sessionFactory = datastore.getSessionFactory();

      return datastore
          .getHibernateTemplate()
          .executeWithExistingOrCreateNewSession(sessionFactory, callable);
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

  /**
   * Prepare multi tenant closure.
   */
  protected <T> Closure<T> prepareMultiTenantClosure(final Closure<T> callable) {
    final boolean isMultiTenant =
        getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR;
    Closure<T> multiTenantCallable;
    if (isMultiTenant) {
      multiTenantCallable =
          new Closure<T>(this) {
            @Override
            public T call(Object... args) {
              enableMultiTenancyFilter();
              try {
                return callable.call(args);
              } finally {
                disableMultiTenancyFilter();
              }
            }
          };
    } else {
      multiTenantCallable = callable;
    }
    return multiTenantCallable;
  }
}

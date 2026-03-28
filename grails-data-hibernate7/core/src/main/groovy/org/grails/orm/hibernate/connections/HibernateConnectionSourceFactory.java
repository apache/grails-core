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
package org.grails.orm.hibernate.connections;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.annotation.Nullable;

import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.Configuration;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.Resource;

import org.grails.datastore.gorm.jdbc.connections.CachedDataSourceConnectionSourceFactory;
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettingsBuilder;
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.grails.datastore.mapping.core.connections.AbstractConnectionSourceFactory;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.HibernateEventListeners;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;

/**
 * Constructs {@link SessionFactory} instances from a {@link HibernateMappingContext}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@SuppressWarnings({"PMD.CloseResource", "PMD.AvoidCatchingThrowable", "PMD.DataflowAnomalyAnalysis"})
public class HibernateConnectionSourceFactory
        extends AbstractConnectionSourceFactory<SessionFactory, HibernateConnectionSourceSettings>
        implements ApplicationContextAware, MessageSourceAware {

    static {
        // use Slf4j logging by default
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    protected DataSourceConnectionSourceFactory dataSourceConnectionSourceFactory =
            new CachedDataSourceConnectionSourceFactory();

    protected HibernateMappingContext mappingContext;
    protected final Class<?>[] persistentClasses;
    protected final org.grails.orm.hibernate.proxy.GrailsBytecodeProvider bytecodeProvider;
    protected HibernateEventListeners hibernateEventListeners;
    protected Interceptor interceptor;
    protected MessageSource messageSource = new StaticMessageSource();
    private ApplicationContext applicationContext;

    public org.grails.orm.hibernate.proxy.GrailsBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    public HibernateConnectionSourceFactory(org.grails.orm.hibernate.proxy.GrailsBytecodeProvider bytecodeProvider, Class<?>... classes) {
        this.bytecodeProvider = bytecodeProvider;
        this.persistentClasses = classes != null ? classes.clone() : new Class[0];
    }

    public HibernateConnectionSourceFactory(Class<?>... classes) {
        this(new org.grails.orm.hibernate.proxy.GrailsBytecodeProvider(), classes);
    }

    private static void applyResources(Resource[] resources, ResourceConfigurer configurer) {
        if (resources == null) return;
        for (Resource resource : resources) {
            try {
                configurer.apply(resource);
            } catch (IOException e) {
                throw new ConfigurationException(
                        "Cannot configure Hibernate config for location: " + resource.getFilename(), e);
            }
        }
    }

    private static void configureNamingStrategy(
            String name,
            HibernateMappingContextConfiguration configuration,
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings) {
        try {
            Class<? extends PhysicalNamingStrategy> namingStrategy = hibernateSettings.getNaming_strategy();
            if (namingStrategy != null) {
                configuration.getNamingStrategyProvider().configureNamingStrategy(name, namingStrategy);
            }
        } catch (Throwable e) {
            throw new ConfigurationException("Error configuring naming strategy: " + e.getMessage(), e);
        }
    }

    private static ClosureEventTriggeringInterceptor resolveEventTriggeringInterceptor(
            Class<? extends ClosureEventTriggeringInterceptor> clazz) {
        return clazz != null ? BeanUtils.instantiateClass(clazz) : new ClosureEventTriggeringInterceptor();
    }

    private static <F extends ConnectionSourceSettings> DataSourceSettings extractDataSourceFallback(
            F fallbackSettings) {
        if (fallbackSettings instanceof HibernateConnectionSourceSettings hcs) {
            return hcs.getDataSource();
        }
        if (fallbackSettings instanceof DataSourceSettings ds) {
            return ds;
        }
        return null;
    }

    public Class<?>[] getPersistentClasses() {
        return persistentClasses != null ? persistentClasses.clone() : new Class[0];
    }

    public void setHibernateEventListeners(HibernateEventListeners hibernateEventListeners) {
        this.hibernateEventListeners = hibernateEventListeners;
    }

    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    public HibernateMappingContext getMappingContext() {
        return mappingContext;
    }

    public ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> create(
            String name,
            ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource,
            HibernateConnectionSourceSettings settings) {
        HibernateMappingContextConfiguration configuration =
                buildConfiguration(name, dataSourceConnectionSource, settings);
        SessionFactory sessionFactory = configuration.buildSessionFactory();
        return new HibernateConnectionSource(name, sessionFactory, dataSourceConnectionSource, settings);
    }

    public HibernateMappingContextConfiguration buildConfiguration(
            String name,
            ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource,
            HibernateConnectionSourceSettings settings) {
        if (mappingContext == null) {
            mappingContext = new HibernateMappingContext(settings, applicationContext, persistentClasses);
        }

        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
        HibernateMappingContextConfiguration configuration = resolveConfiguration(hibernateSettings.getConfigClass());
        configuration.setBytecodeProvider(this.bytecodeProvider);
        configuration.getProperties().put("jakarta.persistence.nonJtaDataSource", dataSourceConnectionSource.getSource());
        if (applicationContext != null) {
            configuration.setApplicationContext(applicationContext);
        }

        configureValidator(configuration, dataSourceConnectionSource.getSettings());
        configureDataSource(configuration, dataSourceConnectionSource);
        configureResourceLocations(configuration, hibernateSettings);

        if (interceptor != null) configuration.setInterceptor(interceptor);
        if (hibernateSettings.getAnnotatedClasses() != null)
            configuration.addAnnotatedClasses(hibernateSettings.getAnnotatedClasses());
        if (hibernateSettings.getAnnotatedPackages() != null)
            configuration.addPackages(hibernateSettings.getAnnotatedPackages());
        if (hibernateSettings.getPackagesToScan() != null)
            configuration.scanPackages(hibernateSettings.getPackagesToScan());

        configureNamingStrategy(name, configuration, hibernateSettings);

        ClosureEventTriggeringInterceptor eventTriggeringInterceptor =
                resolveEventTriggeringInterceptor(hibernateSettings.getClosureEventTriggeringInterceptorClass());
        hibernateSettings.setEventTriggeringInterceptor(eventTriggeringInterceptor);

        configuration.setEventListeners(HibernateConnectionSourceSettings.HibernateSettings.toHibernateEventListeners(
                eventTriggeringInterceptor));
        configuration.setHibernateEventListeners(
                this.hibernateEventListeners != null ?
                        this.hibernateEventListeners :
                        hibernateSettings.getHibernateEventListeners());
        configuration.setHibernateMappingContext(mappingContext);
        configuration.setDataSourceName(name);
        configuration.setSessionFactoryBeanName(
                ConnectionSource.DEFAULT.equals(name) ? "sessionFactory" : "sessionFactory_" + name);
        configuration.addProperties(settings.toProperties());
        return configuration;
    }

    private HibernateMappingContextConfiguration resolveConfiguration(Class<? extends Configuration> configClass) {
        if (configClass == null) return new HibernateMappingContextConfiguration();
        if (!HibernateMappingContextConfiguration.class.isAssignableFrom(configClass)) {
            throw new ConfigurationException(
                    "The configClass setting must be a subclass for [HibernateMappingContextConfiguration]");
        }
        return (HibernateMappingContextConfiguration) BeanUtils.instantiateClass(configClass);
    }

    private void configureValidator(
            HibernateMappingContextConfiguration configuration, DataSourceSettings dataSourceSettings) {
        if (!JakartaValidatorRegistry.isAvailable() || messageSource == null) return;
        ValidatorRegistry registry = new JakartaValidatorRegistry(mappingContext, dataSourceSettings, messageSource);
        mappingContext.setValidatorRegistry(registry);
        configuration.getProperties().put("jakarta.persistence.validation.factory", registry);
    }

    private void configureDataSource(
            HibernateMappingContextConfiguration configuration,
            ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource) {
        String dsName = dataSourceConnectionSource.getName();
        String beanName = ConnectionSource.DEFAULT.equals(dsName) ? "dataSource" : "dataSource_" + dsName;
        if (applicationContext != null && applicationContext.containsBean(beanName)) {
            configuration.setApplicationContext(applicationContext);
        } else {
            configuration.setDataSourceConnectionSource(dataSourceConnectionSource);
        }
    }

    private void configureResourceLocations(
            HibernateMappingContextConfiguration configuration,
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings) {
        applyResources(hibernateSettings.getConfigLocations(), r -> configuration.configure(r.getURL()));
        applyResources(hibernateSettings.getMappingLocations(), r -> {
            try (var is = r.getInputStream()) {
                configuration.addInputStream(is);
            }
        });
        applyResources(
                hibernateSettings.getCacheableMappingLocations(), r -> configuration.addCacheableFile(r.getFile()));
        applyResources(hibernateSettings.getMappingJarLocations(), r -> configuration.addJar(r.getFile()));
        applyResources(hibernateSettings.getMappingDirectoryLocations(), r -> {
            File file = r.getFile();
            if (!file.isDirectory()) {
                throw new IllegalArgumentException(
                        "Mapping directory location [" + r + "] does not denote a directory");
            }
            configuration.addDirectory(file);
        });
    }

    public void setDataSourceConnectionSourceFactory(
            DataSourceConnectionSourceFactory dataSourceConnectionSourceFactory) {
        this.dataSourceConnectionSourceFactory = dataSourceConnectionSourceFactory;
    }

    @Override
    public ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> create(
            String name, HibernateConnectionSourceSettings settings) {
        ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource =
                dataSourceConnectionSourceFactory.create(name, settings.getDataSource());
        return create(name, dataSourceConnectionSource, settings);
    }

    @Override
    public Serializable getConnectionSourcesConfigurationKey() {
        return Settings.SETTING_DATASOURCES;
    }

    @Override
    public <F extends ConnectionSourceSettings> HibernateConnectionSourceSettings buildRuntimeSettings(
            String name, PropertyResolver configuration, F fallbackSettings) {
        return buildSettingsWithPrefix(configuration, fallbackSettings, "");
    }

    @Override
    protected <F extends ConnectionSourceSettings> HibernateConnectionSourceSettings buildSettings(
            String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        if (isDefaultDataSource) {
            String qualified = Settings.SETTING_DATASOURCES + '.' + Settings.SETTING_DATASOURCE;
            HibernateConnectionSourceSettings settings =
                    new HibernateConnectionSourceSettingsBuilder(configuration, "", fallbackSettings).build();
            var config = configuration.getProperty(qualified, Map.class, Collections.emptyMap());
            if (!config.isEmpty()) {
                DataSourceSettings dsFallback = extractDataSourceFallback(fallbackSettings);
                settings.setDataSource(new DataSourceSettingsBuilder(configuration, qualified, dsFallback).build());
            }
            return settings;
        }
        return buildSettingsWithPrefix(configuration, fallbackSettings, Settings.SETTING_DATASOURCES + "." + name);
    }

    private <F extends ConnectionSourceSettings> HibernateConnectionSourceSettings buildSettingsWithPrefix(
            PropertyResolver configuration, F fallbackSettings, String prefix) {
        DataSourceSettings dsFallback = extractDataSourceFallback(fallbackSettings);
        HibernateConnectionSourceSettings settings =
                new HibernateConnectionSourceSettingsBuilder(configuration, prefix, fallbackSettings).build();
        if (prefix.isEmpty() ||
                configuration
                        .getProperty(prefix + ".dataSource", Map.class, Collections.emptyMap())
                        .isEmpty()) {
            settings.setDataSource(new DataSourceSettingsBuilder(configuration, prefix, dsFallback).build());
        }
        return settings;
    }

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.messageSource = applicationContext;
    }

    @Override
    public void setMessageSource(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @FunctionalInterface
    private interface ResourceConfigurer {

        void apply(Resource resource) throws IOException;
    }
}

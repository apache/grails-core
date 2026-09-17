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
package org.grails.orm.hibernate.connections

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.hibernate.Interceptor
import org.hibernate.SessionFactory
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.cfg.Configuration
import org.springframework.beans.BeanUtils
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceAware
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.env.PropertyResolver
import org.springframework.core.io.Resource

import org.grails.datastore.gorm.jdbc.connections.CachedDataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettingsBuilder
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry
import org.grails.datastore.mapping.core.connections.AbstractConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.grails.orm.hibernate.HibernateEventListeners
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor

import javax.sql.DataSource

/**
 * Constructs {@link SessionFactory} instances from a {@link HibernateMappingContext}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@SuppressWarnings(['PMD.CloseResource', 'PMD.AvoidCatchingThrowable', 'PMD.DataflowAnomalyAnalysis'])
@CompileStatic
class HibernateConnectionSourceFactory
        extends AbstractConnectionSourceFactory<SessionFactory, HibernateConnectionSourceSettings>
        implements ApplicationContextAware, MessageSourceAware {

    static {
        // use Slf4j logging by default
        System.setProperty('org.jboss.logging.provider', 'slf4j')
    }

    protected DataSourceConnectionSourceFactory dataSourceConnectionSourceFactory =
            new CachedDataSourceConnectionSourceFactory()

    protected HibernateMappingContext mappingContext
    protected final Class<?>[] persistentClasses
    protected final GrailsBytecodeProvider bytecodeProvider
    protected HibernateEventListeners hibernateEventListeners
    protected Interceptor interceptor
    protected MessageSource messageSource = new StaticMessageSource()
    private ApplicationContext applicationContext

    GrailsBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider
    }

    HibernateConnectionSourceFactory(GrailsBytecodeProvider bytecodeProvider, Class<?>... classes) {
        this.bytecodeProvider = bytecodeProvider
        this.persistentClasses = classes != null ? classes.clone() : new Class[0]
    }

    HibernateConnectionSourceFactory(Class<?>... classes) {
        this(new GrailsBytecodeProvider(), classes)
    }

    private static void applyResources(Resource[] resources, ResourceConfigurer configurer) {
        if (resources == null) return
        for (Resource resource : resources) {
            try {
                configurer.apply(resource)
            }
            catch (IOException e) {
                throw new ConfigurationException(
                        "Cannot configure Hibernate config for location: ${resource.filename}", e)
            }
        }
    }

    private static void configureNamingStrategy(
            String name,
            HibernateMappingContextConfiguration configuration,
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings) {
        try {
            Class<? extends PhysicalNamingStrategy> namingStrategy = hibernateSettings.naming_strategy
            if (namingStrategy != null) {
                configuration.namingStrategyProvider.configureNamingStrategy(name, namingStrategy)
            }
        }
        catch (Throwable e) {
            throw new ConfigurationException("Error configuring naming strategy: ${e.message}", e)
        }
    }

    private static ClosureEventTriggeringInterceptor resolveEventTriggeringInterceptor(
            Class<? extends ClosureEventTriggeringInterceptor> clazz) {
        return clazz != null ? BeanUtils.instantiateClass(clazz) : new ClosureEventTriggeringInterceptor()
    }

    private static <F extends ConnectionSourceSettings> DataSourceSettings extractDataSourceFallback(
            F fallbackSettings) {
        if (fallbackSettings instanceof HibernateConnectionSourceSettings) {
            return fallbackSettings.dataSource
        }
        if (fallbackSettings instanceof DataSourceSettings) {
            return fallbackSettings
        }
        return null
    }

    Class<?>[] getPersistentClasses() {
        return persistentClasses != null ? persistentClasses.clone() : new Class[0]
    }

    void setHibernateEventListeners(HibernateEventListeners hibernateEventListeners) {
        this.hibernateEventListeners = hibernateEventListeners
    }

    void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor
    }

    HibernateMappingContext getMappingContext() {
        return mappingContext
    }

    ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> create(
            String name,
            ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource,
            HibernateConnectionSourceSettings settings) {
        HibernateMappingContextConfiguration configuration =
                buildConfiguration(name, dataSourceConnectionSource, settings)
        SessionFactory sessionFactory = configuration.buildSessionFactory()
        return new HibernateConnectionSource(name, sessionFactory, dataSourceConnectionSource, settings)
    }

    HibernateMappingContextConfiguration buildConfiguration(
            String name,
            ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource,
            HibernateConnectionSourceSettings settings) {
        if (mappingContext == null) {
            mappingContext = new HibernateMappingContext(settings, applicationContext, persistentClasses)
        }

        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.hibernate
        HibernateMappingContextConfiguration configuration = resolveConfiguration(hibernateSettings.configClass)
        configuration.setBytecodeProvider(this.bytecodeProvider)
        configuration.setDataSourceName(name)
        configuration.properties.put('jakarta.persistence.nonJtaDataSource', dataSourceConnectionSource.source)
        if (applicationContext != null) {
            configuration.setApplicationContext(applicationContext)
        }

        configureValidator(configuration, dataSourceConnectionSource.settings)
        configureDataSource(configuration, dataSourceConnectionSource)
        configureResourceLocations(configuration, hibernateSettings)

        if (interceptor != null) configuration.setInterceptor(interceptor)
        if (hibernateSettings.annotatedClasses != null)
            configuration.addAnnotatedClasses(hibernateSettings.annotatedClasses)
        if (hibernateSettings.annotatedPackages != null)
            configuration.addPackages(hibernateSettings.annotatedPackages)
        if (hibernateSettings.packagesToScan != null)
            configuration.scanPackages(hibernateSettings.packagesToScan)

        configureNamingStrategy(name, configuration, hibernateSettings)

        ClosureEventTriggeringInterceptor eventTriggeringInterceptor =
                resolveEventTriggeringInterceptor(hibernateSettings.closureEventTriggeringInterceptorClass)
        hibernateSettings.setEventTriggeringInterceptor(eventTriggeringInterceptor)

        configuration.setEventListeners(HibernateConnectionSourceSettings.HibernateSettings.toHibernateEventListeners(
                eventTriggeringInterceptor))
        configuration.setHibernateEventListeners(
                this.hibernateEventListeners != null ?
                        this.hibernateEventListeners :
                        hibernateSettings.hibernateEventListeners)
        configuration.setHibernateMappingContext(mappingContext)
        configuration.setDataSourceName(name)
        configuration.setSessionFactoryBeanName(
                ConnectionSource.DEFAULT.equals(name) ? 'sessionFactory' : "sessionFactory_${name}")
        configuration.addProperties(settings.toProperties())
        return configuration
    }

    private HibernateMappingContextConfiguration resolveConfiguration(Class<? extends Configuration> configClass) {
        if (configClass == null) return new HibernateMappingContextConfiguration()
        if (!HibernateMappingContextConfiguration.isAssignableFrom(configClass)) {
            throw new ConfigurationException(
                    'The configClass setting must be a subclass for [HibernateMappingContextConfiguration]')
        }
        return (HibernateMappingContextConfiguration) BeanUtils.instantiateClass(configClass)
    }

    private void configureValidator(
            HibernateMappingContextConfiguration configuration, DataSourceSettings dataSourceSettings) {
        if (!JakartaValidatorRegistry.available || messageSource == null) return
        ValidatorRegistry registry = new JakartaValidatorRegistry(mappingContext, dataSourceSettings, messageSource)
        mappingContext.setValidatorRegistry(registry)
        configuration.properties.put('jakarta.persistence.validation.factory', registry)
    }

    private void configureDataSource(
            HibernateMappingContextConfiguration configuration,
            ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource) {
        String dsName = dataSourceConnectionSource.name
        String beanName = ConnectionSource.DEFAULT.equals(dsName) ? 'dataSource' : "dataSource_${dsName}"
        if (applicationContext != null && applicationContext.containsBean(beanName)) {
            configuration.setApplicationContext(applicationContext)
        }
        else {
            configuration.setDataSourceConnectionSource(dataSourceConnectionSource)
        }
    }

    private void configureResourceLocations(
            HibernateMappingContextConfiguration configuration,
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings) {
        applyResources(hibernateSettings.configLocations,
                { Resource r -> configuration.configure(r.URL) } as ResourceConfigurer)
        applyResources(hibernateSettings.mappingLocations, { Resource r ->
            InputStream is = r.inputStream
            try {
                configuration.addInputStream(is)
            }
            finally {
                is.close()
            }
        } as ResourceConfigurer)
        applyResources(hibernateSettings.cacheableMappingLocations,
                { Resource r -> configuration.addCacheableFile(r.getFile()) } as ResourceConfigurer)
        applyResources(hibernateSettings.mappingJarLocations,
                { Resource r -> configuration.addJar(r.getFile()) } as ResourceConfigurer)
        applyResources(hibernateSettings.mappingDirectoryLocations, { Resource r ->
            File file = r.getFile()
            if (!file.isDirectory()) {
                throw new IllegalArgumentException(
                        "Mapping directory location [${r}] does not denote a directory")
            }
            configuration.addDirectory(file)
        } as ResourceConfigurer)
    }

    void setDataSourceConnectionSourceFactory(
            DataSourceConnectionSourceFactory dataSourceConnectionSourceFactory) {
        this.dataSourceConnectionSourceFactory = dataSourceConnectionSourceFactory
    }

    @Override
    ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> create(
            String name, HibernateConnectionSourceSettings settings) {
        ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource =
                dataSourceConnectionSourceFactory.create(name, settings.dataSource)
        return create(name, dataSourceConnectionSource, settings)
    }

    @Override
    Serializable getConnectionSourcesConfigurationKey() {
        return Settings.SETTING_DATASOURCES
    }

    @Override
    def <F extends ConnectionSourceSettings> HibernateConnectionSourceSettings buildRuntimeSettings(
            String name, PropertyResolver configuration, F fallbackSettings) {
        return buildSettingsWithPrefix(configuration, fallbackSettings, '')
    }

    @Override
    protected <F extends ConnectionSourceSettings> HibernateConnectionSourceSettings buildSettings(
            String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        if (isDefaultDataSource) {
            String qualified = "${Settings.SETTING_DATASOURCES}.${Settings.SETTING_DATASOURCE}"
            HibernateConnectionSourceSettings settings =
                    new HibernateConnectionSourceSettingsBuilder(configuration, '', fallbackSettings).build()
            Map config = configuration.getProperty(qualified, Map, Collections.emptyMap())
            if (!config.isEmpty()) {
                DataSourceSettings dsFallback = extractDataSourceFallback(fallbackSettings)
                settings.setDataSource(new DataSourceSettingsBuilder(configuration, qualified, dsFallback).build())
            }
            return settings
        }
        return buildSettingsWithPrefix(configuration, fallbackSettings, "${Settings.SETTING_DATASOURCES}.${name}")
    }

    private <F extends ConnectionSourceSettings> HibernateConnectionSourceSettings buildSettingsWithPrefix(
            PropertyResolver configuration, F fallbackSettings, String prefix) {
        DataSourceSettings dsFallback = extractDataSourceFallback(fallbackSettings)
        HibernateConnectionSourceSettings settings =
                new HibernateConnectionSourceSettingsBuilder(configuration, prefix, fallbackSettings).build()
        if (prefix.isEmpty() ||
                configuration
                        .getProperty("${prefix}.dataSource", Map, Collections.emptyMap())
                        .isEmpty()) {
            settings.setDataSource(new DataSourceSettingsBuilder(configuration, prefix, dsFallback).build())
        }
        return settings
    }

    @Override
    void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext
        this.messageSource = applicationContext
    }

    @Override
    void setMessageSource(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @FunctionalInterface
    private interface ResourceConfigurer {

        void apply(Resource resource) throws IOException

    }

}

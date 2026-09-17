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
package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import org.hibernate.HibernateException
import org.hibernate.MappingException
import org.hibernate.SessionFactory
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.spi.AdditionalMappingContributor
import org.hibernate.bytecode.spi.BytecodeProvider
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.BytecodeSettings
import org.hibernate.cfg.Configuration
import org.hibernate.cfg.Environment
import org.hibernate.cfg.JdbcSettings
import org.hibernate.context.spi.CurrentSessionContext
import org.hibernate.internal.util.config.ConfigurationHelper
import org.hibernate.service.ServiceRegistry
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternUtils
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.MetadataReaderFactory
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.TypeFilter
import org.springframework.util.ClassUtils

import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.DevToolsClassLoaders
import org.grails.orm.hibernate.EventListenerIntegrator
import org.grails.orm.hibernate.GrailsSessionContext
import org.grails.orm.hibernate.HibernateEventListeners
import org.grails.orm.hibernate.MetadataIntegrator
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider
import org.grails.orm.hibernate.proxy.GrailsBytecodeProvider

import javax.sql.DataSource

/**
 * A Configuration that uses a MappingContext to configure Hibernate
 *
 * @since 5.0
 */
@SuppressWarnings(['rawtypes', 'PMD.UseProperClassLoader', 'PMD.DataflowAnomalyAnalysis', 'PMD.CloseResource'])
@CompileStatic
class HibernateMappingContextConfiguration extends Configuration
        implements ApplicationContextAware, Serializable {

    private static final long serialVersionUID = -7115087342689305517L

    private static final String RESOURCE_PATTERN = '/**/*.class'

    private static final TypeFilter[] ENTITY_TYPE_FILTERS = [
        new AnnotationTypeFilter(Entity, false),
        new AnnotationTypeFilter(Embeddable, false),
        new AnnotationTypeFilter(MappedSuperclass, false),
    ] as TypeFilter[]
    private static final String FALSE_LITERAL = 'false'
    private final Class<? extends CurrentSessionContext> currentSessionContext = GrailsSessionContext
    private final Set<Class> additionalClasses = new HashSet<>()
    protected String sessionFactoryBeanName = 'sessionFactory'
    protected String dataSourceName = ConnectionSource.DEFAULT
    protected transient HibernateMappingContext hibernateMappingContext
    private transient HibernateEventListeners hibernateEventListeners
    private Map<String, Object> eventListeners
    private transient ServiceRegistry serviceRegistry
    private transient ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver()
    private transient NamingStrategyProvider namingStrategyProvider = new NamingStrategyProvider()
    protected GrailsBytecodeProvider bytecodeProvider

    void setBytecodeProvider(GrailsBytecodeProvider bytecodeProvider) {
        this.bytecodeProvider = bytecodeProvider
    }

    NamingStrategyProvider getNamingStrategyProvider() {
        return namingStrategyProvider
    }

    void setNamingStrategyProvider(NamingStrategyProvider namingStrategyProvider) {
        this.namingStrategyProvider = namingStrategyProvider
    }

    MappingCacheHolder getMappingCacheHolder() {
        return hibernateMappingContext != null ? hibernateMappingContext.mappingCacheHolder : null
    }

    void setHibernateMappingContext(HibernateMappingContext hibernateMappingContext) {
        this.hibernateMappingContext = hibernateMappingContext
    }

    @Override
    void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(applicationContext)
        String dsName = ConnectionSource.DEFAULT.equals(dataSourceName) ? 'dataSource' : "dataSource_${dataSourceName}"
        Properties properties = getProperties()

        if (applicationContext != null) {
            if (!properties.containsKey(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE) && applicationContext.containsBean(dsName)) {
                properties.put(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE, applicationContext.getBean(dsName))
            }
            properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, currentSessionContext.name)
            properties.put(
                    'hibernate.enhancer.bytecodeprovider.instance',
                    grailsBytecodeProvider)
            properties.put('hibernate.bytecode.allow_enhancement_as_proxy', FALSE_LITERAL)
            properties.put('hibernate.bytecode.enhancement_metadata_cache', FALSE_LITERAL)
            properties.put('hibernate.enhancer.enableLazyInitialization', FALSE_LITERAL)
            properties.put('hibernate.enhancer.enableDirtyTracking', FALSE_LITERAL)
            properties.put('hibernate.enhancer.enableAssociationManagement', FALSE_LITERAL)
            ClassLoader applicationClassLoader = applicationContext.classLoader
            // Keep CLASSLOADERS absent when the context loader is null and DevTools restart is not
            // active so buildSessionFactory can fall back to this class's loader.
            if (applicationClassLoader != null ||
                    DevToolsClassLoaders.isRestartClassLoaderOrDescendant(Thread.currentThread().contextClassLoader)) {
                properties.put(AvailableSettings.CLASSLOADERS,
                        DevToolsClassLoaders.preferRestartClassLoader(applicationClassLoader))
            }
        }
    }

    protected GrailsBytecodeProvider getGrailsBytecodeProvider() {
        return bytecodeProvider != null ? bytecodeProvider : new GrailsBytecodeProvider()
    }

    /**
     * Set the target SQL {@link DataSource}
     *
     * @param connectionSource The data source to use
     */
    void setDataSourceConnectionSource(ConnectionSource<DataSource, DataSourceSettings> connectionSource) {
        this.dataSourceName = connectionSource.name
        DataSource source = connectionSource.source
        properties.put(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE, source)
        properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, GrailsSessionContext.name)
        setBytecodeProvider(grailsBytecodeProvider)
        properties.put(AvailableSettings.CLASSLOADERS,
                DevToolsClassLoaders.preferRestartClassLoader(connectionSource.class.classLoader))
    }

    /**
     * Add the given annotated classes in a batch.
     *
     * @return Configuration
     * @see #addAnnotatedClass
     * @see #scanPackages
     */
    @Override
    Configuration addAnnotatedClasses(Class... annotatedClasses) {
        for (Class<?> annotatedClass : annotatedClasses) {
            addAnnotatedClass(annotatedClass)
        }
        return this
    }

    @Override
    Configuration addAnnotatedClass(Class annotatedClass) {
        additionalClasses.add(annotatedClass)
        return super.addAnnotatedClass(annotatedClass)
    }

    @Override
    HibernateMappingContextConfiguration addPackages(String... annotatedPackages) {
        for (String annotatedPackage : annotatedPackages) {
            addPackage(annotatedPackage)
        }
        return this
    }

    /**
     * Perform Spring-based scanning for entity classes, registering them as annotated classes with
     * this {@code Configuration}.
     *
     * @param packagesToScan one or more Java package names
     * @throws HibernateException if scanning fails for any reason
     */
    void scanPackages(String... packagesToScan) throws HibernateException {
        try {
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resourcePatternResolver)
            for (String pkg : packagesToScan) {
                String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                        ClassUtils.convertClassNameToResourcePath(pkg) +
                        RESOURCE_PATTERN
                Resource[] resources = resourcePatternResolver.getResources(pattern)
                for (Resource resource : resources) {
                    if (resource.readable) {
                        MetadataReader reader = readerFactory.getMetadataReader(resource)
                        String className = reader.classMetadata.className
                        if (matchesFilter(reader, readerFactory)) {
                            ClassLoader classLoader = resourcePatternResolver.classLoader
                            Class<?> loadedClass = classLoader != null ?
                                    classLoader.loadClass(className) :
                                    ClassUtils.forName(className, null)
                            addAnnotatedClasses(loadedClass)
                        }
                    }
                }
            }
        }
        catch (IOException ex) {
            throw new MappingException('Failed to scan classpath for unlisted classes', ex)
        }
        catch (ClassNotFoundException ex) {
            throw new MappingException('Failed to load annotated classes from classpath', ex)
        }
    }

    /**
     * Check whether any of the configured entity type filters matches the current class descriptor
     * contained in the metadata reader.
     */
    protected boolean matchesFilter(MetadataReader reader, MetadataReaderFactory readerFactory) throws IOException {
        for (TypeFilter filter : ENTITY_TYPE_FILTERS) {
            if (filter.match(reader, readerFactory)) {
                return true
            }
        }
        return false
    }

    void setSessionFactoryBeanName(String name) {
        sessionFactoryBeanName = name
    }

    void setDataSourceName(String name) {
        dataSourceName = name
    }

    /* (non-Javadoc)
     * @see org.hibernate.cfg.Configuration#buildSessionFactory()
     */
    @Override
    SessionFactory buildSessionFactory() throws HibernateException {
        // 1. FORCE the custom bytecode provider instance right before bootstrap
        // This bypasses the ServiceLoader and ensures your GrailsBytecodeProvider is used.
        GrailsBytecodeProvider bytecodeProvider = grailsBytecodeProvider
        properties.put(
                BytecodeSettings.BYTECODE_PROVIDER_INSTANCE,
                bytecodeProvider)

        // set the class loader to load Groovy classes

        // work around for HHH-2624
        SessionFactory sessionFactory

        ClassLoader appClassLoader = resolveSessionFactoryClassLoader()

        ConfigurationHelper.resolvePlaceHolders(properties)

        final GrailsDomainBinder domainBinder = new GrailsDomainBinder(
                dataSourceName,
                sessionFactoryBeanName,
                hibernateMappingContext,
                namingStrategyProvider,
                hibernateMappingContext.mappingCacheHolder)

        List<Class> annotatedClasses = new ArrayList<>()
        for (PersistentEntity persistentEntity : hibernateMappingContext.persistentEntities) {
            Class<?> javaClass = persistentEntity.javaClass
            if (javaClass.isAnnotationPresent(Entity)) {
                annotatedClasses.add(javaClass)
            }
        }

        if (!additionalClasses.isEmpty()) {
            for (Class additionalClass : additionalClasses) {
                if (GormEntity.isAssignableFrom(additionalClass)) {
                    hibernateMappingContext.addPersistentEntity(additionalClass)
                }
            }
        }

        addAnnotatedClasses(annotatedClasses.toArray(new Class[0]))

        ClassLoaderService classLoaderService = new ClassLoaderServiceImpl(appClassLoader) {
            @Override
            <S> Collection<S> loadJavaServices(Class<S> serviceContract) {
                // Ensure Grails contributes mappings for GORM entities even if they lack JPA @Entity
                if (AdditionalMappingContributor.isAssignableFrom(serviceContract)) {
                    // Include the GrailsDomainBinder first, then any other contributors
                    // discovered by the parent classloader (e.g., Envers AdditionalMappingContributorImpl).
                    // Without this, Envers' AdditionalMappingContributor would be excluded,
                    // preventing EnversService from being initialized before EnversIntegrator runs.
                    Collection<S> parentContributors = super.loadJavaServices(serviceContract)
                    S grailsBinder = (S) domainBinder
                    List<S> allContributors = new ArrayList<>(parentContributors.size() + 1)
                    allContributors.add(grailsBinder)
                    allContributors.addAll(parentContributors)
                    return allContributors
                }
                return super.loadJavaServices(serviceContract)
            }
        }
        EventListenerIntegrator eventListenerIntegrator =
                new EventListenerIntegrator(hibernateEventListeners, eventListeners)
        BootstrapServiceRegistry bootstrapServiceRegistry = createBootstrapServiceRegistryBuilder()
                .applyIntegrator(eventListenerIntegrator)
                .applyIntegrator(new MetadataIntegrator())
                .applyClassLoaderService(classLoaderService)
                .build()

        StandardServiceRegistryBuilder standardServiceRegistryBuilder =
                createStandardServiceRegistryBuilder(bootstrapServiceRegistry).applySettings((Map) properties)

        Object dataSource = properties.get(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE)
        if (dataSource instanceof DataSource) {
            standardServiceRegistryBuilder.applySetting(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
        }

        standardServiceRegistryBuilder.addService(BytecodeProvider, bytecodeProvider)

        StandardServiceRegistry ssr = standardServiceRegistryBuilder.build()
        ClassLoader previous = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = appClassLoader
            sessionFactory = super.buildSessionFactory(ssr)
        }
        catch (Exception e) {
            throw new RuntimeException(e)
        }
        finally {
            Thread.currentThread().contextClassLoader = previous
        }
        this.serviceRegistry = ssr

        return sessionFactory
    }

    ClassLoader resolveSessionFactoryClassLoader() {
        Object classLoaderObject = properties.get(AvailableSettings.CLASSLOADERS)
        ClassLoader storedClassLoader = classLoaderObject instanceof ClassLoader ?
                (ClassLoader) classLoaderObject : getClass().classLoader
        // addProperties() or a custom configClass may have replaced CLASSLOADERS after the
        // setters ran. GrailsDomainBinder binds entities by class name and Hibernate resolves
        // them through this loader, so it has to see the restarted application classes.
        return DevToolsClassLoaders.preferRestartClassLoader(storedClassLoader)
    }

    /**
     * Creates the {@link BootstrapServiceRegistryBuilder} to use
     *
     * @return The {@link BootstrapServiceRegistryBuilder}
     */
    protected BootstrapServiceRegistryBuilder createBootstrapServiceRegistryBuilder() {
        return new BootstrapServiceRegistryBuilder()
    }

    /**
     * Creates the standard service registry builder. Subclasses can override to customize the
     * creation of the StandardServiceRegistry
     *
     * @param bootstrapServiceRegistry The {@link BootstrapServiceRegistry}
     * @return The {@link StandardServiceRegistryBuilder}
     */
    protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder(
            BootstrapServiceRegistry bootstrapServiceRegistry) {
        return new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
    }

    /**
     * Default listeners.
     *
     * @param listeners the listeners
     */
    void setEventListeners(Map<String, Object> listeners) {
        eventListeners = listeners
    }

    /**
     * User-specifiable extra listeners.
     *
     * @param listeners the listeners
     */
    void setHibernateEventListeners(HibernateEventListeners listeners) {
        hibernateEventListeners = listeners
    }

    ServiceRegistry getServiceRegistry() {
        return serviceRegistry
    }

}

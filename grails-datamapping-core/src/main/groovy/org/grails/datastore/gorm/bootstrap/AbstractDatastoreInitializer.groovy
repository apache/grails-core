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

package org.grails.datastore.gorm.bootstrap

import java.beans.Introspector

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import org.springframework.util.ClassUtils

import grails.gorm.annotation.Entity
import org.apache.grails.common.aot.AheadOfTimeProcessing
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.gorm.plugin.support.PersistenceContextInterceptorAggregator
import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.mapping.config.DatastoreServiceMethodInvokingFactoryBean
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.BasicTypeConverterRegistrar
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.services.Service
import org.grails.datastore.mapping.services.ServiceDefinition
import org.grails.datastore.mapping.services.SoftServiceLoader
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager

/**
 * Abstract class for datastore initializers to implement
 *
 * @author Graeme Rocher
 * @since 3.0.3
 */
@CompileStatic
abstract class AbstractDatastoreInitializer implements ResourceLoaderAware {

    public static final String TRANSACTION_MANAGER_BEAN = 'transactionManager'
    public static final String ENTITY_CLASS_RESOURCE_PATTERN = '/**/*.class'
    public static final String OSIV_CLASS_NAME = 'org.grails.datastore.mapping.web.support.OpenSessionInViewInterceptor'
    public static final String WEB_APPLICATION_CONTEXT_CLASS_NAME = 'org.springframework.web.context.WebApplicationContext'

    PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver()
    Collection<Class> persistentClasses = []
    Collection<String> packages = []
    PropertyResolver configuration = new StandardEnvironment()
    boolean registerApplicationIfNotPresent = true
    Object originalConfiguration

    protected ClassLoader classLoader = Thread.currentThread().contextClassLoader
    protected boolean secondaryDatastore = false

    AbstractDatastoreInitializer() {
    }

    AbstractDatastoreInitializer(ClassLoader classLoader, String... packages) {
        this(packages)
        this.classLoader = classLoader ?: Thread.currentThread().contextClassLoader
    }
    AbstractDatastoreInitializer(String... packages) {
        this.packages = packages.toList()
    }

    AbstractDatastoreInitializer(Collection<Class> persistentClasses) {
        this.persistentClasses = persistentClasses
    }

    AbstractDatastoreInitializer(Class... persistentClasses) {
        this(persistentClasses.toList())
    }

    AbstractDatastoreInitializer(PropertyResolver configuration, Collection<Class> persistentClasses) {
        this.configuration = configuration
        this.persistentClasses = persistentClasses
    }

    AbstractDatastoreInitializer(PropertyResolver configuration, Class...persistentClasses) {
        this(configuration, Arrays.asList(persistentClasses))
    }

    AbstractDatastoreInitializer(PropertyResolver configuration, String...packages) {
        this.configuration = configuration
        this.packages = Arrays.asList(packages)
    }

    AbstractDatastoreInitializer(Map configuration, Collection<Class> persistentClasses) {
        this(DatastoreUtils.createPropertyResolver(configuration), persistentClasses)
        this.originalConfiguration = configuration
    }

    AbstractDatastoreInitializer(Map configuration, Class... persistentClasses) {
        this(configuration, persistentClasses.toList())
    }

    /**
     * What a bean definition should hold in place of the configuration itself.
     *
     * <p>A definition holding the resolver is a definition holding everything the resolver can
     * reach, and generating code for such a definition writes those values out: an environment
     * carries the machine's own variables, so the generated source ends up containing the
     * environment of whatever machine built it, along with whatever was in it -- credentials among
     * them -- and the application then reads its settings from there rather than from where it
     * runs.</p>
     *
     * <p>So while code is being generated, and only then, the context's environment is named
     * instead, which leaves the lookup to be made where the application runs. Every other time the
     * resolver is held as it always was, which matters for a datastore brought up on its own: its
     * configuration is whatever the caller passed and there is no environment holding it.</p>
     */
    protected Object configurationReference(BeanDefinitionRegistry registry) {
        if (!AheadOfTimeProcessing.generatingCode) {
            return configuration
        }
        boolean insideContainer = registry instanceof ConfigurableApplicationContext ||
                resourcePatternResolver.resourceLoader instanceof ConfigurableApplicationContext
        insideContainer
                ? new RuntimeBeanReference(ConfigurableApplicationContext.ENVIRONMENT_BEAN_NAME)
                : configuration
    }

    /**
     * Finds the event publisher to use
     *
     * @param beanDefinitionRegistry The event publisher
     * @return The event publisher
     */
    ApplicationEventPublisher findEventPublisher(BeanDefinitionRegistry beanDefinitionRegistry) {
        ApplicationEventPublisher eventPublisher
        if (beanDefinitionRegistry instanceof ConfigurableApplicationContext) {
            eventPublisher = new ConfigurableApplicationContextEventPublisher((ConfigurableApplicationContext) beanDefinitionRegistry)
        }
        else if (resourcePatternResolver.resourceLoader instanceof ConfigurableApplicationContext) {
            eventPublisher = new ConfigurableApplicationContextEventPublisher((ConfigurableApplicationContext) resourcePatternResolver.resourceLoader)
        }
        else {
            eventPublisher = new DefaultApplicationEventPublisher()
        }
        return eventPublisher
    }

    /**
     * Finds the publisher class a datastore definition should name, in place of a publisher itself.
     *
     * <p>The same choice {@link #findEventPublisher} makes -- one that publishes through the
     * surrounding context, or the one that publishes nowhere -- but left to the container to build.
     * A definition holding an already-constructed publisher is a definition that cannot be generated
     * ahead of time: generating one means writing out what it holds, and a publisher bound to a
     * running context is not something there is any way to write down.</p>
     *
     * @param beanDefinitionRegistry the registry the definitions are being contributed to
     * @return the class to register the publisher bean under
     */
    protected Class<? extends ApplicationEventPublisher> findEventPublisherClass(BeanDefinitionRegistry beanDefinitionRegistry) {
        if (beanDefinitionRegistry instanceof ConfigurableApplicationContext ||
                resourcePatternResolver.resourceLoader instanceof ConfigurableApplicationContext) {
            return ConfigurableApplicationContextEventPublisher
        }
        return DefaultApplicationEventPublisher
    }

    /**
     * Finds the message source to use
     * @param beanDefinitionRegistry The registry
     * @return The message source
     */
    MessageSource findMessageSource(BeanDefinitionRegistry beanDefinitionRegistry) {
        MessageSource messageSource
        if (beanDefinitionRegistry instanceof MessageSource) {
            messageSource = (MessageSource) beanDefinitionRegistry
        }
        else if (resourcePatternResolver.resourceLoader instanceof MessageSource) {
            messageSource = (MessageSource) resourcePatternResolver.resourceLoader
        }
        else {
            messageSource = new StaticMessageSource()
        }
        return messageSource
    }

    /**
     * Configures for an existing Mongo instance
     * @param mongo The instance of Mongo
     * @return The configured ApplicationContext
     */
    @CompileStatic
    ApplicationContext configure() {
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        return applicationContext
    }
    /**
     * Whether this datastore is secondary to another primary datastore (example the SQL DB)
     *
     * @param secondaryDatastore
     */
    void setSecondaryDatastore(boolean secondaryDatastore) {
        this.secondaryDatastore = secondaryDatastore
    }

    @Override
    void setResourceLoader(ResourceLoader resourceLoader) {
        resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader)
    }

    @CompileStatic
    protected void scanForPersistentClasses() {
        // scan defined packages
        def readerFactory = new CachingMetadataReaderFactory(resourcePatternResolver)
        for (pkg in packages) {
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(pkg) + ENTITY_CLASS_RESOURCE_PATTERN

            scanUsingPattern(pattern, readerFactory)
        }

        def entityNames = AstUtils.getKnownEntityNames()
        if (entityNames) {
            // only works at development time
            for (entityName in entityNames) {
                try {
                    persistentClasses << classLoader.loadClass(entityName)
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        else {
            // try the default package in case of a script without recursing into subpackages
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + '*.class'
            scanUsingPattern(pattern, readerFactory)
        }
    }

    @CompileStatic
    private void scanUsingPattern(String pattern, CachingMetadataReaderFactory readerFactory) {
        def resources = this.resourcePatternResolver.getResources(pattern)
        for (Resource res in resources) {
            def reader = readerFactory.getMetadataReader(res)
            def annotationMetadata = reader.annotationMetadata
            if (annotationMetadata.hasAnnotation('grails.persistence.Entity') || annotationMetadata.hasAnnotation(Entity.name) || annotationMetadata.hasAnnotation(jakarta.persistence.Entity.name)) {
                persistentClasses << classLoader.loadClass(reader.classMetadata.className)
            }
        }
    }

    /**
     * Configures an existing BeanDefinitionRegistry
     *
     * @param beanDefinitionRegistry The BeanDefinitionRegistry to configure
     */
    @CompileStatic
    void configureForBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) {

        if (configuration instanceof ConfigurableEnvironment && beanDefinitionRegistry instanceof ConfigurableApplicationContext) {
            def env = (ConfigurableEnvironment) configuration

            def conversionService = ((ConfigurableApplicationContext) beanDefinitionRegistry).getEnvironment().getConversionService()

            BasicTypeConverterRegistrar registrar = new BasicTypeConverterRegistrar()
            registrar.register(conversionService)
            env.setConversionService(conversionService)
        }

        scanForPersistentClasses()

        if (GroovyBeanReaderInit.isAvailable()) {
            GroovyBeanReaderInit.registerBeans(beanDefinitionRegistry, getBeanDefinitions(beanDefinitionRegistry))
        }
        else if (GrailsBeanBuilderInit.isAvailable()) {
            GrailsBeanBuilderInit.registerBeans(beanDefinitionRegistry, getBeanDefinitions(beanDefinitionRegistry))
        }
        else {
            throw new IllegalStateException('Neither Spring 4.0+ nor grails-spring dependency found on classpath to enable GORM configuration. If you are using an earlier version of Spring please add the grails-spring dependency to your classpath.')
        }
    }

    @CompileDynamic
    Closure getCommonConfiguration(BeanDefinitionRegistry registry, String type) {
        return {}
    }

    /**
     * The classes this datastore maps.
     *
     * @param datastoreType the datastore the classes are being collected for
     * @return the classes, which a datastore's definition takes through {@link #mappedClasses}
     */
    protected Collection<Class> collectMappedClasses(String datastoreType) {
        !secondaryDatastore ? persistentClasses : persistentClasses.findAll() { Class cls ->
            isMappedClass(datastoreType, cls)
        }
    }

    /**
     * The classes this datastore maps, as the array its constructor takes.
     *
     * <p>The array type is what makes this survive ahead-of-time processing. A datastore takes its
     * classes as a variable-argument parameter, so the constructor argument the generator writes out
     * is looked up by that parameter's type when the bean is rebuilt. A collection does not answer to
     * {@code Class[]}, so the lookup misses it and the argument is resolved as a dependency instead,
     * which for an array type means every {@code Class} bean in the context: none. The datastore is
     * then built mapping nothing, and the first call on a domain class reports that it is not one.</p>
     *
     * <p>Kept apart from {@link #collectMappedClasses} rather than folded into it, because that one
     * is the seam a datastore outside this repository overrides to say which classes are its own.
     * Changing what it returns would leave such an override no longer overriding anything, and
     * silently unasked.</p>
     *
     * @param datastoreType the datastore the classes are being collected for
     * @return the classes, as the array a datastore's constructor takes
     */
    protected Class[] mappedClasses(String datastoreType) {
        collectMappedClasses(datastoreType) as Class[]
    }

    protected boolean isMappedClass(String datastoreType, Class cls) {
        datastoreType.equals(ClassPropertyFetcher.getStaticPropertyValue(cls, GormProperties.MAPPING_STRATEGY, String))
    }

    abstract Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry)

    /**
     * Internal method aiding in datastore configuration.
     *
     * @param registry The BeanDefinitionRegistry
     * @param type The type of the datastore
     *
     * @return A closure containing bean definitions
     */
    @CompileDynamic
    Closure getAdditionalBeansConfiguration(BeanDefinitionRegistry registry, String type) {
        { ->
            "${type}TransactionManager"(DatastoreTransactionManager) {
                datastore = ref("${type}Datastore")
            }

            def transactionManagerBeanName = TRANSACTION_MANAGER_BEAN
            if (!containsRegisteredBean(delegate, registry, transactionManagerBeanName)) {
                registry.registerAlias("${type}TransactionManager", transactionManagerBeanName)
            }

            "${type}PersistenceInterceptor"(getPersistenceInterceptorClass(), ref("${type}Datastore"))

            "${type}PersistenceContextInterceptorAggregator"(PersistenceContextInterceptorAggregator)

            def classLoader = Thread.currentThread().contextClassLoader
            if (isWebApplicationRegistry(registry) && ClassUtils.isPresent(OSIV_CLASS_NAME, classLoader)) {
                String interceptorName = "${type}OpenSessionInViewInterceptor"
                "${interceptorName}"(ClassUtils.forName(OSIV_CLASS_NAME, classLoader)) {
                    datastore = ref("${type}Datastore")
                }
            }
            loadDataServices(null)
                    .each { serviceName, serviceClass ->
                        "$serviceName"(DatastoreServiceMethodInvokingFactoryBean, serviceClass) {
                            targetObject = ref("${type}Datastore")
                            targetMethod = 'getService'
                            arguments = [serviceClass]
                        }
                    }
        }
    }

    @CompileDynamic
    protected Map<String, Class<?>> loadDataServices(String secondaryDatastore = null) {
        Map<String, Class<?>> services = [:]
        final SoftServiceLoader<Service> softServiceLoader = SoftServiceLoader.load(Service)
        for (ServiceDefinition<Service> serviceDefinition: softServiceLoader) {
            if (serviceDefinition.isPresent()) {
                final Class<Service> clazz = serviceDefinition.getType()
                final Class<?> serviceClass = loadServiceClass(clazz)
                final grails.gorm.services.Service ann = clazz.getAnnotation(grails.gorm.services.Service)
                String serviceName = ann?.name()
                if (!serviceName) {
                    serviceName = Introspector.decapitalize(serviceClass.simpleName)
                }
                if (secondaryDatastore) {
                    serviceName = secondaryDatastore + NameUtils.capitalize(serviceName)
                }
                if (serviceClass != null && serviceClass != Object) {
                    services.put(serviceName, serviceClass)
                }
            }
        }
        return services
    }

    private Class<?> loadServiceClass(Class<Service> clazz) {
        if (clazz.simpleName.startsWith('$') && clazz.simpleName.endsWith('Implementation')) {
            final String serviceClassName = clazz.package.getName() + '.' + clazz.simpleName[1..-15]
            final Class<?> serviceClass = classLoader.loadClass(serviceClassName)
            serviceClass
        } else {
            clazz
        }
    }

    @CompileDynamic
    protected boolean containsRegisteredBean(Object builder, BeanDefinitionRegistry registry, String beanName) {
        registry.containsBeanDefinition(beanName) || (builder.hasProperty('springConfig') && builder.springConfig.containsBean(beanName))
    }

    /**
     * Determines whether the given registry belongs to a web application. The presence of the
     * {@code dispatcherServlet} bean definition is only a reliable signal after Spring Boot
     * auto-configuration has been processed; Grails plugin bean definitions register before
     * that, so the type of the registry (the web application context itself) is checked as well.
     *
     * @param registry The bean definition registry
     * @return true if the registry belongs to a web application
     */
    protected boolean isWebApplicationRegistry(BeanDefinitionRegistry registry) {
        if (registry == null) {
            return false
        }
        if (registry.containsBeanDefinition('dispatcherServlet')) {
            return true
        }
        ClassLoader classLoader = getClass().classLoader
        return ClassUtils.isPresent(WEB_APPLICATION_CONTEXT_CLASS_NAME, classLoader) &&
                ClassUtils.forName(WEB_APPLICATION_CONTEXT_CLASS_NAME, classLoader).isInstance(registry)
    }

    /**
     * @return The class used to define the persistence interceptor
     */
    protected abstract Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass()

    @CompileStatic
    protected Class getGrailsApplicationClass() {
        ClassLoader cl = getClass().getClassLoader()
        if (ClassUtils.isPresent('grails.core.DefaultGrailsApplication', cl)) {
            return ClassUtils.forName('grails.core.DefaultGrailsApplication', cl)
        }
        throw new IllegalStateException('No version of Grails found on classpath')

    }

    protected boolean isGrailsPresent() {
        ClassLoader cl = getClass().getClassLoader()
        if (ClassUtils.isPresent('grails.core.DefaultGrailsApplication', cl)) {
            return true
        }
        return false
    }

    @CompileStatic
    protected Class getGrailsValidatorClass() {
        throw new UnsupportedOperationException('Method getGrailsValidatorClass no longer supported')
    }

    @CompileDynamic
    static class GroovyBeanReaderInit {
        static boolean isAvailable() {
            try {
                Thread.currentThread().contextClassLoader.loadClass('org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader')
                return true
            } catch (e) {
                return false
            }
        }
        static void registerBeans(BeanDefinitionRegistry registry, Closure beanDefinitions) {
            def classLoader = Thread.currentThread().contextClassLoader
            def beanReader = classLoader.loadClass('org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader').newInstance(registry)
            beanReader.beans(beanDefinitions)
        }
    }

    @CompileDynamic
    static class GrailsBeanBuilderInit {
        static boolean isAvailable() {
            try {
                Thread.currentThread().contextClassLoader.loadClass('grails.spring.BeanBuilder')
                return true
            } catch (e) {
                return false
            }
        }

        static void registerBeans(BeanDefinitionRegistry registry, Closure beanDefinitions) {
            def classLoader = Thread.currentThread().contextClassLoader
            def beanBuilder = classLoader.loadClass('grails.spring.BeanBuilder').newInstance()
            beanBuilder.beans(beanDefinitions)
            beanBuilder.registerBeans(registry)
        }
    }
}

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

package grails.boot.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.aot.AotDetector
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ApplicationContextEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.env.AbstractEnvironment
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.Resource

import grails.boot.GrailsApp
import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.GrailsApplicationClass
import grails.core.GrailsApplicationLifeCycle
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.spring.BeanBuilder
import grails.util.Environment
import grails.util.Holders
import org.grails.config.NavigableMap
import org.grails.config.PropertySourcesConfig
import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.model.MappingContext
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfigUtilities
import org.apache.grails.core.plugins.PluginDiscovery

/**
 * A {@link BeanDefinitionRegistryPostProcessor} that enhances any ApplicationContext with plugin manager capabilities
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Slf4j
class GrailsApplicationPostProcessor implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, ApplicationListener<ApplicationContextEvent> {

    static final boolean RELOADING_ENABLED = Environment.isReloadingAgentEnabled()

    final GrailsApplication grailsApplication
    final GrailsApplicationLifeCycle lifeCycle
    final GrailsApplicationClass applicationClass
    final Class[] classes
    protected final GrailsPluginManager pluginManager
    protected final boolean earlyPluginRegistrationRan
    protected ApplicationContext applicationContext
    boolean loadExternalBeans = true
    boolean reloadingEnabled = RELOADING_ENABLED

    GrailsApplicationPostProcessor(GrailsApplicationLifeCycle lifeCycle, ApplicationContext applicationContext, PluginDiscovery pluginDiscovery, Class... classes) {
        this.lifeCycle = lifeCycle
        if (lifeCycle instanceof GrailsApplicationClass) {
            this.applicationClass = (GrailsApplicationClass) lifeCycle
        }
        else {
            this.applicationClass = null
        }
        this.classes = classes != null ? classes : [] as Class[]
        this.earlyPluginRegistrationRan = hasEarlyPluginRegistrationRun(applicationContext)
        if (earlyPluginRegistrationRan) {
            grailsApplication = applicationContext.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)
            if (applicationClass != null && grailsApplication instanceof DefaultGrailsApplication) {
                ((DefaultGrailsApplication) grailsApplication).setApplicationClass(applicationClass)
            }
        }
        else {
            grailsApplication = applicationClass != null ? new DefaultGrailsApplication(applicationClass) : new DefaultGrailsApplication()
        }
        pluginManager = applicationContext?.getBeanNamesForType(GrailsPluginManager) ? applicationContext.getBean(GrailsPluginManager) : new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)
        if (applicationContext != null) {
            setApplicationContext(applicationContext)
        }
    }

    /**
     * Determines whether {@link GrailsEarlyPluginRegistrationPostProcessor} already built the
     * {@code grailsApplication} and {@code pluginManager} singletons and drained the plugin
     * runtime configuration for this context. Checked on the local bean factory only, so a
     * parent context's early phase never short-circuits a child context's lifecycle.
     */
    private static boolean hasEarlyPluginRegistrationRun(ApplicationContext applicationContext) {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            return ((ConfigurableApplicationContext) applicationContext).beanFactory
                    .containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        }
        return false
    }

    /**
     * Resolves the {@link PluginDiscovery} from the application context.
     * The bootstrap registry promotes the discovery bean before context refresh,
     * so it is always available by bean name at this stage.
     *
     * @throws IllegalStateException if the application context is null or does not contain a GrailsPluginDiscovery bean
     */
    private static PluginDiscovery resolvePluginDiscovery(ApplicationContext applicationContext) {
        if (applicationContext == null || !applicationContext.containsBean(PluginDiscovery.BEAN_NAME)) {
            throw new IllegalStateException(
                    'GrailsPluginDiscovery bean not found in ApplicationContext. ' +
                            'Use GrailsApplicationPostProcessor(GrailsApplicationLifeCycle, ApplicationContext, GrailsPluginDiscovery, Class[]) instead.'
            )
        }
        return (PluginDiscovery) applicationContext.getBean(PluginDiscovery.BEAN_NAME)
    }

    protected final void initializeGrailsApplication(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            throw new IllegalStateException('ApplicationContext should not be null')
        }
        Environment.setInitializing(true)
        grailsApplication.applicationContext = applicationContext
        grailsApplication.mainContext = applicationContext
        if (!earlyPluginRegistrationRan) {
            pluginManager.loadPlugins()
            pluginManager.applicationContext = applicationContext
        }
        loadApplicationConfig()
        customizeGrailsApplication(grailsApplication)
        if (earlyPluginRegistrationRan) {
            registerRemainingApplicationClasses()
        }
        else {
            performGrailsInitializationSequence()
        }
    }

    /**
     * When the early plugin registration phase already performed artefact discovery, only the
     * application classes it could not resolve (e.g. a customized {@code classes()} implementation)
     * still need to be registered.
     */
    private void registerRemainingApplicationClasses() {
        Set<String> registeredClassNames = grailsApplication.allArtefacts*.name as Set<String>
        for (cls in classes) {
            if (!registeredClassNames.contains(cls.name)) {
                grailsApplication.addArtefact(cls)
            }
        }
    }

    protected void customizeGrailsApplication(GrailsApplication grailsApplication) {

    }

    protected void performGrailsInitializationSequence() {
        pluginManager.doArtefactConfiguration()
        grailsApplication.initialise()
        // register plugin provided classes first, this gives the oppurtunity
        // for application classes to override those provided by a plugin
        pluginManager.registerProvidedArtefacts(grailsApplication)
        for (cls in classes) {
            grailsApplication.addArtefact(cls)
        }
    }

    /**
     * Registers conversion service converters and creates the {@link PropertySourcesConfig}
     * that backs {@code grailsApplication.config}.
     *
     * <p>Plugin configurations are loaded early by
     * {@link grails.boot.config.GrailsEnvironmentPostProcessor} and are already
     * present in the environment's property sources by the time this method runs.
     * This method simply creates the {@link PropertySourcesConfig} from whatever
     * property sources exist in the environment.</p>
     */
    protected void loadApplicationConfig() {
        org.springframework.core.env.Environment environment = applicationContext.getEnvironment()
        ConfigurableConversionService conversionService = null
        if (environment instanceof ConfigurableEnvironment) {
            if (environment instanceof AbstractEnvironment) {
                conversionService = environment.getConversionService()
                conversionService.addConverter(new Converter<String, Resource>() {
                    @Override
                    Resource convert(String source) {
                        return applicationContext.getResource(source)
                    }
                })
                conversionService.addConverter(new Converter<NavigableMap.NullSafeNavigator, String>() {
                    @Override
                    String convert(NavigableMap.NullSafeNavigator source) {
                        return null
                    }
                })
                conversionService.addConverter(new Converter<NavigableMap.NullSafeNavigator, Object>() {
                    @Override
                    Object convert(NavigableMap.NullSafeNavigator source) {
                        return null
                    }
                })
            }
            def propertySources = environment.getPropertySources()
            def config = new PropertySourcesConfig(propertySources)
            if (conversionService != null) {
                config.setConversionService(conversionService)
            }
            ((DefaultGrailsApplication) grailsApplication).config = config
        }
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registerAnnotationConfigProcessorsForGeneratedArtifacts(registry)

        def springConfig = new DefaultRuntimeSpringConfiguration()
        def application = grailsApplication
        Holders.setGrailsApplication(application)

        if (!earlyPluginRegistrationRan) {
            // first register plugin beans; when the early phase ran they were
            // already drained into the registry ahead of auto-configuration
            pluginManager.doRuntimeConfiguration(springConfig)
        }

        // Running on generated artifacts these beans are already registered: the application's own
        // definitions were read while the artifacts were being generated and what they declared was
        // written out as code. Reading them again would register them a second time, and reading
        // the Groovy one means compiling a script -- which an image cannot do at all, so an
        // application that has a spring/resources.groovy did not start.
        if (loadExternalBeans && !AotDetector.useGeneratedArtifacts()) {
            // now allow overriding via application

            def context = application.mainContext
            def beanResources = context.getResource(RuntimeSpringConfigUtilities.SPRING_RESOURCES_GROOVY)
            if (beanResources?.exists()) {
                def gcl = new GroovyClassLoader(application.classLoader)
                try {
                    RuntimeSpringConfigUtilities.reloadSpringResourcesConfig(springConfig, application, gcl.parseClass(new GroovyCodeSource(beanResources.URL)))
                } catch (Throwable e) {
                    log.error("Error loading spring/resources.groovy file: ${e.message}", e)
                    throw new GrailsConfigurationException("Error loading spring/resources.groovy file: ${e.message}", e)
                }
            }

            beanResources = context.getResource(RuntimeSpringConfigUtilities.SPRING_RESOURCES_XML)
            if (beanResources?.exists()) {
                try {
                    new BeanBuilder(null, springConfig, application.classLoader)
                            .importBeans(beanResources)
                } catch (Throwable e) {
                    log.error("Error loading spring/resources.xml file: ${e.message}", e)
                    throw new GrailsConfigurationException("Error loading spring/resources.xml file: ${e.message}", e)
                }
            }
        }

        if (lifeCycle) {
            def withSpring = lifeCycle.doWithSpring()
            if (withSpring) {
                def bb = new BeanBuilder(null, springConfig, application.classLoader)
                bb.beans(withSpring)
            }
        }

        springConfig.registerBeansWithRegistry(registry)

        if (!earlyPluginRegistrationRan) {
            // the early phase applies plugin registrars itself; on the fallback path (contexts not
            // booted through GrailsApp) apply them here so a plugin's beanRegistrar() behaves the same
            applyPluginBeanRegistrars(registry)
        }

        if (lifeCycle) {
            // the application's BeanRegistrar drains at the same point as its doWithSpring closure,
            // after the DSL flush so registrar beans win any name conflicts with the deprecated DSL
            BeanRegistrar registrar = lifeCycle.beanRegistrar()
            if (registrar != null) {
                new BeanRegistryAdapter(registry, applicationContext, applicationContext.environment, registrar.getClass())
                        .register(registrar)
            }
        }
    }

    /**
     * Applies each enabled plugin's {@link BeanRegistrar} on the fallback path where the early
     * plugin registration phase did not run, mirroring that phase so a plugin's {@code beanRegistrar()}
     * is honoured in every context rather than only those booted through {@code GrailsApp}. Runs after
     * the DSL flush so registrar beans win name conflicts with the deprecated {@code doWithSpring} DSL.
     */
    private void applyPluginBeanRegistrars(BeanDefinitionRegistry registry) {
        String[] activeProfiles = applicationContext.environment.activeProfiles
        for (GrailsPlugin plugin in pluginManager.allPlugins) {
            if (!plugin.supportsCurrentScopeAndEnvironment() || !plugin.isEnabled(activeProfiles)) {
                continue
            }
            BeanRegistrar registrar = plugin.beanRegistrar
            if (registrar != null) {
                new BeanRegistryAdapter(registry, applicationContext, applicationContext.environment, registrar.getClass())
                        .register(registrar)
            }
        }
    }

    /**
     * Restores the processors that read the injection annotations, when running on artifacts
     * generated ahead of time.
     *
     * <p>Generating those artifacts normally makes these unnecessary: the generator reads the
     * annotations itself and writes the field and method access into the code it emits, which is why
     * a controller or a tag library arrives fully injected without them. It can only do that for a
     * bean whose implementation it can see, and a bean contributed as an interface built by a
     * supplier hides it -- the link generator is declared as {@code LinkGenerator} and built by a
     * closure, so the {@code @Autowired} field on the implementation is generated for by nobody.
     * Nothing fails at start-up; the first page that follows a link does.</p>
     *
     * <p>Only the two that inject are restored. Registering the whole set would bring back the
     * processor that reads configuration classes, and reading them again in a context whose
     * configuration has already been generated makes a second definition for beans the generated
     * code has already contributed.</p>
     *
     * <p>They are registered under the names Spring uses itself, so a context that already has them
     * keeps what it has, and a context running without generated artifacts is untouched.</p>
     */
    protected static void registerAnnotationConfigProcessorsForGeneratedArtifacts(BeanDefinitionRegistry registry) {
        if (!AotDetector.useGeneratedArtifacts()) {
            return
        }
        registerInfrastructureBean(registry, AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME,
                AutowiredAnnotationBeanPostProcessor)
        registerInfrastructureBean(registry, AnnotationConfigUtils.COMMON_ANNOTATION_PROCESSOR_BEAN_NAME,
                CommonAnnotationBeanPostProcessor)
    }

    private static void registerInfrastructureBean(BeanDefinitionRegistry registry, String beanName,
            Class<?> beanClass) {
        if (registry.containsBeanDefinition(beanName)) {
            return
        }
        RootBeanDefinition definition = new RootBeanDefinition(beanClass)
        definition.role = BeanDefinition.ROLE_INFRASTRUCTURE
        registry.registerBeanDefinition(beanName, definition)
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanFactory parentBeanFactory = beanFactory.getParentBeanFactory()
        if (parentBeanFactory instanceof ConfigurableBeanFactory) {
            ConfigurableBeanFactory configurableBeanFactory = parentBeanFactory
            registerSingletonIfAbsent(configurableBeanFactory, GrailsApplication.APPLICATION_ID, grailsApplication)
            registerSingletonIfAbsent(configurableBeanFactory, GrailsPluginManager.BEAN_NAME, pluginManager)
        } else {
            registerSingletonIfAbsent(beanFactory, GrailsApplication.APPLICATION_ID, grailsApplication)
            registerSingletonIfAbsent(beanFactory, GrailsPluginManager.BEAN_NAME, pluginManager)
        }
    }

    /**
     * The early plugin registration phase may have already promoted these singletons;
     * {@code registerSingleton} throws {@code IllegalStateException} on double registration.
     */
    private static void registerSingletonIfAbsent(ConfigurableBeanFactory beanFactory, String beanName, Object singleton) {
        if (!beanFactory.containsSingleton(beanName)) {
            beanFactory.registerSingleton(beanName, singleton)
        }
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (this.applicationContext != applicationContext && applicationContext != null) {
            this.applicationContext = applicationContext
            initializeGrailsApplication(applicationContext)
            if (applicationContext instanceof ConfigurableApplicationContext) {
                def configurable = (ConfigurableApplicationContext) applicationContext
                configurable.addApplicationListener(this)
                configurable.environment.addActiveProfile(grailsApplication.getConfig().getProperty(Settings.PROFILE, String, 'web'))
            }
        }
    }

    @Override
    void onApplicationEvent(ApplicationContextEvent event) {
        ApplicationContext context = event.applicationContext

        if (!applicationContext || applicationContext == context) {
            // Only act if the event is for our context
            Collection<GrailsApplicationLifeCycle> lifeCycleBeans = context.getBeansOfType(GrailsApplicationLifeCycle).values()
            if (event instanceof ContextRefreshedEvent) {
                if (context.containsBean('grailsDomainClassMappingContext')) {
                    grailsApplication.setMappingContext(
                        context.getBean('grailsDomainClassMappingContext', MappingContext)
                    )
                }
                Environment.setInitializing(false)
                pluginManager.setApplicationContext(context)
                pluginManager.doDynamicMethods()
                for (GrailsApplicationLifeCycle lifeCycle in lifeCycleBeans) {
                    lifeCycle.doWithDynamicMethods()
                }
                pluginManager.doPostProcessing(context)
                for (GrailsApplicationLifeCycle lifeCycle in lifeCycleBeans) {
                    lifeCycle.doWithApplicationContext()
                }
                Holders.pluginManager = pluginManager
                Map<String, Object> eventMap = [:]
                eventMap.put('source', pluginManager)

                pluginManager.onStartup(eventMap)
                for (GrailsApplicationLifeCycle lifeCycle in lifeCycleBeans) {
                    lifeCycle.onStartup(eventMap)
                }
            }
            else if (event instanceof ContextClosedEvent) {
                Map<String, Object> eventMap = [:]
                eventMap.put('source', pluginManager)
                for (GrailsApplicationLifeCycle lifeCycle in lifeCycleBeans.asList().reverse()) {
                    lifeCycle.onShutdown(eventMap)
                }
                pluginManager.shutdown()
                ShutdownOperations.runOperations()
                Holders.clear()
                GrailsApp.setDevelopmentModeActive(false)
            }
        }
    }
}

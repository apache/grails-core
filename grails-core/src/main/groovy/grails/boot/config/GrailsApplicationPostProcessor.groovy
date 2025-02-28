package grails.boot.config

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
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.config.NavigableMap
import org.grails.config.PrefixedMapPropertySource
import org.grails.config.PropertySourcesConfig
import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.model.MappingContext
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfigUtilities
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
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
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.Resource

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
    protected ApplicationContext applicationContext
    boolean loadExternalBeans = true
    boolean reloadingEnabled = RELOADING_ENABLED

    GrailsApplicationPostProcessor(GrailsApplicationLifeCycle lifeCycle, ApplicationContext applicationContext, Class...classes) {
        this.lifeCycle = lifeCycle
        if(lifeCycle instanceof GrailsApplicationClass) {
            this.applicationClass = (GrailsApplicationClass)lifeCycle
        }
        else {
            this.applicationClass = null
        }
        this.classes = classes != null ? classes : [] as Class[]
        grailsApplication = applicationClass != null ? new DefaultGrailsApplication(applicationClass) : new DefaultGrailsApplication()
        pluginManager = applicationContext?.getBeanNamesForType(GrailsPluginManager) ? applicationContext.getBean(GrailsPluginManager) : new DefaultGrailsPluginManager(grailsApplication)
        if(applicationContext != null) {
            setApplicationContext(applicationContext)
        }
    }

    protected final void initializeGrailsApplication(ApplicationContext applicationContext) {
        if(applicationContext == null) {
            throw new IllegalStateException("ApplicationContext should not be null")
        }
        Environment.setInitializing(true)
        grailsApplication.applicationContext = applicationContext
        grailsApplication.mainContext = applicationContext
        customizePluginManager(pluginManager)
        pluginManager.loadPlugins()
        pluginManager.applicationContext = applicationContext
        loadApplicationConfig()
        customizeGrailsApplication(grailsApplication)
        performGrailsInitializationSequence()
    }

    protected void customizePluginManager(GrailsPluginManager grailsApplication) {

    }

    protected void customizeGrailsApplication(GrailsApplication grailsApplication) {

    }

    protected void performGrailsInitializationSequence() {
        pluginManager.doArtefactConfiguration()
        grailsApplication.initialise()
        // register plugin provided classes first, this gives the oppurtunity
        // for application classes to override those provided by a plugin
        pluginManager.registerProvidedArtefacts(grailsApplication)
        for(cls in classes) {
            grailsApplication.addArtefact(cls)
        }
    }

    protected void loadApplicationConfig() {
        org.springframework.core.env.Environment environment = applicationContext.getEnvironment()
        ConfigurableConversionService conversionService = null
        if(environment instanceof ConfigurableEnvironment) {
            if(environment instanceof AbstractEnvironment) {
                conversionService = environment.getConversionService()
                conversionService.addConverter(new Converter<String, Resource>() {
                    @Override
                    public Resource convert(String source) {
                        return applicationContext.getResource(source);
                    }
                });
                conversionService.addConverter(new Converter<NavigableMap.NullSafeNavigator, String>() {
                    @Override
                    public String convert(NavigableMap.NullSafeNavigator source) {
                        return null;
                    }
                });
                conversionService.addConverter(new Converter<NavigableMap.NullSafeNavigator, Object>() {
                    @Override
                    public Object convert(NavigableMap.NullSafeNavigator source) {
                        return null;
                    }
                });
            }
            def propertySources = environment.getPropertySources()
            def plugins = pluginManager.allPlugins
            if(plugins) {
                for(GrailsPlugin plugin in plugins.reverse()) {
                    def pluginPropertySource = plugin.propertySource
                    if(pluginPropertySource) {
                        if(pluginPropertySource instanceof EnumerablePropertySource) {
                            propertySources.addLast( new PrefixedMapPropertySource( "grails.plugins.$plugin.name", (EnumerablePropertySource)pluginPropertySource ) )
                        }
                        propertySources.addLast pluginPropertySource
                    }
                }
            }
            def config = new PropertySourcesConfig(propertySources)
            if(conversionService != null) {
                config.setConversionService( conversionService )
            }
            ((DefaultGrailsApplication)grailsApplication).config = config
        }
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        def springConfig = new DefaultRuntimeSpringConfiguration()
        def application = grailsApplication
        Holders.setGrailsApplication(application)

        // first register plugin beans
        pluginManager.doRuntimeConfiguration(springConfig)

        if(loadExternalBeans) {
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

        if(lifeCycle) {
            def withSpring = lifeCycle.doWithSpring()
            if(withSpring) {
                def bb = new BeanBuilder(null, springConfig, application.classLoader)
                bb.beans withSpring
            }
        }

        springConfig.registerBeansWithRegistry(registry)
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanFactory parentBeanFactory = beanFactory.getParentBeanFactory()
        if (parentBeanFactory instanceof ConfigurableBeanFactory) {
            ConfigurableBeanFactory configurableBeanFactory = parentBeanFactory
            configurableBeanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
            configurableBeanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
        } else {
            beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
            beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
        }
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(this.applicationContext != applicationContext && applicationContext != null) {
            this.applicationContext = applicationContext
            initializeGrailsApplication(applicationContext)
            if(applicationContext instanceof ConfigurableApplicationContext) {
                def configurable = (ConfigurableApplicationContext) applicationContext
                configurable.addApplicationListener(this)
                configurable.environment.addActiveProfile( grailsApplication.getConfig().getProperty(Settings.PROFILE, String, "web"))
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
                if (context.containsBean("grailsDomainClassMappingContext")) {
                    grailsApplication.setMappingContext(
                        context.getBean("grailsDomainClassMappingContext", MappingContext)
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
            else if(event instanceof ContextClosedEvent) {
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

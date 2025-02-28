package org.grails.testing

import grails.boot.config.GrailsApplicationPostProcessor
import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.core.support.proxy.DefaultProxyHandler
import grails.plugins.GrailsPluginManager
import grails.spring.BeanBuilder
import grails.util.Holders
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import jakarta.servlet.ServletContext
import org.grails.plugins.IncludingPluginFilter
import org.grails.spring.context.support.GrailsPlaceholderConfigurer
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer
import org.grails.transaction.TransactionManagerPostProcessor
import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.annotation.ImportCandidates
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigRegistry
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.ConversionServiceFactoryBean
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.Ordered
import org.springframework.util.ClassUtils

/**
 * Created by jameskleeh on 5/31/17.
 */
@CompileStatic
class GrailsApplicationBuilder {

    public static final boolean isServletApiPresent = ClassUtils.isPresent('jakarta.servlet.ServletContext', GrailsApplicationBuilder.classLoader)

    static final Set DEFAULT_INCLUDED_PLUGINS = ['core', 'eventBus'] as Set

    Closure doWithSpring
    Closure doWithConfig
    Set<String> includePlugins
    boolean loadExternalBeans
    boolean localOverride = false

    GrailsApplication grailsApplication
    Object servletContext

    @CompileDynamic
    GrailsApplicationBuilder build() {

        servletContext = createServletContext()
        def mainContext = createMainContext(servletContext)

        if (isServletApiPresent) {
            // NOTE: The following dynamic class loading hack is temporary so the
            // compile time dependency on the servlet api can be removed from this
            // sub project.  This whole GrailsApplicationTestPlugin class will soon
            // be removed so rather than implement a real solution, this hack will
            // do for now to keep the build healthy.
            try {
                def segads = Class.forName('org.grails.web.context.ServletEnvironmentGrailsApplicationDiscoveryStrategy')
                Holders.addApplicationDiscoveryStrategy(segads.newInstance(servletContext))
            }
            catch (Throwable ignored) {}

            try {
                def gcu = Class.forName('org.grails.web.servlet.context.GrailsConfigUtils')
                gcu.configureServletContextAttributes(servletContext, grailsApplication, mainContext.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager), mainContext)
            }
            catch (Throwable ignored) {}
        }

        grailsApplication = mainContext.getBean('grailsApplication') as GrailsApplication

        if (!grailsApplication.isInitialised()) {
            grailsApplication.initialise()
        }

        return this
    }

    protected Object createServletContext() {

        def context = null

        if (isServletApiPresent) {
            context = ClassUtils.forName('org.springframework.mock.web.MockServletContext').getDeclaredConstructor().newInstance()
            Holders.setServletContext(context)
        }

        return context
    }

    protected ConfigurableApplicationContext createMainContext(Object servletContext) {
        ConfigurableApplicationContext context
        if (isServletApiPresent && servletContext != null) {
            context = (AnnotationConfigServletWebApplicationContext) ClassUtils.forName('org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext').getDeclaredConstructor().newInstance()
            ((AnnotationConfigServletWebApplicationContext) context).setServletContext((ServletContext) servletContext)
        } else {
            context = (ConfigurableApplicationContext) ClassUtils.forName('org.springframework.context.annotation.AnnotationConfigApplicationContext').getDeclaredConstructor().newInstance()
        }

        def classLoader = this.class.classLoader
        ImportCandidates.load(AutoConfiguration, classLoader).asList().findAll {
            it.startsWith("org.grails")
            && !it.contains("UrlMappingsAutoConfiguration") // this currently is causing an issue with tests
        }.each {
            ((AnnotationConfigRegistry) context).register(ClassUtils.forName(it, classLoader))
        }

        def beanFactory = context.getBeanFactory()
        (beanFactory as DefaultListableBeanFactory).with {
            setAllowBeanDefinitionOverriding(true)
            setAllowCircularReferences(true)
        }
        prepareContext(context, beanFactory)
        context.refresh()
        context.registerShutdownHook()
        return context
    }

    protected void prepareContext(ConfigurableApplicationContext applicationContext, ConfigurableBeanFactory beanFactory) {
        registerGrailsAppPostProcessorBean(beanFactory)
        AnnotationConfigUtils.registerAnnotationConfigProcessors((BeanDefinitionRegistry) beanFactory)
        new ConfigDataApplicationContextInitializer().initialize(applicationContext)
    }

    void executeDoWithSpringCallback(GrailsApplication grailsApplication) {
        if (!doWithSpring) return
        defineBeans(grailsApplication, doWithSpring)
    }

    void defineBeans(Closure callable) {
        defineBeans(grailsApplication, callable)
    }

    void defineBeans(GrailsApplication grailsApplication, Closure callable) {
        def binding = new Binding()
        def bb = new BeanBuilder(null, null, grailsApplication.getClassLoader())
        binding.setVariable('application', grailsApplication)
        bb.setBinding(binding)
        bb.beans(callable)
        bb.registerBeans((BeanDefinitionRegistry) grailsApplication.getMainContext())
    }

    @CompileDynamic
    void registerBeans(GrailsApplication grailsApplication) {

        defineBeans(grailsApplication) { ->

            conversionService(ConversionServiceFactoryBean)

            xmlns context: "http://www.springframework.org/schema/context"
            // adds AutowiredAnnotationBeanPostProcessor, CommonAnnotationBeanPostProcessor and others
            // see org.springframework.context.annotation.AnnotationConfigUtils.registerAnnotationConfigProcessors method
            context.'annotation-config'()

            proxyHandler(DefaultProxyHandler)
            messageSource(StaticMessageSource)
            transactionManagerAwarePostProcessor(TransactionManagerPostProcessor)
            grailsPlaceholderConfigurer(GrailsPlaceholderConfigurer, '${', grailsApplication.config.toProperties())
            mapBasedSmartPropertyOverrideConfigurer(MapBasedSmartPropertyOverrideConfigurer) {
                setGrailsApplication(grailsApplication)
            }
        }
    }

    protected void registerGrailsAppPostProcessorBean(ConfigurableBeanFactory beanFactory) {

        GrailsApplication grailsApp

        Closure doWithSpringClosure = {
            registerBeans(grailsApp)
            executeDoWithSpringCallback(grailsApp)
        }

        Closure customizeGrailsApplicationClosure = { GrailsApplication grailsApplication ->
            grailsApp = grailsApplication
            if (doWithConfig) {
                doWithConfig.call(grailsApplication.config)
                // reset flatConfig
                grailsApplication.configChanged()
            }
            Holders.config = grailsApplication.config
        }

        def constructorArgumentValues = new ConstructorArgumentValues()
        constructorArgumentValues.addIndexedArgumentValue(0, doWithSpringClosure)
        constructorArgumentValues.addIndexedArgumentValue(1, includePlugins ?: DEFAULT_INCLUDED_PLUGINS)

        def values = new MutablePropertyValues()
        values.add('localOverride', localOverride)
        values.add('loadExternalBeans', loadExternalBeans)
        values.add('customizeGrailsApplicationClosure', customizeGrailsApplicationClosure)

        def beanDef = new RootBeanDefinition(TestRuntimeGrailsApplicationPostProcessor, constructorArgumentValues, values)
        beanDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
        (beanFactory as BeanDefinitionRegistry).registerBeanDefinition('grailsApplicationPostProcessor', beanDef)
    }

    static class TestRuntimeGrailsApplicationPostProcessor extends GrailsApplicationPostProcessor {

        Closure customizeGrailsApplicationClosure
        Set includedPlugins
        boolean localOverride = false

        TestRuntimeGrailsApplicationPostProcessor(Closure doWithSpringClosure, Set includedPlugins) {
            super([doWithSpring: { -> doWithSpringClosure }] as GrailsApplicationLifeCycle, null, null)
            loadExternalBeans = false
            reloadingEnabled = false
            this.includedPlugins = includedPlugins
        }

        @Override
        protected void customizePluginManager(GrailsPluginManager grailsApplication) {
            pluginManager.pluginFilter = new IncludingPluginFilter(includedPlugins)
        }

        @Override
        protected void customizeGrailsApplication(GrailsApplication grailsApplication) {
            customizeGrailsApplicationClosure?.call(grailsApplication)
        }

        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            super.postProcessBeanDefinitionRegistry(registry)
            PropertySourcesPlaceholderConfigurer propertySourcePlaceholderConfigurer  = (PropertySourcesPlaceholderConfigurer) grailsApplication.mainContext.getBean('grailsPlaceholderConfigurer')
            propertySourcePlaceholderConfigurer.order = Ordered.HIGHEST_PRECEDENCE
            propertySourcePlaceholderConfigurer.setLocalOverride(localOverride)
        }
    }
}

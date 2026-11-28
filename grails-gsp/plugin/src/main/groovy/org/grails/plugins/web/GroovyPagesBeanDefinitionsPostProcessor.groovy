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
package org.grails.plugins.web

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.aot.AotDetector
import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.util.ClassUtils
import org.springframework.web.servlet.view.InternalResourceViewResolver

import grails.config.Config
import grails.core.GrailsApplication
import grails.gsp.PageRenderer
import grails.util.BuildSettings
import grails.util.Environment
import org.grails.gsp.GroovyPageResourceLoader
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.CachingGroovyPageStaticResourceLocator
import org.grails.gsp.jsp.TagLibraryResolverImpl
import org.grails.taglib.TagLibraryLookup
import org.grails.web.errors.ErrorsViewStackTracePrinter
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.gsp.io.CachingGrailsConventionGroovyPageLocator
import org.grails.web.pages.DefaultGroovyPagesUriService
import org.grails.web.pages.FilteringCodecsByContentTypeSettings
import org.grails.web.pages.GroovyPagesServlet
import org.grails.web.servlet.view.GroovyPageViewResolver
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Registers the beans GSP needs: the template engine and its page locator, resource loader and tag
 * library lookup, the page renderers, the {@code abstractViewResolver} parent definition a view
 * resolver of GSP pages inherits from, the {@code groovyTemplateEngine} alias and the GSP servlet.
 * Abstract parents, aliases, autowiring by name and inner beans are what the
 * {@link org.springframework.beans.factory.BeanRegistry} API cannot express, so they are registered
 * here, from the plugin's registrar.
 *
 * <p>It runs as a {@link PriorityOrdered} post-processor with highest precedence, so the definitions
 * are registered before Spring Boot reads the auto-configurations and a {@code @ConditionalOnMissingBean}
 * default backs off from them. A definition registered earlier under one of these names, by a plugin
 * loaded before this one, is replaced. Running on artifacts generated ahead of time, a definition the
 * generator wrote is kept.</p>
 *
 * @since 9.0
 */
@Slf4j
@CompileStatic
class GroovyPagesBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final GrailsApplication grailsApplication
    private final boolean developmentMode

    /**
     * @param grailsApplication the application, whose configuration is read
     * @param developmentMode whether the application is being developed, which decides where pages are
     *        read from and whether the pages compiled at build time are looked up
     */
    GroovyPagesBeanDefinitionsPostProcessor(GrailsApplication grailsApplication, boolean developmentMode) {
        this.grailsApplication = grailsApplication
        this.developmentMode = developmentMode
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Config config = grailsApplication.config
        Environment env = Environment.current

        boolean enableReload = env.isReloadEnabled() ||
                config.getProperty(GroovyPagesTemplateEngine.CONFIG_PROPERTY_GSP_ENABLE_RELOAD, Boolean, false) ||
                (developmentMode && env == Environment.DEVELOPMENT)

        boolean warDeployedWithReload = grailsApplication.warDeployed && enableReload

        long gspCacheTimeout = config.getProperty(GroovyPagesGrailsPlugin.GSP_RELOAD_INTERVAL, Long,
                (developmentMode && env == Environment.DEVELOPMENT) ? 0L : 5000L)
        boolean enableCacheResources = !config.getProperty(GroovyPagesTemplateEngine.CONFIG_PROPERTY_DISABLE_CACHING_RESOURCES, Boolean, false)
        String viewsDir = config.getProperty(GroovyPagesGrailsPlugin.GSP_VIEWS_DIR, '')

        // resolves JSP tag libraries
        boolean resolveJspTagLibraries = ClassUtils.isPresent('org.grails.gsp.jsp.TagLibraryResolverImpl', grailsApplication.classLoader)
        if (resolveJspTagLibraries) {
            register(registry, 'jspTagLibraryResolver', definition(TagLibraryResolverImpl))
        }

        // resolves GSP tag libraries
        register(registry, 'gspTagLibraryLookup', lazy(definition(TagLibraryLookup)))

        // In development the pages are read relative to the base directory, rather than from
        // /WEB-INF as in a deployed application
        String baseResource = null
        boolean lazyResourceLoader = false
        if (viewsDir) {
            log.info("Configuring GSP views directory as '{}'", viewsDir)
            baseResource = "file:${viewsDir}"
        }
        else if (developmentMode) {
            lazyResourceLoader = true
            baseResource = "file:${GroovyPagesGrailsPlugin.transformToValidLocation(BuildSettings.BASE_DIR.absolutePath)}"
        }
        else if (warDeployedWithReload && env.hasReloadLocation()) {
            baseResource = "file:${GroovyPagesGrailsPlugin.transformToValidLocation(env.reloadLocation)}"
        }
        boolean customResourceLoader = baseResource != null
        if (customResourceLoader) {
            GenericBeanDefinition resourceLoader = definition(GroovyPageResourceLoader)
            resourceLoader.lazyInit = lazyResourceLoader
            resourceLoader.propertyValues.add('baseResource', baseResource)
            register(registry, 'groovyPageResourceLoader', resourceLoader)
        }

        GenericBeanDefinition locator = lazy(definition(CachingGrailsConventionGroovyPageLocator))
        if (customResourceLoader) {
            locator.propertyValues.add('resourceLoader', ref('groovyPageResourceLoader'))
        }
        // Where the pages compiled at build time are listed, attached anywhere they are the pages that
        // will be rendered: a deployed application, and one whose code is being written out. The
        // locations are named rather than resolved here, so what is written down is somewhere to look
        // and not a path on the machine that did the building.
        if (!developmentMode) {
            GenericBeanDefinition precompiledGspMap = definition(PropertiesFactoryBean)
            precompiledGspMap.propertyValues.add('ignoreResourceNotFound', true)
            precompiledGspMap.propertyValues.add('locations', ['gsp/views.properties', 'classpath:gsp/views.properties'])
            locator.propertyValues.add('precompiledGspMap', precompiledGspMap)
        }
        if (enableReload) {
            locator.propertyValues.add('cacheTimeout', gspCacheTimeout)
        }
        locator.propertyValues.add('reloadEnabled', enableReload)
        register(registry, 'groovyPageLocator', locator)

        GenericBeanDefinition resourceLocator = definition(CachingGroovyPageStaticResourceLocator)
        resourceLocator.parentName = 'abstractGrailsResourceLocator'
        if (enableReload) {
            resourceLocator.propertyValues.add('cacheTimeout', gspCacheTimeout)
        }
        register(registry, 'grailsResourceLocator', resourceLocator)

        // the main template engine used to render GSPs
        GenericBeanDefinition templateEngine = definition(GroovyPagesTemplateEngine)
        MutablePropertyValues engineProperties = templateEngine.propertyValues
        engineProperties.add('classLoader', ref('classLoader'))
        engineProperties.add('groovyPageLocator', ref('groovyPageLocator'))
        if (enableReload) {
            engineProperties.add('reloadEnabled', enableReload)
        }
        engineProperties.add('tagLibraryLookup', ref('gspTagLibraryLookup'))
        if (resolveJspTagLibraries) {
            engineProperties.add('jspTagLibraryResolver', ref('jspTagLibraryResolver'))
        }
        engineProperties.add('cacheResources', enableCacheResources)
        register(registry, 'groovyPagesTemplateEngine', templateEngine)
        registry.registerAlias('groovyPagesTemplateEngine', 'groovyTemplateEngine')

        GenericBeanDefinition pageRenderer = lazy(definition(PageRenderer))
        pageRenderer.constructorArgumentValues.addGenericArgumentValue(ref('groovyPagesTemplateEngine'))
        pageRenderer.propertyValues.add('groovyPageLocator', ref('groovyPageLocator'))
        register(registry, 'groovyPageRenderer', pageRenderer)

        GenericBeanDefinition templateRenderer = definition(GroovyPagesTemplateRenderer)
        templateRenderer.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_NAME
        if (enableReload) {
            templateRenderer.propertyValues.add('reloadEnabled', enableReload)
        }
        register(registry, 'groovyPagesTemplateRenderer', templateRenderer)

        register(registry, 'groovyPagesUriService', lazy(definition(DefaultGroovyPagesUriService)))

        boolean jstlPresent = ClassUtils.isPresent(
                'jakarta.servlet.jsp.jstl.core.Config', InternalResourceViewResolver.getClassLoader())

        GenericBeanDefinition abstractViewResolver = new GenericBeanDefinition()
        abstractViewResolver.scope = BeanDefinition.SCOPE_SINGLETON
        abstractViewResolver.abstract = true
        MutablePropertyValues viewResolverProperties = abstractViewResolver.propertyValues
        viewResolverProperties.add('prefix', GrailsApplicationAttributes.PATH_TO_VIEWS)
        viewResolverProperties.add('suffix', jstlPresent ? GroovyPageViewResolver.JSP_SUFFIX : GroovyPageViewResolver.GSP_SUFFIX)
        viewResolverProperties.add('resolveJspView', jstlPresent)
        viewResolverProperties.add('templateEngine', ref('groovyPagesTemplateEngine'))
        viewResolverProperties.add('groovyPageLocator', ref('groovyPageLocator'))
        if (enableReload) {
            viewResolverProperties.add('cacheTimeout', gspCacheTimeout)
        }
        register(registry, 'abstractViewResolver', abstractViewResolver)

        // contributes a Spring MVC view resolver of GSP pages if none is defined
        register(registry, 'groovyPagesPostProcessor', definition(GroovyPagesPostProcessor))

        GenericBeanDefinition stackTracePrinter = definition(ErrorsViewStackTracePrinter)
        stackTracePrinter.constructorArgumentValues.addGenericArgumentValue(ref('grailsResourceLocator'))
        register(registry, 'errorsViewStackTracePrinter', stackTracePrinter)

        GenericBeanDefinition codecsSettings = definition(FilteringCodecsByContentTypeSettings)
        codecsSettings.constructorArgumentValues.addGenericArgumentValue(ref('grailsApplication'))
        register(registry, 'filteringCodecsByContentTypeSettings', codecsSettings)

        GenericBeanDefinition servlet = definition(ServletRegistrationBean)
        servlet.constructorArgumentValues.addGenericArgumentValue(definition(GroovyPagesServlet))
        servlet.constructorArgumentValues.addGenericArgumentValue('*.gsp')
        if (Environment.isDevelopmentMode()) {
            servlet.propertyValues.add('initParameters', [showSource: '1'])
        }
        register(registry, 'groovyPagesServlet', servlet)

        register(registry, 'grailsTagDateHelper', definition(DefaultGrailsTagDateHelper))
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }

    private static GenericBeanDefinition definition(Class<?> beanClass) {
        GenericBeanDefinition definition = new GenericBeanDefinition()
        definition.beanClass = beanClass
        definition.scope = BeanDefinition.SCOPE_SINGLETON
        definition
    }

    private static GenericBeanDefinition lazy(GenericBeanDefinition definition) {
        definition.lazyInit = true
        definition
    }

    private static RuntimeBeanReference ref(String beanName) {
        new RuntimeBeanReference(beanName)
    }

    /**
     * Registers a definition, keeping one generated ahead of time: running on generated artifacts,
     * what this registered was written out as code, and registering over it would discard the
     * instance supplier the generator wrote.
     */
    private static void register(BeanDefinitionRegistry registry, String beanName, BeanDefinition definition) {
        if (AotDetector.useGeneratedArtifacts() && registry.containsBeanDefinition(beanName)) {
            return
        }
        registry.registerBeanDefinition(beanName, definition)
    }
}

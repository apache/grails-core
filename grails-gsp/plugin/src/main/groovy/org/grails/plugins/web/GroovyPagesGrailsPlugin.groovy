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

import grails.config.Config
import grails.core.gsp.GrailsTagLibClass
import grails.gsp.PageRenderer
import grails.plugins.Plugin
import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsUtil
import grails.util.Metadata
import grails.web.pages.GroovyPagesUriService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.gsp.GroovyPageResourceLoader
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.CachingGroovyPageStaticResourceLocator
import org.grails.gsp.jsp.TagLibraryResolverImpl
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.plugins.web.taglib.CountryTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.grails.plugins.web.taglib.FormatTagLib
import org.grails.plugins.web.taglib.JavascriptTagLib
import org.grails.plugins.web.taglib.PluginTagLib
import org.grails.plugins.web.taglib.RenderTagLib
import org.grails.plugins.web.taglib.UrlMappingTagLib
import org.grails.plugins.web.taglib.ValidationTagLib
import org.grails.spring.RuntimeSpringConfiguration
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagLibraryMetaUtils
import org.grails.web.errors.ErrorsViewStackTracePrinter
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.gsp.io.CachingGrailsConventionGroovyPageLocator
import org.grails.web.pages.DefaultGroovyPagesUriService
import org.grails.web.pages.FilteringCodecsByContentTypeSettings
import org.grails.web.pages.GroovyPagesServlet
import org.grails.web.servlet.view.GroovyPageViewResolver
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.core.io.Resource
import org.springframework.util.ClassUtils
import org.springframework.web.servlet.view.InternalResourceViewResolver

/**
 * Sets up and configures the GSP and GSP tag library support in Grails.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@Slf4j
class GroovyPagesGrailsPlugin extends Plugin {

    public static final String GSP_RELOAD_INTERVAL = 'grails.gsp.reload.interval'
    public static final String GSP_VIEWS_DIR = 'grails.gsp.view.dir'

    def watchedResources = ['file:./plugins/*/grails-app/taglib/**/*TagLib.groovy',
                            'file:./grails-app/taglib/**/*TagLib.groovy']

    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def dependsOn = [core: GrailsUtil.getGrailsVersion(), i18n: GrailsUtil.getGrailsVersion()]
    def observe = ['controllers']
    def loadAfter = ['filters']

    def providedArtefacts = [
            ApplicationTagLib,
            CountryTagLib,
            FormatTagLib,
            FormTagLib,
            JavascriptTagLib,
            RenderTagLib,
            UrlMappingTagLib,
            ValidationTagLib,
            PluginTagLib
    ]


    /**
     * Clear the page cache with the ApplicationContext is loaded
     */
    @CompileStatic
    @Override
    void doWithApplicationContext() {
        applicationContext.getBean('groovyPagesTemplateEngine', GroovyPagesTemplateEngine).clearPageCache()
    }

    /**
     * Configures the various Spring beans required by GSP
     */
    Closure doWithSpring() {
        { ->
            def application = grailsApplication
            Config config = application.config
            boolean developmentMode = isDevelopmentMode()
            Environment env = Environment.current

            boolean enableReload = env.isReloadEnabled() ||
                    config.getProperty(GroovyPagesTemplateEngine.CONFIG_PROPERTY_GSP_ENABLE_RELOAD, Boolean, false) ||
                    (developmentMode && env == Environment.DEVELOPMENT)

            boolean warDeployed = application.warDeployed
            boolean warDeployedWithReload = warDeployed && enableReload

            long gspCacheTimeout = config.getProperty(GSP_RELOAD_INTERVAL, Long, (developmentMode && env == Environment.DEVELOPMENT) ? 0L : 5000L)
            boolean enableCacheResources = !config.getProperty(GroovyPagesTemplateEngine.CONFIG_PROPERTY_DISABLE_CACHING_RESOURCES, Boolean, false)
            String viewsDir = config.getProperty(GSP_VIEWS_DIR, '')

            RuntimeSpringConfiguration spring = springConfig

            // resolves JSP tag libraries
            boolean resolveJspTagLibraries = ClassUtils.isPresent('org.grails.gsp.jsp.TagLibraryResolverImpl', application.classLoader)
            if (resolveJspTagLibraries) {
                jspTagLibraryResolver(TagLibraryResolverImpl)
            }

            // resolves GSP tag libraries
            gspTagLibraryLookup(TagLibraryLookup) { bean ->
                bean.lazyInit = true
            }


            boolean customResourceLoader = false
            // If the development environment is used we need to load GSP files relative to the base directory
            // as oppose to in WAR deployment where views are loaded from /WEB-INF

            if (viewsDir) {
                log.info("Configuring GSP views directory as '{}'", viewsDir)
                customResourceLoader = true
                groovyPageResourceLoader(GroovyPageResourceLoader) {
                    baseResource = "file:${viewsDir}"
                }
            } else {
                if (developmentMode) {
                    customResourceLoader = true
                    groovyPageResourceLoader(GroovyPageResourceLoader) { bean ->
                        bean.lazyInit = true
                        def location = GroovyPagesGrailsPlugin.transformToValidLocation(BuildSettings.BASE_DIR.absolutePath)
                        baseResource = "file:$location"
                    }
                } else {
                    if (warDeployedWithReload && env.hasReloadLocation()) {
                        customResourceLoader = true
                        groovyPageResourceLoader(GroovyPageResourceLoader) {
                            def location = GroovyPagesGrailsPlugin.transformToValidLocation(env.reloadLocation)
                            baseResource = "file:${location}"
                        }
                    }
                }
            }

            def deployed = !Metadata.getCurrent().isDevelopmentEnvironmentAvailable()
            groovyPageLocator(CachingGrailsConventionGroovyPageLocator) { bean ->
                bean.lazyInit = true
                if (customResourceLoader) {
                    resourceLoader = groovyPageResourceLoader
                }
                if (deployed) {
                    Resource defaultViews = applicationContext?.getResource('gsp/views.properties')

                    if (defaultViews != null) {
                        if (!defaultViews.exists()) {
                            defaultViews = applicationContext?.getResource('classpath:gsp/views.properties')
                        }
                    }

                    if (defaultViews?.exists()) {
                        precompiledGspMap = { PropertiesFactoryBean pfb ->
                            ignoreResourceNotFound = true
                            locations = [defaultViews] as Resource[]
                        }
                    }
                }
                if (enableReload) {
                    cacheTimeout = gspCacheTimeout
                }
                reloadEnabled = enableReload
            }

            grailsResourceLocator(CachingGroovyPageStaticResourceLocator) { bean ->
                bean.parent = 'abstractGrailsResourceLocator'
                if (enableReload) {
                    cacheTimeout = gspCacheTimeout
                }
            }

            // Setup the main templateEngine used to render GSPs
            groovyPagesTemplateEngine(GroovyPagesTemplateEngine) {
                classLoader = ref('classLoader')
                groovyPageLocator = groovyPageLocator
                if (enableReload) {
                    reloadEnabled = enableReload
                }
                tagLibraryLookup = gspTagLibraryLookup
                if (resolveJspTagLibraries) {
                    jspTagLibraryResolver = jspTagLibraryResolver
                }
                cacheResources = enableCacheResources
            }

            spring.addAlias('groovyTemplateEngine', 'groovyPagesTemplateEngine')

            groovyPageRenderer(PageRenderer, ref('groovyPagesTemplateEngine')) { bean ->
                bean.lazyInit = true
                groovyPageLocator = groovyPageLocator
            }

            groovyPagesTemplateRenderer(GroovyPagesTemplateRenderer) { bean ->
                bean.autowire = true
                if (enableReload) {
                    reloadEnabled = enableReload
                }
            }

            // Setup the GroovyPagesUriService
            groovyPagesUriService(DefaultGroovyPagesUriService) { bean ->
                bean.lazyInit = true
            }

            boolean jstlPresent = ClassUtils.isPresent(
                    'jakarta.servlet.jsp.jstl.core.Config', InternalResourceViewResolver.class.getClassLoader())

            abstractViewResolver {
                prefix = GrailsApplicationAttributes.PATH_TO_VIEWS
                suffix = jstlPresent ? GroovyPageViewResolver.JSP_SUFFIX : GroovyPageViewResolver.GSP_SUFFIX
                resolveJspView = jstlPresent
                templateEngine = groovyPagesTemplateEngine
                groovyPageLocator = groovyPageLocator
                if (enableReload) {
                    cacheTimeout = gspCacheTimeout
                }
            }
            // Configure a Spring MVC view resolver
            jspViewResolver(GroovyPageViewResolver) { bean ->
                bean.lazyInit = true
                bean.parent = 'abstractViewResolver'
            }

            // Now go through tag libraries and configure them in Spring too. With AOP proxies and so on
            for (taglib in application.tagLibClasses) {

                final tagLibClass = taglib.clazz

                "${taglib.fullName}"(tagLibClass) { bean ->
                    bean.autowire = true
                    bean.lazyInit = true

                    // Taglib scoping support could be easily added here. Scope could be based on a static field in the taglib class.
                    //bean.scope = 'request'
                }
            }

            errorsViewStackTracePrinter(ErrorsViewStackTracePrinter, ref('grailsResourceLocator'))
            filteringCodecsByContentTypeSettings(FilteringCodecsByContentTypeSettings, application)

            groovyPagesServlet(ServletRegistrationBean, new GroovyPagesServlet(), '*.gsp') {
                if (Environment.isDevelopmentMode()) {
                    initParameters = [showSource: '1']
                }
            }

            grailsTagDateHelper(DefaultGrailsTagDateHelper)
        }
    }

    protected boolean isDevelopmentMode() {
        Metadata.getCurrent().isDevelopmentEnvironmentAvailable()
    }

    static String transformToValidLocation(String location) {
        if (location == '.') return location
        if (!location.endsWith(File.separator)) return "${location}${File.separator}"
        return location
    }

    @Override
    void onChange(Map<String, Object> event) {
        def application = grailsApplication
        def ctx = applicationContext

        if (application.isArtefactOfType(TagLibArtefactHandler.TYPE, event.source)) {
            GrailsTagLibClass taglibClass = (GrailsTagLibClass) application.addArtefact(TagLibArtefactHandler.TYPE, event.source)
            if (taglibClass) {
                // replace tag library bean
                def beanName = taglibClass.fullName
                beans {
                    "$beanName"(taglibClass.clazz) { bean ->
                        bean.autowire = true
                    }
                }

                // The tag library lookup class caches 'tag -> taglib class'
                // so we need to update it now.
                def lookup = applicationContext.getBean('gspTagLibraryLookup', TagLibraryLookup)
                lookup.registerTagLib(taglibClass)
                TagLibraryMetaUtils.enhanceTagLibMetaClass(taglibClass, lookup)
            }
        }
        // clear uri cache after changes
        ctx.getBean('groovyPagesUriService', GroovyPagesUriService).clear()
    }

    @CompileStatic
    void onConfigChange(Map<String, Object> event) {
        applicationContext.getBean('filteringCodecsByContentTypeSettings', FilteringCodecsByContentTypeSettings).initialize(grailsApplication)
    }

}

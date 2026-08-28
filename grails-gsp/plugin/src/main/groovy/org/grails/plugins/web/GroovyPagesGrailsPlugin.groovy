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

import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.util.ClassUtils
import org.springframework.web.servlet.view.InternalResourceViewResolver

import grails.config.Config
import grails.core.gsp.GrailsTagLibClass
import grails.gsp.PageRenderer
import grails.plugins.Plugin
import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsUtil
import grails.util.Metadata
import grails.web.pages.GroovyPagesUriService
import org.apache.grails.common.aot.AheadOfTimeProcessing
import org.apache.grails.gsp.taglib.TagLibBeanDefinitionsPostProcessor
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
import org.grails.web.errors.ErrorsViewStackTracePrinter
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.gsp.io.CachingGrailsConventionGroovyPageLocator
import org.grails.web.pages.DefaultGroovyPagesUriService
import org.grails.web.pages.FilteringCodecsByContentTypeSettings
import org.grails.web.pages.GroovyPagesServlet
import org.grails.web.servlet.view.GroovyPageViewResolver
import org.grails.web.util.GrailsApplicationAttributes

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
            PluginTagLib,
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
     * Tag library beans autowire by name and are read from the artefacts the application knows
     * about, which the {@link org.springframework.beans.factory.BeanRegistry} API cannot express, so
     * their definitions are contributed by a dedicated post-processor. Contributing them here rather
     * than from {@code doWithSpring()} leaves the definitions an ahead-of-time image generated, and
     * the injection generated with them, in place.
     */
    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            registry.registerBean('tagLibBeanDefinitionsPostProcessor', TagLibBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new TagLibBeanDefinitionsPostProcessor(grailsApplication)
                }
            }
        } as BeanRegistrar
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

            groovyPageLocator(CachingGrailsConventionGroovyPageLocator) { bean ->
                bean.lazyInit = true
                if (customResourceLoader) {
                    resourceLoader = groovyPageResourceLoader
                }
                // Where the pages compiled at build time are listed, attached anywhere they are
                // the pages that will be rendered: a deployed application, and one whose code is
                // being written out. isDevelopmentMode() answers no to the second, so an image is
                // not built believing it has to compile its pages -- which is the one thing it
                // cannot do -- while a project on disk still reads its pages from source and
                // recompiles them as they are edited.
                //
                // The locations are named rather than resolved here, so what is written down is
                // somewhere to look and not a path on the machine that did the building.
                if (!developmentMode) {
                    precompiledGspMap = { PropertiesFactoryBean pfb ->
                        ignoreResourceNotFound = true
                        locations = ['gsp/views.properties', 'classpath:gsp/views.properties']
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
                    'jakarta.servlet.jsp.jstl.core.Config', InternalResourceViewResolver.getClassLoader())

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
            // Configure a Spring MVC view resolver if none is defined
            groovyPagesPostProcessor(GroovyPagesPostProcessor)

            errorsViewStackTracePrinter(ErrorsViewStackTracePrinter, ref('grailsResourceLocator'))
            filteringCodecsByContentTypeSettings(FilteringCodecsByContentTypeSettings, ref('grailsApplication'))

            groovyPagesServlet(ServletRegistrationBean, bean(GroovyPagesServlet), '*.gsp') {
                if (Environment.isDevelopmentMode()) {
                    initParameters = [showSource: '1']
                }
            }

            grailsTagDateHelper(DefaultGrailsTagDateHelper)
        }
    }

    /**
     * Whether the application is being developed, which decides where its pages are read from and
     * whether they are watched for change.
     *
     * <p>While code is being generated the answer is no, whatever the machine doing the generating
     * looks like. Generation runs in the project directory, so a development environment is
     * available there and the question would otherwise be answered for the machine that built the
     * application rather than the one that runs it: the pages would be read from a directory that
     * exists only on the build machine, whose path would be written into the artifact, and an image
     * cannot compile a page it finds there in any case.</p>
     */
    @CompileStatic
    protected boolean isDevelopmentMode() {
        if (AheadOfTimeProcessing.generatingCode) {
            return false
        }
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
                // Registering with the lookup is enough: tags are resolved through it rather than
                // installed onto each tag library's metaclass.
                lookup.registerTagLib(taglibClass)
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

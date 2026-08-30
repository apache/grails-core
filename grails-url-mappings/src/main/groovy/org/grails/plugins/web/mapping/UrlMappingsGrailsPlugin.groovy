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
package org.grails.plugins.web.mapping

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.aot.AotDetector
import org.springframework.aop.target.HotSwappableTargetSource
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.web.filter.CorsFilter

import grails.config.Settings
import grails.plugins.Plugin
import grails.util.Environment as GrailsEnvironment
import grails.util.GrailsUtil
import grails.web.CamelCaseUrlConverter
import grails.web.HyphenatedUrlConverter
import grails.web.UrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappings
import grails.web.mapping.UrlMappingsHolder
import grails.web.mapping.cors.GrailsCorsConfiguration
import grails.web.mapping.cors.GrailsCorsFilter
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.web.mapping.CachingLinkGenerator
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.UrlMappingsHolderFactoryBean
import org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter
import org.grails.web.mapping.servlet.UrlMappingsErrorPageCustomizer

/**
 * Handles the configuration of URL mappings.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties([GrailsCorsConfiguration])
class UrlMappingsGrailsPlugin extends Plugin {

    def watchedResources = ['file:./grails-app/controllers/*UrlMappings.groovy']

    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [core: version]
    def loadAfter = ['controllers']

    def beans = {
        field('cacheUrls', Boolean).value(Settings.WEB_LINK_GENERATOR_USE_CACHE, '#{null}')
        field('serverURL', String).value(Settings.SERVER_URL, '#{null}')

        // The two mutually exclusive grailsUrlConverter variants share one bean name; the
        // @ConditionalOnProperty selects which registers.
        bean('grailsUrlConverter', UrlConverter).conditionalOnMissingBeanName()
                .annotate(ConditionalOnProperty, name: Settings.WEB_URL_CONVERTER, havingValue: 'camelCase', matchIfMissing: true) {
                    new CamelCaseUrlConverter()
                }

        bean('grailsUrlConverter', UrlConverter).conditionalOnMissingBeanName()
                .annotate(ConditionalOnProperty, name: Settings.WEB_URL_CONVERTER, havingValue: 'hyphenated') {
                    new HyphenatedUrlConverter()
                }

        bean('grailsLinkGenerator', LinkGenerator).conditionalOnMissingBeanName() {
            boolean useCache = cacheUrls != null ? cacheUrls :
                    !GrailsEnvironment.developmentMode && !GrailsEnvironment.current.reloadEnabled
            useCache ? new CachingLinkGenerator(serverURL) : new DefaultLinkGenerator(serverURL)
        }

        // Guarded on CorsFilter (the Spring type GrailsCorsFilter extends), not GrailsCorsFilter:
        // a user replacing CORS handling with any CorsFilter registration should not end up with
        // two CORS filters answering the same requests.
        bean(GrailsCorsFilter).conditionalOnMissingBean(CorsFilter)
                .annotate(ConditionalOnProperty, name: Settings.SETTING_CORS_FILTER,
                        havingValue: 'true', matchIfMissing: true) {
                    GrailsCorsConfiguration grailsCorsConfiguration ->
                }

        bean(UrlMappingsErrorPageCustomizer).conditionalOnMissingBean() { ObjectProvider<UrlMappings> urlMappingsProvider ->
            new UrlMappingsErrorPageCustomizer().tap {
                urlMappings = urlMappingsProvider.ifAvailable
            }
        }

        bean(UrlMappingsInfoHandlerAdapter).conditionalOnMissingBean()
    }

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            if (!grailsApplication.getArtefacts(UrlMappingsArtefactHandler.TYPE)) {
                grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, DefaultUrlMappings)
            }

            // Never proxy for hot-swapping when the bean definitions are the generated ones: AOT
            // code generation drops the custom definition attributes that tell Spring what a factory
            // bean produces, and hot-swapping mappings is meaningless in an image anyway.
            boolean reloadEnabled = !AotDetector.useGeneratedArtifacts() &&
                    (GrailsEnvironment.developmentMode || GrailsEnvironment.current.reloadEnabled)
            boolean corsFilterEnabled = environment.getProperty(Settings.SETTING_CORS_FILTER, Boolean, true)

            // The url-mapping holder is a ProxyFactoryBean (reload mode) whose produced UrlMappings
            // type must stay answerable from the bean definition for by-type autowiring of
            // UrlMappingsHolder — which an instance supplier would hide — so the definitions are
            // contributed by a dedicated post-processor that declares the produced type through
            // FactoryBean.OBJECT_TYPE_ATTRIBUTE. See that class for why the attribute is load-bearing.
            registry.registerBean('urlMappingsBeanDefinitionsPostProcessor', UrlMappingsBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new UrlMappingsBeanDefinitionsPostProcessor(reloadEnabled, corsFilterEnabled)
                }
            }
        }
    }

    @Override
    @CompileDynamic
    void onChange(Map<String, Object> event) {
        def application = grailsApplication
        if (!application.isArtefactOfType(UrlMappingsArtefactHandler.TYPE, event.source)) {
            return
        }

        application.addArtefact(UrlMappingsArtefactHandler.TYPE, event.source)

        ApplicationContext ctx = applicationContext
        UrlMappingsHolder urlMappingsHolder = createUrlMappingsHolder(applicationContext)

        HotSwappableTargetSource ts = ctx.getBean('urlMappingsTargetSource', HotSwappableTargetSource)
        ts.swap(urlMappingsHolder)

        LinkGenerator linkGenerator = ctx.getBean('grailsLinkGenerator', LinkGenerator)
        if (linkGenerator instanceof CachingLinkGenerator) {
            linkGenerator.clearCache()
        }
    }

    @CompileStatic
    private static UrlMappingsHolder createUrlMappingsHolder(ApplicationContext applicationContext) {
        def factory = new UrlMappingsHolderFactoryBean(applicationContext: applicationContext)
        factory.afterPropertiesSet()
        return factory.getObject()
    }

    @CompileDynamic
    static class DefaultUrlMappings {
        static mappings = {
            "/$controller/$action?/$id?(.$format)?"()
        }
    }
}

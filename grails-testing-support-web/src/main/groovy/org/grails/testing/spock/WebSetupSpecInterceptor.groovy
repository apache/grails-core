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

package org.grails.testing.spock

import grails.config.Settings
import grails.core.GrailsApplication
import grails.testing.web.GrailsWebUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.CamelCaseUrlConverter
import grails.web.HyphenatedUrlConverter
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.jsp.TagLibraryResolverImpl
import org.grails.plugins.codecs.CodecsGrailsPlugin
import org.grails.plugins.codecs.DefaultCodecLookup
import org.grails.plugins.converters.ConvertersGrailsPlugin
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.testing.runtime.support.GroovyPageUnitTestResourceLoader
import org.grails.testing.runtime.support.LazyTagLibraryLookup
import org.grails.validation.ConstraintEvalUtils
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.UrlMappingsHolderFactoryBean
import org.grails.web.pages.FilteringCodecsByContentTypeSettings
import org.grails.web.servlet.view.CompositeViewResolver
import org.grails.web.servlet.view.GroovyPageViewResolver
import org.grails.web.util.GrailsApplicationAttributes
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.springframework.util.ClassUtils
import org.springframework.web.multipart.support.StandardServletMultipartResolver
import org.springframework.web.servlet.i18n.SessionLocaleResolver

@CompileStatic
class WebSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        GrailsWebUnitTest test = (GrailsWebUnitTest) invocation.instance
        setup(test)
        invocation.proceed()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void setup(GrailsWebUnitTest test) {

        GrailsApplication grailsApplication = test.grailsApplication
        Map<String, String> groovyPages = test.views

        test.defineBeans(new ConvertersGrailsPlugin())

        def config = grailsApplication.config
        test.defineBeans {

            final classLoader = ControllerUnitTest.class.getClassLoader()

            boolean registerConstraintEvaluator
            if (ClassUtils.isPresent("grails.testing.gorm.DataTest", classLoader)) {
                Class clazz = classLoader.loadClass("grails.testing.gorm.DataTest")
                registerConstraintEvaluator = !clazz.isAssignableFrom(test.class)
            } else {
                registerConstraintEvaluator = true
            }

            if (registerConstraintEvaluator) {
                constraintRegistry(DefaultConstraintRegistry, ref("messageSource"))

                "org.grails.beans.ConstraintsEvaluator"(DefaultConstraintEvaluator, constraintRegistry, new KeyValueMappingContext("test"), ConstraintEvalUtils.getDefaultConstraints(grailsApplication.config))
            }

            rendererRegistry(DefaultRendererRegistry) {
                modelSuffix = config.getProperty('grails.scaffolding.templates.domainSuffix', '')
            }
            String urlConverterType = config.getProperty(Settings.WEB_URL_CONVERTER)
            "${grails.web.UrlConverter.BEAN_NAME}"('hyphenated' == urlConverterType ? HyphenatedUrlConverter : CamelCaseUrlConverter)

            grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean)

            grailsLinkGenerator(DefaultLinkGenerator, config?.grails?.serverURL ?: "http://localhost:8080")

            if (ClassUtils.isPresent("UrlMappings", classLoader)) {
                grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, classLoader.loadClass("UrlMappings"))
            }

            def urlMappingsClass = "${config.getProperty('grails.codegen.defaultPackage', 'null')}.UrlMappings"
            if (ClassUtils.isPresent(urlMappingsClass, classLoader)) {
                grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, classLoader.loadClass(urlMappingsClass))
            }

            try {
                Class viewResolver = classLoader.loadClass('grails.plugin.json.view.mvc.JsonViewResolver')
                jsonSmartViewResolver(viewResolver)
            } catch (ClassNotFoundException e) {
            }

            localeResolver(SessionLocaleResolver)
            multipartResolver(StandardServletMultipartResolver)

            "${CompositeViewResolver.BEAN_NAME}"(CompositeViewResolver)

            if (ClassUtils.isPresent("org.grails.plugins.web.GroovyPagesGrailsPlugin", classLoader)) {
                def lazyBean = { bean ->
                    bean.lazyInit = true
                }
                jspTagLibraryResolver(TagLibraryResolverImpl, lazyBean)
                gspTagLibraryLookup(LazyTagLibraryLookup, lazyBean)
                groovyPageUnitTestResourceLoader(GroovyPageUnitTestResourceLoader, groovyPages)
                groovyPageLocator(GrailsConventionGroovyPageLocator) {
                    resourceLoader = ref('groovyPageUnitTestResourceLoader')
                }
                groovyPagesTemplateEngine(GroovyPagesTemplateEngine) { bean ->
                    bean.lazyInit = true
                    tagLibraryLookup = ref("gspTagLibraryLookup")
                    jspTagLibraryResolver = ref("jspTagLibraryResolver")
                    groovyPageLocator = ref("groovyPageLocator")
                }

                groovyPagesTemplateRenderer(GroovyPagesTemplateRenderer) { bean ->
                    bean.lazyInit = true
                    groovyPageLocator = ref("groovyPageLocator")
                    groovyPagesTemplateEngine = ref("groovyPagesTemplateEngine")
                }

                // Configure a Spring MVC view resolver
                jspViewResolver(GroovyPageViewResolver) { bean ->
                    prefix = GrailsApplicationAttributes.PATH_TO_VIEWS
                    suffix = GroovyPageViewResolver.GSP_SUFFIX
                    templateEngine = groovyPagesTemplateEngine
                    groovyPageLocator = groovyPageLocator
                }
            }
            filteringCodecsByContentTypeSettings(FilteringCodecsByContentTypeSettings, grailsApplication)
            localeResolver(SessionLocaleResolver)
        }

        CodecsGrailsPlugin codecsGrailsPlugin = new CodecsGrailsPlugin()
        test.defineBeans(codecsGrailsPlugin)

        codecsGrailsPlugin.providedArtefacts.each { Class codecClass ->
            test.mockCodec(codecClass, false)
        }

        grailsApplication.mainContext.getBean(DefaultCodecLookup).reInitialize()
    }

}

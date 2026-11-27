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

import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import tools.jackson.databind.json.JsonMapper

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.util.ClassUtils
import org.springframework.validation.Validator
import org.springframework.web.ErrorResponse
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.HandlerMethodReturnValueHandler
import org.springframework.web.multipart.support.StandardServletMultipartResolver
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

import grails.config.Settings
import grails.core.GrailsApplication
import grails.testing.web.GrailsWebUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.CamelCaseUrlConverter
import grails.web.HyphenatedUrlConverter
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.jsp.TagLibraryResolverImpl
import org.grails.plugins.codecs.CodecsGrailsPlugin
import org.grails.plugins.codecs.DefaultCodecLookup
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.testing.GrailsApplicationBuilder
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

@CompileStatic
class WebSetupSpecInterceptor implements IMethodInterceptor {

    private static final String MVC_CONTENT_NEGOTIATION_MANAGER = 'mvcContentNegotiationManager'

    private static final String MVC_HANDLER_ADAPTER = 'requestMappingHandlerAdapter'

    private static final String CONTENT_NEGOTIATION_PROPERTIES = WebSetupSpecInterceptor.name + '.contentNegotiationProperties'

    private static final String SERVER_CONVERTERS_CUSTOMIZER =
            'org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer'

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        GrailsWebUnitTest test = (GrailsWebUnitTest) invocation.instance
        setup(test)
        invocation.proceed()
    }

    /**
     * Builds the message converters as Spring MVC does, since the slice does not start it: Spring's
     * server defaults for the classpath with JSON on Boot's mapper, Boot's converter customizers,
     * then the application's {@link WebMvcConfigurer} beans.
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    protected List<HttpMessageConverter<?>> messageConverters(GrailsWebUnitTest test) {
        def context = test.applicationContext
        JsonMapper mapper = context.getBeanProvider(JsonMapper).getIfUnique() ?:
                context.getBean('jacksonJsonMapper', JsonMapper)
        List<WebMvcConfigurer> configurers = context.getBeanProvider(WebMvcConfigurer).orderedStream().toList()
        List<HttpMessageConverter<?>> converters = []
        configurers.each { WebMvcConfigurer configurer -> configurer.configureMessageConverters(converters) }
        if (converters.isEmpty()) {
            HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer().registerDefaults()
                    .withJsonConverter(new JacksonJsonHttpMessageConverter(mapper))
            ClassLoader classLoader = ControllerUnitTest.getClassLoader()
            if (ClassUtils.isPresent(SERVER_CONVERTERS_CUSTOMIZER, classLoader)) {
                context.getBeanProvider(ClassUtils.forName(SERVER_CONVERTERS_CUSTOMIZER, classLoader))
                        .orderedStream().forEach { customizer -> customizer.customize(builder) }
            }
            configurers.each { WebMvcConfigurer configurer -> configurer.configureMessageConverters(builder) }
            builder.build().each { HttpMessageConverter<?> converter -> converters.add(converter) }
        }
        configurers.each { WebMvcConfigurer configurer -> configurer.extendMessageConverters(converters) }
        return converters
    }

    /**
     * Registers Spring MVC's content negotiation manager, which {@code respond} decides the type of a problem with, built
     * as Spring MVC builds it: from the formats of the message converters, then {@code spring.mvc.contentnegotiation.*}
     * and the test's {@link WebMvcConfigurer} beans, in the order Spring MVC applies them; unless the test declares a bean
     * of its name.
     */
    protected void registerContentNegotiationManager(GrailsWebUnitTest test, List<HttpMessageConverter<?>> converters) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) test.applicationContext
        if (context.containsBean(MVC_CONTENT_NEGOTIATION_MANAGER)) {
            return
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.beanFactory
        registry.registerBeanDefinition(CONTENT_NEGOTIATION_PROPERTIES, new RootBeanDefinition(ContentNegotiationPropertiesConfigurer,
                { -> new ContentNegotiationPropertiesConfigurer(context.environment) } as Supplier<ContentNegotiationPropertiesConfigurer>))
        registry.registerBeanDefinition(MVC_CONTENT_NEGOTIATION_MANAGER, new RootBeanDefinition(ContentNegotiationManager, { ->
            List<WebMvcConfigurer> configurers = context.getBeanProvider(WebMvcConfigurer).orderedStream().toList()
            new UnitTestWebMvcConfiguration(converters, configurers).mvcContentNegotiationManager()
        } as Supplier<ContentNegotiationManager>))
    }

    /**
     * Registers Spring MVC's handler adapter, which {@code respond} writes a {@code ProblemDetail} with, built as Spring
     * MVC builds it: with the message converters, the content negotiation manager and the test's {@link WebMvcConfigurer}
     * beans, so that the {@code ResponseBodyAdvice} and {@code ErrorResponse.Interceptor} beans the test declares apply as
     * in the application; unless the test declares a bean of its name. It is built when a response first needs it.
     */
    protected void registerHandlerAdapter(GrailsWebUnitTest test, List<HttpMessageConverter<?>> converters) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) test.applicationContext
        if (context.containsBean(MVC_HANDLER_ADAPTER)) {
            return
        }
        RootBeanDefinition definition = new RootBeanDefinition(RequestMappingHandlerAdapter, { ->
            List<WebMvcConfigurer> configurers = context.getBeanProvider(WebMvcConfigurer).orderedStream().toList()
            UnitTestWebMvcConfiguration mvc = new UnitTestWebMvcConfiguration(converters, configurers)
            mvc.applicationContext = context
            Validator validator = mvc.mvcValidator()
            if (validator instanceof InitializingBean) {
                ((InitializingBean) validator).afterPropertiesSet()
            }
            mvc.requestMappingHandlerAdapter(context.getBean(MVC_CONTENT_NEGOTIATION_MANAGER, ContentNegotiationManager),
                    mvc.mvcConversionService(), validator)
        } as Supplier<RequestMappingHandlerAdapter>)
        definition.lazyInit = true
        ((BeanDefinitionRegistry) context.beanFactory).registerBeanDefinition(MVC_HANDLER_ADAPTER, definition)
    }

    /** Spring MVC's configuration, as far as content negotiation and its handler adapter, with the converters and configurers of the test. */
    private static class UnitTestWebMvcConfiguration extends WebMvcConfigurationSupport {

        private final List<HttpMessageConverter<?>> converters

        private final List<WebMvcConfigurer> configurers

        UnitTestWebMvcConfiguration(List<HttpMessageConverter<?>> converters, List<WebMvcConfigurer> configurers) {
            this.converters = converters
            this.configurers = configurers
        }

        @Override
        protected HttpMessageConverters createMessageConverters() {
            List<HttpMessageConverter<?>> listed = converters
            return new HttpMessageConverters() {

                @Override
                boolean isEmpty() {
                    listed.isEmpty()
                }

                @Override
                Iterator<HttpMessageConverter<?>> iterator() {
                    listed.iterator()
                }
            }
        }

        @Override
        protected void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
            for (WebMvcConfigurer webMvcConfigurer : configurers) {
                webMvcConfigurer.configureContentNegotiation(configurer)
            }
        }

        @Override
        protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
            for (WebMvcConfigurer webMvcConfigurer : configurers) {
                webMvcConfigurer.addArgumentResolvers(argumentResolvers)
            }
        }

        @Override
        protected void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
            for (WebMvcConfigurer webMvcConfigurer : configurers) {
                webMvcConfigurer.addReturnValueHandlers(returnValueHandlers)
            }
        }

        @Override
        protected void configureErrorResponseInterceptors(List<ErrorResponse.Interceptor> interceptors) {
            for (WebMvcConfigurer webMvcConfigurer : configurers) {
                webMvcConfigurer.addErrorResponseInterceptors(interceptors)
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void setup(GrailsWebUnitTest test) {

        GrailsApplication grailsApplication = test.grailsApplication
        Map<String, String> groovyPages = test.views

        List<HttpMessageConverter<?>> converters = messageConverters(test)
        test.applicationContext.getBean(SpringMessageConverters).setConverters(converters)
        registerContentNegotiationManager(test, converters)
        registerHandlerAdapter(test, converters)

        def config = grailsApplication.config
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) test.applicationContext).beanFactory
        test.defineBeans {

            final classLoader = ControllerUnitTest.getClassLoader()

            boolean registerConstraintEvaluator
            if (ClassUtils.isPresent('grails.testing.gorm.DataTest', classLoader)) {
                Class clazz = classLoader.loadClass('grails.testing.gorm.DataTest')
                registerConstraintEvaluator = !clazz.isAssignableFrom(test.class)
            } else {
                registerConstraintEvaluator = true
            }

            if (registerConstraintEvaluator) {
                constraintRegistry(DefaultConstraintRegistry, ref('messageSource'))

                'org.grails.beans.ConstraintsEvaluator'(DefaultConstraintEvaluator, constraintRegistry, new KeyValueMappingContext('test'), ConstraintEvalUtils.getDefaultConstraints(grailsApplication.config))
            }

            String urlConverterType = config.getProperty(Settings.WEB_URL_CONVERTER)
            "${grails.web.UrlConverter.BEAN_NAME}"('hyphenated' == urlConverterType ? HyphenatedUrlConverter : CamelCaseUrlConverter)

            // A URL mappings holder and link generator the spec declares are kept; the URL mappings plugin's, which a spec
            // including the controllers or urlMappings plugin loads, are replaced, as in Grails 8, so that links follow the
            // application's UrlMappings and absolute links start with grails.serverURL or http://localhost:8080. The
            // plugin's holder takes the place of one the spec's configuration classes declare, so such a spec declares its
            // holder in doWithSpring or beanRegistrar()
            if (!GrailsApplicationBuilder.isDeclaredByTest(beanFactory, 'grailsUrlMappingsHolder')) {
                grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean)
            }

            if (!GrailsApplicationBuilder.isDeclaredByTest(beanFactory, 'grailsLinkGenerator')) {
                grailsLinkGenerator(DefaultLinkGenerator, config?.grails?.serverURL ?: 'http://localhost:8080')
            }

            if (ClassUtils.isPresent('UrlMappings', classLoader)) {
                grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, classLoader.loadClass('UrlMappings'))
            }

            def urlMappingsClass = "${config.getProperty('grails.codegen.defaultPackage', 'null')}.UrlMappings"
            if (ClassUtils.isPresent(urlMappingsClass, classLoader)) {
                grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, classLoader.loadClass(urlMappingsClass))
            }

            try {
                Class viewResolver = classLoader.loadClass('grails.plugin.json.view.mvc.JsonViewResolver')
                jsonSmartViewResolver(viewResolver)
            } catch (ClassNotFoundException ignored) { }

            multipartResolver(StandardServletMultipartResolver)

            "${CompositeViewResolver.BEAN_NAME}"(CompositeViewResolver)

            if (ClassUtils.isPresent('org.grails.plugins.web.GroovyPagesGrailsPlugin', classLoader)) {
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
                    tagLibraryLookup = ref('gspTagLibraryLookup')
                    jspTagLibraryResolver = ref('jspTagLibraryResolver')
                    groovyPageLocator = ref('groovyPageLocator')
                }

                groovyPagesTemplateRenderer(GroovyPagesTemplateRenderer) { bean ->
                    bean.lazyInit = true
                    groovyPageLocator = ref('groovyPageLocator')
                    groovyPagesTemplateEngine = ref('groovyPagesTemplateEngine')
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
            if (!test.applicationContext.containsBean('localeResolver')) {
                localeResolver(SessionLocaleResolver)
            }
        }

        CodecsGrailsPlugin codecsGrailsPlugin = new CodecsGrailsPlugin()
        test.defineBeans(codecsGrailsPlugin)

        codecsGrailsPlugin.providedArtefacts.each { Class codecClass ->
            test.mockCodec(codecClass, false)
        }

        grailsApplication.mainContext.getBean(DefaultCodecLookup).reInitialize()
    }

}

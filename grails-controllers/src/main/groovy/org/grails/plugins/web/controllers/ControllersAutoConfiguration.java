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

package org.grails.plugins.web.controllers;

import java.util.EnumSet;
import java.util.Properties;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.servlet.autoconfigure.HttpEncodingAutoConfiguration;
import org.springframework.boot.servlet.filter.OrderedCharacterEncodingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import grails.config.Settings;
import grails.core.GrailsApplication;
import org.grails.plugins.domain.DomainClassAutoConfiguration;
import org.grails.web.config.http.GrailsFilters;
import org.grails.web.errors.GrailsExceptionResolver;
import org.grails.web.filters.HiddenHttpMethodFilter;
import org.grails.web.servlet.mvc.GrailsDispatcherServlet;
import org.grails.web.servlet.mvc.GrailsWebRequestFilter;

@AutoConfiguration(
        before = {DispatcherServletAutoConfiguration.class, HttpEncodingAutoConfiguration.class, WebMvcAutoConfiguration.class},
        after = {DomainClassAutoConfiguration.class}
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ControllersAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ControllersAutoConfiguration.class);

    // Spring Boot's own hidden-method property, registering its OrderedHiddenHttpMethodFilter under the
    // same "hiddenHttpMethodFilter" bean name this auto-configuration uses.
    private static final String SPRING_HIDDEN_METHOD_FILTER_ENABLED = "spring.mvc.hiddenmethod.filter.enabled";

    @Value("${" + Settings.FILTER_ENCODING + ":utf-8}")
    private String filtersEncoding;

    @Value("${" + Settings.FILTER_FORCE_ENCODING + ":false}")
    private boolean filtersForceEncoding;

    @Value("${" + Settings.RESOURCES_CACHE_PERIOD + ":0}")
    private int resourcesCachePeriod;

    @Value("${" + Settings.RESOURCES_ENABLED + ":true}")
    private boolean resourcesEnabled;

    @Value("${" + Settings.RESOURCES_PATTERN + ":" + Settings.DEFAULT_RESOURCE_PATTERN + "}")
    private String resourcesPattern;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_LOCATION + ":#{null}}")
    private String uploadTmpDir;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_MAX_FILE_SIZE + ":128000}")
    private long maxFileSize;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_MAX_REQUEST_SIZE + ":128000}")
    private long maxRequestSize;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_FILE_SIZE_THRESHOLD + ":0}")
    private int fileSizeThreshold;

    @Value("${" + Settings.WEB_SERVLET_PATH + ":#{null}}")
    String grailsServletPath;

    @Bean
    @ConditionalOnMissingBean(CharacterEncodingFilter.class)
    public CharacterEncodingFilter characterEncodingFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        OrderedCharacterEncodingFilter characterEncodingFilter = new OrderedCharacterEncodingFilter();
        characterEncodingFilter.setEncoding(filtersEncoding);
        characterEncodingFilter.setForceEncoding(filtersForceEncoding);
        characterEncodingFilter.setOrder(GrailsFilters.CHARACTER_ENCODING_FILTER.getOrder());
        return characterEncodingFilter;
    }

    // Lets a browser form request PUT, PATCH or DELETE by POSTing a "_method" parameter or an
    // "X-HTTP-Method-Override" header. Enabled by default for backwards compatibility, and gated on
    // a Grails-owned property rather than Spring Boot's "spring.mvc.hiddenmethod.filter.enabled":
    // this is not Spring's filter, and it does not share its behaviour. Spring's is restricted to
    // PUT, PATCH and DELETE and reads the parameter only, while this one accepts any method name and
    // also trusts the header. Gated on the bean name as well as the filter type, so an application that
    // supplies either its own filter or its own registration under this name makes the framework one back
    // off cleanly rather than relying on bean-definition overriding.
    //
    // Backs off when Boot's property is explicitly enabled: Boot registers its filter under this same bean
    // name, and since bean-definition overriding is disabled by default the two definitions would otherwise
    // collide and fail application startup. An application that opts into Spring's filter gets Spring's
    // filter -- narrower than this one, and ordered by Boot rather than by GrailsFilters.
    @Bean
    @ConditionalOnMissingBean(value = HiddenHttpMethodFilter.class, name = "hiddenHttpMethodFilter")
    @ConditionalOnBooleanProperty(name = Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED, matchIfMissing = true)
    @ConditionalOnProperty(name = SPRING_HIDDEN_METHOD_FILTER_ENABLED, havingValue = "false", matchIfMissing = true)
    public FilterRegistrationBean<Filter> hiddenHttpMethodFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new HiddenHttpMethodFilter());
        registrationBean.addUrlPatterns(Settings.DEFAULT_WEB_SERVLET_PATH);
        registrationBean.setOrder(GrailsFilters.HIDDEN_HTTP_METHOD_FILTER.getOrder());
        return registrationBean;
    }

    // Warns, once at startup, that hidden HTTP method override has been switched off, so a deliberate
    // opt-out is visible rather than surfacing later as an unexplained 404: a "resources" mapping
    // generates PUT, PATCH and DELETE routes for /$controller/$id but no POST route, so a scaffolded
    // form that relied on "_method" no longer matches any mapping.
    @Bean
    @ConditionalOnMissingBean(HiddenHttpMethodFilter.class)
    @ConditionalOnBooleanProperty(name = Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED, havingValue = false)
    @ConditionalOnProperty(name = SPRING_HIDDEN_METHOD_FILTER_ENABLED, havingValue = "false", matchIfMissing = true)
    public HiddenHttpMethodDisabledWarning hiddenHttpMethodDisabledWarning() {
        logger.warn("Hidden HTTP method override is disabled ({}=false): a POST carrying a '{}' " +
                        "parameter or an '{}' header is no longer rewritten to PUT, PATCH or DELETE. " +
                        "Forms rendered by <g:form method=\"PUT|PATCH|DELETE\"> will not match a " +
                        "'resources' URL mapping. Clients must send the real HTTP method instead.",
                Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED,
                HiddenHttpMethodFilter.DEFAULT_METHOD_PARAM,
                HiddenHttpMethodFilter.HEADER_X_HTTP_METHOD_OVERRIDE);
        return new HiddenHttpMethodDisabledWarning();
    }

    // Marker bean whose creation emits the startup warning above; present only when hidden HTTP
    // method override has been explicitly disabled.
    public static final class HiddenHttpMethodDisabledWarning {
    }

    // Auto-configured rather than registered by the plugin descriptor so an application- or
    // plugin-defined 'exceptionHandler' backs this default off instead of overriding it.
    @Bean(GrailsApplication.EXCEPTION_HANDLER_BEAN)
    @ConditionalOnMissingBean(name = GrailsApplication.EXCEPTION_HANDLER_BEAN)
    public GrailsExceptionResolver exceptionHandler() {
        GrailsExceptionResolver exceptionResolver = new GrailsExceptionResolver();
        Properties exceptionMappings = new Properties();
        exceptionMappings.setProperty("java.lang.Exception", "/error");
        exceptionResolver.setExceptionMappings(exceptionMappings);
        return exceptionResolver;
    }

    // GrailsWebRequestFilter extends RequestContextFilter, so Boot's WebMvcAutoConfiguration backs off
    // its own RequestContextFilter and the GrailsWebRequest stays bound. Also gated on the
    // "grailsWebRequestFilter" registration bean name so an application overriding only that registration
    // makes this raw filter back off with it, rather than leaving a duplicate filter on the chain.
    @Bean
    @ConditionalOnMissingBean(value = GrailsWebRequestFilter.class, name = "grailsWebRequestFilter")
    public GrailsWebRequestFilter grailsWebRequest(ApplicationContext applicationContext) {
        GrailsWebRequestFilter grailsWebRequestFilter = new GrailsWebRequestFilter();
        grailsWebRequestFilter.setApplicationContext(applicationContext);
        return grailsWebRequestFilter;
    }

    @Bean
    @ConditionalOnMissingBean(name = "grailsWebRequestFilter")
    public FilterRegistrationBean<GrailsWebRequestFilter> grailsWebRequestFilter(GrailsWebRequestFilter grailsWebRequest) {
        FilterRegistrationBean<GrailsWebRequestFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(grailsWebRequest);
        registrationBean.setDispatcherTypes(EnumSet.of(
                DispatcherType.FORWARD,
                DispatcherType.INCLUDE,
                DispatcherType.REQUEST)
        );
        registrationBean.addUrlPatterns(Settings.DEFAULT_WEB_SERVLET_PATH);
        registrationBean.setOrder(GrailsFilters.GRAILS_WEB_REQUEST_FILTER.getOrder());
        return registrationBean;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        if (uploadTmpDir == null) {
            uploadTmpDir = System.getProperty("java.io.tmpdir");
        }
        return new MultipartConfigElement(uploadTmpDir, maxFileSize, maxRequestSize, fileSizeThreshold);
    }

    @Bean
    public DispatcherServlet dispatcherServlet() {
        return new GrailsDispatcherServlet();
    }

    @Bean
    public DispatcherServletRegistrationBean dispatcherServletRegistration(GrailsApplication application, DispatcherServlet dispatcherServlet, MultipartConfigElement multipartConfigElement) {
        if (grailsServletPath == null) {
            boolean isTomcat = ClassUtils.isPresent("org.apache.catalina.startup.Tomcat", application.getClassLoader());
            grailsServletPath = isTomcat ? Settings.DEFAULT_TOMCAT_SERVLET_PATH : Settings.DEFAULT_WEB_SERVLET_PATH;
        }
        DispatcherServletRegistrationBean dispatcherServletRegistration = new DispatcherServletRegistrationBean(dispatcherServlet, grailsServletPath);
        dispatcherServletRegistration.setLoadOnStartup(2);
        dispatcherServletRegistration.setAsyncSupported(true);
        dispatcherServletRegistration.setMultipartConfig(multipartConfigElement);
        return dispatcherServletRegistration;
    }

    @Bean
    @ConditionalOnMissingBean(GrailsWebMvcConfigurer.class)
    public GrailsWebMvcConfigurer webMvcConfig() {
        return new GrailsWebMvcConfigurer(resourcesCachePeriod, resourcesEnabled, resourcesPattern);
    }

    static class GrailsWebMvcConfigurer implements WebMvcConfigurer {

        private static final String[] SERVLET_RESOURCE_LOCATIONS = new String[] { "/" };

        private static final String[] CLASSPATH_RESOURCE_LOCATIONS = new String[] {
            "classpath:/META-INF/resources/", "classpath:/resources/",
            "classpath:/static/", "classpath:/public/"
        };

        private static final String[] RESOURCE_LOCATIONS;

        static {
            RESOURCE_LOCATIONS = new String[CLASSPATH_RESOURCE_LOCATIONS.length +
                    SERVLET_RESOURCE_LOCATIONS.length];
            System.arraycopy(SERVLET_RESOURCE_LOCATIONS, 0, RESOURCE_LOCATIONS, 0,
                    SERVLET_RESOURCE_LOCATIONS.length);
            System.arraycopy(CLASSPATH_RESOURCE_LOCATIONS, 0, RESOURCE_LOCATIONS,
                    SERVLET_RESOURCE_LOCATIONS.length, CLASSPATH_RESOURCE_LOCATIONS.length);
        }

        boolean addMappings;
        Integer cachePeriod;
        String resourcesPattern;

        GrailsWebMvcConfigurer(Integer cachePeriod, Boolean addMappings, String resourcesPattern) {
            this.addMappings = addMappings;
            this.cachePeriod = cachePeriod;
            this.resourcesPattern = resourcesPattern;
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            if (!addMappings) {
                return;
            }

            if (!registry.hasMappingForPattern("/webjars/**")) {
                registry.addResourceHandler("/webjars/**")
                        .addResourceLocations("classpath:/META-INF/resources/webjars/")
                        .setCachePeriod(cachePeriod);
            }
            if (!registry.hasMappingForPattern(resourcesPattern)) {
                registry.addResourceHandler(resourcesPattern)
                        .addResourceLocations(RESOURCE_LOCATIONS)
                        .setCachePeriod(cachePeriod);
            }
        }
    }
}

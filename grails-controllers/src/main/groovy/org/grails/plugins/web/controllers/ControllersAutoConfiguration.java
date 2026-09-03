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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.servlet.autoconfigure.HttpEncodingAutoConfiguration;
import org.springframework.boot.servlet.filter.OrderedCharacterEncodingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
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
import org.grails.web.servlet.mvc.GrailsDispatcherServlet;
import org.grails.web.servlet.mvc.GrailsWebRequestFilter;
import org.grails.web.util.HiddenHttpMethod;

@AutoConfiguration(
        before = {DispatcherServletAutoConfiguration.class, HttpEncodingAutoConfiguration.class, WebMvcAutoConfiguration.class},
        after = {DomainClassAutoConfiguration.class}
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ControllersAutoConfiguration {

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
    public DispatcherServlet dispatcherServlet(Environment environment) {
        GrailsDispatcherServlet dispatcherServlet = new GrailsDispatcherServlet();
        // Without a servlet filter doing the rewrite, the override is resolved here instead: after multipart
        // handling and after the filter chain, rather than ahead of both.
        dispatcherServlet.setResolveHiddenHttpMethod(!HiddenHttpMethod.isServletFilterMode(environment));
        return dispatcherServlet;
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

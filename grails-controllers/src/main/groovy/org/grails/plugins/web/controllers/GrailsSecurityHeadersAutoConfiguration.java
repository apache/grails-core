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

import jakarta.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import org.grails.web.config.http.GrailsFilters;

/**
 * Registers {@link GrailsSecurityHeadersFilter} to apply baseline browser-hardening
 * response headers.
 *
 * <p>Backs off entirely when Spring Security's header-writing infrastructure
 * ({@code HeaderWriterFilter}) is on the classpath: that filter chain runs after this
 * one would and only writes a header when it is still absent, so an eagerly-applied
 * Grails default would silently win over an application's explicit Spring Security
 * header configuration. Spring Security already ships secure header defaults of its
 * own, so this auto-configuration only fills the gap for applications that don't have
 * it.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "grails.security.headers.enabled", matchIfMissing = true)
@ConditionalOnMissingClass("org.springframework.security.web.header.HeaderWriterFilter")
@EnableConfigurationProperties(GrailsSecurityHeadersProperties.class)
public class GrailsSecurityHeadersAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(value = GrailsSecurityHeadersFilter.class, name = "grailsSecurityHeadersFilter")
    public GrailsSecurityHeadersFilter securityHeadersFilter(GrailsSecurityHeadersProperties properties) {
        return new GrailsSecurityHeadersFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "grailsSecurityHeadersFilter")
    public FilterRegistrationBean<GrailsSecurityHeadersFilter> grailsSecurityHeadersFilter(
            GrailsSecurityHeadersFilter securityHeadersFilter) {
        FilterRegistrationBean<GrailsSecurityHeadersFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(securityHeadersFilter);
        registrationBean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD,
                DispatcherType.INCLUDE, DispatcherType.ERROR));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(GrailsFilters.LAST.getOrder());
        return registrationBean;
    }
}

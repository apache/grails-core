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

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import org.grails.web.config.http.GrailsFilters;

/**
 * Registers {@link GrailsSecurityHeadersFilter} to apply baseline browser-hardening
 * response headers. The filter writes at response commit time and only fills headers
 * that are still absent, so it coexists with Spring Security's header writers and any
 * other filter or controller that sets these headers itself.
 *
 * <p>The filter is registered at {@link GrailsFilters#FIRST}, the outermost Grails slot.
 * Commit-time writers nest, and the innermost fires first, so the outermost one is the
 * last to write and therefore the one that only fills gaps; that is what lets every
 * filter inside it (Spring Security at Spring Boot's default filter order, SiteMesh, an
 * application filter) win. Being outermost among the Grails filters also means a filter
 * that serves the response itself without continuing the chain, as the asset-pipeline
 * filter does for static assets, still passes through this filter's response wrapper and
 * receives the headers.</p>
 *
 * <p>Filters ordered ahead of {@link GrailsFilters#FIRST} run outside this one. Spring
 * Boot's forwarded-header filter and, in a WAR deployment, its error-page filter are such
 * filters, as is a Spring Security chain whose {@code spring.security.filter.order} is set
 * below {@link GrailsFilters#FIRST}. In that arrangement Spring Security's writers run after the
 * Grails defaults have been written, and those of them that only fill absent headers
 * (every writer but {@code XFrameOptionsHeaderWriter}) leave the Grails value in place;
 * disable the corresponding {@code grails.security.headers.<header>} to let such a writer
 * own the header.</p>
 *
 * <p>Both beans back off when the application declares its own
 * {@link GrailsSecurityHeadersFilter} bean, a bean named {@code grailsSecurityHeadersFilter},
 * or a {@link FilterRegistrationBean} whose declared filter type is
 * {@link GrailsSecurityHeadersFilter}.</p>
 *
 * @since 8.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "grails.security.headers.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GrailsSecurityHeadersProperties.class)
public class GrailsSecurityHeadersAutoConfiguration {

    static final String FORWARD_HEADERS_STRATEGY = "server.forward-headers-strategy";

    @Bean
    @ConditionalOnMissingBean(value = GrailsSecurityHeadersFilter.class, name = "grailsSecurityHeadersFilter")
    @Conditional(OnMissingSecurityHeadersFilterRegistration.class)
    public GrailsSecurityHeadersFilter securityHeadersFilter(GrailsSecurityHeadersProperties properties,
            Environment environment) {
        return new GrailsSecurityHeadersFilter(properties, isReverseProxyConfigured(environment));
    }

    /**
     * Whether the deployment declares itself to be behind a reverse proxy: either a
     * forwarded-headers strategy is configured (in which case the forwarding filter or
     * valve strips the forwarded request headers before this filter could see them), or
     * Spring Boot detected a cloud platform, where ingress through a proxy is the norm.
     */
    static boolean isReverseProxyConfigured(Environment environment) {
        String strategy = environment.getProperty(FORWARD_HEADERS_STRATEGY);
        if (strategy != null && !"none".equalsIgnoreCase(strategy.trim())) {
            return true;
        }
        CloudPlatform platform = CloudPlatform.getActive(environment);
        return platform != null && platform != CloudPlatform.NONE;
    }

    @Bean
    @ConditionalOnMissingBean(name = "grailsSecurityHeadersFilter")
    @Conditional(OnMissingSecurityHeadersFilterRegistration.class)
    public FilterRegistrationBean<GrailsSecurityHeadersFilter> grailsSecurityHeadersFilter(
            GrailsSecurityHeadersFilter securityHeadersFilter) {
        FilterRegistrationBean<GrailsSecurityHeadersFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(securityHeadersFilter);
        registrationBean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD,
                DispatcherType.INCLUDE, DispatcherType.ERROR));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(GrailsFilters.FIRST.getOrder());
        return registrationBean;
    }

    /**
     * Matches when no {@link FilterRegistrationBean} declared for
     * {@link GrailsSecurityHeadersFilter} exists under any bean name. Only registrations
     * whose generic filter type is declared (a {@code @Bean} method returning
     * {@code FilterRegistrationBean<GrailsSecurityHeadersFilter>}) are visible without
     * instantiating the bean; a raw {@code FilterRegistrationBean} is recognised only by
     * the {@code grailsSecurityHeadersFilter} name.
     */
    static final class OnMissingSecurityHeadersFilterRegistration extends SpringBootCondition {

        private static final ResolvableType REGISTRATION_TYPE =
                ResolvableType.forClassWithGenerics(FilterRegistrationBean.class, GrailsSecurityHeadersFilter.class);

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ConditionMessage.Builder message = ConditionMessage.forCondition("GrailsSecurityHeadersFilter registration");
            ListableBeanFactory beanFactory = context.getBeanFactory();
            if (beanFactory == null) {
                return ConditionOutcome.match(message.because("no bean factory to inspect"));
            }
            String[] registrations = beanFactory.getBeanNamesForType(REGISTRATION_TYPE, true, false);
            if (registrations.length == 0) {
                return ConditionOutcome.match(message.didNotFind("a FilterRegistrationBean for GrailsSecurityHeadersFilter")
                        .atAll());
            }
            return ConditionOutcome.noMatch(message.found("FilterRegistrationBean for GrailsSecurityHeadersFilter")
                    .items(ConditionMessage.Style.QUOTE, (Object[]) registrations));
        }
    }
}

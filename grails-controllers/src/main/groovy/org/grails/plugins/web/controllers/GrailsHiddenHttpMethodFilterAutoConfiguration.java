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

import jakarta.servlet.Filter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;

import grails.config.Settings;
import org.grails.web.config.http.GrailsFilters;
import org.grails.web.filters.HiddenHttpMethodFilter;
import org.grails.web.util.HiddenHttpMethod;

/**
 * Registers Grails' hidden HTTP method filter, which rewrites a {@code POST} carrying a {@code _method}
 * parameter or an {@code X-HTTP-Method-Override} header before the request reaches the dispatcher.
 *
 * <p>Neither this filter nor Spring Boot's is registered by default as of Grails 8; the {@code _method}
 * parameter is resolved inside the dispatcher instead, after multipart handling and after the filter chain.
 * A filter is contributed only when an application asks for one, through either
 * {@link Settings#WEB_HIDDEN_METHOD_FILTER_ENABLED} or Spring Boot's
 * {@code spring.mvc.hiddenmethod.filter.enabled}.
 *
 * <p>Ordered after {@link WebMvcAutoConfiguration} so that Boot's own filter, when it registers one, is
 * already present and this backs off through {@link ConditionalOnMissingBean} — which also avoids the two
 * definitions colliding on the shared {@code hiddenHttpMethodFilter} bean name. Filling the gap matters:
 * Boot's filter lives on {@link WebMvcAutoConfiguration}, which backs off entirely for an application
 * declaring {@code @EnableWebMvc}, so asking for Boot's filter there would otherwise yield no filter at all.
 * Contributing one keeps the promise that
 * {@link org.grails.web.util.HiddenHttpMethod#isServletFilterMode} relies on: whenever either property is
 * set, a filter really is on the chain.
 *
 * @since 8.0
 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GrailsHiddenHttpMethodFilterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(
            value = {org.springframework.web.filter.HiddenHttpMethodFilter.class, HiddenHttpMethodFilter.class},
            name = "hiddenHttpMethodFilter")
    @ConditionalOnExpression("${" + Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED + ":false} " +
            "or ${" + HiddenHttpMethod.SPRING_FILTER_ENABLED + ":false}")
    public FilterRegistrationBean<Filter> hiddenHttpMethodFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new HiddenHttpMethodFilter());
        registrationBean.addUrlPatterns(Settings.DEFAULT_WEB_SERVLET_PATH);
        registrationBean.setOrder(GrailsFilters.HIDDEN_HTTP_METHOD_FILTER.getOrder());
        return registrationBean;
    }
}

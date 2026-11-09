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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.servlet.filter.OrderedFormContentFilter;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.FormContentFilter;

/**
 * Guarantees a {@link FormContentFilter} for every Grails servlet web application, so
 * {@code application/x-www-form-urlencoded} bodies of {@code PUT}, {@code PATCH} and {@code DELETE}
 * requests are parsed into request parameters and are visible through the standard
 * {@code request.getParameter(...)} API. Only that content type is affected — other request bodies
 * (JSON, multipart, etc.) are untouched and remain available to data binding.
 *
 * Spring Boot's {@link WebMvcAutoConfiguration} already contributes an {@code OrderedFormContentFilter}
 * for a default Grails application, in which case this auto-configuration backs off. It only fills the
 * gap when Boot's is absent — most notably when the application declares {@code @EnableWebMvc}, which
 * backs off the whole {@link WebMvcAutoConfiguration}. Grails supplies the filter itself so that form
 * parsing does not depend on Boot's MVC auto-configuration being active.
 *
 * <p>Ordered after {@link WebMvcAutoConfiguration} so Boot's filter, when present, takes precedence and
 * this one backs off via {@link ConditionalOnMissingBean}. The explicit opt-out
 * {@code spring.mvc.formcontent.filter.enabled=false} disables the filter here just as it does for Boot,
 * so it remains a true off-switch rather than something Grails silently re-enables.
 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GrailsFormContentFilterAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GrailsFormContentFilterAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(FormContentFilter.class)
    @ConditionalOnBooleanProperty(name = "spring.mvc.formcontent.filter.enabled", matchIfMissing = true)
    public OrderedFormContentFilter formContentFilter() {
        return new OrderedFormContentFilter();
    }

    /**
     * Warns, once at startup, that form-content parsing has been switched off. Registered only when
     * {@code spring.mvc.formcontent.filter.enabled=false} is set and no {@link FormContentFilter} is
     * present, so a deliberate opt-out is visible rather than silently leaving PUT/PATCH/DELETE form
     * bodies unparsed — for controllers or for a plugin such as Spring Security.
     */
    @Bean
    @ConditionalOnMissingBean(FormContentFilter.class)
    @ConditionalOnProperty(name = "spring.mvc.formcontent.filter.enabled", havingValue = "false")
    public FormContentParsingDisabledWarning formContentParsingDisabledWarning() {
        logger.warn("Form-content parsing is disabled (spring.mvc.formcontent.filter.enabled=false): " +
                "form-encoded PUT, PATCH and DELETE request bodies will not be parsed into request " +
                "parameters, for controllers or for the Spring Security filter chain.");
        return new FormContentParsingDisabledWarning();
    }

    /**
     * Marker bean whose creation emits the startup warning above; present only when the form-content
     * filter has been explicitly disabled.
     */
    public static final class FormContentParsingDisabledWarning {
    }
}

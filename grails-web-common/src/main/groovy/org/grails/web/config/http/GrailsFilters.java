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
package org.grails.web.config.http;

import org.springframework.core.Ordered;

/**
 * The orders of the servlet filters in a Grails application, listed in the order they run: those Grails and the
 * plugins in the Grails repository register, and the slots Grails gives to Spring Boot's own. Place a filter of
 * your own relative to them, for example at {@code GrailsFilters.ASSET_PIPELINE_FILTER.getOrder() + 1} to run right
 * after asset-pipeline's filter. To run right after Spring Boot's character encoding filter, use
 * {@code Ordered.HIGHEST_PRECEDENCE + 1}.
 *
 * <p>Several filters share {@link Ordered#HIGHEST_PRECEDENCE}. Among filters of the same order, Spring Boot runs
 * the ones registered through a {@code FilterRegistrationBean} first and plain {@code Filter} beans after them;
 * within each kind, the order their beans are defined in decides.</p>
 *
 * <p>The filters from {@link #FIRST} to {@link #LAST} run before the Spring Security filter chain.</p>
 *
 * @since 7.0
 */
public enum GrailsFilters {

    /**
     * The startup progress filter, a {@code FilterRegistrationBean}. It answers requests while the application
     * starts, without passing them on.
     */
    STARTUP_PROGRESS_FILTER(Ordered.HIGHEST_PRECEDENCE),

    /**
     * The latency filter of {@code grails-testing-support-latency}, a {@code FilterRegistrationBean}, so that the
     * delay it adds covers the whole request.
     */
    LATENCY_FILTER(Ordered.HIGHEST_PRECEDENCE),

    /**
     * Grails' CORS filter, a plain {@code Filter} bean, registered unless {@code grails.cors.filter} is false. It
     * reads no request parameters.
     */
    CORS_FILTER(Ordered.HIGHEST_PRECEDENCE),

    /**
     * The order of the character encoding filter, {@link Ordered#HIGHEST_PRECEDENCE}. Spring Boot registers its
     * encoding filter at this order, as a plain {@code Filter} bean, so of the filters at this order only those
     * registered through a {@code FilterRegistrationBean} run before it.
     *
     * @deprecated since 9.0, for removal. Grails no longer registers a character encoding filter of its own; order a
     * filter after Spring Boot's with {@code Ordered.HIGHEST_PRECEDENCE + 1}.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    CHARACTER_ENCODING_FILTER(Ordered.HIGHEST_PRECEDENCE),

    /**
     * The CAS plugin's single sign-out filter, right after the character encoding filter, because it reads the
     * {@code logoutRequest} parameter of every POST.
     */
    CAS_SINGLE_SIGN_OUT_FILTER(Ordered.HIGHEST_PRECEDENCE + 1),

    /**
     * Spring's {@code FormContentFilter}, which parses the form of a PUT, PATCH or DELETE request into request
     * parameters: Spring Boot's, at its default order, or Grails' at the same order when Spring Boot registers none.
     */
    FORM_CONTENT_FILTER(-9900),

    /**
     * The first of the Grails filter slots. A filter at this order runs ahead of the other Grails filters, and after
     * the character encoding filter, with the request encoding already set. Grails' security headers filter is
     * registered here.
     */
    FIRST(-200),

    ASSET_PIPELINE_FILTER(-190),
    HIDDEN_HTTP_METHOD_FILTER(-170),
    SITEMESH_FILTER(-160),
    GRAILS_WEB_REQUEST_FILTER(-150),
    LAST(GrailsFilterOrder.DEFAULT_FILTER_ORDER - 10),

    /**
     * The Spring Security filter chain, registered by the Spring Security plugin.
     */
    SPRING_SECURITY_FILTER_CHAIN(GrailsFilterOrder.DEFAULT_FILTER_ORDER);

    private final int order;

    GrailsFilters(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }

}

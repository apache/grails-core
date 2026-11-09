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
package org.grails.web.util;

import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.core.env.PropertyResolver;
import org.springframework.http.HttpMethod;

import grails.config.Settings;

/**
 * Resolves the hidden HTTP method override a browser form requests through a {@code _method} parameter.
 *
 * <p>Browsers submit only {@code GET} and {@code POST}, so a form that needs to reach a {@code PUT},
 * {@code PATCH} or {@code DELETE} route names the method in a request parameter instead. This is the same
 * convention {@code org.grails.web.filters.HiddenHttpMethodFilter} implements, applied inside the dispatcher
 * rather than ahead of it — deliberately narrower than the filter, which accepts any method name and also
 * trusts an {@code X-HTTP-Method-Override} header.
 *
 * @since 8.0
 */
public final class HiddenHttpMethod {

    /** Default method parameter: <code>_method</code> */
    public static final String DEFAULT_METHOD_PARAM = "_method";

    /** Spring Boot's equivalent of {@link Settings#WEB_HIDDEN_METHOD_FILTER_ENABLED}, also false by default. */
    public static final String SPRING_FILTER_ENABLED = "spring.mvc.hiddenmethod.filter.enabled";

    /**
     * Request attribute carrying the method a request asked to be treated as, published by the dispatcher
     * when it resolves an override.
     */
    public static final String OVERRIDDEN_METHOD_ATTRIBUTE = HiddenHttpMethod.class.getName() + ".METHOD";

    /**
     * The only methods a form may ask for: the three a browser cannot submit itself. Matches the set
     * Spring's own {@code HiddenHttpMethodFilter} permits, so a POST can never be turned into a GET.
     */
    private static final Set<String> OVERRIDABLE_METHODS =
            Set.of(HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

    private HiddenHttpMethod() {
    }

    /**
     * Whether a servlet filter rewrites the request method, rather than it being resolved inside the
     * dispatcher. True when either this application or Spring Boot has asked for a filter.
     * <p>
     * Whenever this returns true a filter really is on the chain, so callers need not check the context for
     * one - see {@code GrailsHiddenHttpMethodFilterAutoConfiguration}, which holds that invariant up.
     *
     * @param properties the environment or configuration to read
     * @return true when a servlet filter performs the override
     */
    public static boolean isServletFilterMode(PropertyResolver properties) {
        return properties.getProperty(Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED, Boolean.class, Boolean.FALSE) ||
                properties.getProperty(SPRING_FILTER_ENABLED, Boolean.class, Boolean.FALSE);
    }

    /**
     * The method this request is being handled as: the override the dispatcher resolved, when there was one,
     * and otherwise the request's own method.
     * <p>
     * Use this wherever a decision depends on the method the handler was selected for -- {@code
     * allowedMethods}, for instance -- rather than on the method the client actually sent. A servlet filter
     * doing the override rewrites {@link HttpServletRequest#getMethod()} and this returns the same answer,
     * so it is correct in either mode.
     *
     * @param request the current request
     * @return the effective method name, never {@code null}
     */
    public static String effectiveMethod(HttpServletRequest request) {
        Object overridden = request.getAttribute(OVERRIDDEN_METHOD_ATTRIBUTE);
        return overridden instanceof String method ? method : request.getMethod();
    }

    /**
     * The method this request asks to be treated as, or {@code null} when it asks for nothing: it is not a
     * POST, carries no {@code _method} parameter, or names a method that may not be requested this way.
     *
     * @param request the current request
     * @return the overriding method name in upper case, or {@code null}
     */
    public static String resolveOverride(HttpServletRequest request) {
        if (!HttpMethod.POST.name().equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String requested = request.getParameter(DEFAULT_METHOD_PARAM);
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String candidate = requested.toUpperCase(Locale.ROOT);
        return OVERRIDABLE_METHODS.contains(candidate) ? candidate : null;
    }

    /**
     * Wraps a request so that {@link HttpServletRequest#getMethod()} reports the overriding method, leaving
     * everything else delegating to the wrapped request.
     *
     * @param method the overriding method name
     * @param request the request to wrap
     * @return the wrapped request
     */
    public static HttpServletRequest wrap(String method, HttpServletRequest request) {
        return new HttpMethodRequestWrapper(method, request);
    }

    private static final class HttpMethodRequestWrapper extends HttpServletRequestWrapper {

        private final String method;

        private HttpMethodRequestWrapper(String method, HttpServletRequest request) {
            super(request);
            this.method = method;
        }

        @Override
        public String getMethod() {
            return this.method;
        }
    }
}

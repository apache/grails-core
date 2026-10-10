/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.web.controllers;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ForwardedHeaderUtils;

/**
 * Applies baseline browser hardening response headers.
 *
 * <p>Headers are written immediately before the response commits (or when the filter
 * chain returns, whichever comes first) and only when nothing further down the chain
 * has already set them, so a controller, an interceptor, another filter or Spring
 * Security's header writers running inside this filter win over the Grails defaults.
 * A filter that runs <em>outside</em> this one and, like most of Spring Security's header
 * writers, only fills headers that are still absent finds the Grails value already in
 * place; see {@link GrailsSecurityHeadersAutoConfiguration} for the ordering.</p>
 *
 * <p>When {@code grails.security.headers.defaults} is {@code auto} and the request is
 * detected as having come through a reverse proxy, the built-in default values are
 * suppressed and only headers the application configured explicitly are applied, for
 * deployments where the proxy owns these headers and the application cannot see what it
 * adds. See {@link GrailsSecurityHeadersProperties.Defaults} for the controlling
 * setting.</p>
 *
 * <p>When {@link GrailsSecurityHeadersProperties#isEnabled()} is {@code false} the filter
 * passes every request through untouched. The auto-configuration does not register the
 * filter at all in that case; the check here covers filters an application constructs
 * itself.</p>
 *
 * @since 8.0
 */
public class GrailsSecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * Request headers whose presence indicates the request was relayed by a reverse
     * proxy or load balancer.
     */
    static final List<String> REVERSE_PROXY_REQUEST_HEADERS = List.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host", "Via", "X-Real-IP");

    private static final Logger logger = LoggerFactory.getLogger(GrailsSecurityHeadersFilter.class);

    private static final String HTTPS = "https";

    private final GrailsSecurityHeadersProperties properties;

    private final boolean reverseProxyConfigured;

    private final AtomicBoolean reverseProxyLogged = new AtomicBoolean();

    private final Set<String> blankValueLogged = ConcurrentHashMap.newKeySet();

    public GrailsSecurityHeadersFilter(GrailsSecurityHeadersProperties properties) {
        this(properties, false);
    }

    /**
     * @param properties the header configuration
     * @param reverseProxyConfigured whether the deployment is known to sit behind a reverse
     * proxy from configuration alone (for example a forwarded-headers strategy or an active
     * cloud platform), independent of any per-request signal
     */
    public GrailsSecurityHeadersFilter(GrailsSecurityHeadersProperties properties, boolean reverseProxyConfigured) {
        this.properties = properties;
        this.reverseProxyConfigured = reverseProxyConfigured;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!this.properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean applyDefaults = shouldApplyDefaults(request);
        boolean secure = isEnabled(this.properties.getHsts()) && isSecure(request);
        SecurityHeadersResponseWrapper wrapped = new SecurityHeadersResponseWrapper(response,
                () -> writeHeaders(response, applyDefaults, secure));
        try {
            filterChain.doFilter(request, wrapped);
        }
        finally {
            wrapped.beforeCommit();
        }
    }

    private void writeHeaders(HttpServletResponse response, boolean applyDefaults, boolean secure) {
        applyHeader(response, "X-Content-Type-Options", "content-type-options",
                this.properties.getContentTypeOptions(), applyDefaults);
        applyHeader(response, "X-Frame-Options", "frame-options", this.properties.getFrameOptions(), applyDefaults);
        applyHeader(response, "Referrer-Policy", "referrer-policy", this.properties.getReferrerPolicy(), applyDefaults);
        applyHeader(response, "X-XSS-Protection", "xss-protection", this.properties.getXssProtection(), applyDefaults);
        if (secure) {
            applyHeader(response, "Strict-Transport-Security", "hsts", this.properties.getHsts(), applyDefaults);
        }
        applyHeader(response, "Content-Security-Policy", "content-security-policy",
                this.properties.getContentSecurityPolicy(), applyDefaults);
    }

    private boolean shouldApplyDefaults(HttpServletRequest request) {
        return switch (this.properties.getDefaults()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> !isBehindReverseProxy(request);
        };
    }

    private boolean isBehindReverseProxy(HttpServletRequest request) {
        String signal = this.reverseProxyConfigured ? "server configuration" : detectReverseProxyHeader(request);
        if (signal == null) {
            return false;
        }
        if (this.reverseProxyLogged.compareAndSet(false, true)) {
            logger.info("Reverse proxy detected via {} and grails.security.headers.defaults is 'auto': the default " +
                    "Grails security headers are not applied and only explicitly configured " +
                    "grails.security.headers.* values are sent. Set grails.security.headers.defaults to 'always' " +
                    "to apply the defaults behind the proxy, or to 'never' to rely solely on explicit " +
                    "configuration.", signal);
        }
        return true;
    }

    private static String detectReverseProxyHeader(HttpServletRequest request) {
        for (String name : REVERSE_PROXY_REQUEST_HEADERS) {
            if (StringUtils.hasText(request.getHeader(name))) {
                return name + " request header";
            }
        }
        return null;
    }

    /**
     * Whether the client connection is secure. Falls back to the scheme a reverse proxy
     * forwards in the RFC 7239 {@code Forwarded} header or in {@code X-Forwarded-Proto}
     * (and {@code X-Forwarded-Ssl}), read the same way Spring's {@code ForwardedHeaderFilter}
     * reads them, when the container itself saw plain HTTP. That is the case behind a
     * TLS-terminating proxy that has not been configured through
     * {@code server.forward-headers-strategy}. Trusting the forwarded scheme is safe for
     * HSTS: user agents ignore a {@code Strict-Transport-Security} header received over a
     * non-secure transport (RFC 6797, section 8.1), so a spoofed header on a plain
     * connection has no effect.
     */
    private static boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        HttpHeaders headers = new ServletServerHttpRequest(request).getHeaders();
        try {
            // Only the scheme is needed, so the forwarded headers are applied to the request's
            // scheme alone rather than to its URL: java.net.URI rejects request paths the
            // container may accept, such as those allowed through Tomcat's relaxedPathChars.
            URI base = URI.create(request.getScheme() + "://localhost");
            String scheme = ForwardedHeaderUtils.adaptFromForwardedHeaders(base, headers).build().getScheme();
            return HTTPS.equalsIgnoreCase(scheme);
        }
        catch (IllegalArgumentException malformedForwardedHeaders) {
            // Malformed forwarded headers: no trustworthy scheme to go on.
            return false;
        }
    }

    private static boolean isEnabled(GrailsSecurityHeadersProperties.Header header) {
        return header != null && header.isEnabled();
    }

    private void applyHeader(HttpServletResponse response, String name, String propertyName,
            GrailsSecurityHeadersProperties.Header header, boolean applyDefaults) {
        if (!isEnabled(header)) {
            return;
        }
        if (!StringUtils.hasText(header.getValue())) {
            if (this.blankValueLogged.add(name)) {
                logger.warn("The {} response header is enabled but grails.security.headers.{}.value is blank, so the " +
                        "header is not sent. Set a value or set grails.security.headers.{}.enabled to false.",
                        name, propertyName, propertyName);
            }
            return;
        }
        if (!applyDefaults && !header.isExplicit()) {
            return;
        }
        if (!response.containsHeader(name)) {
            response.setHeader(name, header.getValue());
        }
    }
}

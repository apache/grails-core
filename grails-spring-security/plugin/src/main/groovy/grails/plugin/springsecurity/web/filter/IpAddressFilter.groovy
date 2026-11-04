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
package grails.plugin.springsecurity.web.filter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.access.ConfigAttribute
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.GenericFilterBean
import org.springframework.web.util.UrlPathHelper

import grails.plugin.springsecurity.InterceptedUrl
import grails.plugin.springsecurity.ReflectionUtils
import org.grails.web.util.WebUtils

/**
 * Blocks access to protected resources based on IP address. Sends 404 rather than
 * reporting error to hide visibility of the resources.
 * <br>
 * Supports either single IP addresses or CIDR masked patterns
 * (e.g. 192.168.1.0/24, 202.24.0.0/14, 10.0.0.0/8, etc.).
 *
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class IpAddressFilter extends GenericFilterBean {

    protected static final String IPV4_LOOPBACK = '127.0.0.1'
    protected static final String IPV6_LOOPBACK = '0:0:0:0:0:0:0:1'

    protected final AntPathMatcher pathMatcher = new AntPathMatcher()
    protected final UrlPathHelper urlPathHelper = UrlPathHelper.defaultInstance
    protected final UrlPathHelper rawUrlPathHelper = rawUrlPathHelper()

    protected List<InterceptedUrl> restrictions

    /** Dependency injection for whether to allow localhost calls (useful for testing). TODO document. */
    boolean allowLocalhost = true

    void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req
        HttpServletResponse response = (HttpServletResponse) res

        if (!isAllowed(request)) {
            deny request, response
            return
        }

        chain.doFilter request, response
    }

    protected void deny(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // send 404 to hide the existence of the resource
        res.sendError HttpServletResponse.SC_NOT_FOUND
    }

    @Override
    protected void initFilterBean() {
        assert restrictions, 'ipRestrictions map is required'
    }

    /**
     * Dependency injection for the ip/pattern restriction map. Keys are URL patterns and values
     * are either single <code>String</code>s or <code>List</code>s of <code>String</code>s
     * representing IP address patterns to allow for the specified URLs.
     *
     * @param ipRestrictions the map
     */
    void setIpRestrictions(List<Map<String, Object>> ipRestrictions) {
        restrictions = ipRestrictions.collect { Map<String, Object> entry ->
            List tokens
            def access = entry.access
            if (access?.getClass()?.array) {
                access = access as List
            }
            if (access instanceof Collection) {
                tokens = ((Collection) access)*.toString()
            } else { // String/GString
                tokens = [access.toString()]
            }
            new InterceptedUrl(entry.pattern as String, null, ReflectionUtils.buildConfigAttributes(tokens, false))
        }
    }

    protected boolean isAllowed(HttpServletRequest request) {
        String ip = request.remoteAddr
        if (allowLocalhost && (IPV4_LOOPBACK == ip || IPV6_LOOPBACK == ip)) {
            return true
        }

        String uri = getPathWithinApplication(request)

        List<InterceptedUrl> matching = findMatchingRules(uri)
        if (!matching) {
            return true
        }

        for (InterceptedUrl iu in matching) {
            for (ConfigAttribute ipPattern in iu.configAttributes) {
                if (new IpAddressMatcher(ipPattern.attribute).matches(request)) {
                    return true
                }
            }
        }

        log.warn 'disallowed request {} from {}', uri, ip
        false
    }

    /**
     * Resolves the path that restriction patterns are matched against, in the form Grails URL mapping dispatch
     * resolves it: path parameters removed per segment before percent-decoding (Jakarta Servlet 6.0 section 3.5.2),
     * decoded exactly once (RFC 3986 section 2.4) and relative to the context path, so an encoded or matrix-parameter
     * variant of a restricted path is subject to the same restriction. For a forwarded request the original request
     * URI is checked, canonicalized the same way. Like dispatch, and unlike RFC 3986 section 6.2.2.1, the context
     * path is compared case-insensitively. A URI with an illegal percent escape, which a Servlet 6.0 container
     * rejects with 400 before the filter chain runs, is matched undecoded rather than aborting the chain.
     */
    protected String getPathWithinApplication(HttpServletRequest request) {
        String forwardUri = request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE) as String
        if (!forwardUri) {
            try {
                return urlPathHelper.getPathWithinApplication(request)
            } catch (IllegalArgumentException ignored) {
                return rawUrlPathHelper.getPathWithinApplication(request)
            }
        }
        String path = urlPathHelper.removeSemicolonContent(forwardUri)
        String contextPath = (request.getAttribute(WebUtils.FORWARD_CONTEXT_PATH_ATTRIBUTE) as String) ?: request.contextPath
        try {
            path = urlPathHelper.decodeRequestString(request, path)
            contextPath = urlPathHelper.decodeRequestString(request, contextPath)
        } catch (IllegalArgumentException ignored) {
            // illegal percent escape: match the undecoded path
        }
        if (contextPath && contextPath != '/' && path.regionMatches(true, 0, contextPath, 0, contextPath.length())) {
            path = path.substring(contextPath.length())
        }
        path ?: '/'
    }

    private static UrlPathHelper rawUrlPathHelper() {
        UrlPathHelper helper = new UrlPathHelper()
        helper.urlDecode = false
        helper
    }

    protected List<InterceptedUrl> findMatchingRules(String uri) {
        restrictions.findAll { InterceptedUrl iu -> pathMatcher.match iu.pattern, uri }
    }
}

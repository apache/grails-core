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

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter
import org.springframework.security.web.savedrequest.RequestCache

import grails.plugin.springsecurity.SpringSecurityUtils

/**
 * Stores a SavedRequest so remember-me autologin gets redirected to requested url.
 *
 * <p>Spring Security's filter hands every request that logs in through remember-me to the authentication success handler,
 * which redirects, and then does not run the rest of the filter chain. That suits a page that is fetched with a GET. A
 * request that carries data (POST, PUT, PATCH, DELETE), or one that comes from Ajax, would be turned into a GET, or into the
 * Ajax success url, and its action would never run. So the success handler is kept here and used only for a GET that is
 * not an Ajax call, and not the request for the login form; any other request goes on down the filter chain as the remembered user.</p>
 *
 * @author Burt Beckwith
 */
@CompileStatic
class GrailsRememberMeAuthenticationFilter extends RememberMeAuthenticationFilter {

    /** The request attribute that holds the authentication of a request that is to be redirected. */
    private static final String REDIRECT_AUTHENTICATION = GrailsRememberMeAuthenticationFilter.name + '.REDIRECT_AUTHENTICATION'

    protected RequestCache requestCache

    /** Dependency injection for createSessionOnSuccess. */
    boolean createSessionOnSuccess = true

    /** Dependency injection for the url of the login form, the page a user who is not fully authenticated is sent to. */
    String loginFormUrl

    private AuthenticationSuccessHandler redirectHandler

    GrailsRememberMeAuthenticationFilter(AuthenticationManager authenticationManager, RememberMeServices rememberMeServices,
                                         RequestCache requestCache) {
        super(authenticationManager, rememberMeServices)
        this.requestCache = requestCache
    }

    /**
     * Kept in this filter and not handed to Spring Security's, which would redirect every request that logs in and stop the chain.
     */
    @Override
    void setAuthenticationSuccessHandler(AuthenticationSuccessHandler successHandler) {
        this.redirectHandler = successHandler
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        super.doFilter(request, response, { ServletRequest filteredRequest, ServletResponse filteredResponse ->
            Authentication redirectAuthentication = (Authentication) filteredRequest.getAttribute(REDIRECT_AUTHENTICATION)
            if (redirectAuthentication) {
                filteredRequest.removeAttribute(REDIRECT_AUTHENTICATION)
                redirectHandler.onAuthenticationSuccess((HttpServletRequest) filteredRequest, (HttpServletResponse) filteredResponse, redirectAuthentication)
            }
            else {
                chain.doFilter(filteredRequest, filteredResponse)
            }
        } as FilterChain)
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) {
        if (redirectHandler && redirects(request)) {
            if (!requestCache.getRequest(request, response)) {
                requestCache.saveRequest request, response
            }
            request.setAttribute REDIRECT_AUTHENTICATION, authResult
        }

        try {
            if (createSessionOnSuccess) {
                request.session
            }
        }
        catch (IllegalStateException ignored) {
        }
    }

    /**
     * @return whether a request that logs in through remember-me is redirected to the page it asked for: a GET that is not an Ajax
     * call and not the request for the login form. A page that needs a full login sends a remembered user to the login form,
     * where the cookie logs in again; redirecting back to the page from there would bounce between the two.
     */
    protected boolean redirects(HttpServletRequest request) {
        'GET' == request.method && !SpringSecurityUtils.isAjax(request) && !isLoginForm(request)
    }

    /**
     * The login form url is a path of the application, or an absolute url, which Spring Security's login entry point accepts as
     * well. The request has reached this application, so its path is enough to tell whether it is the login form: only the
     * path of the url is compared, without its query or fragment, and a scheme, host or port that the request reports
     * are not, as behind a proxy that does not forward them they differ from the public url. The path of an absolute url
     * includes the context path.
     */
    protected boolean isLoginForm(HttpServletRequest request) {
        if (!loginFormUrl) {
            return false
        }
        String lower = loginFormUrl.toLowerCase(Locale.ROOT)
        if (lower.startsWith('http://') || lower.startsWith('https://')) {
            URI form
            try {
                form = new URI(loginFormUrl)
            }
            catch (URISyntaxException ignored) {
                return false
            }
            // the path the browser sends has no dot segments, so the configured one has none either
            return request.requestURI == (withoutDotSegments(form.rawPath ?: '/') ?: '/')
        }
        request.requestURI == request.contextPath + withoutDotSegments(withoutQueryAndFragment(loginFormUrl))
    }

    /**
     * The path as a browser sends it: a segment of one or two dots is removed, with the segment before it for two, and a
     * {@code ..} at the root stays at the root. A browser reads {@code %2e} as a dot, in either case, so {@code %2e%2e},
     * {@code .%2E} and the like are dot segments too.
     */
    private static String withoutDotSegments(String path) {
        String[] segments = path.split('/', -1)
        List<String> kept = []
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i]
            String dots = segment.toLowerCase(Locale.ROOT).replace('%2e', '.')
            boolean last = i == segments.length - 1
            if (dots == '.' || dots == '..') {
                if (dots == '..' && kept.size() > 1) {
                    kept.remove(kept.size() - 1)
                }
                if (last) {
                    kept.add('')
                }
            }
            else {
                kept.add(segment)
            }
        }
        kept.join('/')
    }

    private static String withoutQueryAndFragment(String url) {
        int end = url.length()
        ['?', '#'].each { String mark ->
            int at = url.indexOf(mark)
            if (at >= 0 && at < end) {
                end = at
            }
        }
        url.substring(0, end)
    }

    @Override
    void afterPropertiesSet() {
        super.afterPropertiesSet()
        assert requestCache, 'requestCache is required'
    }
}

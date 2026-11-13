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
 * not an Ajax call; any other request goes on down the filter chain as the remembered user.</p>
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
     * @return whether a request that logs in through remember-me is redirected to the page it asked for: a GET that is not an Ajax call
     */
    protected boolean redirects(HttpServletRequest request) {
        'GET' == request.method && !SpringSecurityUtils.isAjax(request)
    }

    @Override
    void afterPropertiesSet() {
        super.afterPropertiesSet()
        assert requestCache, 'requestCache is required'
    }
}

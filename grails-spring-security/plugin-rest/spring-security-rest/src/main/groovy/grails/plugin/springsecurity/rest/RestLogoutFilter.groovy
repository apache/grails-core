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
package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.reader.TokenReader
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.web.filter.GenericFilterBean

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Filter exposing an endpoint for deleting tokens. It will read the token from an HTTP header. If found, will delete it
 * from the storage, sending a 200 response. Otherwise, it will send a 404 response.
 */
@Slf4j
@CompileStatic
class RestLogoutFilter extends GenericFilterBean {

    String endpointUrl
    String headerName
    TokenReader tokenReader

    TokenStorageService tokenStorageService

    SpringSecurityRestFilterRequestMatcher requestMatcher

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest servletRequest = request as HttpServletRequest
        HttpServletResponse servletResponse = response as HttpServletResponse

        //Only apply filter to the configured URL
        if (requestMatcher.matches(servletRequest)) {

            //Only POST is supported
            if (servletRequest.method != 'POST') {
                log.debug "${servletRequest.method} HTTP method is not supported. Setting status to ${HttpServletResponse.SC_METHOD_NOT_ALLOWED}"
                servletResponse.setStatus HttpServletResponse.SC_METHOD_NOT_ALLOWED
                return
            }

            AccessToken accessToken = tokenReader.findToken(servletRequest)

            if (accessToken) {
                log.debug "Token found: ${accessToken}"

                try {
                    log.debug "Trying to remove the token"
                    tokenStorageService.removeToken accessToken.accessToken
                } catch (TokenNotFoundException ignored) {
                    servletResponse.sendError HttpServletResponse.SC_NOT_FOUND, "Token not found"
                }
            } else {
                log.debug "Token is missing. Sending a ${HttpServletResponse.SC_BAD_REQUEST} Bad Request response"
                servletResponse.sendError HttpServletResponse.SC_BAD_REQUEST, "Token header is missing"
            }

        } else {
            chain.doFilter(request, response)
        }
    }
}

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

import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.credentials.CredentialsExtractor
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.TokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.authentication.AuthenticationDetailsSource
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.web.filter.GenericFilterBean

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * This filter starts the initial authentication flow. It uses the configured {@link AuthenticationManager} bean, allowing
 * to use any authentication provider defined by other plugins or by the application.
 *
 * If the authentication manager authenticates the request, a token is generated using a {@link TokenGenerator} and
 * stored via {@link TokenStorageService}. Finally, a {@link AuthenticationSuccessHandler} is used to render the REST
 * response to the client.
 *
 * If there is an authentication failure, the configured {@link AuthenticationFailureHandler} will render the response.
 */
@Slf4j
@CompileStatic
class RestAuthenticationFilter extends GenericFilterBean {

    CredentialsExtractor credentialsExtractor

    String endpointUrl

    AuthenticationManager authenticationManager

    AuthenticationSuccessHandler authenticationSuccessHandler
    AuthenticationFailureHandler authenticationFailureHandler
    RestAuthenticationEventPublisher authenticationEventPublisher
    SpringSecurityRestFilterRequestMatcher requestMatcher

    AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource

    TokenGenerator tokenGenerator
    TokenStorageService tokenStorageService

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = request as HttpServletRequest
        HttpServletResponse httpServletResponse = response as HttpServletResponse

        //Only apply filter to the configured URL
        if (requestMatcher.matches(httpServletRequest)) {
            log.debug "Applying authentication filter to this request"

            //Only POST is supported
            if (httpServletRequest.method != 'POST') {
                log.debug "${httpServletRequest.method} HTTP method is not supported. Setting status to ${HttpServletResponse.SC_METHOD_NOT_ALLOWED}"
                httpServletResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication()
            Authentication authenticationResult

            UsernamePasswordAuthenticationToken authenticationRequest = credentialsExtractor.extractCredentials(httpServletRequest)
        
            boolean authenticationRequestIsCorrect = (authenticationRequest?.principal && authenticationRequest?.credentials)
            
            if(authenticationRequestIsCorrect){
                authenticationRequest.details = authenticationDetailsSource.buildDetails(httpServletRequest)
                
                try {
                    log.debug "Trying to authenticate the request"
                    authenticationResult = authenticationManager.authenticate(authenticationRequest)
              
                    if (authenticationResult.authenticated) {
                        log.debug "Request authenticated. Storing the authentication result in the security context"
                        log.debug "Authentication result: ${authenticationResult}"

                        AccessToken accessToken = tokenGenerator.generateAccessToken(authenticationResult.principal as UserDetails)
                        log.debug "Generated token: ${accessToken}"

                        tokenStorageService.storeToken(accessToken)
                        authenticationEventPublisher.publishTokenCreation(accessToken)
                        authenticationSuccessHandler.onAuthenticationSuccess(httpServletRequest, httpServletResponse, accessToken)
                        SecurityContextHolder.context.setAuthentication(accessToken)
                    } else {
                        log.debug "Not authenticated. Rest authentication token not generated."
                    }
                } catch (AuthenticationException ae) {
                    log.debug "Authentication failed: ${ae.message}"
                    authenticationFailureHandler.onAuthenticationFailure(httpServletRequest, httpServletResponse, ae)
                }
            
            }else{
                log.debug "Username and/or password parameters are missing."
                if(!authentication){
                    log.debug "Setting status to ${HttpServletResponse.SC_BAD_REQUEST}"
                    httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                    return
                }else{
                    log.debug "Using authentication already in security context."
                    authenticationResult = authentication
                }
            }

        } else {
            chain.doFilter(request, response)
        }


    }
}

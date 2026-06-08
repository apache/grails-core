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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.web.util.matcher.RequestMatcher

import jakarta.servlet.http.HttpServletRequest

/**
 * Determines whether a given request matches against a configured endpoint URL
 *
 * @author Álvaro Sánchez-Mariscal
 */
@Slf4j
@CompileStatic
class SpringSecurityRestFilterRequestMatcher implements RequestMatcher {

    private String endpointUrl

    SpringSecurityRestFilterRequestMatcher(String endpointUrl) {
        this.endpointUrl = endpointUrl
    }

    @Override
    boolean matches(HttpServletRequest request) {
        String actualUri =  request.requestURI - request.contextPath
        log.debug "Actual URI is ${actualUri}; endpoint URL is ${endpointUrl}"
        return actualUri == endpointUrl
    }

}

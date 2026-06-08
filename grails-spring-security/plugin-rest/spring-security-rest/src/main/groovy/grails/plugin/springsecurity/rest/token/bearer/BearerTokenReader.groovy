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
package grails.plugin.springsecurity.rest.token.bearer

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.reader.TokenReader
import groovy.util.logging.Slf4j
import org.springframework.http.MediaType

import jakarta.servlet.http.HttpServletRequest

/**
 * RFC 6750 implementation of a {@link TokenReader}
 */
@Slf4j
class BearerTokenReader implements TokenReader {

    /**
     * Finds the bearer token within the specified request.  It will attempt to look in all places allowed by the
     * specification: Authorization header, form encoded body, and query string.
     *
     * @param request
     * @return the token if found, null otherwise
     */
    @Override
    AccessToken findToken(HttpServletRequest request) {
        log.debug "Looking for bearer token in Authorization header, query string or Form-Encoded body parameter"
        String tokenValue = null
        String header = request.getHeader('Authorization')

        if (header?.startsWith('Bearer') && header.length()>=8) {
            log.debug "Found bearer token in Authorization header"
            tokenValue = header.substring(7)
        } else if (isFormEncoded(request) && !request.get) {
            log.debug "Found bearer token in request body"
            tokenValue = request.parameterMap['access_token']?.first()
        } else if (request.queryString?.contains('access_token')) {
            log.debug "Found bearer token in query string"
            tokenValue = request.getParameter('access_token')
        } else {
            log.debug "No token found"
        }

        log.debug "Token: ${tokenValue}"
        return tokenValue ? new AccessToken(tokenValue) : null
    }

    private boolean isFormEncoded(HttpServletRequest servletRequest) {
        servletRequest.contentType && MediaType.parseMediaType(servletRequest.contentType).isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)
    }
}

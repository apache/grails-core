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
package grails.plugin.springsecurity.rest.token.reader

import grails.plugin.springsecurity.rest.token.AccessToken
import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

/**
 * Reads the token from a configurable HTTP Header
 */

@CompileStatic
class HttpHeaderTokenReader implements TokenReader {

    String headerName

    /**
     * @return the token from the header {@link #headerName}, null otherwise
     */
    @Override
    AccessToken findToken(HttpServletRequest request) {
        String tokenValue = request.getHeader(headerName)
        return tokenValue ? new AccessToken(tokenValue) : null
    }
}

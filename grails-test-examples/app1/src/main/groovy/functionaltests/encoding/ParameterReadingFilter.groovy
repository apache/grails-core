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
package functionaltests.encoding

import groovy.transform.CompileStatic

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.web.filter.OncePerRequestFilter

/**
 * Reads a request parameter before the request reaches Grails, as a single sign-out or referral filter does,
 * and keeps what it read for the controller to report.
 */
@CompileStatic
class ParameterReadingFilter extends OncePerRequestFilter {

    static final String READ_BY_FILTER = 'textReadByFilter'

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        request.setAttribute(READ_BY_FILTER, request.getParameter('text'))
        filterChain.doFilter(request, response)
    }
}

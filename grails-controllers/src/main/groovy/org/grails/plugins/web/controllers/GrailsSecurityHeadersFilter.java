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
package org.grails.plugins.web.controllers;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class GrailsSecurityHeadersFilter extends OncePerRequestFilter {

    private final GrailsSecurityHeadersProperties properties;

    public GrailsSecurityHeadersFilter(GrailsSecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        applyHeader(response, "X-Content-Type-Options", properties.getContentTypeOptions());
        applyHeader(response, "X-Frame-Options", properties.getFrameOptions());
        applyHeader(response, "Referrer-Policy", properties.getReferrerPolicy());
        applyHeader(response, "X-XSS-Protection", properties.getXssProtection());
        if (request.isSecure()) {
            applyHeader(response, "Strict-Transport-Security", properties.getHsts());
        }
        applyHeader(response, "Content-Security-Policy", properties.getContentSecurityPolicy());
        filterChain.doFilter(request, response);
    }

    private static void applyHeader(HttpServletResponse response, String name,
            GrailsSecurityHeadersProperties.Header header) {
        if (header != null && header.isEnabled() && StringUtils.hasText(header.getValue()) &&
                !response.containsHeader(name)) {
            response.setHeader(name, header.getValue());
        }
    }
}

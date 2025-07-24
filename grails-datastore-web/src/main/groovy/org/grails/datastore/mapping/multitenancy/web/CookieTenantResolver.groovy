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

package org.grails.datastore.mapping.multitenancy.web

import groovy.transform.CompileStatic

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest

import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

/**
 * Resolves the tenant id from a cookie
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class CookieTenantResolver implements TenantResolver {

    public static final String COOKIE_NAME = "gorm.tenantId"

    /**
     * The name of the cookie
     */
    String cookieName = COOKIE_NAME

    @Override
    Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes()
        if(requestAttributes instanceof ServletWebRequest) {

            HttpServletRequest servletRequest = ((ServletWebRequest) requestAttributes).getRequest()
            Cookie[] cookies = servletRequest.getCookies()
            if(cookies != null) {

                for (Cookie cookie : cookies) {
                    if( cookieName.equals( cookie.name ) ) {
                        return cookie.getValue()
                    }
                }
            }
            throw new TenantNotFoundException()
        }
        throw new TenantNotFoundException("Tenant could not be resolved outside a web request")
    }
}

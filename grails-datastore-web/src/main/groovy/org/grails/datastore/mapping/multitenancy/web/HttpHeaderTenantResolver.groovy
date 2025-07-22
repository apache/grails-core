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
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest

import jakarta.servlet.http.HttpServletRequest

/**
 * A tenant resolver that resolves the tenant from the request HTTP Header
 *
 * @author Sergio del Amo
 * @since 6.1.7
 */
@CompileStatic
class HttpHeaderTenantResolver implements TenantResolver {
    public static final String HEADER_NAME = "gorm.tenantId"

    /**
     * The name of the header
     */
    String headerName = HEADER_NAME

    @Override
    Serializable resolveTenantIdentifier() {

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes()
        if(requestAttributes instanceof ServletWebRequest) {

            HttpServletRequest httpServletRequest = ((ServletWebRequest) requestAttributes).getRequest()
            String tenantId = httpServletRequest.getHeader(headerName.toLowerCase())

            if ( tenantId ) {
                return tenantId
            }
            throw new TenantNotFoundException("Tenant could not be resolved from HTTP Header: ${headerName}")
        }
        throw new TenantNotFoundException("Tenant could not be resolved outside a web request")
    }
}

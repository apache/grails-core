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
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest

import jakarta.servlet.http.HttpServletRequest

/**
 * A tenant resolver that resolves the tenant from the Subdomain
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class SubDomainTenantResolver implements TenantResolver{

    @Override
    Serializable resolveTenantIdentifier() {

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes()
        if(requestAttributes instanceof ServletWebRequest) {

            HttpServletRequest httpServletRequest = ((ServletWebRequest) requestAttributes).getRequest()
            String subdomain = httpServletRequest.getRequestURL().toString()
            String requestURI = httpServletRequest.getRequestURI()
            def i = requestURI.length()
            if(i > 0) {
                subdomain = subdomain.substring(0, subdomain.length()-i)
            }
            subdomain = subdomain.substring(subdomain.indexOf("/") + 2);
            if( subdomain.indexOf(".") > -1 ) {
                return subdomain.substring(0, subdomain.indexOf("."))
            }
            else {
                return ConnectionSource.DEFAULT
            }
        }
        throw new TenantNotFoundException("Tenant could not be resolved outside a web request")
    }
}

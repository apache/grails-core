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

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import spock.lang.PendingFeatureIf
import spock.lang.Specification

/**
 * Created by graemerocher on 08/07/2016.
 */
class SubDomainTenantResolverSpec extends Specification {

    @PendingFeatureIf({
        // thrown does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void "Test subdomain resolver throws an exception outside a web request"() {
        when:
        new SubDomainTenantResolver().resolveTenantIdentifier()

        then:
        def e = thrown(TenantNotFoundException)
        e.message == "Tenant could not be resolved outside a web request"
    }

    void "Test that the subdomain is the tenant id when a request is present"() {

        setup:
        def request = new MockHttpServletRequest("GET", "/foo")
        RequestContextHolder.setRequestAttributes(new ServletWebRequest(request))

        when:
        def tenantId = new SubDomainTenantResolver().resolveTenantIdentifier()

        then:
        tenantId == ConnectionSource.DEFAULT

        when:
        request.setServerName("foo.mycompany.com")
        tenantId = new SubDomainTenantResolver().resolveTenantIdentifier()

        then:
        tenantId == "foo"

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }

    void "Test subdomain with dot in path"() {

        setup:
        def request = new MockHttpServletRequest("GET", "/foo")
        RequestContextHolder.setRequestAttributes(new ServletWebRequest(request))

        when:
        request.setServerPort(8080)
        request.setServerName("localhost")
        request.setRequestURI("/x/y/z.html")

        def tenantId = new SubDomainTenantResolver().resolveTenantIdentifier()

        then:
        tenantId == ConnectionSource.DEFAULT

        when:
        request.setServerPort(8080)
        request.setServerName("foo.mycompany.com")
        request.setRequestURI("/x/y/z.html")
        tenantId = new SubDomainTenantResolver().resolveTenantIdentifier()

        then:
        tenantId == "foo"

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }
}

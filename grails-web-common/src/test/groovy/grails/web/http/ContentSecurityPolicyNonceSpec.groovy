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
package grails.web.http

import java.lang.reflect.Modifier

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.header.ContentSecurityPolicyNonceGeneratingFilter
import spock.lang.Specification
import spock.lang.Unroll

class ContentSecurityPolicyNonceSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest('GET', '/')

    void 'a nonce is 128 random bits in Base64 without padding, generated once'() {
        given:
        def nonce = new ContentSecurityPolicyNonce()

        expect:
        nonce.nonce ==~ /[A-Za-z0-9+\/]{22}/
        nonce.nonce == nonce.nonce
        nonce.nonce != new ContentSecurityPolicyNonce().nonce
    }

    void 'the nonce the Grails security headers filter put on the request is read'() {
        given:
        def nonce = new ContentSecurityPolicyNonce()
        request.setAttribute('_csp', nonce)

        expect:
        ContentSecurityPolicyNonce.ATTRIBUTE == '_csp'
        ContentSecurityPolicyNonce.nonceOf(request) == nonce.nonce
    }

    void 'the nonce Spring Security generated is read through its public interface'() {
        given: 'a view behind Spring Security\'s own nonce filter'
        String read = null
        Object carried = null
        FilterChain view = { viewRequest, viewResponse ->
            read = ContentSecurityPolicyNonce.nonceOf(viewRequest as HttpServletRequest)
            carried = viewRequest.getAttribute('_csp')
        } as FilterChain

        when:
        new ContentSecurityPolicyNonceGeneratingFilter().doFilter(request, new MockHttpServletResponse(), view)

        then: 'the filter carries it in a class that is not public, behind the interface'
        !Modifier.isPublic(carried.getClass().modifiers)
        carried instanceof org.springframework.security.web.header.ContentSecurityPolicyNonce

        and:
        read ==~ /[A-Za-z0-9+\/]{22}/
        read == (carried as org.springframework.security.web.header.ContentSecurityPolicyNonce).nonce
    }

    @Unroll
    void 'a request carries no nonce when #description'() {
        given:
        if (holder != null) {
            request.setAttribute('_csp', holder)
        }

        expect:
        ContentSecurityPolicyNonce.nonceOf(request) == null

        where:
        description                                               | holder
        'nothing is under the attribute'                          | null
        'something else is under the attribute'                   | 'not a nonce'
        'Spring Security\'s nonce is blank'                       | blankSpringSecurityNonce()
        'another type with a nonce getter is under the attribute' | new LookalikeNonce()
    }

    private static org.springframework.security.web.header.ContentSecurityPolicyNonce blankSpringSecurityNonce() {
        { -> ' ' } as org.springframework.security.web.header.ContentSecurityPolicyNonce
    }

    /** Has a public nonce getter, but is neither the Grails nonce nor Spring Security's. */
    static class LookalikeNonce {

        String getNonce() {
            'abc'
        }
    }
}

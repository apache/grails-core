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
package grails.plugin.springsecurity.rest.rfc6750

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.bearer.BearerTokenReader
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by ajbrown on 6/25/14.
 */
class BearerTokenReaderSpec extends Specification {

    def tokenReader = new BearerTokenReader()
    def request = new GrailsMockHttpServletRequest()
    def response = new MockHttpServletResponse()

    def setup() {
        //TODO, untill we get spring-test 4.0.5 or greater, there is no getParts() on MockHttpServletRequest.  We
        // just need to define it so that an exception doesn't occur when checking for parts.
        request.metaClass.getParts = { -> [] }
    }

    @Unroll
    def "token value can be read from #method request Authorization header (prefixed with 'Bearer ')"() {
        given:
        def token    = 'mytestotkenvalue'
        request.addHeader( 'Authorization', 'Bearer ' + token )
        request.method = method
        request.contentType = MediaType.TEXT_PLAIN_VALUE

        expect:
        tokenReader.findToken(request) == new AccessToken(token)

        where:
        method << [ 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS' ]
    }

    @Unroll
    def "token value cannot be read from #method request access_token query string parameter, due to its insecurity"() {
        given:
        def token = 'mytesttokenvalue'
        request.queryString = 'access_token=' + token
        request.contentType = MediaType.TEXT_PLAIN_VALUE

        expect:
        tokenReader.findToken(request) == null

        where:
        method << [ 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS' ]
    }

    @Unroll
    def "token value can be read from application/x-www-form-url-encoded #method request body"() {
        given:
        def token = 'mytesttokenvalue'
        request.contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE
        request.addParameter( 'access_token', token )
        request.method = method

        expect:
        tokenReader.findToken(request) == new AccessToken(token)

        where:
        method << [ 'POST', 'PUT', 'PATCH' ]
    }

    @Unroll
    def "token value will not be read from #method request Authorization header not prefix with 'Bearer'"() {
        given:
        def token = 'abadtokenvalue'
        request.addHeader( 'Authorization', token )
        request.method = method
        request.contentType = MediaType.TEXT_PLAIN_VALUE

        expect:
        !tokenReader.findToken(request)

        where:

        method << [ 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS' ]
    }

    @Unroll
    def "token value will not be read from #method request body if not form encoded"() {
        given:
        def token = 'abadtokenvalue'
        request.addParameter( 'access_token', token )
        request.contentType = MediaType.MULTIPART_FORM_DATA_VALUE
        request.method = method

        expect:
        !tokenReader.findToken(request)

        where:
        method << [ 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS' ]
    }

    @Unroll
    def "token value will not be read from request body if request method is #method"() {
        given:
        def token = 'mytesttokenvalue'
        request.contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE
        request.addParameter( 'access_token', token )
        request.method = method

        expect:
        !tokenReader.findToken(request)

        where:
        method << [ 'GET' ]
    }

    @Unroll
    def "when the mediatype is null still works"() {
        given:
        request.contentType = null

        expect:
        !tokenReader.findToken(request)
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/235")
    def "it doesn't crash if token is missing"() {
        given:
        request.addHeader('Authorization', 'Bearer')

        when:
        tokenReader.findToken(request)

        then:
        notThrown(Throwable)
    }

}

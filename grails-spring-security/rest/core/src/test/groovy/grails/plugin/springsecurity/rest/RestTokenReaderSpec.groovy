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
package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.rest.token.reader.HttpHeaderTokenReader
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(HttpHeaderTokenReader)
class RestTokenReaderSpec extends Specification {

    def tokenReader = new HttpHeaderTokenReader()
    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()


    @Unroll
    def "token value can be read from a custom header in a #method request"() {
        def token  = 'mytokenvalue'
        def header = 'mycustomheader'

        tokenReader.headerName = header
        request.addHeader( header, token )
        request.method = method

        expect:
        tokenReader.findToken(request).accessToken == token

        where:
        method << [ 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS' ]
    }

}

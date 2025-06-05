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
package grails.plugin.springsecurity.rest.token.bearer

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification

/**
 * Created by Sean Brady on 12/3/14.
 */
class BearerTokenAccessDeniedHandlerSpec extends Specification {

    def bearerTokenAccessDeniedHandler = new BearerTokenAccessDeniedHandler()

    def "sets the correct bearer token header when forbidden"() {
        given:
        def response = new MockHttpServletResponse()
        def request = new MockHttpServletRequest()
        when:
        bearerTokenAccessDeniedHandler.handle(request,response,new AccessDeniedException("fake"))
        then:
        response.getHeader( 'WWW-Authenticate' ) == 'Bearer error="insufficient_scope"'
    }
}

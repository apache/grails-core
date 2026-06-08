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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import spock.lang.Specification
import spock.lang.Unroll

class SpringSecurityRestFilterRequestMatcherSpec extends Specification {

    @Unroll
    void "if the context path is #contextPath, the URI #uri matches: #matches"(String contextPath, String uri, boolean matches) {
        given:
        SpringSecurityRestFilterRequestMatcher requestMatcher = new SpringSecurityRestFilterRequestMatcher("/api/login")
        MockServletContext servletContext = new MockServletContext()
        servletContext.setContextPath(contextPath)
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "POST", uri)

        when:
        boolean matchesResult = requestMatcher.matches(request)

        then:
        matchesResult == matches

        where:
        contextPath         | uri                   || matches
        "/"                 | "/api/login"          || true
        "/"                 | "/api/loginxxx"       || false
        "/foo"              | "/api/login/foo"      || false
    }

}

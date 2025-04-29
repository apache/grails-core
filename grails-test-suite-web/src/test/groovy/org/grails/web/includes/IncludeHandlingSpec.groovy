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
package org.grails.web.includes

import grails.testing.web.GrailsWebUnitTest
import org.springframework.web.util.WebUtils
import spock.lang.Specification

/**
 * Tests the behavior of the include tag
 */
class IncludeHandlingSpec extends Specification implements GrailsWebUnitTest {

    void "Test the appropriate request headers are set and URI of a page included"() {
        given:"A template that includes a view"
            views['/foo/_bar.gsp'] = 'Include = <g:include view="/foo/include.gsp" model="[foo:\'bar\']"/>'

        when:"The template is rendered"
            request.foo = "dontchange"
            def content = render(template:"/foo/bar")

        then:"The include status is valid"
            request.foo == "dontchange"
            content == "Include = "
            response.includedUrls
            response.includedUrls[0] == '/foo/include.gsp'
            request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE) == null
            request.getAttribute(WebUtils.INCLUDE_CONTEXT_PATH_ATTRIBUTE) == null
            request.getAttribute(WebUtils.INCLUDE_PATH_INFO_ATTRIBUTE) == null
            request.getAttribute(WebUtils.INCLUDE_QUERY_STRING_ATTRIBUTE) == null
            request.getAttribute(WebUtils.INCLUDE_SERVLET_PATH_ATTRIBUTE) == null
    }
}

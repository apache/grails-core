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
package org.grails.web.servlet

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification
import spock.lang.Unroll

class GrailsUrlPathHelperSpec extends Specification {

    GrailsUrlPathHelper helper = new GrailsUrlPathHelper()

    @Unroll
    void 'the /grails prefix and .dispatch suffix are stripped from #uri'() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest()
        request.requestURI = uri

        expect:
        helper.getPathWithinApplication(request) == expected

        where:
        uri                          | expected
        '/grails/book/list.dispatch' | '/book/list'
        '/grails/book/list'          | '/book/list'
        '/book/list.dispatch'        | '/book/list'
        '/book/list'                 | '/book/list'
        '/grails'                    | ''
        '/'                          | '/'
    }

    void 'the constants match the historical grails path conventions'() {
        expect:
        GrailsUrlPathHelper.GRAILS_DISPATCH_EXTENSION == '.dispatch'
        GrailsUrlPathHelper.GRAILS_SERVLET_PATH == '/grails'
        helper instanceof org.springframework.web.util.UrlPathHelper
    }

}

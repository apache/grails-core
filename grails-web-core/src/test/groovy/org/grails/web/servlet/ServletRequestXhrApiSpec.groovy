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

class ServletRequestXhrApiSpec extends Specification{

    void "Test identification of XHR requests"() {
        when:"a regular request is used"
            def request = new MockHttpServletRequest()
        then:"it isn't an XHR request"
            request.isXhr() == false

        when:"A non XHR request is sent with the X-Requested-With header"
            request.addHeader("X-Requested-With","com.android.browser")

        then:"It is not an XHR request"
            request.isXhr() == false

        when:"A request is sent with a X-Requested-With value of XMLHttpRequest"
            request = new MockHttpServletRequest()
            request.addHeader("X-Requested-With", "XMLHttpRequest")

        then:"It is an XHR request"
            request.isXhr() == true
    }
}

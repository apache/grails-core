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
package grails.plugin.json.view

import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Specification

class JsonViewTestHttpSpec extends Specification implements JsonViewTest {

    void "Test that it is possible to specify HTTP headers"() {

        when:"A template is rendered with HTTP headers"
        def template ='''
json {
    userAgent request.getHeader('User-Agent')
    lang params.lang
}
'''
        def result = render(template) {
            header "User-Agent", "FooBar"
            params lang: 'de'
        }

        then:"The result is correct"
        result.json.userAgent == 'FooBar'
        result.json.lang == 'de'
    }

    void "Test that it is possible to specify HTTP attributes"() {
        when:"A template is rendered with HTTP attributes"
        def template ='''
    json {
        foo request.getAttribute('foo')
    }
    '''
        def result = render(template) {
            attribute "foo", [a: 1, b: 2]
        }

        then:"The result is correct"
        result.json.foo.a == 1
        result.json.foo.b == 2
    }
}

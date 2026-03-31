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
package functional.tests

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class TestGsonControllerSpec extends Specification implements HttpClientSupport {

    void 'Test that responding with a map is possible'() {
        when: 'When JSON is requested'
        def response = http('/testGson/testRespondWithMap')

        then: 'The JSON view is rendered'
        response.assertEquals('{"message":"two"}')
    }

    void 'Test that responding with a map is possible with object template'() {
        when: 'When JSON is requested'
        def response = http('/testGson/testRespondWithMapObjectTemplate.json')

        then: 'The JSON view is rendered'
        response.assertEquals('{"one":"two"}')

    }
    
    void 'Test that it is possible to use the template engine directly'() {
        when: 'When JSON is requested'
        def response = http('/testGson/testTemplateEngine')

        then: 'The JSON view is rendered'
        response.assertJson('''
            {
                "title": "The Stand",
                "timeZone": "America/New_York",
                "vendor": "MyCompany"
            }
        ''')
    }

    void 'Test the respond method returns a GSON view for JSON request'() {
        when: 'When JSON is requested'
        def response = http('/testGson/testRespond.json')

        then: 'The JSON view is rendered'
        response.assertEquals('{"test":{"name":"Bob"}}')

        when: 'When HTML is requested'
       response = http('/testGson/testRespond.html')

        then: 'The GSP is rendered'
        response.assertContains('<h1>Test Bob HTML</h1>')
    }

    void 'Test the respond method returns a GSON named after the domain view for JSON request'() {
        when: 'When JSON is requested'
        def response = http('/testGson/testRespondWithTemplateForDomain.json')

        then: 'The JSON view is rendered'
        response.assertJson('''
            {
                "test": {
                    "name": "Bob",
                    "age": 60
                }
            }
        ''')
    }

    void 'Test template rendering works'() {
        when: 'A view that renders templates is rendered'
        def response = http('/testGson/testTemplate.json')

        then: 'The result is correct'
        response.assertJson('''
            {
                "test": {
                    "name": "Bob",
                    "child": {
                        "child": {
                            "name": "Joe",
                            "age": 10
                        }
                    },
                    "children": [
                        {
                            "child": {
                                "name": "Joe",
                                "age": 10
                            }
                        }
                    ]
                }
            }
        ''')
    }

    void 'Test views from plugins are rendered'() {
        when: 'A view that renders templates is rendered'
        def response = http('/testGson/testGsonFromPlugin')

        then: 'The result is correct'
        response.assertEquals('{"message":"Hello from Plugin"}')
    }

    void 'Test view that inherits from plugins are rendered'() {
        when:
        def response = http('/testGson/testInheritsFromPlugin')

        then:
        response.assertJson('''
            {
                "message": "Hello from Plugin Template",
                "foo": "bar"
            }
        ''')
    }

    void 'Test augmenting model'() {
        when: 'When JSON is requested'
        def response = http('/testGson/testAugmentModel.json')

        then: 'The JSON view is rendered'
        response.assertJson('''
            {
                "test": {
                    "name": "John",
                    "age": 20
                }
            }
        ''')

        when: 'When HTML is requested'
        response = http('/testGson/testAugmentModel.html')

        then: 'The GSP is rendered'
        response.assertContains('<h1>Test John (20) HTML</h1>')
    }
}

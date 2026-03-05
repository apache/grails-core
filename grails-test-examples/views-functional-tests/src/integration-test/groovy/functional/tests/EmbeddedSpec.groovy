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

import spock.lang.Issue
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class EmbeddedSpec extends Specification implements HttpClientSupport {

    void 'Test render can handle a domain with an embedded src/groovy class'() {
        when:
        def response = http('/embedded')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            {
                "id": 1,
                "customClass": {
                    "name": "Bar"
                },
                "name": "Foo",
                "inSameFile": {
                    "text": "FooBar"
                }
            }
        ''')
    }

    void 'Test jsonapi render can handle a domain with an embedded src/groovy class'() {
        when:
        def response = http('/embedded/jsonapi')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            {
                "data": {
                    "type": "embedded",
                    "id": "2",
                    "attributes": {
                        "customClass": {
                            "name": "Bar2"
                        },
                        "name": "Foo2",
                        "inSameFile": {
                            "text": "FooBar2"
                        }
                    }
                },
                "links": {
                    "self": "/embedded/show/2"
                }
            }
        ''')
    }

    @Issue('https://github.com/apache/grails-views/issues/171')
    void 'test render can handle a domain with an embedded and includes src/groovy class'() {
        when:
        def response = http('/embedded/embeddedWithIncludes')

        then: 'the response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            {
                "customClass": {
                    "name": "Bar3"
                },
                "name": "Foo3"
            }
        ''')
    }

    @Issue('https://github.com/apache/grails-views/issues/171')
    void 'Test jsonapi render can handle a domain with an embedded and includes src/groovy class'() {
        when:
        def response = http('/embedded/embeddedWithIncludesJsonapi')

        then: 'the response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            {
                "data": {
                    "type": "embedded",
                    "id": "4",
                    "attributes": {
                        "customClass": {
                            "name": "Bar4"
                        },
                        "name": "Foo4"
                    }
                },
                "links": {
                    "self": "/embedded/show/4"
                }
            }
        ''')
    }
}
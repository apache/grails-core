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

package rest

import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Cross-origin requests to the endpoints of the plugin, in an application with {@code grails.cors.enabled}.
 */
abstract class AbstractCorsSpec extends AbstractRestSpec {

    void "OPTIONS requests are allowed"() {
        when:
        TestHttpResponse response = request('OPTIONS', '/api/login', [
                'Origin'                       : 'http://www.example.com',
                'Access-Control-Request-Method': 'POST'
        ])

        then:
        response.statusCode() == 200
        response.headerValue('Access-Control-Allow-Origin') == 'http://www.example.com'
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package issue15819

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
@Issue('https://github.com/apache/grails-core/issues/15819')
class ResponseRendererFunctionalSpec extends Specification implements HttpClientSupport {

    @Unroll
    void '#action renders only the _results.gsp template fragment with ajax=#ajax'() {
        when:
        def response = http(ajax ? ['X-Requested-With': 'XMLHttpRequest'] : [:], "/search/$action")

        then: 'no conventional action view is resolved and no layout or error page is appended'
        response.assertStatus(200)
        response.headerValue('Content-Type').startsWith('text/html')
        response.body().replaceAll(/\s+/, ' ').trim() ==
                '<ul id="results"> <li>Grails</li> <li>Groovy</li> <li>Spring Boot</li> </ul>'

        where:
        action             | ajax
        'results'          | false
        'results'          | true
    }
}

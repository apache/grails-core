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
package functionaltests.requestpath

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.MultipartBody

/**
 * One request through the whole path, against a running server: the servlet filter chain, multipart
 * resolution, the dispatcher resolving the hidden method override, URL mapping selecting the route,
 * allowedMethods admitting it, params carrying the multipart fields and the uploaded file, a command object
 * bound from them, and the response rendered.
 *
 * <p>The pieces have unit coverage of their own; what this asserts is that they agree with each other in a
 * single round trip, and that a browser form still reaches a PUT route with no filter rewriting the method
 * ahead of the dispatcher.</p>
 */
@Integration
@Tag('http-client')
class RequestPathSpec extends Specification implements HttpClientSupport {

    def 'a multipart form POST naming PUT reaches the PUT route with its fields and file intact'() {
        given: 'what <g:uploadForm method="PUT"> submits'
        def body = MultipartBody.builder()
                .addPart('_method', 'PUT')
                .addPart('description', 'a description carried as a multipart field')
                .addPart('file', 'chapter.txt', 'text/plain', 'content'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/request-path/1', body)

        then: 'the mapping picked the PUT route, and allowedMethods admitted it as a PUT'
        response.assertStatus(200)
        response.json().action == 'update'

        and: 'the multipart text field reached params, and the file reached the request'
        response.json().params.description == 'a description carried as a multipart field'
        response.json().params.id == '1'
        response.json().filename == 'chapter.txt'

        and: 'a command object was bound from the same fields'
        response.json().commandBound == 'a description carried as a multipart field'

        and: 'while the request reports the method it actually arrived as'
        response.json().requestMethod == 'POST'
    }

    def 'a bare POST to the member URL reaches update'() {
        given: 'the shape AngularJS $resource and the clients modelled on it use, with no _method at all'
        when:
        def response = httpPostForm('/request-path/1', [description: 'posted without a parameter'])

        then: 'the route generated alongside PUT answers it - issue #9926'
        response.assertStatus(200)
        response.json().action == 'update'
        response.json().requestMethod == 'POST'
        response.json().commandBound == 'posted without a parameter'
    }

    def 'a form POST naming DELETE reaches the DELETE route'() {
        when: 'delete and update share the member URL, so only the parameter tells them apart'
        def response = httpPostForm('/request-path/1', [_method: 'DELETE'])

        then:
        response.assertStatus(200)
        response.json().action == 'delete'
        response.json().requestMethod == 'POST'
    }

    def 'a form POST naming PATCH reaches the PATCH route, which is not update'() {
        when:
        def response = httpPostForm('/request-path/1', [_method: 'PATCH', description: 'patched'])

        then: 'PATCH maps to its own action, so suppressing the parameter would have sent this to update'
        response.assertStatus(200)
        response.json().action == 'patch'
        response.json().commandBound == 'patched'
    }

    def 'a method a browser can send is not overridable'() {
        when: 'the override is deliberately narrower than the servlet filter it replaces'
        def response = httpPostForm('/request-path/1', [_method: 'GET'])

        then: 'it stays a POST, so it reaches update rather than becoming a read'
        response.assertStatus(200)
        response.json().action == 'update'
    }

    def 'a real DELETE still routes as itself'() {
        when:
        def response = httpDelete('/request-path/1')

        then:
        response.assertStatus(200)
        response.json().action == 'delete'
        response.json().requestMethod == 'DELETE'
    }
}

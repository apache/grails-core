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
package app4

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.MultipartBody
import spock.lang.Specification

/**
 * An upload past the configured limit fails when the container parses the request parts, and from then
 * on every parameter read on that request fails with it. The request has to survive that far enough to
 * reach the application's own error handling, including a {@code "413"} status code URL mapping - which
 * runs on the container's error dispatch, where the framework reads request parameters again.
 */
@Integration
class OversizedUploadFunctionalSpec extends Specification implements HttpClientSupport {

    def 'an upload past the configured limit is handled by the "413" status code mapping'() {
        given: 'a payload larger than the default grails.controllers.upload.maxRequestSize of 128000 bytes'
        def body = MultipartBody.builder()
                .addPart('file', 'huge.txt', 'text/plain', ('X' * 200000).bytes)
                .build()

        when:
        def response = httpPostMultipart('/upload/upload', body)

        then: 'the mapped controller action renders it, rather than the container serving its own error page'
        response.assertJson(413, [
                error    : 'Content Too Large',
                handledBy: 'errors.tooLarge'
        ])
    }

    def 'an upload within the configured limit reaches the controller'() {
        given:
        def body = MultipartBody.builder()
                .addPart('file', 'small.txt', 'text/plain', 'hello'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/upload/upload', body)

        then:
        response.assertJson(200, [
                controller: 'upload',
                action    : 'upload'
        ])
    }
}

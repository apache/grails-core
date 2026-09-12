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
package undertowapp

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.MultipartBody
import spock.lang.Specification

/**
 * Undertow applies the servlet multipart size limit while it reads the request entity, so an upload past
 * the limit is refused by the HTTP layer and the servlet is never dispatched. Unlike Tomcat and Jetty,
 * where the failure surfaces as a {@code MultipartException} during dispatch, no application code runs -
 * no filter, no handler, no {@code "413"} status code URL mapping - so the status is all the application
 * can contribute to. This pins that difference so a change in it is noticed.
 */
@Integration
class UndertowOversizedUploadSpec extends Specification implements HttpClientSupport {

    def 'an upload past the configured limit is refused by the container before the application sees it'() {
        given: 'a payload larger than the default grails.controllers.upload.maxRequestSize of 128000 bytes'
        def body = MultipartBody.builder()
                .addPart('file', 'huge.txt', 'text/plain', ('X' * 200000).bytes)
                .build()

        when:
        def response = httpPostMultipart('/upload/upload', body)

        then: 'the status is right, but the body is empty because the "413" mapping never runs'
        response.assertEquals(413, '')
    }

    def 'an upload within the configured limit reaches the controller'() {
        given:
        def body = MultipartBody.builder()
                .addPart('file', 'small.txt', 'text/plain', 'hello'.bytes)
                .build()

        when:
        def response = httpPostMultipart('/upload/upload', body)

        then:
        response.assertEquals(200, 'uploaded')
    }
}

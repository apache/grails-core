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

package example

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.http.client.HttpClient

@Integration
class BookControllerSpec extends Specification {

    @Shared
    HttpClient client

    @OnceBefore
    void init() {
        String baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(baseUrl.toURL())
    }

    void 'test books can be fetched'() {
        expect:
        client.toBlocking().retrieve('/book/grails').contains('The definitive Guide to Grails 2')
        !client.toBlocking().retrieve('/book/grails').contains('Groovy in Action')

        client.toBlocking().retrieve('/book/groovy').contains('Groovy in Action')
        !client.toBlocking().retrieve('/book/groovy').contains('The definitive Guide to Grails 2')
    }
}
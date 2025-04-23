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

package functionaltests

import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

@Integration(applicationClass = Application)
class AtResourceSpec extends Specification {

    def "A domain class annotated with @Resources exposes an endpoint"() {
        given:
        String baseUrl = "http://localhost:$serverPort"
        HttpClient client  = HttpClient.create(new URL(baseUrl))

        when:
        HttpResponse<String> rsp = client.toBlocking().exchange(HttpRequest.GET("/stuffs"), String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        cleanup:
        client.close()
    }


}

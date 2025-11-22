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

package issue11767.app


import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

@Integration
class GsonViewRespondSpec extends Specification {

    @Shared
    ObjectMapper objectMapper

    def setupSpec() {
        objectMapper = new ObjectMapper()
    }

    @Shared
    @AutoCleanup
    HttpClient httpClient

    void setup() {
        def baseUrl = "http://localhost:$serverPort"
        httpClient = HttpClient.create(baseUrl.toURL())
    }

    void 'respond with Error gson view'() {
        when: 'The app controller is visited on errorView'
        def request = HttpRequest.GET('/app/errorView?foo=Too+Short').accept(MediaType.APPLICATION_JSON)
        httpClient.toBlocking().exchange(request, String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNPROCESSABLE_ENTITY

        and:
        objectMapper.readTree(ex.response.body() as String) == objectMapper.readTree('''
            {
              "error": {
                "errors": [
                  {
                    "field": "foo",
                    "rejectedValue": "Too Short",
                    "message": "Property [foo] of class [class issue15228.app.ValidateableObject] with value [Too Short] is less than the minimum size of [10]"
                  }
                ]
              }
            }''')
    }

    void 'respond with gson view from action name'() {
        when: 'The app controller is visited on normalView'
        def request = HttpRequest.GET('/app/normalView?foo=Testing+normal+view').accept(MediaType.APPLICATION_JSON)
        def response = httpClient.toBlocking().exchange(request, String)

        then:
        objectMapper.readTree(response.body() as String) == objectMapper.readTree('''{
              "normal": {
                "foo": "Testing normal view"
              } 
           }''')
    }

    void 'respond with gson view from type'() {
        when: 'The app controller is visited on typeView'
        def request = HttpRequest.GET('/app/typeView?foo=Testing+type+view').accept(MediaType.APPLICATION_JSON)
        def response = httpClient.toBlocking().exchange(request, String)

        then:
        objectMapper.readTree(response.body() as String) == objectMapper.readTree('''{
              "type": {
                "foo": "Testing type view"
              } 
           }''')
    }

}

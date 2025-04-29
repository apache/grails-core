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

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import grails.web.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach

@Integration(applicationClass = Application)
@Rollback
class CircularSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "test deep rendering of circular domain relationships"() {
        when:"A GET is issued"
        HttpRequest request = HttpRequest.GET("/circular/show/1")
        HttpResponse<Map> resp = client.toBlocking().exchange(request, Map)
        def json = resp.body()

        then:"The REST resource is retrieved and the correct JSON is returned"
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        json.id == 1
        json.name == "topLevel"
        json.myEnum == "BAR"
        json.circulars.size() == 2
        json.circulars.find { it.id == 3 }.parent.id == 1
        json.circulars.find { it.id == 2 }.parent.id == 1
    }

    void "test nested template rendering of circular domain relationships"() {
        when:"A GET is issued"
        HttpRequest request = HttpRequest.GET("/circular/circular/1")
        HttpResponse<Map> resp = client.toBlocking().exchange(request, Map)
        def json = resp.body()

        then:"The REST resource is retrieved and the correct JSON is returned"
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        json.name == "topLevel"
        json.children.size() == 2
        json.children.find { it.name == "topLevel-3" }
        json.children.find { it.name == "topLevel-2" }
    }
}

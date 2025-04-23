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

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class VehicleSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test that domain subclasses render their properties"() {
        when:
        HttpRequest request = HttpRequest.GET('/vehicle/list')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then:"The correct response is returned"
        resp.status == HttpStatus.OK
        resp.body() == '[{"id":1,"route":"around town","maxPassengers":30},{"id":2,"make":"Subaru","model":"WRX","year":2016,"maxPassengers":4}]'

    }

    void "Test that domain association subclasses render their properties"() {
        when:
        HttpRequest request = HttpRequest.GET('/vehicle/garage')
        HttpResponse<Map> resp = client.toBlocking().exchange(request, Map)
        def json = resp.body()

        then:"The correct response is returned"
        resp.status == HttpStatus.OK

        json.id == 1
        json.owner == "Jay Leno"
        json.vehicles.find { it.id == 1 }.maxPassengers == 30
        json.vehicles.find { it.id == 1 }.route == "around town"
        json.vehicles.find { it.id == 2 }.maxPassengers == 4
        json.vehicles.find { it.id == 2 }.make == "Subaru"
        json.vehicles.find { it.id == 2 }.model == "WRX"
        json.vehicles.find { it.id == 2 }.year == 2016
    }
}

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

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class VehicleSpec extends Specification implements HttpClientSupport {

    void "Test that domain subclasses render their properties"() {
        when:
        def response = http('/vehicle/list')

        then: "The correct response is returned"
        response.assertJson(200, '''
            [
                {
                    "id": 1,
                    "route": "around town",
                    "maxPassengers": 30
                },
                {
                    "id": 2,
                    "make": "Subaru",
                    "model": "WRX",
                    "year": 2016,
                    "maxPassengers": 4
                }
            ]
        ''')
    }

    void "Test that domain association subclasses render their properties"() {
        when:
        def response = http('/vehicle/garage')

        then: "The correct response is returned"
        def json = response.assertStatus(200).json()
        json.id == 1
        json.owner == 'Jay Leno'
        json.vehicles.find { it.id == 1 }.maxPassengers == 30
        json.vehicles.find { it.id == 1 }.route == 'around town'
        json.vehicles.find { it.id == 2 }.maxPassengers == 4
        json.vehicles.find { it.id == 2 }.make == 'Subaru'
        json.vehicles.find { it.id == 2 }.model == 'WRX'
        json.vehicles.find { it.id == 2 }.year == 2016
    }
}

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

import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import spock.lang.Specification
import spock.lang.Tag

import grails.converters.JSON
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * With {@code grails.converters.json.legacy} set to {@code false}, as application.yml sets it, {@code render ... as JSON}
 * writes through the {@code JsonMapper} that Spring Boot auto-configures, with {@code spring.jackson.time-zone}, and
 * renders a lazy association, a Hibernate proxy, as a reference of its id without initializing it.
 */
@Integration(applicationClass = Application)
@Tag('http-client')
class JsonMapperConverterSpec extends Specification implements HttpClientSupport {

    Long shipmentId

    Long carrierId

    void setup() {
        Shipment.withNewTransaction {
            def carrier = new Carrier(name: 'Grails Freight').save(failOnError: true)
            carrierId = carrier.id
            shipmentId = new Shipment(shipped: new Date(0), carrier: carrier).save(failOnError: true, flush: true).id
        }
    }

    void cleanup() {
        Shipment.withNewTransaction {
            Shipment.list()*.delete()
            Carrier.list()*.delete(flush: true)
        }
    }

    void "a domain class instance renders through the application's JsonMapper, with a lazy association as a reference"() {
        when: 'the shipment is loaded in the request, so its carrier is a proxy'
        def response = http("/shipment/show/$shipmentId")

        then: 'the date is written in the time zone that spring.jackson.time-zone sets, where Grails 8 wrote UTC'
        response.assertEquals(200,
                '{"id":' + shipmentId + ',"shipped":"1969-12-31T19:00:00.000-05:00","carrier":{"id":' + carrierId + '}}')
    }

    void "rendering a lazy association as a reference leaves its proxy uninitialized"() {
        when: 'the shipment is loaded in a new session, so its carrier is a proxy, and rendered'
        def carrier = null
        boolean initializedBeforeRendering = true
        String json = null
        Shipment.withNewSession {
            def shipment = Shipment.get(shipmentId)
            carrier = shipment.@carrier
            initializedBeforeRendering = Hibernate.isInitialized(carrier)
            json = (shipment as JSON).toString()
        }

        then: 'the carrier is written as a reference without loading it'
        carrier instanceof HibernateProxy
        !initializedBeforeRendering
        json == '{"id":' + shipmentId + ',"shipped":"1969-12-31T19:00:00.000-05:00","carrier":{"id":' + carrierId + '}}'
        !Hibernate.isInitialized(carrier)
    }
}

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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpStatus
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class BookRestfulControllerSpec extends Specification implements HttpClientSupport {

    def "A RestfulController exposes an index endpoint for a domain class"() {
        when:
        def rsp = httpClient.exchange('/bookRestful', String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        and: 'the book persisted in BootStrap is retrieved'
        def json = new JsonSlurper().parseText(rsp.body()) as List<Map<String, Object>>
        json[0]['id'] != null
        json[0]['title'] == 'Example Book'
        json[0]['lastUpdated'] != null
        json[0]['dateCreated'] != null
    }
}

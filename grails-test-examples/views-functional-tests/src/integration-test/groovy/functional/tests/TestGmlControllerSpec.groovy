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
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import org.junit.jupiter.api.BeforeEach

@Integration(applicationClass = Application)
class TestGmlControllerSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test GML response from action that returns a model"() {
        when:"When an action that renders a GML view is requested"
        String uri = '/testGml/testView'
        HttpRequest request = HttpRequest.GET(uri)
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)
        GPathResult content = new XmlSlurper().parseText(rsp.body())

        then:"The XML view is rendered"
        content.car.size() == 1
        content.car[0].@make.text() == "Audi"
    }
}

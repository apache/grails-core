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

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Integration
class ConfigLoadingSpec extends Specification {

    @Shared
    @AutoCleanup
    HttpClient httpClient

    @OnceBefore
    void init() {
        def baseUrl = "http://localhost:$serverPort"
        httpClient = HttpClient.create(baseUrl.toURL())
    }

    @Unroll
    void '#beanType beans can load plugin config values'(String beanType, String expectedResponseValue) {

        when: 'The app controller is visited'
            def request = HttpRequest.GET('/app')
            String response = httpClient.toBlocking().retrieve(request, String)

        then: 'The value from the plugin is found'
            response.contains(expectedResponseValue)

        where:
            beanType                  || expectedResponseValue
            'Plugin Groovy Spring'    || 'Plugin Groovy Spring Bean - my.value2: this is value 2 from plugin.yml'
            'Plugin Groovy Micronaut' || 'Plugin Groovy Micronaut Bean - my.value2: this is value 2 from plugin.yml'
            'Plugin Java Micronaut'   || 'Plugin Java Micronaut Bean - my.value2: this is value 2 from plugin.yml'
            'App Groovy Micronaut'    || 'App Groovy Micronaut Bean - my.value2: this is value 2 from plugin.yml'
    }
}

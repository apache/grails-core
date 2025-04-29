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
import io.micronaut.http.MediaType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Unroll

@Integration(applicationClass = Application)
class ModelInterceptorIntSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    @Autowired
    ModelInterceptor modelInterceptor

    @Unroll
    void "interceptor should get model from #controller"() {
        given:
        def request = HttpRequest.GET("/$controller")
                .contentType(MediaType.APPLICATION_JSON)
        HttpResponse response = client.toBlocking().exchange(request, List<Map>)

        expect:
        response.status == HttpStatus.OK
        modelInterceptor.latestModel != null

        where:
        controller << ["modelAndView",
                       "respond",
                       "return"]
    }
}

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

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
class ModelInterceptorIntSpec extends Specification implements HttpClientSupport {

    @Autowired
    ModelInterceptor modelInterceptor

    @Unroll
    void "interceptor should get model from #controller"() {
        given:
        def response = http("/$controller")

        expect:
        response.assertStatus(200)
        modelInterceptor.latestModel != null

        where:
        controller << [
                'modelAndView',
                'respond',
                'return'
        ]
    }

    @Issue('https://github.com/apache/grails-core/issues/12081')
    void "interceptor after() can read model set by render(template:..., model:...) via TEMPLATE_MODEL fallback"() {
        given: "a controller that calls render(template: 'snippet', model: [title: 'x'])"
        def response = http('/renderTemplate')

        expect: "the HTTP response succeeds"
        response.assertStatus(200)

        and: "the interceptor's after() received the exact model passed to render(template:..., model:...)"
        modelInterceptor.latestModel == [title: 'x']
    }

    @Issue('https://github.com/apache/grails-core/issues/12081')
    void "interceptor after() reads only the model argument when render(template:...) also passes a bean"() {
        given: "a controller that calls render(template: 'snippet', bean: 'b', model: [title: 'x'])"
        def response = http('/renderTemplate/bean')

        expect: "the HTTP response succeeds"
        response.assertStatus(200)

        and: "the interceptor's after() received the model argument without the bean"
        modelInterceptor.latestModel == [title: 'x']
    }
}

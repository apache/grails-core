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
package grails.async.services

import java.util.concurrent.Executor

import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import grails.async.Promises
import grails.async.decorator.PromiseDecorator
import grails.async.web.WebPromises
import grails.util.GrailsWebMockUtil
import org.grails.async.factory.future.CompletableFuturePromiseFactory
import org.grails.plugins.web.async.GrailsWebRequestTaskDecorator
import org.grails.web.servlet.mvc.GrailsWebRequest

class WebPromisesSpec extends Specification {

    void setup() {
        GrailsWebRequestTaskDecorator taskDecorator = new GrailsWebRequestTaskDecorator()
        Executor executor = { Runnable task -> taskDecorator.decorate(task).run() }
        WebPromises.promiseFactory = new CompletableFuturePromiseFactory(executor)
    }

    void cleanup() {
        WebPromises.promiseFactory = null
        RequestContextHolder.resetRequestAttributes()
    }

    void 'Test web promises handling'() {

        setup:
            GrailsWebMockUtil.bindMockWebRequest()

        when: 'A promise is created'
            def webPromise = WebPromises.task {
                RequestContextHolder.currentRequestAttributes()
            }
            webPromise.get() != null

        then: 'Async was requested'
            def e = thrown(IllegalStateException)
            e.message.startsWith('Async support must be enabled')

        when: 'A normal promise is used'
            def promise = Promises.task { 'good' }

        then: 'No request is bound'
            promise.get() == 'good'

    }

    void 'multiple web tasks reuse Spring managed asynchronous processing'() {
        given:
        def servletContext = new MockServletContext()
        def response = new MockHttpServletResponse()
        def request = new MockHttpServletRequest(servletContext).tap {
            asyncSupported = true
        }
        RequestContextHolder.setRequestAttributes(new GrailsWebRequest(request, response, servletContext))

        when:
        def first = WebPromises.task { RequestContextHolder.currentRequestAttributes() }
        def second = WebPromises.task { RequestContextHolder.currentRequestAttributes() }

        then:
        first.get() instanceof GrailsWebRequest
        second.get() instanceof GrailsWebRequest
        request.asyncStarted
    }

    void 'explicit promise decorators are retained'() {
        given:
        PromiseDecorator decorator = { Closure original ->
            return { "decorated ${original.call()}" }
        }

        when:
        def promise = WebPromises.createPromise({ 'value' }, [decorator])

        then:
        promise.get() == 'decorated value'
    }
}

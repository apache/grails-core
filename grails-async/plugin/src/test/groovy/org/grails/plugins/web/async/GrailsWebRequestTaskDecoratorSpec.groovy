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
package org.grails.plugins.web.async

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import grails.util.GrailsWebMockUtil
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

class GrailsWebRequestTaskDecoratorSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'propagates and then clears the current Grails web request'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            assert GrailsWebRequest.lookup().currentRequest.is(original.currentRequest)
            assert !GrailsWebRequest.lookup().is(original)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        GrailsWebRequest.lookup() == null
    }

    void 'a task leaves the request thread finding its own web request on the request'() {
        given: 'a request being processed, and a task of it that runs on another thread'
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        HttpServletRequest request = original.currentRequest
        // Stands for the multipart request the dispatcher wraps the request in, which finds the web request
        // through the request attribute
        HttpServletRequest wrapper = new HttpServletRequestWrapper(request)
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {}

        when:
        runOnAnotherThread(decorated)

        then: 'the request still holds the request thread web request'
        request.getAttribute(GrailsApplicationAttributes.WEB_REQUEST).is(original)
        GrailsWebRequest.lookup(request).is(original)
        GrailsWebRequest.lookup(wrapper).is(original)
        GrailsWebRequest.lookup().is(original)
    }

    void 'the request thread web request keeps its state once a task of the request has run'() {
        given: 'params the request thread read before the dispatcher resolved the multipart request'
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        original.params.put('name', 'unresolved')
        HttpServletRequest multipart = new HttpServletRequestWrapper(original.currentRequest)
        runOnAnotherThread(new GrailsWebRequestTaskDecorator().decorate {})

        when: 'the dispatcher reports the multipart request it resolved, as it does after the filters'
        GrailsWebRequest.lookup(multipart).multipartRequestResolved()

        then: 'the params of the request thread are rebuilt, not those of the task'
        !original.params.containsKey('name')
    }

    void 'a task finds its own web request from the request while the request thread is processing it'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        HttpServletRequest request = original.currentRequest
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.bound = GrailsWebRequest.lookup()
            seen.fromRequest = GrailsWebRequest.lookup(request)
        }

        when:
        runOnAnotherThread(decorated)

        then: 'the task is bound to a web request of its own, which it finds from the request it was given'
        !seen.bound.is(original)
        seen.fromRequest.is(seen.bound)
    }

    void 'a task finds its own web request from a wrapper of its request, so its params stay its own'() {
        given: 'a request whose params the request thread has read'
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        HttpServletRequest request = original.currentRequest
        original.params.put('id', 'request')
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            // As data binding looks the web request up from the request it binds, which can be a wrapper
            GrailsWebRequest found = GrailsWebRequest.lookup(new HttpServletRequestWrapper(request))
            found.params.put('id', 'task')
            seen.found = found
            seen.bound = GrailsWebRequest.lookup()
        }

        when:
        runOnAnotherThread(decorated)

        then: 'the task wrote to its own params'
        seen.found.is(seen.bound)
        seen.bound.params.id == 'task'

        and: 'the request thread keeps its own'
        original.params.id == 'request'
    }

    void 'a task given a wrapper of the request finds its own web request from the request it wraps'() {
        given: 'a web request bound to a wrapper of the request, as a filter inside the web request filter makes'
        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext())
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request)
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), request,
                new MockHttpServletResponse())
        GrailsWebRequest wrapped = new GrailsWebRequest(wrapper, original.currentResponse, original.attributes)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate({
            seen.bound = GrailsWebRequest.lookup()
            seen.fromRequest = GrailsWebRequest.lookup(request)
        } as Runnable, wrapped)

        when:
        runOnAnotherThread(decorated)

        then:
        seen.bound.currentRequest.is(wrapper)
        seen.fromRequest.is(seen.bound)
    }

    void 'checked failures retain their identity and restore the previous context'() {
        given:
        def previous = GrailsWebMockUtil.bindMockWebRequest()
        def failure = new IOException('io')
        Runnable work = () -> { throw failure }
        def decorated = new GrailsWebRequestTaskDecorator().decorate(work)

        when:
        decorated.run()

        then:
        def observed = thrown(IOException)
        observed.is(failure)
        GrailsWebRequest.lookup().is(previous)
    }

    private static void runOnAnotherThread(Runnable task) {
        Throwable[] failure = new Throwable[1]
        Thread thread = new Thread({
            try {
                task.run()
            }
            catch (Throwable t) {
                failure[0] = t
            }
        })
        thread.start()
        thread.join()
        if (failure[0] != null) {
            throw failure[0]
        }
    }
}

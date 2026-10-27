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

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeUtils
import org.grails.web.mime.DefaultMimeTypeResolver
import org.grails.web.servlet.mvc.GrailsWebRequest
import spock.lang.Issue
import spock.lang.Specification

class GrailsWebRequestTaskDecoratorSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'propagates and then clears the current Grails web request'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        original.currentRequest.setAttribute('stage', 'processing')
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            assert !GrailsWebRequest.lookup().is(original)
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            assert request.getAttribute('stage') == 'processing'
            request.setAttribute('task', 'ran')
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then: 'the task saw the request it was started during, which is still active'
        GrailsWebRequest.lookup() == null
        original.currentRequest.getAttribute('task') == 'ran'
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

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that runs after the request completes sees a copy of the request'() {
        given: 'a request the container recycles once it completes, as Tomcat does'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.title = request.getParameter('title')
            seen.accept = request.getHeader('accept')
            seen.model = request.getAttribute('model')
            seen.locale = GrailsWebRequest.lookup().locale
            seen.session = request.getSession(false)
            seen.uri = request.requestURI
            request.setAttribute('task', 'ran')
            seen.task = request.getAttribute('task')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the request completes and is recycled before the task runs'
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        seen.title == 'Grails'
        seen.accept == 'application/json'
        seen.model == 'books'
        seen.locale == Locale.FRANCE
        seen.session.is(live.session)
        seen.uri == '/books/save'
        seen.task == 'ran'

        and: 'what the task set stayed with the task'
        live.getAttribute('task') == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that binds a map after the request completes resolves the request format from the copy'() {
        given:
        MockHttpServletRequest live = request()
        live.contentType = 'application/json'
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        MimeType resolved
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            resolved = MimeTypeUtils.resolveMimeType([name: 'Grails'], new DefaultMimeTypeResolver())
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        resolved == MimeType.JSON
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task still running when the request completes sees the request until then and the copy afterwards'() {
        given:
        MockHttpServletRequest live = request()
        live.setAttribute('stage', 'processing')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.before = request.getAttribute('stage')
            request.setAttribute('task', 'started')
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            seen.after = request.getAttribute('stage')
            seen.task = request.getAttribute('task')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the task starts while the request is processing'
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)

        then: 'what it sets reaches the request'
        live.getAttribute('task') == 'started'

        when: 'the request completes and is recycled while the task is running'
        live.setAttribute('stage', 'rendered')
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then: 'the task read the request as it completed'
        seen.before == 'processing'
        seen.after == 'rendered'
        seen.task == 'started'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task of a request processed asynchronously sees the request until the asynchronous processing completes'() {
        given: 'a request whose response is produced asynchronously'
        MockHttpServletRequest live = request()
        live.asyncSupported = true
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))
        live.startAsync()

        and:
        Runnable rendering = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest.lookup().currentRequest.setAttribute('rendered', 'by the task')
        }
        Map seen = [:]
        Runnable outliving = new GrailsWebRequestTaskDecorator().decorate {
            seen.rendered = GrailsWebRequest.lookup().currentRequest.getAttribute('rendered')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the dispatch that started the asynchronous processing ends, and the task the response waits for runs'
        original.requestCompleted()
        rendering.run()

        then: 'the task rendered through the request'
        live.getAttribute('rendered') == 'by the task'

        when: 'the asynchronous processing completes and the request is recycled'
        live.asyncContext.complete()
        recycled.set(true)
        outliving.run()

        then:
        seen.rendered == 'by the task'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task started from a task sees the request that task sees'() {
        given:
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        GrailsWebRequestTaskDecorator decorator = new GrailsWebRequestTaskDecorator()
        Map seen = [:]
        Runnable nested
        Runnable decorated = decorator.decorate {
            seen.outer = GrailsWebRequest.lookup().currentRequest
            nested = decorator.decorate {
                seen.inner = GrailsWebRequest.lookup().currentRequest
                seen.title = seen.inner.getParameter('title')
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()
        original.requestCompleted()
        recycled.set(true)
        nested.run()

        then:
        seen.inner.is(seen.outer)
        seen.title == 'Grails'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that outlives the request cannot do what only the live request can do'() {
        given:
        MockHttpServletRequest live = request()
        live.session = null
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map<String, Throwable> failures = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            [
                    inputStream: { request.inputStream },
                    session    : { request.getSession(true) },
                    dispatcher : { request.getRequestDispatcher('/books') },
                    async      : { request.startAsync() },
                    role       : { request.isUserInRole('admin') }
            ].each { String name, Closure operation ->
                try {
                    operation.call()
                }
                catch (Throwable failure) {
                    failures[name] = failure
                }
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        failures.keySet() == ['inputStream', 'session', 'dispatcher', 'async', 'role'] as Set
        failures.values().every { it instanceof IllegalStateException && it.message.startsWith('The request has completed') }
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext(), 'POST', '/books/save')
        request.addParameter('title', 'Grails')
        request.addHeader('Accept', 'application/json')
        request.setAttribute('model', 'books')
        request.addPreferredLocale(Locale.FRANCE)
        request.session = new MockHttpSession()
        return request
    }

    private static GrailsWebRequest bind(HttpServletRequest request) {
        GrailsWebRequest webRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), request.servletContext)
        RequestContextHolder.setRequestAttributes(webRequest)
        return webRequest
    }

    // Stands for the facade Tomcat gives the application, which fails once the request it stands for is recycled
    private static HttpServletRequest recyclable(MockHttpServletRequest request, AtomicBoolean recycled) {
        return (HttpServletRequest) Proxy.newProxyInstance(GrailsWebRequestTaskDecoratorSpec.classLoader,
                [HttpServletRequest] as Class[]) { proxy, method, args ->
            if (recycled.get()) {
                throw new IllegalStateException('The request object has been recycled and is no longer associated with this facade')
            }
            try {
                return method.invoke(request, args)
            }
            catch (InvocationTargetException e) {
                throw e.targetException
            }
        }
    }
}

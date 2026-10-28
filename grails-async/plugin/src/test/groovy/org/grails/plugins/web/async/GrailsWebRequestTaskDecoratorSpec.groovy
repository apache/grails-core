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

import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.multipart.MultipartHttpServletRequest

import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeUtils
import org.grails.web.mime.DefaultMimeTypeResolver
import org.grails.web.servlet.mvc.GrailsWebRequest
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

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
                    session   : { request.getSession(true) },
                    dispatcher: { request.getRequestDispatcher('/books') },
                    async     : { request.startAsync() },
                    role      : { request.isUserInRole('admin') },
                    parts     : { request.parts }
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
        failures.keySet() == ['session', 'dispatcher', 'async', 'role', 'parts'] as Set
        failures.values().every { it instanceof IllegalStateException && it.message.startsWith('The request has completed') }
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that outlives the request reads the body from the copy'() {
        given:
        MockHttpServletRequest live = request()
        live.contentType = 'application/json'
        live.characterEncoding = 'UTF-8'
        live.content = '{"title":"Grails"}'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.streamed = request.inputStream.getText('UTF-8')
            seen.read = request.reader.text
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        seen.streamed == '{"title":"Grails"}'
        seen.read == '{"title":"Grails"}'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the copy keeps what remains of a body the request was reading through getReader'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = 'first line\nsecond line'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))
        live.reader.readLine()

        and:
        String remaining = null
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            remaining = GrailsWebRequest.lookup().currentRequest.reader.text
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        remaining == 'second line'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a multipart request stays multipart for a task until the request completes'() {
        given:
        MockMultipartHttpServletRequest live = new MockMultipartHttpServletRequest(new MockServletContext())
        live.method = 'POST'
        live.requestURI = '/books/save'
        live.addFile(new MockMultipartFile('cover', 'cover.png', 'image/png', [1, 2, 3] as byte[]))
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable during = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.multipart = request instanceof MultipartHttpServletRequest
            seen.file = ((MultipartHttpServletRequest) request).getFile('cover')?.originalFilename
        }
        Runnable outliving = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            try {
                ((MultipartHttpServletRequest) request).getFile('cover')
            }
            catch (Throwable failure) {
                seen.afterwards = failure
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the request is still live'
        during.run()

        then: 'the task sees the multipart view a filter resolved before Grails'
        seen.multipart
        seen.file == 'cover.png'

        when: 'the request completes before the next task'
        original.requestCompleted()
        recycled.set(true)
        outliving.run()

        then: 'the upload needed the live request'
        seen.afterwards instanceof IllegalStateException
        seen.afterwards.message.startsWith('The request has completed')
        seen.afterwards.message.contains('getFile')
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'the copy reads a date header in the #form form as the live request does'() {
        given:
        MockHttpServletRequest live = request()
        live.addHeader('If-Modified-Since', value)
        long expected = live.getDateHeader('If-Modified-Since')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.date = GrailsWebRequest.lookup().currentRequest.getDateHeader('If-Modified-Since')
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        expected > 0
        seen.date == expected

        where:
        form       | value
        'RFC 1123' | 'Sun, 06 Nov 1994 08:49:37 GMT'
        'RFC 850'  | 'Sunday, 06-Nov-94 08:49:37 GMT'
        'asctime'  | 'Sun Nov 06 08:49:37 1994'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a date header that is not a date fails on the copy as on the live request'() {
        given:
        MockHttpServletRequest live = request()
        live.addHeader('If-Modified-Since', 'yesterday')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            try {
                GrailsWebRequest.lookup().currentRequest.getDateHeader('If-Modified-Since')
            }
            catch (Throwable failure) {
                seen.failure = failure
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        seen.failure instanceof IllegalArgumentException
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the request is released once the copy replaces it'() {
        given:
        MockHttpServletRequest live = request()
        GrailsWebRequest original = bind(live)
        WeakReference<MockHttpServletRequest> reference = new WeakReference<>(live)

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.title = GrailsWebRequest.lookup().currentRequest.getParameter('title')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the request completes and only the task still refers to it'
        original.requestCompleted()
        original = null
        live = null

        then: 'nothing holds the request any more'
        collected(reference)

        when:
        decorated.run()

        then: 'the task reads the copy'
        seen.title == 'Grails'
    }

    private static boolean collected(WeakReference<?> reference) {
        for (int attempt = 0; attempt < 100 && reference.get() != null; attempt++) {
            System.gc()
            Thread.sleep(10)
        }
        return reference.get() == null
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
        Class[] interfaces = ([HttpServletRequest] + request.getClass().interfaces.toList()).unique() as Class[]
        return (HttpServletRequest) Proxy.newProxyInstance(GrailsWebRequestTaskDecoratorSpec.classLoader,
                interfaces) { proxy, method, args ->
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

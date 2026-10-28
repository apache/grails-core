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
import jakarta.servlet.http.HttpServletRequestWrapper

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeUtils
import org.grails.web.mime.DefaultMimeTypeResolver
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
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
    void 'a task started from a task reads the request through a view of its own'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = '{"title":"Grails"}'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and: 'a task that reads the body and starts another task'
        GrailsWebRequestTaskDecorator decorator = new GrailsWebRequestTaskDecorator()
        Map seen = [:]
        Runnable nested
        Runnable decorated = decorator.decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            request.setAttribute('outer', 'ran')
            seen.outer = request.inputStream.getText('UTF-8')
            nested = decorator.decorate {
                HttpServletRequest inner = GrailsWebRequest.lookup().currentRequest
                seen.shared = inner.getAttribute('outer')
                seen.inner = inner.inputStream.getText('UTF-8')
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'both run after the request completed'
        original.requestCompleted()
        recycled.set(true)
        decorated.run()
        nested.run()

        then: 'each read the whole body, from the one copy whose attributes they share'
        seen.outer == '{"title":"Grails"}'
        seen.inner == '{"title":"Grails"}'
        seen.shared == 'ran'
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
    void 'every task of the request reads the whole unread body from the one copy'() {
        given:
        MockHttpServletRequest live = request()
        live.contentType = 'application/json'
        live.characterEncoding = 'UTF-8'
        live.content = '{"title":"Grails"}'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and: 'two tasks submitted during the request'
        Map seen = [:]
        Runnable first = new GrailsWebRequestTaskDecorator().decorate {
            seen.streamed = GrailsWebRequest.lookup().currentRequest.inputStream.getText('UTF-8')
        }
        Runnable second = new GrailsWebRequestTaskDecorator().decorate {
            seen.read = GrailsWebRequest.lookup().currentRequest.reader.text
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        first.run()
        second.run()

        then: 'each read the whole body, through a stream or a reader of its own'
        seen.streamed == '{"title":"Grails"}'
        seen.read == '{"title":"Grails"}'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task reads the copied body through one stream whose position persists, as on the live request'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = '{"title":"Grails"}'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            InputStream stream = request.inputStream
            seen.first = ((char) stream.read()).toString()
            seen.same = request.inputStream.is(stream)
            seen.rest = request.inputStream.getText('UTF-8')
            try {
                request.reader
            }
            catch (Throwable failure) {
                seen.reading = failure
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then: 'the stream is one, and a later read continues where the last ended'
        seen.first == '{'
        seen.same
        seen.rest == '"title":"Grails"}'

        and: 'the reader is unavailable once the stream is in use, as on the live request'
        seen.reading instanceof IllegalStateException
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
    void 'a multipart request stays multipart for a task, and once the request completes it has no uploads'() {
        given:
        MockMultipartHttpServletRequest live = new MockMultipartHttpServletRequest(new MockServletContext())
        live.method = 'POST'
        live.requestURI = '/books/save'
        live.addParameter('title', 'Grails')
        live.addHeader('Accept', 'application/json')
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
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            MultipartHttpServletRequest request = (MultipartHttpServletRequest) webRequest.currentRequest
            seen.params = webRequest.params
            seen.afterwards = [
                    file: request.getFile('cover'),
                    files: request.getFiles('cover'),
                    fileNames: request.fileNames.toList(),
                    fileMap: request.fileMap,
                    multiFileMap: request.multiFileMap,
                    contentType: request.getMultipartContentType('cover'),
                    partHeaders: request.getMultipartHeaders('cover'),
                    method: request.requestMethod,
                    accept: request.requestHeaders.getFirst('Accept')]
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

        then: 'the uploads went with the request, and params still has its parameters'
        seen.params.title == 'Grails'
        !seen.params.containsKey('cover')
        seen.afterwards.file == null
        seen.afterwards.files.isEmpty()
        seen.afterwards.fileNames.isEmpty()
        seen.afterwards.fileMap.isEmpty()
        seen.afterwards.multiFileMap.isEmpty()
        seen.afterwards.contentType == null
        seen.afterwards.partHeaders == null

        and: 'the request itself is described as it was'
        seen.afterwards.method == HttpMethod.POST
        seen.afterwards.accept == 'application/json'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task finds a multipart request inside an ordinary wrapper, as params does on the request itself'() {
        given: 'a multipart request a filter resolved, inside the wrapper of a filter after it'
        MockMultipartHttpServletRequest multipart = new MockMultipartHttpServletRequest(new MockServletContext())
        multipart.method = 'POST'
        multipart.requestURI = '/books/save'
        multipart.addParameter('title', 'Grails')
        MultipartFile cover = new MockMultipartFile('cover', 'cover.png', 'image/png', [1, 2, 3] as byte[])
        multipart.addFile(cover)
        GrailsWebRequest original = bind(new HttpServletRequestWrapper(multipart))

        and:
        Map seen = [:]
        Runnable during = readingUpload(seen, 'during')
        Runnable outliving = readingUpload(seen, 'afterwards')
        RequestContextHolder.resetRequestAttributes()

        when: 'the request is still live'
        during.run()

        then: 'params and the request find the upload inside the wrapper'
        seen.during.title == 'Grails'
        seen.during.cover.is(cover)
        seen.during.file.is(cover)

        when: 'the request completes before the next task'
        original.requestCompleted()
        outliving.run()

        then: 'the upload went with the request, and params still has its parameters'
        seen.afterwards.title == 'Grails'
        seen.afterwards.cover == null
        seen.afterwards.file == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the copy stands in for the multipart request the dispatcher resolved, without its uploads'() {
        given: 'a multipart request the dispatcher resolved from the request and published on it, as Grails does'
        MockHttpServletRequest live = request()
        MultipartFile cover = new MockMultipartFile('cover', 'cover.png', 'image/png', [1, 2, 3] as byte[])
        live.setAttribute(WebUtils.MULTIPART_HTTP_SERVLET_REQUEST_ATTRIBUTE,
                new LazyMultipartHttpServletRequest(live, new AtomicBoolean(), cover))
        GrailsWebRequest original = bind(live)

        and:
        Map seen = [:]
        Runnable during = readingUpload(seen, 'during')
        Runnable outliving = readingUpload(seen, 'afterwards')
        RequestContextHolder.resetRequestAttributes()

        when: 'the request is still live'
        during.run()

        then: 'params and the request find the upload through the attribute'
        seen.during.title == 'Grails'
        seen.during.cover.is(cover)
        seen.during.file.is(cover)

        when: 'the request completes before the next task'
        original.requestCompleted()
        outliving.run()

        then: 'the multipart request, which wraps the request, is not kept, and the copy has no uploads in its place'
        seen.afterwards.title == 'Grails'
        seen.afterwards.cover == null
        seen.afterwards.file == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task starts for a request wrapping one with an interface the class loader of the request cannot see'() {
        given: 'a request from a class loader of its own, inside a wrapper from the application class loader'
        GroovyClassLoader isolated = new GroovyClassLoader(GrailsWebRequestTaskDecoratorSpec.classLoader)
        Class<?> hidden = isolated.parseClass('interface IsolatedRequestView {}')
        MockHttpServletRequest live = request()
        HttpServletRequest inner = (HttpServletRequest) Proxy.newProxyInstance(isolated,
                [HttpServletRequest, hidden] as Class[]) { proxy, method, args ->
            try {
                return method.invoke(live, args)
            }
            catch (InvocationTargetException e) {
                throw e.targetException
            }
        }
        GrailsWebRequest original = bind(new HttpServletRequestWrapper(inner))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.title = request.getParameter('title')
            seen.hidden = hidden.isInstance(request)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then: 'the task request leaves out the interface, which a proxy of that class loader cannot implement'
        original.isActive()
        seen.title == 'Grails'
        !seen.hidden

        cleanup:
        isolated.close()
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the copy keeps the parameters of a multipart request that can no longer parse them as it completes'() {
        given: 'a lazily resolved multipart request whose body is gone by the time it completes'
        MockHttpServletRequest live = request()
        AtomicBoolean consumed = new AtomicBoolean()
        GrailsWebRequest original = bind(new LazyMultipartHttpServletRequest(live, consumed))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            seen.title = webRequest.currentRequest.getParameter('title')
            seen.params = webRequest.params
            seen.upload = ((MultipartHttpServletRequest) webRequest.currentRequest).getFile('cover')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'parsing the parameters fails as the request completes, as it does once the body is consumed'
        consumed.set(true)
        original.requestCompleted()
        decorated.run()

        then: 'the copy holds the parameters of the request the wrapper wraps'
        seen.title == 'Grails'
        seen.params.title == 'Grails'

        and: 'the upload went with the request'
        seen.upload == null
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
        given: 'a request attributes of which hold the request itself, as a filter may park it, its response, and the multipart request the dispatcher resolved from it'
        MockHttpServletRequest live = request()
        live.setAttribute('itself', live)
        live.setAttribute(WebUtils.MULTIPART_HTTP_SERVLET_REQUEST_ATTRIBUTE,
                new LazyMultipartHttpServletRequest(live, new AtomicBoolean()))
        GrailsWebRequest original = bind(live)
        live.setAttribute('response', original.currentResponse)
        WeakReference<MockHttpServletRequest> reference = new WeakReference<>(live)

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.title = request.getParameter('title')
            seen.itself = request.getAttribute('itself')
            seen.response = request.getAttribute('response')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the request completes and only the task still refers to it'
        original.requestCompleted()
        original = null
        live = null

        then: 'nothing holds the request any more, not even through its copied attributes'
        collected(reference)

        when:
        decorated.run()

        then: 'the task reads the copy, which left out what held the request'
        seen.title == 'Grails'
        seen.itself == null
        seen.response == null
    }

    // A task reading an upload as an action does, through params and through the multipart request
    private static Runnable readingUpload(Map seen, String stage) {
        return new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            seen[stage] = [title: webRequest.params.title, cover: webRequest.params.cover,
                           file: WebUtils.resolveMultipartRequest(webRequest.currentRequest)?.getFile('cover')]
        }
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
        // As the web request filter binds it, which also parks the web request in a request attribute
        WebUtils.storeGrailsWebRequest(webRequest)
        return webRequest
    }

    // Stands for a lazily resolving multipart wrapper, which parses the body for its parameters when they are
    // first read and can no longer do so once the body is consumed, as the request completes
    private static class LazyMultipartHttpServletRequest extends HttpServletRequestWrapper
            implements MultipartHttpServletRequest {

        private final AtomicBoolean consumed
        private final MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>()

        LazyMultipartHttpServletRequest(HttpServletRequest request, AtomicBoolean consumed, MultipartFile... uploads) {
            super(request)
            this.consumed = consumed
            for (MultipartFile upload : uploads) {
                files.add(upload.name, upload)
            }
        }

        private void parse() {
            if (consumed.get()) {
                throw new MultipartException('The multipart body was consumed before it was parsed')
            }
        }

        @Override
        String getParameter(String name) {
            parse()
            return super.getParameter(name)
        }

        @Override
        Enumeration<String> getParameterNames() {
            parse()
            return super.getParameterNames()
        }

        @Override
        String[] getParameterValues(String name) {
            parse()
            return super.getParameterValues(name)
        }

        @Override
        Map<String, String[]> getParameterMap() {
            parse()
            return super.getParameterMap()
        }

        @Override
        HttpMethod getRequestMethod() {
            return HttpMethod.valueOf(method)
        }

        @Override
        HttpHeaders getRequestHeaders() {
            return new HttpHeaders()
        }

        @Override
        HttpHeaders getMultipartHeaders(String paramOrFileName) {
            return null
        }

        @Override
        Iterator<String> getFileNames() {
            return files.keySet().iterator()
        }

        @Override
        MultipartFile getFile(String name) {
            return files.getFirst(name)
        }

        @Override
        List<MultipartFile> getFiles(String name) {
            return files.get(name) ?: Collections.<MultipartFile>emptyList()
        }

        @Override
        Map<String, MultipartFile> getFileMap() {
            return files.toSingleValueMap()
        }

        @Override
        MultiValueMap<String, MultipartFile> getMultiFileMap() {
            return files
        }

        @Override
        String getMultipartContentType(String paramOrFileName) {
            return files.getFirst(paramOrFileName)?.contentType
        }
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

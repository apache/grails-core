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
import java.security.Principal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
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
import org.springframework.web.util.ContentCachingRequestWrapper

import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import grails.web.servlet.mvc.GrailsParameterMap
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
    void 'each task sees the request it was given, though an earlier task of the request was given the request it wraps'() {
        given: 'a request the web request filter bound'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)
        GrailsWebRequest original = bind(facade)

        and: 'a task started then'
        List<Map> first = []
        Runnable started = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            first << [summary: webRequest.params.summary, marked: webRequest.currentRequest.getAttribute('marked')]
        }

        and: 'a task started later, while a web request is bound to a wrapper of the request, as a forward binds one'
        FieldsRequestWrapper wrapper = new FieldsRequestWrapper(facade)
        wrapper.addField('summary', 'A web framework')
        bind(wrapper)
        List<Map> later = []
        Runnable startedLater = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            later << [summary: webRequest.params.summary]
            webRequest.currentRequest.setAttribute('marked', "run ${later.size()}".toString())
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'both run while the request is live'
        startedLater.run()
        started.run()

        then: 'each sees the request it was given, and what one sets reaches the other'
        later == [[summary: 'A web framework']]
        first == [[summary: null, marked: 'run 1']]

        when: 'the request completes and is recycled, and both run again'
        original.requestCompleted()
        recycled.set(true)
        startedLater.run()
        started.run()

        then: 'each sees the copy of the request it was given, and the copies share the attributes'
        later[1] == [summary: 'A web framework']
        first[1] == [summary: null, marked: 'run 2']
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
    void 'the copy keeps the text fields only the multipart request the dispatcher resolved has, with the request value of a field both have'() {
        given: 'a multipart request the dispatcher resolved and published, with text fields the request does not have'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)
        LazyMultipartHttpServletRequest multipart = new LazyMultipartHttpServletRequest(facade, new AtomicBoolean())
        multipart.addField('summary', 'A web framework')
        multipart.addField('title', 'Overridden')
        facade.setAttribute(WebUtils.MULTIPART_HTTP_SERVLET_REQUEST_ATTRIBUTE, multipart)
        GrailsWebRequest original = bind(facade)

        and:
        Map seen = [:]
        Runnable during = readingFields(seen, 'during')
        Runnable outliving = readingFields(seen, 'afterwards')
        RequestContextHolder.resetRequestAttributes()

        when: 'the request is still live'
        during.run()

        then: 'params adds the text fields of the multipart request, keeping the value of the request'
        seen.during == [title: 'Grails', summary: 'A web framework']

        when: 'the request completes and is recycled before the next task'
        original.requestCompleted()
        recycled.set(true)
        outliving.run()

        then: 'the copy has what params had'
        seen.afterwards == [title: 'Grails', summary: 'A web framework']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'Groovy code calls the request of a task through its interfaces, though the request is written in Groovy'() {
        given: 'a request in a wrapper written in Groovy, as the requests of Grails unit tests are'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        FieldsRequestWrapper wrapper = new FieldsRequestWrapper(recyclable(live, recycled))
        wrapper.addField('summary', 'A web framework')
        GrailsWebRequest original = bind(wrapper)

        and: 'a task calling the request dynamically, as Groovy code does'
        List<Map> seen = []
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            def request = GrailsWebRequest.lookup().currentRequest
            Map read = [groovyObject: request instanceof GroovyObject, summary: request.getParameter('summary'),
                        uri: request.requestURI]
            try {
                request.addField('title', 'Changed')
            }
            catch (MissingMethodException ignored) {
                read.addField = 'missing'
            }
            seen << read
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the task runs while the request is live, and again once it has completed'
        decorated.run()
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then: 'the interfaces answer, and a method only the class of the request has is missing, as on any request'
        seen == [[groovyObject: false, summary: 'A web framework', uri: '/books/save', addField: 'missing']] * 2
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
    void 'a multipart request the container cannot parse is copied without parameters, as params reads it'() {
        given: 'a multipart request whose body the container refused to parse, as when an upload breaks a limit'
        UnparsableRequest live = new UnparsableRequest(new IllegalStateException('The upload exceeds the maximum size'))
        live.contentType = 'multipart/form-data; boundary=grails'
        boolean noLiveParams = new GrailsParameterMap(live).isEmpty()
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            seen.accept = webRequest.currentRequest.getHeader('Accept')
            seen.params = webRequest.params
            seen.title = webRequest.currentRequest.getParameter('title')
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then: 'the rest of the request is copied'
        seen.accept == 'application/json'

        and: 'it has no parameters, as params has none on the live request'
        noLiveParams
        seen.params.isEmpty()
        seen.title == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a request whose parameters cannot be read is still copied, and reading them fails as on the live request'() {
        given:
        IllegalArgumentException failure = new IllegalArgumentException('The parameters are malformed')
        UnparsableRequest live = new UnparsableRequest(failure)
        live.contentType = 'application/x-www-form-urlencoded'
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.accept = request.getHeader('Accept')
            seen.uri = request.requestURI
            try {
                request.getParameter('title')
            }
            catch (Throwable thrown) {
                seen.failure = thrown
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then: 'the rest of the request is copied'
        seen.accept == 'application/json'
        seen.uri == '/books/save'

        and: 'reading the parameters fails as it did on the live request'
        seen.failure.is(failure)
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

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task started during a forward reads the live request until the request that forwarded has been processed'() {
        given: 'a request the container recycles once it completes, without a session yet'
        MockHttpServletRequest live = request()
        live.session = null
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)

        and: 'processing that forwards, as an action does, and goes on once the forward returns'
        List<Map> seen = []
        Runnable decorated = null
        FilterChain processing = { ServletRequest request, ServletResponse response ->
            GrailsWebRequest outer = bind((HttpServletRequest) request)
            // The forward binds a web request of its own to the request the container dispatches, and completes it
            GrailsWebRequest forward = bind(new ForwardedRequest((HttpServletRequest) request, '/books/show'))
            decorated = readingLate(seen)
            forward.requestCompleted()
            WebUtils.storeGrailsWebRequest(outer)
            decorated.run()
            request.setAttribute('late', 'set after the forward')
            outer.requestCompleted()
        } as FilterChain

        when: 'the request is processed, and recycled before the task runs again'
        new TaskRequestFilter().doFilter(facade, new MockHttpServletResponse(), processing)
        recycled.set(true)
        RequestContextHolder.resetRequestAttributes()
        decorated.run()

        then: 'once the forward had returned, the task still read the live request, so it could create the session'
        seen[0] == [late: null, session: true, uri: '/books/show']
        live.session != null

        and: 'the copy holds what the request had once it had been processed'
        seen[1] == [late: 'set after the forward', session: true, uri: '/books/show']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task started before a forward that completes the web request it reused reads the live request until the request has been processed'() {
        given:
        MockHttpServletRequest live = request()
        live.session = null
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)

        and: 'processing that forwards through a request dispatcher, as a view of a forward does'
        List<Map> seen = []
        Runnable decorated = null
        FilterChain processing = { ServletRequest request, ServletResponse response ->
            GrailsWebRequest outer = bind((HttpServletRequest) request)
            decorated = readingLate(seen)
            // The dispatcher servlet the forward reaches reuses the active web request, and completes it as it returns
            outer.requestCompleted()
            decorated.run()
            request.setAttribute('late', 'set after the forward')
        } as FilterChain

        when:
        new TaskRequestFilter().doFilter(facade, new MockHttpServletResponse(), processing)
        recycled.set(true)
        RequestContextHolder.resetRequestAttributes()
        decorated.run()

        then:
        seen[0] == [late: null, session: true, uri: '/books/save']
        seen[1] == [late: 'set after the forward', session: true, uri: '/books/save']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a dispatch within one the filter already marks, such as an error page some containers dispatch, does not end its processing'() {
        given:
        MockHttpServletRequest live = request()
        live.session = null
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)

        and:
        List<Map> seen = []
        Runnable decorated = null
        FilterChain processing = { ServletRequest request, ServletResponse response ->
            GrailsWebRequest outer = bind((HttpServletRequest) request)
            decorated = readingLate(seen)
            new TaskRequestFilter().doFilter(request, response, { ServletRequest dispatched, ServletResponse rendered -> } as FilterChain)
            decorated.run()
            request.setAttribute('late', 'set after the error page')
            outer.requestCompleted()
        } as FilterChain

        when:
        new TaskRequestFilter().doFilter(facade, new MockHttpServletResponse(), processing)
        recycled.set(true)
        RequestContextHolder.resetRequestAttributes()
        decorated.run()

        then:
        seen[0] == [late: null, session: true, uri: '/books/save']
        seen[1] == [late: 'set after the error page', session: true, uri: '/books/save']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a request the filter marks that is processed asynchronously is copied once the asynchronous processing completes'() {
        given:
        MockHttpServletRequest live = request()
        live.asyncSupported = true
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)

        and: 'processing that starts the asynchronous processing and a task the response waits for'
        Map seen = [:]
        Runnable rendering = null
        Runnable outliving = null
        FilterChain processing = { ServletRequest request, ServletResponse response ->
            GrailsWebRequest outer = bind((HttpServletRequest) request)
            request.startAsync()
            rendering = new GrailsWebRequestTaskDecorator().decorate {
                GrailsWebRequest.lookup().currentRequest.setAttribute('rendered', 'by the task')
            }
            outliving = new GrailsWebRequestTaskDecorator().decorate {
                seen.rendered = GrailsWebRequest.lookup().currentRequest.getAttribute('rendered')
            }
            outer.requestCompleted()
        } as FilterChain

        when: 'the dispatch ends, and the task the response waits for runs'
        new TaskRequestFilter().doFilter(facade, new MockHttpServletResponse(), processing)
        RequestContextHolder.resetRequestAttributes()
        rendering.run()

        then: 'the task rendered through the live request'
        live.getAttribute('rendered') == 'by the task'

        when: 'the asynchronous processing completes and the request is recycled'
        live.asyncContext.complete()
        recycled.set(true)
        outliving.run()

        then:
        seen.rendered == 'by the task'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'each copy keeps the forward attributes its own request shows, when the task given the #first was started first'() {
        given: 'a request, and a forward of it, which keeps the forward attributes itself'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)
        GrailsWebRequest original = bind(facade)
        GrailsWebRequest forward = bind(new ForwardedRequest(facade, '/books/show'))

        and: 'a task given each, in that order'
        Map<String, GrailsWebRequest> webRequests = [request: original, forward: forward]
        Map seen = [:]
        Map<String, Runnable> tasks = [:]
        for (String name : order) {
            WebUtils.storeGrailsWebRequest(webRequests[name])
            tasks[name] = readingDispatch(seen, name)
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the request completes and is recycled before the tasks run'
        forward.requestCompleted()
        original.requestCompleted()
        recycled.set(true)
        tasks.forward.run()
        tasks.request.run()

        then: 'the copy of the forward is a forward from the request, and the copy of the request is not'
        seen.forward == [forward: true, forwardURI: '/books/save', uri: '/books/show', shared: null]
        seen.request == [forward: false, forwardURI: '/books/save', uri: '/books/save', shared: 'set by the forward task']

        where:
        first     | order
        'request' | ['request', 'forward']
        'forward' | ['forward', 'request']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task changes an attribute a forward keeps itself on the copy of the forward only, as on the live request'() {
        given: 'a request holding an include attribute, and a forward of it, which answers the dispatch attributes itself'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)
        facade.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI, '/books/sidebar')
        GrailsWebRequest original = bind(facade)

        and:
        Map seen = [:]
        Runnable onRequest = readingAttributes(seen, 'request')
        GrailsWebRequest forward = bind(new ForwardedRequest(facade, '/books/show'))
        Runnable changing = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, '/books/list')
            request.removeAttribute(RequestDispatcher.FORWARD_SERVLET_PATH)
            request.setAttribute('model', 'authors')
        }
        Runnable onForward = readingAttributes(seen, 'forward')
        RequestContextHolder.resetRequestAttributes()

        when: 'the request completes and is recycled, and a task changes attributes of the copy of the forward'
        forward.requestCompleted()
        original.requestCompleted()
        recycled.set(true)
        changing.run()
        onRequest.run()
        onForward.run()

        then: 'the forward attributes changed on the copy of the forward only, and the shared attribute on both'
        seen.forward == [forwardURI: '/books/list', servletPath: null, include: null, model: 'authors',
                         names: [RequestDispatcher.FORWARD_REQUEST_URI]]
        seen.request == [forwardURI: null, servletPath: null, include: '/books/sidebar', model: 'authors',
                         names: [RequestDispatcher.INCLUDE_REQUEST_URI]]
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'params of each copy reads what it read on the live request, when the task given the #first was started first'() {
        given: 'a multipart request the dispatcher resolved and published, and a forward that adds a parameter'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)
        LazyMultipartHttpServletRequest multipart = new LazyMultipartHttpServletRequest(facade, new AtomicBoolean())
        multipart.addField('summary', 'A web framework')
        facade.setAttribute(WebUtils.MULTIPART_HTTP_SERVLET_REQUEST_ATTRIBUTE, multipart)
        GrailsWebRequest original = bind(facade)
        ForwardedRequest forwarded = new ForwardedRequest(facade, '/books/show')
        forwarded.addField('page', '2')
        GrailsWebRequest forward = bind(forwarded)

        and: 'a task given each, in that order'
        Map<String, GrailsWebRequest> webRequests = [request: original, forward: forward]
        Map seen = [:]
        Map<String, Runnable> tasks = [:]
        for (String name : order) {
            WebUtils.storeGrailsWebRequest(webRequests[name])
            tasks[name] = readingParams(seen, name)
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'both run while the request is live'
        tasks.values()*.run()
        Map during = new LinkedHashMap(seen)

        and: 'again once the request has completed and been recycled'
        forward.requestCompleted()
        original.requestCompleted()
        recycled.set(true)
        tasks.values()*.run()

        then: 'params added the text field of the multipart request to each, and the parameter of the forward to its own'
        during.request == [title: 'Grails', summary: 'A web framework', page: null]
        during.forward == [title: 'Grails', summary: 'A web framework', page: '2']
        seen == during

        where:
        first     | order
        'request' | ['request', 'forward']
        'forward' | ['forward', 'request']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the body of a multipart request is not copied, so an upload rejected for its size is not read'() {
        given: 'a multipart request whose body the container refused to parse for its size'
        UnparsableRequest live = new UnparsableRequest(new IllegalStateException('The upload exceeds the maximum size'))
        live.contentType = 'multipart/form-data; boundary=grails'
        live.body = new ArrivingBody(16 * 1024 * 1024)
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.body = GrailsWebRequest.lookup().currentRequest.inputStream.bytes.length
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then: 'nothing of the upload was read, and the copy has no body'
        live.body.consumed == 0
        seen.body == 0
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'a body longer than the copy keeps is not read whole, and reading it from the copy fails, when the request was reading it through #source'() {
        given: 'a request with a long body nothing read, or that was being read through a reader'
        StreamingRequest live = new StreamingRequest()
        live.contentType = 'application/octet-stream'
        live.body = new ArrivingBody(16 * 1024 * 1024)
        if (reading) {
            live.reader.read()
        }
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map<String, Throwable> failures = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            [stream: { request.inputStream }, reader: { request.reader }].each { String name, Closure operation ->
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

        then: 'what was read of the body is about what the copy keeps'
        live.body.consumed < 4 * 1024 * 1024

        and: 'the copy has no body to read'
        failures.keySet() == ['stream', 'reader'] as Set
        failures.values().every { it instanceof IllegalStateException && it.message.startsWith('The request has completed') }

        where:
        source       | reading
        'the stream' | false
        'a reader'   | true
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a body as long as the copy keeps is copied whole'() {
        given:
        StreamingRequest live = new StreamingRequest()
        live.contentType = 'application/octet-stream'
        live.body = new ArrivingBody(CompletedHttpServletRequest.BODY_LIMIT)
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.body = GrailsWebRequest.lookup().currentRequest.inputStream.bytes.length
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then:
        seen.body == CompletedHttpServletRequest.BODY_LIMIT
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task started during a forward of a wrapped request keeps the forward once the container restored the wrapper'() {
        given: 'a request a filter wrapped, which every dispatch of the request is given'
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(recyclable(live, recycled))

        and: 'processing that forwards it, as Tomcat does: inserting its own wrapper underneath, then removing it'
        Map<String, List<Map>> seen = [request: [], forward: []]
        Runnable onRequest = null
        Runnable onForward = null
        FilterChain processing = { ServletRequest request, ServletResponse response ->
            GrailsWebRequest outer = bind(wrapped)
            onRequest = readingForward(seen.request)
            ForwardedRequest forwarded = new ForwardedRequest((HttpServletRequest) wrapped.request, '/books/show')
            forwarded.addField('page', '2')
            wrapped.request = forwarded
            GrailsWebRequest forward = bind(wrapped)
            onForward = readingForward(seen.forward)
            forward.requestCompleted()
            wrapped.request = forwarded.request
            WebUtils.storeGrailsWebRequest(outer)
            onForward.run()
            onRequest.run()
            outer.requestCompleted()
        } as FilterChain

        when: 'the request is processed, and recycled before the tasks run again'
        new TaskRequestFilter().doFilter(wrapped, new MockHttpServletResponse(), processing)
        recycled.set(true)
        RequestContextHolder.resetRequestAttributes()
        onForward.run()
        onRequest.run()

        then: 'the task started during the forward keeps the forward, its parameters too, once it has returned'
        seen.forward == [[uri: '/books/show', forward: true, forwardURI: '/books/save', page: '2']] * 2

        and: 'the task started before it keeps the request'
        seen.request == [[uri: '/books/save', forward: false, forwardURI: '/books/save', page: null]] * 2
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a request processed asynchronously keeps who made it, though the thread completing it does not know'() {
        given: 'a request whose user only the thread processing it knows, as with a security filter'
        ThreadLocal<Principal> signedIn = new ThreadLocal<>()
        MockHttpServletRequest live = request()
        live.asyncSupported = true
        AtomicBoolean recycled = new AtomicBoolean()
        AuthenticatedRequest authenticated = new AuthenticatedRequest(recyclable(live, recycled), signedIn)

        and: 'processing that starts the asynchronous processing and a task'
        Map seen = [:]
        Runnable decorated = null
        FilterChain processing = { ServletRequest request, ServletResponse response ->
            GrailsWebRequest outer = bind(authenticated)
            request.startAsync()
            decorated = new GrailsWebRequestTaskDecorator().decorate {
                HttpServletRequest current = GrailsWebRequest.lookup().currentRequest
                seen.user = [principal: current.userPrincipal?.name, remoteUser: current.remoteUser, authType: current.authType]
            }
            outer.requestCompleted()
        } as FilterChain

        when: 'the security filter around the dispatch signs the user in and out, and the processing completes after it'
        signedIn.set([getName: { 'alice' }] as Principal)
        try {
            new TaskRequestFilter().doFilter(authenticated, new MockHttpServletResponse(), processing)
        }
        finally {
            signedIn.remove()
        }
        live.asyncContext.complete()
        recycled.set(true)
        RequestContextHolder.resetRequestAttributes()
        decorated.run()

        then:
        seen.user == [principal: 'alice', remoteUser: 'alice', authType: 'FORM']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'a task reading the body through #source when the request completes reads on where it stopped'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = content.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and: 'a task that reads the start of the body, and the rest through the same stream or reader once the request has completed'
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            Object body = open.call(GrailsWebRequest.lookup().currentRequest)
            seen.start = start.call(body)
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            seen.rest = rest.call(body)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then:
        seen.start == first
        seen.rest == remaining

        where:
        source       | content                   | first        | remaining     | open                                    | start                                         | rest
        'the stream' | 'abcdef'                  | 'a'          | 'bcdef'       | { HttpServletRequest r -> r.inputStream } | { InputStream s -> ((char) s.read()).toString() } | { InputStream s -> s.getText('UTF-8') }
        'a reader'   | 'first line\nsecond line' | 'first line' | 'second line' | { HttpServletRequest r -> r.reader }      | { BufferedReader s -> s.readLine() }              | { BufferedReader s -> s.text }
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a wrapper whose parameters cannot be read fails the same once the request completes, as on the live request'() {
        given: 'an ordinary wrapper that fails to read its parameters, around a request that has them'
        IllegalArgumentException failure = new IllegalArgumentException('The parameters are malformed')
        MockHttpServletRequest live = request()
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(new FailingParametersRequest(recyclable(live, recycled), failure))

        and:
        List<Throwable> failures = []
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            try {
                GrailsWebRequest.lookup().currentRequest.getParameter('title')
            }
            catch (Throwable thrown) {
                failures << thrown
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the task reads the parameters while the request is live, and once it has completed'
        decorated.run()
        original.requestCompleted()
        recycled.set(true)
        decorated.run()

        then: 'both reads fail as the wrapper does, rather than reading the parameters of the request it wraps'
        failures.size() == 2
        failures.every { it.is(failure) }
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task marks and resets the body reader while the request is live, and across its completion'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = 'first line\nsecond line'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and: 'a task that reads a prefix and resets, then marks again, and resets once the request has completed'
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            BufferedReader reader = GrailsWebRequest.lookup().currentRequest.reader
            seen.markable = reader.markSupported()
            reader.mark(100)
            seen.prefix = readCharacters(reader, 5)
            reader.reset()
            seen.first = reader.readLine()
            reader.mark(100)
            seen.secondPrefix = readCharacters(reader, 3)
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            reader.reset()
            seen.second = reader.readLine()
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then:
        seen == [markable: true, prefix: 'first', first: 'first line', secondPrefix: 'sec', second: 'second line']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task marks and resets the body stream when the live stream supports it, across the completion of the request'() {
        given:
        StreamingRequest live = new StreamingRequest()
        live.markableBody = new MarkableBody('abcdef'.getBytes('UTF-8'))
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            InputStream stream = GrailsWebRequest.lookup().currentRequest.inputStream
            seen.markable = stream.markSupported()
            stream.mark(10)
            seen.prefix = new String(stream.readNBytes(2), 'UTF-8')
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            stream.reset()
            seen.rest = stream.getText('UTF-8')
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then:
        seen == [markable: true, prefix: 'ab', rest: 'abcdef']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'an attribute a task sets while the request is being copied is in the copy'() {
        given: 'a request whose copy reads the body while a task sets an attribute'
        StreamingRequest live = new StreamingRequest()
        CountDownLatch copying = new CountDownLatch(1)
        AtomicReference<Thread> setter = new AtomicReference<>()
        live.pausingBody = new PausingBody(copying, setter)
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Runnable setting = new GrailsWebRequestTaskDecorator().decorate {
            copying.await(10, TimeUnit.SECONDS)
            GrailsWebRequest.lookup().currentRequest.setAttribute('late', 'set during the copy')
        }
        Map seen = [:]
        Runnable reading = new GrailsWebRequestTaskDecorator().decorate {
            seen.late = GrailsWebRequest.lookup().currentRequest.getAttribute('late')
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the task sets the attribute once the copy has taken the attributes and is reading the body'
        Thread task = Thread.start { setting.run() }
        setter.set(task)
        original.requestCompleted()
        recycled.set(true)
        task.join(10000)
        reading.run()

        then:
        seen.late == 'set during the copy'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    @Unroll
    void 'the copy keeps what a reader that read ahead holds, when #reader read the first line of the body'() {
        given: 'a request whose reader reads ahead while its stream stays available, as ContentCachingRequestWrapper does'
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = 'first line\nsecond line'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        ContentCachingRequestWrapper caching = new ContentCachingRequestWrapper(recyclable(live, recycled), 1024)
        GrailsWebRequest original = bind(caching)

        and:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            BufferedReader body = byTask ? request.reader : null
            if (byTask) {
                seen.first = body.readLine()
            }
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            seen.rest = (body ?: request.reader).text
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        if (!byTask) {
            seen.first = caching.reader.readLine()
        }
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then:
        seen == [first: 'first line', rest: 'second line']

        where:
        reader        | byTask
        'a task'      | true
        'the request' | false
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the body reader of a task keeps a line ended by a carriage return and its line feed within a mark, as BufferedReader does'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = 'abcd\r\nxy'.getBytes('UTF-8')
        bind(live)

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            BufferedReader reader = GrailsWebRequest.lookup().currentRequest.reader
            reader.mark(5)
            seen.line = reader.readLine()
            reader.reset()
            seen.again = reader.readLine()
            seen.next = reader.readLine()
            reader.mark(Integer.MAX_VALUE)
            reader.reset()
            reader.mark(Integer.MAX_VALUE)
            seen.last = reader.readLine()
            reader.reset()
            seen.lastAgain = reader.readLine()
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        seen == [line: 'abcd', again: 'abcd', next: 'xy', last: null, lastAgain: null]
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a line ended by a carriage return is read without waiting for what follows, as BufferedReader reads it'() {
        given: 'a body of which only a line ended by a carriage return has arrived'
        StreamingRequest live = new StreamingRequest()
        CountDownLatch more = new CountDownLatch(1)
        live.pausingBody = new TricklingBody('cmd1\r'.getBytes('UTF-8'), more)
        bind(live)

        and: 'a task reading the line with a mark set'
        Map seen = [:]
        CountDownLatch read = new CountDownLatch(1)
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            BufferedReader reader = GrailsWebRequest.lookup().currentRequest.reader
            reader.mark(100)
            seen.line = reader.readLine()
            read.countDown()
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }

        then: 'the line is read before anything else arrives'
        read.await(5, TimeUnit.SECONDS)
        seen.line == 'cmd1'

        cleanup:
        more.countDown()
        task?.join(10000)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the copy reads the rest of the body from the reader a task took, though the request hands out a new reader each time'() {
        given:
        MockHttpServletRequest live = request()
        live.characterEncoding = 'UTF-8'
        live.content = 'first line\nsecond line'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(new NewReaderEachTime(recyclable(live, recycled)))

        and:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            BufferedReader reader = GrailsWebRequest.lookup().currentRequest.reader
            seen.first = reader.readLine()
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            seen.rest = reader.text
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then:
        seen == [first: 'first line', rest: 'second line']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a body a wrapper fails to read as the request completes fails a task reading it, and the rest of the request is copied'() {
        given: 'a wrapper that rejects a body longer than it caches, as a subclass of ContentCachingRequestWrapper does'
        IllegalArgumentException rejected = new IllegalArgumentException('The body is too long')
        MockHttpServletRequest live = request()
        live.content = 'a body longer than the wrapper caches'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(new RejectingRequestWrapper(recyclable(live, recycled), 5, rejected))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.accept = request.getHeader('Accept')
            try {
                request.inputStream.read()
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
        seen.accept == 'application/json'
        seen.failure.is(rejected)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the copy keeps the rest of a body a task reads through a reader in the encoding that reader decodes'() {
        given: 'a request without an encoding, in a wrapper that decodes the body as UTF-8'
        MockHttpServletRequest live = request()
        live.content = 'h\u00e9llo\n\u20acuro'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        HttpServletRequest facade = recyclable(live, recycled)
        GrailsWebRequest original = bind(facade)
        Runnable onRequest = new GrailsWebRequestTaskDecorator().decorate { }
        GrailsWebRequest decoding = bind(new Utf8Request(facade))

        and:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            BufferedReader reader = GrailsWebRequest.lookup().currentRequest.reader
            seen.first = reader.readLine()
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            seen.rest = reader.text
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        decoding.requestCompleted()
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)
        onRequest.run()

        then:
        seen == [first: 'h\u00e9llo', rest: '\u20acuro']
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a body whose rest cannot be read as the request completes fails a read from the copy, as on the request, not ending early'() {
        given: 'a request whose client aborts the upload once the copy reads the rest'
        IOException aborted = new IOException('The client aborted the upload')
        StreamingRequest live = new StreamingRequest()
        live.pausingBody = new AbortingBody(aborted)
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.accept = request.getHeader('Accept')
            InputStream body = request.inputStream
            try {
                body.read()
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
        seen.accept == 'application/json'
        seen.failure.is(aborted)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a body reader a task closed stays closed once the request has completed'() {
        given:
        MockHttpServletRequest live = request()
        live.content = 'first line\nsecond line'.getBytes('UTF-8')
        AtomicBoolean recycled = new AtomicBoolean()
        GrailsWebRequest original = bind(recyclable(live, recycled))

        and:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            BufferedReader reader = GrailsWebRequest.lookup().currentRequest.reader
            reader.close()
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            try {
                reader.readLine()
            }
            catch (IOException failure) {
                seen.failure = failure.message
            }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        recycled.set(true)
        completed.countDown()
        task.join(10000)

        then:
        seen.failure == 'Stream closed'
    }

    private static String readCharacters(Reader reader, int count) {
        char[] read = new char[count]
        int total = 0
        while (total < count) {
            int last = reader.read(read, total, count - total)
            if (last == -1) {
                break
            }
            total += last
        }
        return new String(read, 0, total)
    }

    // A task reading how its request was dispatched, and a parameter only a forward adds
    private static Runnable readingForward(List<Map> seen) {
        return new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            HttpServletRequest request = webRequest.currentRequest
            seen << [uri: request.requestURI, forward: WebUtils.isForward(request), forwardURI: WebUtils.getForwardURI(request),
                     page: webRequest.params.page]
        }
    }

    // A task reading what changes on the request after a forward, and creating the session, which only the live
    // request can do
    private static Runnable readingLate(List<Map> seen) {
        return new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen << [late: request.getAttribute('late'), session: request.getSession(true) != null, uri: request.requestURI]
        }
    }

    // A task reading how its request was dispatched, as WebUtils tells it, and setting an attribute every task shares
    private static Runnable readingDispatch(Map seen, String name) {
        return new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen[name] = [forward: WebUtils.isForward(request), forwardURI: WebUtils.getForwardURI(request),
                          uri: request.requestURI, shared: request.getAttribute('shared')]
            request.setAttribute('shared', "set by the $name task".toString())
        }
    }

    private static Runnable readingAttributes(Map seen, String name) {
        return new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen[name] = [forwardURI: request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI),
                          servletPath: request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH),
                          include: request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI),
                          model: request.getAttribute('model'),
                          names: Collections.list(request.attributeNames).findAll { String attribute ->
                              attribute.startsWith('jakarta.servlet.')
                          }.sort()]
        }
    }

    private static Runnable readingParams(Map seen, String name) {
        return new GrailsWebRequestTaskDecorator().decorate {
            GrailsParameterMap params = GrailsWebRequest.lookup().params
            seen[name] = [title: params.title, summary: params.summary, page: params.page]
        }
    }

    // A task reading an upload as an action does, through params and through the multipart request
    private static Runnable readingUpload(Map seen, String stage) {
        return new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            seen[stage] = [title: webRequest.params.title, cover: webRequest.params.cover,
                           file: WebUtils.resolveMultipartRequest(webRequest.currentRequest)?.getFile('cover')]
        }
    }

    // A task reading the fields of a form as an action does, through params
    private static Runnable readingFields(Map seen, String stage) {
        return new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            seen[stage] = [title: webRequest.params.title, summary: webRequest.params.summary]
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

    // Stands for a wrapper whose parameters have fields the request it wraps does not have, such as the wrapper of
    // FormContentFilter, or a multipart request, which reads the text fields of the body; a field wins over the
    // request's value, as it does on a multipart request
    private static class FieldsRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> fields = new LinkedHashMap<>()

        FieldsRequestWrapper(HttpServletRequest request) {
            super(request)
        }

        void addField(String name, String value) {
            fields.put(name, [value] as String[])
        }

        @Override
        String getParameter(String name) {
            String[] values = getParameterMap().get(name)
            return values ? values[0] : null
        }

        @Override
        Enumeration<String> getParameterNames() {
            return Collections.enumeration(getParameterMap().keySet())
        }

        @Override
        String[] getParameterValues(String name) {
            return getParameterMap().get(name)
        }

        @Override
        Map<String, String[]> getParameterMap() {
            Map<String, String[]> parameters = new LinkedHashMap<>(super.getParameterMap())
            parameters.putAll(fields)
            return parameters
        }
    }

    // Stands for a lazily resolving multipart wrapper, which parses the body for its parameters when they are
    // first read and can no longer do so once the body is consumed, as the request completes
    private static class LazyMultipartHttpServletRequest extends FieldsRequestWrapper
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

        @Override
        Map<String, String[]> getParameterMap() {
            if (consumed.get()) {
                throw new MultipartException('The multipart body was consumed before it was parsed')
            }
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

    // Stands for the request a container dispatches for a forward, such as Tomcat's, which answers the dispatch
    // attributes itself, and whose parameters can have those of the query string of the forward
    private static class ForwardedRequest extends FieldsRequestWrapper {

        private final String uri
        private final Map<String, Object> dispatchAttributes = new LinkedHashMap<>()

        ForwardedRequest(HttpServletRequest request, String uri) {
            super(request)
            this.uri = uri
            dispatchAttributes.put(RequestDispatcher.FORWARD_REQUEST_URI, request.requestURI)
            dispatchAttributes.put(RequestDispatcher.FORWARD_SERVLET_PATH, request.requestURI)
        }

        @Override
        Object getAttribute(String name) {
            return dispatchAttribute(name) ? dispatchAttributes.get(name) : super.getAttribute(name)
        }

        @Override
        Enumeration<String> getAttributeNames() {
            Set<String> names = new LinkedHashSet<>()
            for (String name : Collections.list(super.getAttributeNames())) {
                if (!dispatchAttribute(name)) {
                    names.add(name)
                }
            }
            names.addAll(dispatchAttributes.keySet())
            return Collections.enumeration(names)
        }

        @Override
        void setAttribute(String name, Object value) {
            if (!dispatchAttribute(name)) {
                super.setAttribute(name, value)
            }
            else if (value == null) {
                dispatchAttributes.remove(name)
            }
            else {
                dispatchAttributes.put(name, value)
            }
        }

        @Override
        void removeAttribute(String name) {
            if (dispatchAttribute(name)) {
                dispatchAttributes.remove(name)
            }
            else {
                super.removeAttribute(name)
            }
        }

        @Override
        String getRequestURI() {
            return uri
        }

        @Override
        DispatcherType getDispatcherType() {
            return DispatcherType.FORWARD
        }

        private static boolean dispatchAttribute(String name) {
            return name.startsWith('jakarta.servlet.forward.') || name.startsWith('jakarta.servlet.include.')
        }
    }

    // Stands for a request whose user only the thread processing it knows, as Spring Security's wrapper reads it
    // from the security context of the thread
    private static class AuthenticatedRequest extends HttpServletRequestWrapper {

        private final ThreadLocal<Principal> signedIn

        AuthenticatedRequest(HttpServletRequest request, ThreadLocal<Principal> signedIn) {
            super(request)
            this.signedIn = signedIn
        }

        @Override
        Principal getUserPrincipal() {
            return signedIn.get()
        }

        @Override
        String getRemoteUser() {
            return signedIn.get()?.name
        }

        @Override
        String getAuthType() {
            return signedIn.get() == null ? null : 'FORM'
        }
    }

    // Stands for an ordinary wrapper that cannot read its parameters, which fails every read of them
    private static class FailingParametersRequest extends HttpServletRequestWrapper {

        private final RuntimeException failure

        FailingParametersRequest(HttpServletRequest request, RuntimeException failure) {
            super(request)
            this.failure = failure
        }

        @Override
        String getParameter(String name) {
            throw failure
        }

        @Override
        Enumeration<String> getParameterNames() {
            throw failure
        }

        @Override
        String[] getParameterValues(String name) {
            throw failure
        }

        @Override
        Map<String, String[]> getParameterMap() {
            throw failure
        }
    }

    // Stands for a body that arrives as it is read, counting what was read of it
    private static class ArrivingBody extends ServletInputStream {

        private final long length
        long consumed

        ArrivingBody(long length) {
            this.length = length
        }

        @Override
        int read() {
            if (consumed >= length) {
                return -1
            }
            consumed++
            return 120
        }

        @Override
        int read(byte[] destination, int offset, int count) {
            if (count == 0) {
                return 0
            }
            if (consumed >= length) {
                return -1
            }
            int filled = (int) Math.min((long) count, length - consumed)
            Arrays.fill(destination, offset, offset + filled, (byte) 120)
            consumed += filled
            return filled
        }

        @Override
        boolean isFinished() {
            return consumed >= length
        }

        @Override
        boolean isReady() {
            return true
        }

        @Override
        void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException()
        }
    }

    // Stands for a body whose stream supports a mark
    private static class MarkableBody extends ServletInputStream {

        private final ByteArrayInputStream content

        MarkableBody(byte[] content) {
            this.content = new ByteArrayInputStream(content)
        }

        @Override
        int read() {
            return content.read()
        }

        @Override
        boolean markSupported() {
            return true
        }

        @Override
        void mark(int readLimit) {
            content.mark(readLimit)
        }

        @Override
        void reset() {
            content.reset()
        }

        @Override
        boolean isFinished() {
            return content.available() == 0
        }

        @Override
        boolean isReady() {
            return true
        }

        @Override
        void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException()
        }
    }

    // Stands for a body of which only the start has arrived, and the rest arrives once it is let through
    private static class TricklingBody extends ServletInputStream {

        private final ByteArrayInputStream arrived
        private final CountDownLatch more

        TricklingBody(byte[] arrived, CountDownLatch more) {
            this.arrived = new ByteArrayInputStream(arrived)
            this.more = more
        }

        @Override
        int read() {
            int read = arrived.read()
            if (read == -1) {
                more.await(10, TimeUnit.SECONDS)
            }
            return read
        }

        @Override
        int read(byte[] destination, int offset, int length) {
            if (arrived.available() == 0) {
                more.await(10, TimeUnit.SECONDS)
                return -1
            }
            return arrived.read(destination, offset, length)
        }

        @Override
        int available() {
            return arrived.available()
        }

        @Override
        boolean isFinished() {
            return false
        }

        @Override
        boolean isReady() {
            return true
        }

        @Override
        void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException()
        }
    }

    // Stands for a body whose client aborts the upload as the rest of it is read
    private static class AbortingBody extends ServletInputStream {

        private final IOException aborted

        AbortingBody(IOException aborted) {
            this.aborted = aborted
        }

        @Override
        int read() {
            throw aborted
        }

        @Override
        boolean isFinished() {
            return false
        }

        @Override
        boolean isReady() {
            return true
        }

        @Override
        void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException()
        }
    }

    // Stands for a wrapper that decodes the body as UTF-8, whatever the request says
    private static class Utf8Request extends HttpServletRequestWrapper {

        private BufferedReader decoded

        Utf8Request(HttpServletRequest request) {
            super(request)
        }

        @Override
        String getCharacterEncoding() {
            return 'UTF-8'
        }

        @Override
        BufferedReader getReader() {
            if (decoded == null) {
                decoded = new BufferedReader(new InputStreamReader(getInputStream(), 'UTF-8'))
            }
            return decoded
        }
    }

    // Stands for a wrapper caching the body that hands out a new reader of its stream each time it is asked
    private static class NewReaderEachTime extends HttpServletRequestWrapper {

        NewReaderEachTime(HttpServletRequest request) {
            super(request)
        }

        @Override
        BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), 'UTF-8'))
        }
    }

    // Stands for a subclass of ContentCachingRequestWrapper that rejects a body longer than it caches
    private static class RejectingRequestWrapper extends ContentCachingRequestWrapper {

        private final RuntimeException rejection

        RejectingRequestWrapper(HttpServletRequest request, int limit, RuntimeException rejection) {
            super(request, limit)
            this.rejection = rejection
        }

        @Override
        protected void handleContentOverflow(int contentCacheLimit) {
            throw rejection
        }
    }

    // Stands for a body the copy reads only once the task setting an attribute is calling the request
    private static class PausingBody extends ServletInputStream {

        private final CountDownLatch copying
        private final AtomicReference<Thread> setter

        PausingBody(CountDownLatch copying, AtomicReference<Thread> setter) {
            this.copying = copying
            this.setter = setter
        }

        @Override
        int read() {
            copying.countDown()
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline && !calling(setter.get())) {
                Thread.sleep(5)
            }
            return -1
        }

        // Waiting for the copy to end, or done setting the attribute, as the old code let it
        private static boolean calling(Thread thread) {
            return thread != null && (thread.state in [Thread.State.WAITING, Thread.State.BLOCKED, Thread.State.TERMINATED])
        }

        @Override
        boolean isFinished() {
            return false
        }

        @Override
        boolean isReady() {
            return true
        }

        @Override
        void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException()
        }
    }

    // Stands for a request whose body arrives as it is read, through a stream or a reader
    private static class StreamingRequest extends MockHttpServletRequest {

        ArrivingBody body
        ServletInputStream markableBody
        ServletInputStream pausingBody
        private BufferedReader bodyReader

        StreamingRequest() {
            super(new MockServletContext(), 'POST', '/books/save')
            addHeader('Accept', 'application/json')
        }

        @Override
        ServletInputStream getInputStream() {
            if (markableBody != null || pausingBody != null) {
                return markableBody ?: pausingBody
            }
            if (body == null) {
                return super.getInputStream()
            }
            if (bodyReader != null) {
                throw new IllegalStateException('getReader() has already been called for this request')
            }
            return body
        }

        @Override
        BufferedReader getReader() {
            InputStream source = markableBody ?: pausingBody ?: body
            if (source == null) {
                return super.getReader()
            }
            if (bodyReader == null) {
                bodyReader = new BufferedReader(new InputStreamReader(source, 'UTF-8'))
            }
            return bodyReader
        }
    }

    // Stands for a request whose parameters the container cannot parse, which fails every read of them
    private static class UnparsableRequest extends StreamingRequest {

        private final RuntimeException failure

        UnparsableRequest(RuntimeException failure) {
            this.failure = failure
        }

        @Override
        String getParameter(String name) {
            throw failure
        }

        @Override
        Enumeration<String> getParameterNames() {
            throw failure
        }

        @Override
        String[] getParameterValues(String name) {
            throw failure
        }

        @Override
        Map<String, String[]> getParameterMap() {
            throw failure
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

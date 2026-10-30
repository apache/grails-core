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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.ServletRequestEvent
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.Part

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.util.ContentCachingRequestWrapper

import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeUtils
import org.grails.web.mime.DefaultMimeTypeResolver
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Issue
import spock.lang.Specification

class GrailsWebRequestTaskDecoratorSpec extends Specification {

    private static final String COMPLETED = 'The request has completed, so a task that outlives it can no longer use its'

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'propagates and then clears the current Grails web request'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            assert GrailsWebRequest.lookup().currentRequest.request.is(original.currentRequest)
            assert !GrailsWebRequest.lookup().is(original)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        GrailsWebRequest.lookup() == null
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
    void 'while the request is live a task calls the request and response it was started during, and unwrapping them finds them'() {
        given:
        MockHttpServletRequest live = new MockHttpServletRequest('POST', '/books/save')
        live.addParameter('title', 'Grails')
        live.setAttribute('model', 'books')
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(live, 1024)
        MockHttpServletResponse liveResponse = new MockHttpServletResponse()
        GrailsWebRequest bound = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), live, liveResponse)
        GrailsWebRequest original = new GrailsWebRequest(wrapped, liveResponse, bound.attributes)
        RequestContextHolder.requestAttributes = original

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            HttpServletResponse response = GrailsWebRequest.lookup().currentResponse
            seen.title = request.getParameter('title')
            seen.model = request.getAttribute('model')
            request.setAttribute('task', 'ran')
            response.setHeader('X-Task', 'ran')
            seen.cachingWrapper = org.springframework.web.util.WebUtils.getNativeRequest(request, ContentCachingRequestWrapper)
            seen.mock = org.springframework.web.util.WebUtils.getNativeRequest(request, MockHttpServletRequest)
            seen.mockResponse = org.springframework.web.util.WebUtils.getNativeResponse(response, MockHttpServletResponse)
            seen.wrapperFor = request.isWrapperFor(live) && request.isWrapperFor(MockHttpServletRequest)
            seen.responseWrapperFor = response.isWrapperFor(liveResponse) && response.isWrapperFor(MockHttpServletResponse)
        }

        when:
        decorated.run()

        then:
        seen.title == 'Grails'
        seen.model == 'books'
        live.getAttribute('task') == 'ran'
        liveResponse.getHeader('X-Task') == 'ran'
        seen.cachingWrapper.is(wrapped)
        seen.mock.is(live)
        seen.mockResponse.is(liveResponse)
        seen.wrapperFor
        seen.responseWrapperFor

        and: 'the request keeps its own web request, which the container uses after the tasks are detached'
        live.getAttribute(GrailsApplicationAttributes.WEB_REQUEST).is(bound)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'once the request has completed, a task no longer finds a web request, and what it took from the request throws'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        live.setAttribute('model', 'books')
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            HttpServletRequest request = webRequest.currentRequest
            HttpServletResponse response = webRequest.currentResponse
            seen.live = request.getAttribute('model')
            started.countDown()
            completed.await(10, TimeUnit.SECONDS)
            seen.lookup = GrailsWebRequest.lookup()
            seen.lookupOfRequest = GrailsWebRequest.lookup(request)
            seen.attribute = failureOf { request.getAttribute('model') }
            seen.contentType = failureOf { response.contentType }
            seen.committed = failureOf { response.committed }
            seen.webRequestAttribute = failureOf { webRequest.getAttribute('model', 0) }
            seen.unwrapped = request.request
            seen.unwrappedResponse = response.response
            seen.unwrappedCall = failureOf { request.request.getAttribute('model') }
            seen.native = org.springframework.web.util.WebUtils.getNativeRequest(request, MockHttpServletRequest)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        Thread task = Thread.start { decorated.run() }
        assert started.await(10, TimeUnit.SECONDS)
        original.requestCompleted()
        completed.countDown()
        task.join(10000)

        then:
        seen.live == 'books'
        seen.containsKey('lookup')
        seen.lookup == null
        seen.containsKey('lookupOfRequest')
        seen.lookupOfRequest == null
        [seen.attribute, seen.contentType, seen.committed, seen.webRequestAttribute, seen.unwrappedCall].every {
            it instanceof IllegalStateException && it.message.startsWith(COMPLETED)
        }
        seen.attribute.message == "$COMPLETED request"
        seen.contentType.message == "$COMPLETED response"
        !seen.unwrapped.is(live)
        !(seen.unwrappedResponse instanceof MockHttpServletResponse)
        seen.native == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that binds a map after the request completes resolves the format as code outside a request does'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        ((MockHttpServletRequest) original.currentRequest).contentType = 'application/json'

        and:
        MimeType resolved
        MimeType resolvedLive
        Runnable live = new GrailsWebRequestTaskDecorator().decorate {
            resolvedLive = MimeTypeUtils.resolveMimeType([name: 'Grails'], new DefaultMimeTypeResolver())
        }
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            resolved = MimeTypeUtils.resolveMimeType([name: 'Grails'], new DefaultMimeTypeResolver())
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        live.run()
        original.requestCompleted()
        decorated.run()

        then:
        resolvedLive == MimeType.JSON
        resolved == MimeType.ALL
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task that starts after the request has completed runs without a web request'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.lookup = GrailsWebRequest.lookup()
            seen.attributes = RequestContextHolder.requestAttributes
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        original.requestCompleted()
        decorated.run()

        then:
        seen.containsKey('lookup')
        seen.lookup == null
        seen.attributes == null
        ((MockHttpServletRequest) original.currentRequest).getAttribute(GrailsApplicationAttributes.WEB_REQUEST).is(original)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task started from a task has wrappers of its own of the request that task sees, and both are detached when the request completes'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        HttpServletRequestWrapper forward = new HttpServletRequestWrapper(live)
        Map seen = [:]
        Runnable child
        Runnable parent = new GrailsWebRequestTaskDecorator().decorate {
            TaskRequest request = (TaskRequest) GrailsWebRequest.lookup().currentRequest
            seen.parentRequest = request
            // The container wraps the parent's request for a forward while the child is started
            request.request = forward
            child = new GrailsWebRequestTaskDecorator().decorate {
                seen.childRequest = GrailsWebRequest.lookup().currentRequest
                seen.childResponse = GrailsWebRequest.lookup().currentResponse
            }
            request.request = live
        }
        Runnable later
        RequestContextHolder.resetRequestAttributes()

        when:
        parent.run()
        child.run()

        then:
        seen.childRequest instanceof TaskRequest
        !seen.childRequest.is(seen.parentRequest)
        seen.childRequest.request.is(forward)
        seen.parentRequest.request.is(live)
        seen.childResponse instanceof TaskResponse
        seen.childResponse.response.is(original.currentResponse)

        when:
        original.requestCompleted()
        later = new GrailsWebRequestTaskDecorator().decorate({ seen.laterLookup = GrailsWebRequest.lookup() },
                new GrailsWebRequest((HttpServletRequest) seen.childRequest, original.currentResponse, original.attributes))
        later.run()

        then:
        failureOf { seen.childRequest.getAttribute('anything') }.message == "$COMPLETED request"
        failureOf { seen.parentRequest.getAttribute('anything') }.message == "$COMPLETED request"
        seen.containsKey('laterLookup')
        seen.laterLookup == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'the body stream, reader and response output a task took while the request was live throw once it has completed'() {
        given:
        MockHttpServletRequest live = new MockHttpServletRequest('POST', '/books/save')
        live.content = 'first line\nsecond line\n'.bytes
        MockHttpServletRequest liveForReader = new MockHttpServletRequest('POST', '/books/save')
        liveForReader.content = 'first line\nsecond line\n'.bytes
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), live, new MockHttpServletResponse())
        GrailsWebRequest other = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), liveForReader, new MockHttpServletResponse())

        and:
        Map taken = [:]
        Runnable streams = new GrailsWebRequestTaskDecorator().decorate({
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            HttpServletResponse response = GrailsWebRequest.lookup().currentResponse
            taken.stream = request.inputStream
            taken.sameStream = request.inputStream.is(taken.stream)
            taken.firstByte = ((ServletInputStream) taken.stream).read()
            taken.output = response.outputStream
            ((ServletOutputStream) taken.output).write('out'.bytes)
        }, original)
        Runnable readers = new GrailsWebRequestTaskDecorator().decorate({
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            HttpServletResponse response = GrailsWebRequest.lookup().currentResponse
            taken.reader = request.reader
            taken.firstLine = ((BufferedReader) taken.reader).readLine()
            taken.writer = response.writer
            ((PrintWriter) taken.writer).print('written')
            ((PrintWriter) taken.writer).flush()
        }, other)
        RequestContextHolder.resetRequestAttributes()

        when:
        streams.run()
        readers.run()
        original.requestCompleted()
        other.requestCompleted()

        then:
        taken.sameStream
        taken.firstByte == ('f' as char) as int
        taken.firstLine == 'first line'
        ((MockHttpServletResponse) original.currentResponse).contentAsString == 'out'
        ((MockHttpServletResponse) other.currentResponse).contentAsString == 'written'

        and: 'each throws once the request has completed'
        failureOf { ((ServletInputStream) taken.stream).read() }.message == "$COMPLETED request body"
        failureOf { ((ServletInputStream) taken.stream).close() }.message == "$COMPLETED request body"
        failureOf { ((BufferedReader) taken.reader).readLine() }.message == "$COMPLETED request body"
        failureOf { ((ServletOutputStream) taken.output).write(1) }.message == "$COMPLETED response output"
        failureOf { ((PrintWriter) taken.writer).print('late') }.message == "$COMPLETED response output"

        and: 'nothing more was read or written'
        live.inputStream.readAllBytes().length == 'irst line\nsecond line\n'.length()
        ((MockHttpServletResponse) other.currentResponse).contentAsString == 'written'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'what the request returns that the container reuses is a snapshot, which stays readable after the request completes'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        live.addParameter('title', 'Grails')
        live.addHeader('Accept', 'application/json')
        Map taken = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            taken.parameters = request.parameterMap
            taken.headerNames = request.headerNames
            taken.parameterNames = request.parameterNames
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()
        original.requestCompleted()
        live.removeAllParameters()
        live.removeHeader('Accept')

        then:
        taken.parameters.title == ['Grails'] as String[]
        Collections.list((Enumeration) taken.headerNames) == ['Accept']
        Collections.list((Enumeration) taken.parameterNames) == ['title']

        when:
        taken.parameters.put('other', ['x'] as String[])

        then:
        thrown(UnsupportedOperationException)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'nothing is read from the request as it completes, so a body or upload nothing read stays unread'() {
        given:
        ReadCountingRequest live = new ReadCountingRequest()
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), live, new MockHttpServletResponse())
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            GrailsWebRequest.lookup().currentRequest.getAttribute('anything')
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()
        original.requestCompleted()

        then:
        live.reads == []
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a call a task is making on the request finishes before the request completes, and the next one throws'() {
        given:
        BlockingRequest live = new BlockingRequest()
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), live, new MockHttpServletResponse())
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.during = request.getAttribute('slow')
            seen.after = failureOf { request.getAttribute('slow') }
        }
        RequestContextHolder.resetRequestAttributes()

        when: 'the request completes while the task is inside a call to it'
        Thread task = Thread.start { decorated.run() }
        assert live.entered.await(10, TimeUnit.SECONDS)
        Thread completing = Thread.start { original.requestCompleted() }
        waitUntilBlocked(completing)

        then: 'completing waits for the call'
        completing.alive

        when:
        live.release.countDown()
        completing.join(10000)
        task.join(10000)

        then:
        !completing.alive
        seen.during == 'slow value'
        seen.after.message == "$COMPLETED request"
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'with the listener, a request completes when the container is done with it, not when a forward completes its web request'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        MockHttpServletRequest live = new MockHttpServletRequest(servletContext)
        TaskRequestListener listener = new TaskRequestListener()
        listener.requestInitialized(new ServletRequestEvent(servletContext, live))
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(servletContext, live, new MockHttpServletResponse())
        HttpServletRequest taskRequest
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            taskRequest = GrailsWebRequest.lookup().currentRequest
        }
        RequestContextHolder.resetRequestAttributes()
        decorated.run()
        live.setAttribute('stage', 'after the forward')

        when: 'the web request completes as a forward returns'
        original.requestCompleted()

        then:
        taskRequest.getAttribute('stage') == 'after the forward'

        when: 'the container is done with the request'
        listener.requestDestroyed(new ServletRequestEvent(servletContext, live))

        then:
        failureOf { taskRequest.getAttribute('stage') }.message == "$COMPLETED request"
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a request processed asynchronously completes once its asynchronous processing completes'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        MockHttpServletRequest live = new MockHttpServletRequest(servletContext)
        live.asyncSupported = true
        TaskRequestListener listener = new TaskRequestListener()
        listener.requestInitialized(new ServletRequestEvent(servletContext, live))
        GrailsWebMockUtil.bindMockWebRequest(servletContext, live, new MockHttpServletResponse())
        HttpServletRequest taskRequest
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            taskRequest = GrailsWebRequest.lookup().currentRequest
        }
        RequestContextHolder.resetRequestAttributes()
        decorated.run()
        live.startAsync()

        when: 'the dispatch ends with asynchronous processing started'
        listener.requestDestroyed(new ServletRequestEvent(servletContext, live))
        live.setAttribute('stage', 'async')

        then:
        taskRequest.getAttribute('stage') == 'async'

        when:
        live.asyncContext.complete()

        then:
        failureOf { taskRequest.getAttribute('stage') }.message == "$COMPLETED request"
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a wrapper the container sets on the task request while live is called, and setting one once the request completed throws'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        TaskRequest taskRequest
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            taskRequest = (TaskRequest) GrailsWebRequest.lookup().currentRequest
        }
        RequestContextHolder.resetRequestAttributes()
        decorated.run()
        HttpServletRequestWrapper forward = new HttpServletRequestWrapper(live) {
            @Override
            String getRequestURI() {
                return '/forwarded'
            }
        }

        when:
        taskRequest.request = forward

        then:
        taskRequest.request.is(forward)
        taskRequest.requestURI == '/forwarded'

        when:
        taskRequest.request = live
        original.requestCompleted()
        taskRequest.request = forward

        then:
        IllegalStateException failure = thrown()
        failure.message == "$COMPLETED request"
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'once the request has completed, a task that outlives it no longer holds the request or response'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        WeakReference<HttpServletRequest> request = new WeakReference<>(original.currentRequest)
        WeakReference<HttpServletResponse> response = new WeakReference<>(original.currentResponse)
        Map taken = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            taken.request = GrailsWebRequest.lookup().currentRequest
            taken.response = GrailsWebRequest.lookup().currentResponse
            taken.stream = GrailsWebRequest.lookup().currentRequest.inputStream
            taken.output = GrailsWebRequest.lookup().currentResponse.outputStream
        }
        RequestContextHolder.resetRequestAttributes()
        decorated.run()

        when:
        original.requestCompleted()
        original = null
        for (int i = 0; i < 50 && (request.get() != null || response.get() != null); i++) {
            System.gc()
            Thread.sleep(20)
        }

        then:
        taken.request != null && taken.stream != null && taken.output != null
        request.get() == null
        response.get() == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task first started during an include completes with the request, though the include removes the attributes it added'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        MockHttpServletRequest live = new MockHttpServletRequest(servletContext)
        TaskRequestListener listener = new TaskRequestListener()
        listener.requestInitialized(new ServletRequestEvent(servletContext, live))
        GrailsWebMockUtil.bindMockWebRequest(servletContext, live, new MockHttpServletResponse())
        Set<String> beforeInclude = Collections.list(live.attributeNames) as Set<String>

        when: 'the first task starts during an include, which then removes the attributes added during it, as Spring does'
        HttpServletRequest taskRequest
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            taskRequest = GrailsWebRequest.lookup().currentRequest
        }
        decorated.run()
        Collections.list(live.attributeNames).findAll { !(it in beforeInclude) }.each { live.removeAttribute(it) }
        listener.requestDestroyed(new ServletRequestEvent(servletContext, live))

        then:
        failureOf { taskRequest.getAttribute('anything') }.message == "$COMPLETED request"
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task started once the container is done with the request, such as from an error page rendered after that, runs without it'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        MockHttpServletRequest live = new MockHttpServletRequest(servletContext)
        TaskRequestListener listener = new TaskRequestListener()
        listener.requestInitialized(new ServletRequestEvent(servletContext, live))
        GrailsWebMockUtil.bindMockWebRequest(servletContext, live, new MockHttpServletResponse())
        listener.requestDestroyed(new ServletRequestEvent(servletContext, live))

        and:
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.lookup = GrailsWebRequest.lookup()
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        seen.containsKey('lookup')
        seen.lookup == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task submitted with a web request whose request the container has recycled runs without it'() {
        given:
        RecyclableRequest live = new RecyclableRequest()
        GrailsWebRequest stale = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), live, new MockHttpServletResponse())
        live.recycled = true
        RequestContextHolder.resetRequestAttributes()
        Map seen = [:]
        Runnable task = { seen.lookup = GrailsWebRequest.lookup() }

        when:
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate(task, stale)
        decorated.run()

        then:
        decorated.is(task)
        seen.containsKey('lookup')
        seen.lookup == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'an attribute value a task reads is the object the request holds'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        Map<String, Object> model = [title: 'Grails']
        Set<String> types = ['application/json'] as Set
        live.setAttribute('model', model)
        live.setAttribute('types', types)
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.model = request.getAttribute('model')
            seen.types = request.getAttribute('types')
            ((Map) seen.model).put('author', 'Graeme')
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        seen.model.is(model)
        seen.types.is(types)
        model.author == 'Graeme'
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task finds its own web request through its request, and does not replace the web request the request is bound to'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen.attribute = request.getAttribute(GrailsApplicationAttributes.WEB_REQUEST)
            seen.names = Collections.list(request.attributeNames)
            seen.lookup = GrailsWebRequest.lookup(request)
            seen.own = GrailsWebRequest.lookup()
            request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, GrailsWebRequest.lookup())
            request.removeAttribute(GrailsApplicationAttributes.WEB_REQUEST)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        seen.attribute == null
        !(GrailsApplicationAttributes.WEB_REQUEST in seen.names)
        seen.lookup.is(seen.own)
        !seen.own.is(original)
        live.getAttribute(GrailsApplicationAttributes.WEB_REQUEST).is(original)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a forward or include one task makes through its request does not change the request of another task'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest live = (MockHttpServletRequest) original.currentRequest
        live.requestURI = '/books'
        List<TaskRequest> requests = []
        List<HttpServletResponse> responses = []
        Closure<Void> take = {
            requests << (TaskRequest) GrailsWebRequest.lookup().currentRequest
            responses << GrailsWebRequest.lookup().currentResponse
            return null
        }
        Runnable first = new GrailsWebRequestTaskDecorator().decorate(take as Runnable)
        Runnable second = new GrailsWebRequestTaskDecorator().decorate(take as Runnable)
        RequestContextHolder.resetRequestAttributes()
        first.run()
        second.run()

        when: 'the container wraps the request of the first task for an include, as it does with the innermost wrapper'
        requests[0].request = new HttpServletRequestWrapper(live) {
            @Override
            String getRequestURI() {
                return '/fragment'
            }
        }

        then:
        requests[0].requestURI == '/fragment'
        requests[1].requestURI == '/books'
        !responses[0].is(responses[1])
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void 'a task has an encoding state of its own, whichever thread runs it'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            seen.registry = GrailsWebRequest.lookup().encodingStateRegistry
        }

        when: 'the thread submitting the task runs it, with the web request of the request bound'
        decorated.run()

        then:
        seen.registry != null
        !seen.registry.is(original.encodingStateRegistry)
        GrailsWebRequest.lookup().is(original)
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void '#call on the body stream does not hold off the request completing, which the container may do while it answers'() {
        given:
        ListeningRequest live = new ListeningRequest()
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), live, new MockHttpServletResponse())
        HttpServletRequest taskRequest
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            taskRequest = GrailsWebRequest.lookup().currentRequest
            // The container completes the request on another thread before it answers
            live.onCall = {
                Thread completing = Thread.start { original.requestCompleted() }
                completing.join(5000)
                live.completedInTime = !completing.alive
            }
            ServletInputStream body = taskRequest.inputStream
            seen.failure = failureOf { calling.call(body) }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then: 'the request completed meanwhile, which the call reports'
        live.completedInTime
        seen.failure.message == "$COMPLETED request body"
        failureOf { taskRequest.getAttribute('anything') }.message == "$COMPLETED request"

        when: 'the container calls a listener it was given'
        live.listener?.onDataAvailable()

        then: 'the task is no longer called'
        0 * _

        where:
        call              | calling
        'setReadListener' | { ServletInputStream body -> body.setReadListener(Mock(ReadListener)) }
        'isReady'         | { ServletInputStream body -> body.ready }
    }

    @Issue('https://github.com/apache/grails-core/issues/15851')
    void '#call on the response output does not hold off the request completing, and its listener is not called once it has'() {
        given:
        ListeningResponse liveResponse = new ListeningResponse()
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), new MockHttpServletRequest(), liveResponse)
        WriteListener listener = Mock()
        Map seen = [:]
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            liveResponse.onCall = {
                Thread completing = Thread.start { original.requestCompleted() }
                completing.join(5000)
                liveResponse.completedInTime = !completing.alive
            }
            ServletOutputStream output = GrailsWebRequest.lookup().currentResponse.outputStream
            seen.failure = failureOf { calling.call(output, listener) }
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()
        liveResponse.listener?.onWritePossible()

        then:
        liveResponse.completedInTime
        seen.failure.message == "$COMPLETED response output"
        0 * listener._

        where:
        call               | calling
        'setWriteListener' | { ServletOutputStream output, WriteListener given -> output.setWriteListener(given) }
        'isReady'          | { ServletOutputStream output, WriteListener given -> output.ready }
    }

    private static Throwable failureOf(Closure<?> call) {
        try {
            call.call()
        }
        catch (Throwable failure) {
            return failure
        }
        return null
    }

    private static void waitUntilBlocked(Thread thread) {
        for (int i = 0; i < 500 && thread.state != Thread.State.WAITING && thread.state != Thread.State.TIMED_WAITING; i++) {
            Thread.sleep(10)
        }
    }

    // Records every read of the body or parameters, which the container answers by reading the body
    static class ReadCountingRequest extends MockHttpServletRequest {

        final List<String> reads = []

        ReadCountingRequest() {
            super('POST', '/upload')
            contentType = 'multipart/form-data; boundary=x'
            content = new byte[4096]
        }

        @Override
        ServletInputStream getInputStream() {
            reads << 'inputStream'
            return super.getInputStream()
        }

        @Override
        BufferedReader getReader() {
            reads << 'reader'
            return super.getReader()
        }

        @Override
        Map<String, String[]> getParameterMap() {
            reads << 'parameterMap'
            return super.getParameterMap()
        }

        @Override
        String getParameter(String name) {
            reads << 'parameter'
            return super.getParameter(name)
        }

        @Override
        Collection<Part> getParts() {
            reads << 'parts'
            return super.getParts()
        }
    }

    // A request whose attribute 'slow' answers once the test releases it
    static class BlockingRequest extends MockHttpServletRequest {

        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        Object getAttribute(String name) {
            if (name == 'slow') {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
                return 'slow value'
            }
            return super.getAttribute(name)
        }
    }

    // A request that throws as Tomcat's does once it has been recycled
    static class RecyclableRequest extends MockHttpServletRequest {

        boolean recycled

        @Override
        Object getAttribute(String name) {
            if (recycled) {
                throw new IllegalStateException('The request object has been recycled and is no longer associated with this facade')
            }
            return super.getAttribute(name)
        }
    }

    // A request whose body stream runs a callback as it answers whether it is ready or is given a read listener
    static class ListeningRequest extends MockHttpServletRequest {

        Closure<?> onCall
        volatile boolean completedInTime
        ReadListener listener

        @Override
        ServletInputStream getInputStream() {
            ServletInputStream body = super.getInputStream()
            return new ServletInputStream() {
                @Override
                int read() {
                    return body.read()
                }

                @Override
                boolean isFinished() {
                    return body.finished
                }

                @Override
                boolean isReady() {
                    onCall.call()
                    return true
                }

                @Override
                void setReadListener(ReadListener readListener) {
                    listener = readListener
                    onCall.call()
                }
            }
        }
    }

    // A response whose output runs a callback as it answers whether it is ready or is given a write listener
    static class ListeningResponse extends MockHttpServletResponse {

        Closure<?> onCall
        volatile boolean completedInTime
        WriteListener listener

        @Override
        ServletOutputStream getOutputStream() {
            ServletOutputStream output = super.getOutputStream()
            return new ServletOutputStream() {
                @Override
                void write(int b) {
                    output.write(b)
                }

                @Override
                boolean isReady() {
                    onCall.call()
                    return true
                }

                @Override
                void setWriteListener(WriteListener writeListener) {
                    listener = writeListener
                    onCall.call()
                }
            }
        }
    }
}

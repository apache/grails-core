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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletRequestWrapper
import jakarta.servlet.http.HttpServletRequest

import org.springframework.web.context.request.RequestAttributes

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * The request a task started during a web request sees.
 *
 * <p>Until the request completes, it is the request the task was given, with every public view that request and
 * the requests it wraps offer, such as a multipart wrapper's, so a task the response waits for, such as one started
 * with {@code WebPromises}, renders through it. Tasks of one request can be given different requests, such as the
 * request and the request of a forward, and each task keeps its own. The container recycles the request once it
 * completes, including any asynchronous processing, so from then on each task sees a
 * {@link CompletedHttpServletRequest copy} of its request taken as the request completed, and the recycled request
 * is released. The copies are taken once for the request, however many tasks it has, and each task reads its copy
 * through a view of its own, so each has the body to read. A multipart request stays one, without the uploads,
 * which go with the request.</p>
 *
 * <p>A task keeps how its request was dispatched when it started: its path, the forward and include attributes and,
 * for a forward or an include, its parameters, though a container that gave the forward the very wrapper the
 * request had, with its own wrapper inserted underneath, restores that wrapper as the forward returns. A task that
 * is reading the body when the request completes reads on from its view of the copy, where it stopped.</p>
 *
 * <p>A request completes when the dispatch the container started for it ends, as {@link TaskRequestFilter} tells,
 * after any forward or include within it, though each of those completes the web request it bound or reused as it
 * returns. Without the filter, such as in a unit test, the request completes with the web request the first of its
 * tasks was started during.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequest implements InvocationHandler {

    private static final String STATE = TaskRequest.name + '.STATE'
    private static final String PROCESSING = TaskRequest.name + '.PROCESSING'
    private static final String COMPLETION_CALLBACK = TaskRequest.name + '.COMPLETION'

    private final GivenRequest given
    private CompletedHttpServletRequest view
    private ServletInputStream body
    private BufferedReader bodyReader

    private TaskRequest(GivenRequest given) {
        this.given = given
    }

    /**
     * The request a task started during a web request sees. It has to be called while the web request is the
     * current one, when the task is submitted.
     *
     * @param webRequest the web request the task is started during
     * @return the request for the task
     */
    static HttpServletRequest of(GrailsWebRequest webRequest) {
        HttpServletRequest current = webRequest.currentRequest
        if (Proxy.isProxyClass(current.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(current)
            if (handler instanceof TaskRequest) {
                // A task started from a task reads the request that task was given through a view of its own
                return (HttpServletRequest) Proxy.newProxyInstance(current.getClass().classLoader,
                        current.getClass().interfaces, new TaskRequest(((TaskRequest) handler).given))
            }
        }
        ClassLoader loader = current.getClass().classLoader ?: TaskRequest.classLoader
        return (HttpServletRequest) Proxy.newProxyInstance(loader, interfacesOf(current, loader),
                new TaskRequest(givenOf(webRequest, current, DispatchView.of(current))))
    }

    /**
     * Marks the request while the dispatch the container started for it is processed, unless an enclosing dispatch
     * already marked it.
     *
     * @param request the request of the dispatch
     * @return whether the request was marked, so the dispatch has to call {@link #processed} as it ends
     */
    @PackageScope
    static boolean processing(ServletRequest request) {
        ServletRequest underlying = underlying(request)
        if (underlying.getAttribute(PROCESSING) != null) {
            return false
        }
        underlying.setAttribute(PROCESSING, Boolean.TRUE)
        return true
    }

    /**
     * Ends the processing {@link #processing} marked: the requests the tasks of the request were given are copied,
     * or, while the request is processed asynchronously, as that processing completes.
     *
     * @param request the request of the dispatch
     */
    @PackageScope
    static void processed(ServletRequest request) {
        ServletRequest underlying = underlying(request)
        underlying.removeAttribute(PROCESSING)
        Object state = underlying.getAttribute(STATE)
        if (state instanceof RequestState) {
            ((RequestState) state).requestCompleted()
        }
    }

    // One state for the request, however many tasks it has and whichever request each was given, so completing it
    // copies every request its tasks were given at once
    private static GivenRequest givenOf(GrailsWebRequest webRequest, HttpServletRequest current, DispatchView view) {
        // The state lives on the request the container created, which every wrapper of it, such as the request a
        // forward dispatched, shows, so the tasks of the request find it, unless the request has completed
        ServletRequest underlying = underlying(current)
        GivenRequest given = boundTo(underlying)?.give(current, view)
        if (given != null) {
            return given
        }
        RequestState state = new RequestState()
        given = state.give(current, view)
        if (marked(underlying)) {
            // Copied as the dispatch the container started ends, though a forward or an include within it binds a
            // web request of its own, or reuses this one, and completes it as it returns
            underlying.setAttribute(STATE, state)
        }
        else if (webRequest.isActive()) {
            underlying.setAttribute(STATE, state)
            webRequest.registerDestructionCallback(COMPLETION_CALLBACK, state::requestCompleted,
                    RequestAttributes.SCOPE_REQUEST)
        }
        else {
            state.requestCompleted()
        }
        return given
    }

    private static RequestState boundTo(ServletRequest underlying) {
        try {
            Object state = underlying.getAttribute(STATE)
            return state instanceof RequestState ? (RequestState) state : null
        }
        catch (IllegalStateException ignored) {
            // The container has already recycled the request
            return null
        }
    }

    private static boolean marked(ServletRequest underlying) {
        try {
            return underlying.getAttribute(PROCESSING) != null
        }
        catch (IllegalStateException ignored) {
            return false
        }
    }

    /**
     * The request the container created, which the given request is or wraps.
     *
     * @param request a request
     * @return the innermost request of its wrappers
     */
    @PackageScope
    static ServletRequest underlying(ServletRequest request) {
        ServletRequest underlying = request
        for (ServletRequest wrapped = unwrap(request); wrapped != null; wrapped = unwrap(wrapped)) {
            underlying = wrapped
        }
        return underlying
    }

    // Every public view of the request and of the requests it wraps, such as a multipart wrapper's, so the task
    // finds what a filter resolved however the request is wrapped, as unwrapping the request finds it
    private static Class<?>[] interfacesOf(HttpServletRequest request, ClassLoader loader) {
        Set<Class<?>> interfaces = new LinkedHashSet<>()
        interfaces.add(HttpServletRequest)
        for (ServletRequest wrapped = request; wrapped != null; wrapped = unwrap(wrapped)) {
            for (Class<?> type = wrapped.getClass(); type != null; type = type.superclass) {
                addInterfaces(type.interfaces, loader, interfaces)
            }
        }
        return interfaces.toArray(new Class<?>[0])
    }

    private static void addInterfaces(Class<?>[] candidates, ClassLoader loader, Set<Class<?>> interfaces) {
        for (Class<?> candidate : candidates) {
            // A proxy class can only implement interfaces it can access, through the class loader that defines it.
            // Nor is it a Groovy object, so Groovy calls it through these interfaces, not through the meta class of
            // the request's class, whose methods it does not have
            if (Modifier.isPublic(candidate.modifiers) && !GroovyObject.isAssignableFrom(candidate) &&
                    visibleFrom(loader, candidate) && interfaces.add(candidate)) {
                addInterfaces(candidate.interfaces, loader, interfaces)
            }
        }
    }

    private static boolean visibleFrom(ClassLoader loader, Class<?> type) {
        try {
            return Class.forName(type.name, false, loader).is(type)
        }
        catch (ClassNotFoundException ignored) {
            return false
        }
    }

    // The request, or the first request it wraps that offers the method, such as a multipart request in a wrapper
    private static ServletRequest offering(ServletRequest request, Class<?> type) {
        for (ServletRequest candidate = request; candidate != null; candidate = unwrap(candidate)) {
            if (type.isInstance(candidate)) {
                return candidate
            }
        }
        return null
    }

    private static ServletRequest unwrap(ServletRequest request) {
        return request instanceof ServletRequestWrapper ? ((ServletRequestWrapper) request).request : null
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.name == 'equals' && method.parameterCount == 1) {
            return proxy.is(args[0])
        }
        if (method.name == 'hashCode' && method.parameterCount == 0) {
            return System.identityHashCode(proxy)
        }
        if (method.parameterCount == 0 && method.name == 'getInputStream') {
            return inputStream()
        }
        if (method.parameterCount == 0 && method.name == 'getReader') {
            return reader()
        }
        HttpServletRequest current = given.live
        if (current == null) {
            current = view()
        }
        else if (given.view != null) {
            // How the request was dispatched when the task was given it, which the container restores after a
            // forward or an include
            Object answer = given.view.answer(method, args, current)
            if (!answer.is(DispatchView.UNANSWERED)) {
                return answer
            }
        }
        ServletRequest target = offering(current, method.declaringClass)
        if (target == null) {
            // Only the live request offers the views the copy does not keep, such as a custom wrapper's
            throw new IllegalStateException('The request has completed, so a task that outlives it cannot call ' +
                    "${method.declaringClass.simpleName}.${method.name}, which the copy of the request does not keep")
        }
        try {
            return method.invoke(target, args)
        }
        catch (InvocationTargetException e) {
            throw e.targetException
        }
    }

    @PackageScope
    synchronized CompletedHttpServletRequest view() {
        if (view == null) {
            view = given.copyView()
        }
        return view
    }

    // The body as the live request serves it until the request is copied, and then from the task's view of the
    // copy, which starts where the live request stopped, so a task reading it when the request completes goes on
    private synchronized ServletInputStream inputStream() {
        if (body == null) {
            if (bodyReader != null) {
                throw new IllegalStateException('getReader() has already been called, so the body is not available as a stream')
            }
            synchronized (given.bodyLock) {
                HttpServletRequest live = given.live
                if (live == null) {
                    return view().inputStream
                }
                body = new TaskInputStream(given, this, live.inputStream)
            }
        }
        return body
    }

    private synchronized BufferedReader reader() {
        if (bodyReader == null) {
            if (body != null) {
                throw new IllegalStateException('getInputStream() has already been called, so the body is not available as a reader')
            }
            synchronized (given.bodyLock) {
                HttpServletRequest live = given.live
                if (live == null) {
                    return view().reader
                }
                bodyReader = new TaskReader(given, this, live.reader)
            }
        }
        return bodyReader
    }

    // What the tasks of one request share: its completion, which copies every request they were given at once
    private static final class RequestState implements AsyncListener {

        private final List<GivenRequest> given = new ArrayList<>()
        // Held while a task reads the live body and while the requests are copied, so a read and the copy of the
        // body do not interleave
        private final Object body = new Object()
        private boolean completed

        // The request a task was given, shared by the tasks given the same request dispatched the same way, or null
        // once the request has completed
        private synchronized GivenRequest give(HttpServletRequest request, DispatchView view) {
            if (completed) {
                return null
            }
            for (GivenRequest existing : given) {
                if (existing.live.is(request) && DispatchView.same(existing.view, view)) {
                    return existing
                }
            }
            GivenRequest added = new GivenRequest(request, view, body)
            given.add(added)
            return added
        }

        private synchronized HttpServletRequest first() {
            return completed ? null : given.get(0).live
        }

        private void requestCompleted() {
            HttpServletRequest current = first()
            if (current == null) {
                return
            }
            try {
                if (current.isAsyncStarted()) {
                    // The response is still being produced, so a task it waits for may still be rendering it. The
                    // asynchronous processing completes on a thread outside the filters, so who made the request is
                    // taken now, while a security filter still tells
                    rememberIdentities()
                    current.asyncContext.addListener(this)
                    return
                }
            }
            catch (IllegalStateException ignored) {
                // Too late to be told when the asynchronous processing completes, so the request is copied now
            }
            copyRequests(false)
        }

        private synchronized void rememberIdentities() {
            for (GivenRequest request : given) {
                try {
                    request.identity = new CompletedHttpServletRequest.Identity(request.live)
                }
                catch (RuntimeException ignored) {
                    // The copy reads who made the request from the request itself
                }
            }
        }

        // Copied inside the dispatch that ends, or, once the asynchronous processing completes, outside it, with
        // who made the request as the last dispatch told
        private synchronized void copyRequests(boolean outsideDispatch) {
            if (completed) {
                return
            }
            completed = true
            List<HttpServletRequest> requests = new ArrayList<>()
            List<DispatchView> views = new ArrayList<>()
            List<CompletedHttpServletRequest.Identity> identities = new ArrayList<>()
            for (GivenRequest request : given) {
                requests.add(request.live)
                views.add(request.view)
                identities.add(outsideDispatch ? request.identity : null)
            }
            // A task reading the live body finishes its read first, and goes on from the copy
            synchronized (body) {
                try {
                    underlying(requests.get(0)).removeAttribute(STATE)
                    List<CompletedHttpServletRequest> copies = CompletedHttpServletRequest.copyOf(requests, views, identities)
                    for (int i = 0; i < copies.size(); i++) {
                        given.get(i).copy = copies.get(i)
                    }
                }
                catch (RuntimeException ignored) {
                    // The container has already recycled the request, so there is nothing left to copy
                }
                finally {
                    // Released whether or not the copies were taken, so no task holds the recycled request
                    for (GivenRequest request : given) {
                        request.live = null
                    }
                }
            }
        }

        @Override
        void onComplete(AsyncEvent event) {
            copyRequests(true)
        }

        @Override
        void onTimeout(AsyncEvent event) {
        }

        @Override
        void onError(AsyncEvent event) {
        }

        @Override
        void onStartAsync(AsyncEvent event) {
            // Starting another asynchronous cycle removes the listeners of the one before
            event.asyncContext.addListener(this)
        }
    }

    // A request tasks were given: the request itself until the request completes, then the copy of it
    private static final class GivenRequest {

        @PackageScope
        final DispatchView view

        @PackageScope
        final Object bodyLock

        @PackageScope
        volatile HttpServletRequest live

        @PackageScope
        volatile CompletedHttpServletRequest copy

        @PackageScope
        volatile CompletedHttpServletRequest.Identity identity

        @PackageScope
        GivenRequest(HttpServletRequest live, DispatchView view, Object bodyLock) {
            this.live = live
            this.view = view
            this.bodyLock = bodyLock
        }

        @PackageScope
        CompletedHttpServletRequest copyView() {
            CompletedHttpServletRequest completed = copy
            if (completed == null) {
                throw new IllegalStateException(
                        'The request has completed, and it was recycled before it could be copied, so a task that outlives it cannot read it')
            }
            return completed.newView()
        }
    }

    // The body stream of a task: the live request's until the request is copied, then the task's view of the copy,
    // which holds what the live stream had not served yet
    private static final class TaskInputStream extends ServletInputStream {

        private final GivenRequest given
        private final TaskRequest task
        private final ServletInputStream live

        private TaskInputStream(GivenRequest given, TaskRequest task, ServletInputStream live) {
            this.given = given
            this.task = task
            this.live = live
        }

        @Override
        int read() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.read()
                }
            }
            return copied().read()
        }

        @Override
        int read(byte[] destination, int offset, int length) throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.read(destination, offset, length)
                }
            }
            return copied().read(destination, offset, length)
        }

        @Override
        long skip(long count) throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.skip(count)
                }
            }
            return copied().skip(count)
        }

        @Override
        int available() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.available()
                }
            }
            return copied().available()
        }

        @Override
        void close() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    live.close()
                    return
                }
            }
            copied().close()
        }

        @Override
        boolean isFinished() {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.isFinished()
                }
            }
            return task.view().inputStream.isFinished()
        }

        @Override
        boolean isReady() {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.isReady()
                }
            }
            return task.view().inputStream.isReady()
        }

        @Override
        void setReadListener(ReadListener readListener) {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    live.setReadListener(readListener)
                    return
                }
            }
            task.view().inputStream.setReadListener(readListener)
        }

        private ServletInputStream copied() throws IOException {
            try {
                return task.view().inputStream
            }
            catch (IllegalStateException unavailable) {
                throw new IOException(unavailable.message, unavailable)
            }
        }
    }

    // The body reader of a task: the live request's until the request is copied, then the task's view of the copy,
    // which holds what the live reader had not served yet
    private static final class TaskReader extends BufferedReader {

        private final GivenRequest given
        private final TaskRequest task
        private final BufferedReader live

        private TaskReader(GivenRequest given, TaskRequest task, BufferedReader live) {
            super(Reader.nullReader())
            this.given = given
            this.task = task
            this.live = live
        }

        @Override
        int read() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.read()
                }
            }
            return copied().read()
        }

        @Override
        int read(char[] destination, int offset, int length) throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.read(destination, offset, length)
                }
            }
            return copied().read(destination, offset, length)
        }

        @Override
        String readLine() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.readLine()
                }
            }
            return copied().readLine()
        }

        @Override
        long skip(long count) throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.skip(count)
                }
            }
            return copied().skip(count)
        }

        @Override
        boolean ready() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    return live.ready()
                }
            }
            return copied().ready()
        }

        @Override
        boolean markSupported() {
            return false
        }

        @Override
        void mark(int readAheadLimit) throws IOException {
            throw new IOException('mark() is not supported')
        }

        @Override
        void reset() throws IOException {
            throw new IOException('reset() is not supported')
        }

        @Override
        void close() throws IOException {
            synchronized (given.bodyLock) {
                if (given.live != null) {
                    live.close()
                    return
                }
            }
            copied().close()
        }

        private BufferedReader copied() throws IOException {
            try {
                return task.view().reader
            }
            catch (IllegalStateException unavailable) {
                throw new IOException(unavailable.message, unavailable)
            }
        }
    }
}

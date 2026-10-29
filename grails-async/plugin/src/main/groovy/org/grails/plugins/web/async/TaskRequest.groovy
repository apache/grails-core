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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

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
 * is reading the body when the request completes reads on from its view of the copy, where it stopped, and a mark
 * it set still holds. A task's calls to the live request and the copy do not interleave, so what a task sets while
 * the copy is taken is in it.</p>
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
        // A call to the live request and the copy of the requests do not interleave: what a task sets on the live
        // request is in the copy, and once the copy is taken, the task's calls go to it
        given.lockLive()
        try {
            HttpServletRequest live = given.live
            if (live != null) {
                if (given.view != null) {
                    // How the request was dispatched when the task was given it, which the container restores
                    // after a forward or an include
                    Object answer = given.view.answer(method, args, live)
                    if (!answer.is(DispatchView.UNANSWERED)) {
                        return answer
                    }
                }
                return call(live, method, args)
            }
        }
        finally {
            given.unlockLive()
        }
        return call(view(), method, args)
    }

    private static Object call(HttpServletRequest request, Method method, Object[] args) throws Throwable {
        ServletRequest target = offering(request, method.declaringClass)
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
            given.lockLive()
            try {
                HttpServletRequest live = given.live
                if (live == null) {
                    return view().inputStream
                }
                ServletInputStream stream = live.inputStream
                body = new TaskInputStream(given, this, stream)
                // The copy takes the rest of the body from the stream the task reads, wherever it stands
                given.takenBody.compareAndSet(null, new CompletedHttpServletRequest.TakenBody(stream, null))
            }
            finally {
                given.unlockLive()
            }
        }
        return body
    }

    private synchronized BufferedReader reader() {
        if (bodyReader == null) {
            if (body != null) {
                throw new IllegalStateException('getInputStream() has already been called, so the body is not available as a reader')
            }
            given.lockLive()
            try {
                HttpServletRequest live = given.live
                if (live == null) {
                    return view().reader
                }
                BufferedReader liveReader = live.reader
                bodyReader = new TaskReader(given, this, liveReader)
                // The reader may read ahead of what it serves, so the copy takes the rest of the body through it
                given.takenBody.compareAndSet(null,
                        new CompletedHttpServletRequest.TakenBody(liveReader, live.characterEncoding))
            }
            finally {
                given.unlockLive()
            }
        }
        return bodyReader
    }

    // What the tasks of one request share: its completion, which copies every request they were given at once
    private static final class RequestState implements AsyncListener {

        private final List<GivenRequest> given = new ArrayList<>()
        // Read while tasks call the live request, which they can do at once, and written while the requests are
        // copied, so a call and the copy do not interleave
        private final ReentrantReadWriteLock copyLock = new ReentrantReadWriteLock()
        // The live stream or reader the first task to read the body took, which the copy reads the rest from
        private final AtomicReference<CompletedHttpServletRequest.TakenBody> takenBody = new AtomicReference<>()
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
            GivenRequest added = new GivenRequest(request, view, copyLock, takenBody)
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
            // A task's call to the live request, such as a read of the body, finishes first, and the task goes on
            // with the copy
            // A thread calling the live request from a task, which the container can call back on, cannot take the
            // write lock, so it copies as it is
            boolean exclusive = copyLock.readHoldCount == 0
            if (exclusive) {
                copyLock.writeLock().lock()
            }
            try {
                try {
                    underlying(requests.get(0)).removeAttribute(STATE)
                    List<CompletedHttpServletRequest> copies = CompletedHttpServletRequest.copyOf(requests, views, identities,
                            takenBody.get())
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
            finally {
                if (exclusive) {
                    copyLock.writeLock().unlock()
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
        final ReentrantReadWriteLock copyLock

        @PackageScope
        final AtomicReference<CompletedHttpServletRequest.TakenBody> takenBody

        @PackageScope
        volatile HttpServletRequest live

        @PackageScope
        volatile CompletedHttpServletRequest copy

        @PackageScope
        volatile CompletedHttpServletRequest.Identity identity

        @PackageScope
        GivenRequest(HttpServletRequest live, DispatchView view, ReentrantReadWriteLock copyLock,
                AtomicReference<CompletedHttpServletRequest.TakenBody> takenBody) {
            this.live = live
            this.view = view
            this.copyLock = copyLock
            this.takenBody = takenBody
        }

        // Held while a task calls the live request: tasks can call it at once, and the copy waits for them
        @PackageScope
        void lockLive() {
            copyLock.readLock().lock()
        }

        @PackageScope
        void unlockLive() {
            copyLock.readLock().unlock()
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
    // which holds what the live stream had not served yet. When the live stream supports a mark, the task's stream
    // keeps its own, so a reset serves again what was read since, across the copy too
    private static final class TaskInputStream extends ServletInputStream {

        private static final int COPIED = -2

        private final GivenRequest given
        private final TaskRequest task
        private final ServletInputStream live
        private final boolean markable
        private boolean closed
        // What was read since the mark, or null without a valid mark, what a reset serves again, and where
        private ByteArrayOutputStream marked
        private byte[] history
        private int position
        private long markLimit

        private TaskInputStream(GivenRequest given, TaskRequest task, ServletInputStream live) {
            this.given = given
            this.task = task
            this.live = live
            this.markable = live.markSupported()
        }

        @Override
        synchronized int read() throws IOException {
            checkOpen()
            if (replaying()) {
                return history[position++] & 0xFF
            }
            byte[] single = new byte[1]
            return readAnew(single, 0, 1) == -1 ? -1 : single[0] & 0xFF
        }

        @Override
        synchronized int read(byte[] destination, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, destination.length)
            checkOpen()
            if (length == 0) {
                return 0
            }
            if (replaying()) {
                int served = Math.min(length, history.length - position)
                System.arraycopy(history, position, destination, offset, served)
                position += served
                return served
            }
            return readAnew(destination, offset, length)
        }

        @Override
        synchronized int available() throws IOException {
            if (replaying()) {
                return history.length - position
            }
            given.lockLive()
            try {
                if (given.live != null) {
                    return live.available()
                }
            }
            finally {
                given.unlockLive()
            }
            return copied().available()
        }

        @Override
        boolean markSupported() {
            return markable
        }

        @Override
        synchronized void mark(int readLimit) {
            if (!markable) {
                return
            }
            // What a reset still had to serve again is the start of what the new mark covers
            byte[] remaining = replaying() ? Arrays.copyOfRange(history, position, history.length) : new byte[0]
            marked = new ByteArrayOutputStream()
            marked.write(remaining, 0, remaining.length)
            history = remaining
            position = 0
            markLimit = (long) readLimit + remaining.length
        }

        @Override
        synchronized void reset() throws IOException {
            if (!markable) {
                throw new IOException('mark/reset not supported')
            }
            if (marked == null) {
                throw new IOException('The stream was not marked, or more was read since the mark than it allowed')
            }
            history = marked.toByteArray()
            position = 0
        }

        @Override
        synchronized void close() throws IOException {
            closed = true
            marked = null
            history = null
            // Outside the lock: the container can call back on this thread, and the copy needs the lock
            if (liveNow()) {
                live.close()
            }
            else {
                copied().close()
            }
        }

        @Override
        synchronized boolean isFinished() {
            if (replaying()) {
                return false
            }
            given.lockLive()
            try {
                if (given.live != null) {
                    return live.isFinished()
                }
            }
            finally {
                given.unlockLive()
            }
            return task.view().inputStream.isFinished()
        }

        @Override
        synchronized boolean isReady() {
            if (replaying()) {
                return true
            }
            given.lockLive()
            try {
                if (given.live != null) {
                    return live.isReady()
                }
            }
            finally {
                given.unlockLive()
            }
            return task.view().inputStream.isReady()
        }

        @Override
        void setReadListener(ReadListener readListener) {
            // Outside the lock: the container can call the listener on this thread, which can complete the request
            if (liveNow()) {
                live.setReadListener(readListener)
            }
            else {
                task.view().inputStream.setReadListener(readListener)
            }
        }

        private boolean liveNow() {
            given.lockLive()
            try {
                return given.live != null
            }
            finally {
                given.unlockLive()
            }
        }

        private boolean replaying() {
            return history != null && position < history.length
        }

        private void checkOpen() throws IOException {
            if (closed) {
                throw new IOException('Stream closed')
            }
        }

        // Past what a reset serves again: the live stream, or the copy once it is taken, and what the mark covers
        private int readAnew(byte[] destination, int offset, int length) throws IOException {
            int count = COPIED
            given.lockLive()
            try {
                if (given.live != null) {
                    count = live.read(destination, offset, length)
                }
            }
            finally {
                given.unlockLive()
            }
            if (count == COPIED) {
                count = copied().read(destination, offset, length)
            }
            if (count > 0 && marked != null) {
                marked.write(destination, offset, count)
                if (marked.size() > markLimit) {
                    marked = null
                }
            }
            return count
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
    // which holds what the live reader had not served yet. It keeps its own mark, as the reader of a request
    // supports one, so a reset serves again what was read since, across the copy too
    private static final class TaskReader extends BufferedReader {

        private static final int COPIED = -2
        private static final int LINE_FEED = 10
        private static final int CARRIAGE_RETURN = 13

        private final GivenRequest given
        private final TaskRequest task
        private final BufferedReader live
        // What was read since the mark, or null without a valid mark, and where a reset serves it again from
        private StringBuilder marked
        private int position
        private long markLimit
        // A line ended with a carriage return, so a line feed right after it belongs to that line, as in
        // BufferedReader; kept with the mark, so a reset restores it
        private boolean skipLineFeed
        private boolean markedSkipLineFeed
        private boolean closed

        private TaskReader(GivenRequest given, TaskRequest task, BufferedReader live) {
            super(Reader.nullReader())
            this.given = given
            this.task = task
            this.live = live
        }

        @Override
        synchronized int read() throws IOException {
            int character = readOne()
            if (skipLineFeed) {
                skipLineFeed = false
                if (character == LINE_FEED) {
                    character = readOne()
                }
            }
            return character
        }

        @Override
        synchronized int read(char[] destination, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, destination.length)
            if (length == 0) {
                return 0
            }
            if (skipLineFeed || replaying()) {
                // What is already at hand, without waiting for more, after the line feed a line may end with
                int first = read()
                if (first == -1) {
                    return -1
                }
                destination[offset] = (char) first
                int served = 1
                while (served < length && replaying()) {
                    destination[offset + served++] = marked.charAt(position++)
                }
                return served
            }
            int count = readFromSource(destination, offset, length)
            for (int i = 0; i < count; i++) {
                remember((int) destination[offset + i])
            }
            return count
        }

        @Override
        synchronized String readLine() throws IOException {
            if (closed) {
                throw new IOException('Stream closed')
            }
            if (marked == null && !skipLineFeed) {
                // Nothing to serve again or to remember, so the line is the source's own
                given.lockLive()
                try {
                    if (given.live != null) {
                        return live.readLine()
                    }
                }
                finally {
                    given.unlockLive()
                }
                return copied().readLine()
            }
            StringBuilder line = null
            for (int character = read(); character != -1; character = read()) {
                if (line == null) {
                    line = new StringBuilder()
                }
                if (character == LINE_FEED) {
                    return line.toString()
                }
                if (character == CARRIAGE_RETURN) {
                    skipLineFeed = true
                    return line.toString()
                }
                line.append((char) character)
            }
            return line?.toString()
        }

        @Override
        synchronized long skip(long count) throws IOException {
            if (count < 0) {
                throw new IllegalArgumentException('skip value is negative')
            }
            long skipped = 0
            while (skipped < count && read() != -1) {
                skipped++
            }
            return skipped
        }

        @Override
        synchronized boolean ready() throws IOException {
            if (replaying()) {
                return true
            }
            given.lockLive()
            try {
                if (given.live != null) {
                    return live.ready()
                }
            }
            finally {
                given.unlockLive()
            }
            return copied().ready()
        }

        @Override
        boolean markSupported() {
            return true
        }

        @Override
        synchronized void mark(int readAheadLimit) {
            if (readAheadLimit < 0) {
                throw new IllegalArgumentException('Read-ahead limit < 0')
            }
            // What a reset still had to serve again is the start of what the new mark covers
            StringBuilder covered = new StringBuilder()
            if (replaying()) {
                covered.append(marked, position, marked.length())
            }
            marked = covered
            position = 0
            markLimit = (long) readAheadLimit + covered.length()
            markedSkipLineFeed = skipLineFeed
        }

        @Override
        synchronized void reset() throws IOException {
            if (marked == null) {
                throw new IOException('Stream not marked, or more was read since the mark than it allowed')
            }
            position = 0
            skipLineFeed = markedSkipLineFeed
        }

        @Override
        synchronized void close() throws IOException {
            closed = true
            marked = null
            boolean liveNow
            given.lockLive()
            try {
                liveNow = given.live != null
            }
            finally {
                given.unlockLive()
            }
            // Outside the lock, which the copy needs
            if (liveNow) {
                live.close()
            }
            else {
                copied().close()
            }
        }

        private boolean replaying() {
            return marked != null && position < marked.length()
        }

        // The next character: served again after a reset, or read anew and remembered for the mark
        private int readOne() throws IOException {
            if (closed) {
                throw new IOException('Stream closed')
            }
            if (replaying()) {
                return (int) marked.charAt(position++)
            }
            char[] single = new char[1]
            int character = readFromSource(single, 0, 1) == -1 ? -1 : (int) single[0]
            remember(character)
            return character
        }

        private void remember(int character) {
            if (marked == null || character == -1) {
                return
            }
            marked.append((char) character)
            position++
            if (marked.length() > markLimit) {
                marked = null
                position = 0
            }
        }

        private int readFromSource(char[] destination, int offset, int length) throws IOException {
            if (closed) {
                throw new IOException('Stream closed')
            }
            given.lockLive()
            try {
                if (given.live != null) {
                    return live.read(destination, offset, length)
                }
            }
            finally {
                given.unlockLive()
            }
            return copied().read(destination, offset, length)
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

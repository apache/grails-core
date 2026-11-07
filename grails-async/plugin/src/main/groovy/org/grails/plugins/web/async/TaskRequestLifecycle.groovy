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
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletRequestWrapper

import org.springframework.web.context.request.RequestAttributes

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * The life of a request as the tasks started during it see it: live until the request completes, then detached.
 *
 * <p>Every object a task is given that reaches into the request, its {@link TaskRequest}, {@link TaskResponse} and
 * the streams, readers and writers they return, fails with an {@link IllegalStateException} once the request has
 * completed. A task's call holds the read lock, and completing takes the write lock once no call can start any
 * more, so a call a task is making finishes first, for as long as the lifecycle waits for one, in case it waits
 * for a lock of the container that the thread completing the request holds. A call the container may answer on a
 * lock of its own does not hold it: asking whether the body is ready, setting a read or write listener, reading the
 * body or the parts once a read listener was set through a task's stream, and starting asynchronous processing.
 * Those are checked before and after instead. Nothing is read from the request as it completes.</p>
 *
 * <p>The request completes when the container is done with it, as {@link TaskRequestListener} tells, after any
 * forward, include or asynchronous processing within it. A container can tell that at the end of a dispatch that
 * started asynchronous processing, which then completes later, so the lifecycle listens for that processing to
 * complete, from the first task started once it has begun. A container can also tell it once the asynchronous
 * processing has already completed while the request still reports it as started, as Tomcat does when it was
 * completed before the dispatch ended. If no task was started once it had begun, the lifecycle is not told, so
 * meanwhile every call checks that the request still holds the lifecycle, which the container clears as it
 * recycles the request. Without the listener, such as in a unit test, it completes with the web request the first
 * of its tasks was started during.</p>
 */
@PackageScope
@CompileStatic
@Slf4j
final class TaskRequestLifecycle implements AsyncListener {

    private static final String LIFECYCLE = TaskRequestLifecycle.name + '.LIFECYCLE'
    private static final String COMPLETION_CALLBACK = TaskRequestLifecycle.name + '.COMPLETION'
    private static final String COMPLETED_PREFIX = 'The request has completed, so a task that outlives it can no longer use its '

    private static final int PURGE_THRESHOLD = 32

    /**
     * How long completing a request waits by default for the calls its tasks are making, as long as the container
     * lets a blocking read or write wait by default.
     */
    static final Duration DEFAULT_CALL_WAIT = Duration.ofSeconds(60)

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock()
    private final Object monitor = new Object()
    // What calls into the request, held weakly, so what a finished task dropped is not kept until the request
    // completes. Created with the first task, so a request without tasks allocates nothing more
    private List<WeakReference<Detachable>> attached
    private int purgeAt = PURGE_THRESHOLD
    // Whether the container tells the listener when it is done with the request
    private final boolean observed
    // How long completing waits for the calls tasks are making, in case one waits for a lock of the container that
    // the thread completing the request holds
    private final Duration callWait
    private volatile boolean completed
    // The request the container said it was done with while asynchronous processing was started, until the
    // lifecycle completes
    private volatile ServletRequest awaiting
    // Whether the lifecycle listens for the asynchronous processing of the request to complete
    private boolean listening
    // Whether a read listener was set through a task's stream, so the body is read without the lock, and how many
    // were set
    private volatile boolean readingWithoutLock
    private int readListeners
    // Whether completing did not wait for the calls tasks were making under the lock, so one may have been overtaken
    private volatile boolean abandoned

    private TaskRequestLifecycle(boolean observed, Duration callWait) {
        this.observed = observed
        this.callWait = callWait
    }

    /**
     * The lifecycle of the request of a web request, shared by every task started during the request, whichever
     * request of it, such as the request of a forward, each was given. It has to be called while the web request
     * is the current one, when the task is submitted. Once the container is done with the request, it is the
     * completed lifecycle, so a task started then, such as during an error page rendered after that, runs without
     * the request.
     *
     * @param webRequest the web request a task is started during
     * @return the lifecycle of its request
     * @throws IllegalStateException when the container has already recycled the request
     */
    static TaskRequestLifecycle of(GrailsWebRequest webRequest) {
        // Kept on the request the container created, which every wrapper of it shows
        ServletRequest underlying = underlying(webRequest.currentRequest)
        Object existing = underlying.getAttribute(LIFECYCLE)
        if (existing instanceof TaskRequestLifecycle &&
                (((TaskRequestLifecycle) existing).observed || !((TaskRequestLifecycle) existing).completed)) {
            TaskRequestLifecycle lifecycle = (TaskRequestLifecycle) existing
            if (lifecycle.observed) {
                lifecycle.listenToAsync(underlying)
            }
            return lifecycle
        }
        // Without the listener, the request completes with the web request the first task was started during
        synchronized (TaskRequestLifecycle) {
            existing = underlying.getAttribute(LIFECYCLE)
            if (existing instanceof TaskRequestLifecycle && !((TaskRequestLifecycle) existing).completed) {
                return (TaskRequestLifecycle) existing
            }
            TaskRequestLifecycle lifecycle = new TaskRequestLifecycle(false, DEFAULT_CALL_WAIT)
            if (webRequest.isActive()) {
                underlying.setAttribute(LIFECYCLE, lifecycle)
                webRequest.registerDestructionCallback(COMPLETION_CALLBACK, lifecycle::complete, RequestAttributes.SCOPE_REQUEST)
            }
            else {
                lifecycle.complete()
            }
            return lifecycle
        }
    }

    /**
     * Gives a request the container will tell {@link TaskRequestListener} it is done with its lifecycle, before
     * anything processes it, so the attribute holding it is one an include keeps.
     *
     * @param request the request the container created
     * @param callWait how long completing the request waits for the calls its tasks are making
     */
    static void initialized(ServletRequest request, Duration callWait) {
        if (!(request.getAttribute(LIFECYCLE) instanceof TaskRequestLifecycle)) {
            request.setAttribute(LIFECYCLE, new TaskRequestLifecycle(true, callWait))
        }
    }

    /**
     * Completes the lifecycle of a request the container is done with, or, while the request is still processed
     * asynchronously, once that processing completes.
     *
     * @param request the request the container created
     */
    static void destroyed(ServletRequest request) {
        Object found = request.getAttribute(LIFECYCLE)
        if (!(found instanceof TaskRequestLifecycle)) {
            return
        }
        TaskRequestLifecycle lifecycle = (TaskRequestLifecycle) found
        try {
            if (request.isAsyncStarted()) {
                // Told at the end of a dispatch, as Jetty does, while the asynchronous processing goes on, or told
                // once it has already completed, as Tomcat does when it was completed before the dispatch ended
                lifecycle.await(request)
                return
            }
        }
        catch (IllegalStateException ignored) {
            // Too late to be told when the asynchronous processing completes
        }
        lifecycle.complete()
    }

    // Listens for the asynchronous processing of the request to complete, once, from when it has begun, so a
    // container that completes it before the dispatch ends tells the lifecycle before it recycles the request
    private void listenToAsync(ServletRequest request) {
        if (!request.isAsyncStarted()) {
            return
        }
        synchronized (monitor) {
            if (completed || listening) {
                return
            }
            listening = true
        }
        try {
            request.asyncContext.addListener(this)
        }
        catch (IllegalStateException ignored) {
            // The container no longer takes listeners for this asynchronous processing, such as after an error, so
            // the lifecycle relies on the container telling the listener it is done with the request
            synchronized (monitor) {
                listening = false
            }
        }
    }

    private void await(ServletRequest request) {
        synchronized (monitor) {
            if (completed) {
                return
            }
            awaiting = request
        }
        listenToAsync(request)
    }

    private static ServletRequest underlying(ServletRequest request) {
        ServletRequest underlying = request
        while (underlying instanceof ServletRequestWrapper) {
            underlying = ((ServletRequestWrapper) underlying).request
        }
        return underlying
    }

    /**
     * @return whether a read listener was set through a task's stream, so the body and the parts are read without
     *         the lock, since a nonblocking read asks the container whether more is ready
     */
    boolean isReadingWithoutLock() {
        return readingWithoutLock
    }

    /**
     * Reads the body without the lock from now on, as a read listener is being set through a task's stream: the
     * container may read on the listener's behalf before setting it returns.
     */
    void settingReadListener() {
        readingWithoutLock = true
    }

    /**
     * Ends {@link #settingReadListener}: once a read listener was set, the body is read without the lock, and
     * otherwise as before.
     *
     * @param set whether the listener was set
     */
    void readListenerSet(boolean set) {
        synchronized (monitor) {
            if (set) {
                readListeners++
            }
            readingWithoutLock = readListeners > 0
        }
    }

    /**
     * @return whether the request has completed, which, while the lifecycle waits for asynchronous processing to
     *         complete, includes the container having recycled the request
     */
    boolean isCompleted() {
        if (!completed && awaiting != null && !stillHeld()) {
            // The container has recycled the request already, so there is no call left worth waiting for
            completeNow(false)
        }
        return completed
    }

    // Whether the request the container said it was done with still holds this lifecycle, which it no longer does
    // once the container has recycled it
    private boolean stillHeld() {
        ServletRequest request = awaiting
        try {
            return request == null || request.getAttribute(LIFECYCLE).is(this)
        }
        catch (RuntimeException ignored) {
            return false
        }
    }

    /**
     * Attaches an object that calls into the request, so completing the request detaches it. One attached once the
     * request has completed is detached at once.
     *
     * @param detachable the object
     */
    void attach(Detachable detachable) {
        synchronized (monitor) {
            if (!completed) {
                if (attached == null) {
                    attached = new ArrayList<>()
                }
                attached.add(new WeakReference<Detachable>(detachable))
                if (attached.size() >= purgeAt) {
                    attached.removeIf { WeakReference<Detachable> reference -> reference.get() == null }
                    purgeAt = Math.max(PURGE_THRESHOLD, attached.size() * 2)
                }
                return
            }
        }
        detachable.detach()
    }

    /**
     * Runs work that creates what calls into the request while it is live: completing the request waits for the work
     * as it waits for a call a task is making, though a call the work makes into the request throws once completing
     * has begun.
     *
     * @param work the work
     * @return what the work returned, or null, without running it, once the request has completed
     */
    <T> T whileLive(Supplier<T> work) {
        if (isCompleted()) {
            return null
        }
        lock.readLock().lock()
        try {
            return completed ? null : work.get()
        }
        finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Enters a call to a live object of the request. The caller exits once the call returns, unless this throws.
     *
     * @param live the live object, or null once it is detached
     * @param what what the object is, for the failure
     * @return the live object
     * @throws IllegalStateException once the request has completed
     */
    <T> T enter(T live, String what) {
        if (live == null || isCompleted()) {
            throw completedFailure(what)
        }
        lock.readLock().lock()
        if (completed) {
            lock.readLock().unlock()
            throw completedFailure(what)
        }
        return live
    }

    /**
     * Checks a live object of the request before or after a call made without the lock, which the request
     * completing may have overtaken.
     *
     * @param live the live object, or null once it is detached
     * @param what what the object is, for the failure
     * @return the live object
     * @throws IllegalStateException once the request has completed
     */
    <T> T check(T live, String what) {
        if (live == null || isCompleted()) {
            throw completedFailure(what)
        }
        return live
    }

    void exit() {
        lock.readLock().unlock()
    }

    static IllegalStateException completedFailure(String what) {
        return new IllegalStateException("$COMPLETED_PREFIX$what".toString())
    }

    /**
     * What a call made without the lock throws: the failure that the request has completed, with what the call
     * threw as its cause, when the request completing overtook the call, and otherwise what the call threw.
     *
     * @param what what the object is, for the failure
     * @param failure what the call threw
     * @return the failure to throw
     */
    Throwable overtaken(String what, Throwable failure) {
        return isCompleted() ? completed(what, failure) : failure
    }

    /**
     * What a call made under the lock throws: the failure that the request has completed, with what the call threw
     * as its cause, when completing did not wait for the call, and otherwise what the call threw, since completing
     * waits for it.
     *
     * @param what what the object is, for the failure
     * @param failure what the call threw
     * @return the failure to throw
     */
    Throwable abandoned(String what, Throwable failure) {
        return abandoned ? completed(what, failure) : failure
    }

    private static Throwable completed(String what, Throwable failure) {
        if (failure instanceof IllegalStateException && failure.message?.startsWith(COMPLETED_PREFIX)) {
            return failure
        }
        IllegalStateException completedFailure = completedFailure(what)
        completedFailure.initCause(failure)
        return completedFailure
    }

    /**
     * Completes the request: no call can start any more, a call a task is making under the lock finishes first, for
     * as long as the lifecycle waits for one, and then every object attached is detached.
     */
    void complete() {
        completeNow(true)
    }

    private void completeNow(boolean waitForCalls) {
        List<WeakReference<Detachable>> detaching
        synchronized (monitor) {
            if (completed) {
                return
            }
            completed = true
            awaiting = null
            detaching = attached
            attached = null
        }
        if (detaching == null) {
            // No task was given the request, so there is no call to wait for
            return
        }
        // A thread completing the request from within a call into it cannot take the write lock
        boolean exclusive = waitForCalls && lock.readHoldCount == 0 && awaitCalls()
        if (!exclusive) {
            abandoned = true
        }
        try {
            for (WeakReference<Detachable> reference : detaching) {
                reference.get()?.detach()
            }
        }
        finally {
            if (exclusive) {
                lock.writeLock().unlock()
            }
        }
    }

    // Takes the write lock once the calls tasks are making have finished, or gives up after a while: a call may wait
    // for a lock of the container that the thread completing the request holds, such as the asynchronous state a
    // failing write reports its error to
    private boolean awaitCalls() {
        try {
            if (lock.writeLock().tryLock(callWait.toNanos(), TimeUnit.NANOSECONDS)) {
                return true
            }
            log.warn('A task was still calling the request {} after the request completed, so the request completed ' +
                    'without waiting for it any longer', callWait)
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
        }
        return false
    }

    @Override
    void onComplete(AsyncEvent event) {
        complete()
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

    /**
     * An object that calls into the request until the request completes.
     */
    @PackageScope
    static interface Detachable {

        /**
         * Releases the live object, so every later call throws.
         */
        void detach()
    }
}

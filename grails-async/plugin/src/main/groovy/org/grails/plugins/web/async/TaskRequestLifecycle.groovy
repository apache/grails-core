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
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

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
 * the streams, readers and writers they return, calls the live object only while it holds the read lock and the
 * request has not completed. Completing takes the write lock, so a call a task is making finishes first, and then
 * releases the live objects, so every later call throws an {@link IllegalStateException}. Nothing is read from the
 * request as it completes.</p>
 *
 * <p>The request completes when the container is done with it, as {@link TaskRequestListener} tells, after any
 * forward, include or asynchronous processing within it. Without the listener, such as in a unit test, it completes
 * with the web request the first of its tasks was started during.</p>
 */
@PackageScope
@CompileStatic
final class TaskRequestLifecycle implements AsyncListener {

    private static final String LIFECYCLE = TaskRequestLifecycle.name + '.LIFECYCLE'
    private static final String COMPLETION_CALLBACK = TaskRequestLifecycle.name + '.COMPLETION'

    private static final int PURGE_THRESHOLD = 32

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock()
    private final Object monitor = new Object()
    // What calls into the request, held weakly, so what a finished task dropped is not kept until the request
    // completes. Created with the first task, so a request without tasks allocates nothing more
    private List<WeakReference<Detachable>> attached
    private int purgeAt = PURGE_THRESHOLD
    // Whether the container tells the listener when it is done with the request
    private final boolean observed
    private volatile boolean completed

    private TaskRequestLifecycle(boolean observed) {
        this.observed = observed
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
            return (TaskRequestLifecycle) existing
        }
        // Without the listener, the request completes with the web request the first task was started during
        synchronized (TaskRequestLifecycle) {
            existing = underlying.getAttribute(LIFECYCLE)
            if (existing instanceof TaskRequestLifecycle && !((TaskRequestLifecycle) existing).completed) {
                return (TaskRequestLifecycle) existing
            }
            TaskRequestLifecycle lifecycle = new TaskRequestLifecycle(false)
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
     */
    static void initialized(ServletRequest request) {
        if (!(request.getAttribute(LIFECYCLE) instanceof TaskRequestLifecycle)) {
            request.setAttribute(LIFECYCLE, new TaskRequestLifecycle(true))
        }
    }

    /**
     * Completes the lifecycle of a request the container is done with, or, while the request is still processed
     * asynchronously, once that processing completes.
     *
     * @param request the request the container created
     */
    static void destroyed(ServletRequest request) {
        Object lifecycle = request.getAttribute(LIFECYCLE)
        if (!(lifecycle instanceof TaskRequestLifecycle)) {
            return
        }
        try {
            if (request.isAsyncStarted()) {
                request.asyncContext.addListener((TaskRequestLifecycle) lifecycle)
                return
            }
        }
        catch (IllegalStateException ignored) {
            // Too late to be told when the asynchronous processing completes
        }
        ((TaskRequestLifecycle) lifecycle).complete()
    }

    private static ServletRequest underlying(ServletRequest request) {
        ServletRequest underlying = request
        while (underlying instanceof ServletRequestWrapper) {
            underlying = ((ServletRequestWrapper) underlying).request
        }
        return underlying
    }

    boolean isCompleted() {
        return completed
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
     * Runs work that calls into the request while it is live, so the request does not complete meanwhile.
     *
     * @param work the work
     * @return what the work returned, or null, without running it, once the request has completed
     */
    <T> T whileLive(Supplier<T> work) {
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
        lock.readLock().lock()
        if (live == null || completed) {
            lock.readLock().unlock()
            throw completedFailure(what)
        }
        return live
    }

    void exit() {
        lock.readLock().unlock()
    }

    static IllegalStateException completedFailure(String what) {
        return new IllegalStateException("The request has completed, so a task that outlives it can no longer use its $what")
    }

    /**
     * Completes the request: a call a task is making on it finishes first, and then every object attached is
     * detached.
     */
    void complete() {
        synchronized (monitor) {
            if (attached == null) {
                // No task was given the request, so there is no call to wait for
                completed = true
                return
            }
        }
        // A thread completing the request from within a call into it cannot take the write lock
        boolean exclusive = lock.readHoldCount == 0
        if (exclusive) {
            lock.writeLock().lock()
        }
        try {
            List<WeakReference<Detachable>> detaching
            synchronized (monitor) {
                if (completed) {
                    return
                }
                completed = true
                detaching = attached
                attached = null
            }
            for (WeakReference<Detachable> reference : detaching ?: Collections.<WeakReference<Detachable>>emptyList()) {
                reference.get()?.detach()
            }
        }
        finally {
            if (exclusive) {
                lock.writeLock().unlock()
            }
        }
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

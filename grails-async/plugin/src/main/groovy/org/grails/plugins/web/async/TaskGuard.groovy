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
import java.lang.reflect.Proxy

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletOutputStream

/**
 * What a {@link TaskRequest} or {@link TaskResponse} delegates to: the live request or response, called under the
 * read lock of the request's {@link TaskRequestLifecycle}, until the request completes, and then nothing, so every
 * call throws an {@link IllegalStateException}.
 *
 * <p>What {@code getInputStream}, {@code getReader}, {@code getOutputStream} and {@code getWriter} return is guarded
 * as well, by {@link TaskStreams}. Starting asynchronous processing, and reading the parts once a read listener was
 * set through a task's stream, are not called under the read lock: the container may answer them on its own locks,
 * which a thread completing the request may hold while it waits for the lifecycle's lock, so they are checked
 * before and after instead. The parameters, headers, attribute names, locales, trailer fields and parts are
 * returned as a copy, since the container reuses what backs them for the requests that follow. Anything else, such
 * as an attribute's value, is returned as it is.</p>
 */
@PackageScope
@CompileStatic
final class TaskGuard implements InvocationHandler, TaskRequestLifecycle.Detachable {

    // What the container answers from what it reuses for the requests that follow, so a task keeps a copy
    private static final Set<String> SNAPSHOTS = Set.of('getParameterMap', 'getParameterNames', 'getHeaderNames',
            'getHeaders', 'getAttributeNames', 'getLocales', 'getTrailerFields', 'getParts')

    // What returns the body's or the output's stream, reader or writer, which a task reads or writes later
    private static final Set<String> STREAMS = Set.of('getInputStream', 'getReader', 'getOutputStream', 'getWriter')

    // What reads the body to answer, which, once a read listener was set through a task's stream, reads it without
    // blocking, so the container may answer it on the connection's own lock
    private static final Set<String> PARTS = Set.of('getPart', 'getParts')

    private final TaskRequestLifecycle lifecycle
    private final String what
    // The guarded stream, reader or writer of each the live object returned, so a task asking again gets the same
    private final Map<Object, Object> guarded = new IdentityHashMap<>()
    private volatile Object live

    private TaskGuard(TaskRequestLifecycle lifecycle, Object live, String what) {
        this.lifecycle = lifecycle
        this.live = live
        this.what = what
    }

    /**
     * A guard of a live object, attached to the lifecycle of its request.
     *
     * @param lifecycle the lifecycle of the request
     * @param live the live request or response
     * @param what what it is, for the failure
     * @return the guard
     */
    static TaskGuard of(TaskRequestLifecycle lifecycle, Object live, String what) {
        TaskGuard guard = new TaskGuard(lifecycle, live, what)
        lifecycle.attach(guard)
        return guard
    }

    /**
     * The guarded view of the live object, through the one interface given.
     *
     * @param type the interface, HttpServletRequest or HttpServletResponse
     * @return a proxy calling the live object while the request is live
     */
    <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.classLoader, [type] as Class<?>[], this))
    }

    /**
     * @return the live object, or null once the request has completed
     */
    Object getLive() {
        return live
    }

    /**
     * Has the guard call another live object, as a container does when it wraps the request for a forward and
     * unwraps it after.
     *
     * @param replacement the object to call from now on
     */
    void replace(Object replacement) {
        lifecycle.enter(live, what)
        try {
            live = replacement
        }
        finally {
            lifecycle.exit()
        }
    }

    @Override
    void detach() {
        live = null
        synchronized (guarded) {
            guarded.clear()
        }
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.declaringClass == Object) {
            switch (method.name) {
                case 'equals':
                    return proxy.is(args[0])
                case 'hashCode':
                    return System.identityHashCode(proxy)
                default:
                    return live == null ? "The $what of a completed request".toString() : "The $what of a task".toString()
            }
        }
        // Starting asynchronous processing waits for the asynchronous state of the container, which a thread
        // completing the request holds while it waits for the lock
        if (method.name == 'startAsync' || (PARTS.contains(method.name) && lifecycle.readingWithoutLock)) {
            return callUnlocked(method, args)
        }
        Object target = lifecycle.enter(live, what)
        try {
            return guard(method, method.invoke(target, args))
        }
        catch (InvocationTargetException e) {
            // A call completing did not wait for reports that the request has completed
            throw lifecycle.abandoned(what, e.targetException)
        }
        finally {
            lifecycle.exit()
        }
    }

    // Checked before and after, so a call the request completing overtook reports it, whatever it did
    private Object callUnlocked(Method method, Object[] args) throws Throwable {
        Object target = lifecycle.check(live, what)
        Object result
        try {
            result = method.invoke(target, args)
        }
        catch (InvocationTargetException e) {
            throw lifecycle.overtaken(what, e.targetException)
        }
        lifecycle.check(this, what)
        return guard(method, result)
    }

    private Object guard(Method method, Object result) {
        if (STREAMS.contains(method.name)) {
            return guardStream(result)
        }
        if (!SNAPSHOTS.contains(method.name)) {
            return result
        }
        if (result instanceof Enumeration) {
            return Collections.enumeration(Collections.list((Enumeration<?>) result))
        }
        if (result instanceof Map) {
            return Collections.unmodifiableMap(new LinkedHashMap<Object, Object>((Map<Object, Object>) result))
        }
        if (result instanceof Collection) {
            return Collections.unmodifiableList(new ArrayList<Object>((Collection<Object>) result))
        }
        return result
    }

    private Object guardStream(Object result) {
        if (result instanceof ServletInputStream || result instanceof BufferedReader ||
                result instanceof ServletOutputStream || result instanceof PrintWriter) {
            synchronized (guarded) {
                Object existing = guarded.get(result)
                if (existing == null) {
                    existing = TaskStreams.guard(lifecycle, result, what)
                    guarded.put(result, existing)
                }
                return existing
            }
        }
        return result
    }
}

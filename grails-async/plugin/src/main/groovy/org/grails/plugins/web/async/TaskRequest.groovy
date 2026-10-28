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

import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.http.HttpServletRequest

import org.springframework.web.context.request.RequestAttributes

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * The request a task started during a web request sees.
 *
 * <p>Until the request completes, it is that request, with every public view the request offers, such as a
 * multipart wrapper's, so a task the response waits for, such as one started with {@code WebPromises}, renders
 * through it. The container recycles the request once it completes, including any asynchronous processing, so
 * from then on it is a {@link CompletedHttpServletRequest copy} of the request taken as it completed, and the
 * recycled request is released. One copy is taken for the request, however many tasks it has, and each task
 * reads it through a view of its own, so each has the body to read.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequest implements InvocationHandler {

    private static final String STATE = TaskRequest.name + '.STATE'
    private static final String COMPLETION_CALLBACK = TaskRequest.name + '.COMPLETION'

    private final RequestState state
    private CompletedHttpServletRequest view

    private TaskRequest(RequestState state) {
        this.state = state
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
                // A task started from a task reads the same request through a view of its own
                return (HttpServletRequest) Proxy.newProxyInstance(current.getClass().classLoader,
                        current.getClass().interfaces, new TaskRequest(((TaskRequest) handler).state))
            }
        }
        ClassLoader loader = current.getClass().classLoader ?: TaskRequest.classLoader
        return (HttpServletRequest) Proxy.newProxyInstance(loader, interfacesOf(current),
                new TaskRequest(stateOf(webRequest, current)))
    }

    // One state for the request, however many tasks it has, so completing it takes the one copy they all read
    private static RequestState stateOf(GrailsWebRequest webRequest, HttpServletRequest current) {
        RequestState existing = boundTo(current)
        if (existing != null) {
            return existing
        }
        RequestState state = new RequestState(current)
        if (webRequest.isActive()) {
            current.setAttribute(STATE, state)
            webRequest.registerDestructionCallback(COMPLETION_CALLBACK, state::requestCompleted,
                    RequestAttributes.SCOPE_REQUEST)
        }
        else {
            state.requestCompleted()
        }
        return state
    }

    private static RequestState boundTo(HttpServletRequest current) {
        try {
            Object state = current.getAttribute(STATE)
            return state instanceof RequestState ? (RequestState) state : null
        }
        catch (IllegalStateException ignored) {
            // The container has already recycled the request
            return null
        }
    }

    // Every public view of the request, such as a multipart wrapper's, so the task finds what a filter resolved
    private static Class<?>[] interfacesOf(HttpServletRequest request) {
        Set<Class<?>> interfaces = new LinkedHashSet<>()
        interfaces.add(HttpServletRequest)
        for (Class<?> type = request.getClass(); type != null; type = type.superclass) {
            addInterfaces(type.interfaces, interfaces)
        }
        return interfaces.toArray(new Class<?>[0])
    }

    private static void addInterfaces(Class<?>[] candidates, Set<Class<?>> interfaces) {
        for (Class<?> candidate : candidates) {
            // A proxy class can only implement interfaces it can access
            if (Modifier.isPublic(candidate.modifiers) && interfaces.add(candidate)) {
                addInterfaces(candidate.interfaces, interfaces)
            }
        }
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.name == 'equals' && method.parameterCount == 1) {
            return proxy.is(args[0])
        }
        if (method.name == 'hashCode' && method.parameterCount == 0) {
            return System.identityHashCode(proxy)
        }
        HttpServletRequest current = state.live
        if (current == null) {
            current = view()
        }
        if (!method.declaringClass.isInstance(current)) {
            // Only the live request offers the views beyond HttpServletRequest, such as a multipart wrapper's
            throw new IllegalStateException('The request has completed, so a task that outlives it cannot call ' +
                    "${method.declaringClass.simpleName}.${method.name}, which the copy of the request does not keep")
        }
        try {
            return method.invoke(current, args)
        }
        catch (InvocationTargetException e) {
            throw e.targetException
        }
    }

    private synchronized HttpServletRequest view() {
        if (view == null) {
            view = state.copyView()
        }
        return view
    }

    private static final class RequestState implements AsyncListener {

        private volatile HttpServletRequest live
        private volatile CompletedHttpServletRequest copy

        private RequestState(HttpServletRequest live) {
            this.live = live
        }

        private void requestCompleted() {
            HttpServletRequest current = live
            if (current == null) {
                return
            }
            try {
                if (current.isAsyncStarted()) {
                    // The response is still being produced, so a task it waits for may still be rendering it
                    current.asyncContext.addListener(this)
                    return
                }
            }
            catch (IllegalStateException ignored) {
                // Too late to be told when the asynchronous processing completes, so the request is copied now
            }
            copyRequest()
        }

        private void copyRequest() {
            HttpServletRequest current = live
            if (current == null) {
                return
            }
            try {
                current.removeAttribute(STATE)
                copy = CompletedHttpServletRequest.copyOf(current)
            }
            catch (RuntimeException ignored) {
                // The container has already recycled the request, so there is nothing left to copy
            }
            finally {
                // Released whether or not the copy was taken, so no task holds the recycled request
                live = null
            }
        }

        private CompletedHttpServletRequest copyView() {
            CompletedHttpServletRequest completed = copy
            if (completed == null) {
                throw new IllegalStateException(
                        'The request has completed, and it was recycled before it could be copied, so a task that outlives it cannot read it')
            }
            return completed.newView()
        }

        @Override
        void onComplete(AsyncEvent event) {
            copyRequest()
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
}

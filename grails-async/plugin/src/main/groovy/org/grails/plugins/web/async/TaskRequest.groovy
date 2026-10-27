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
import java.util.concurrent.atomic.AtomicLong

import groovy.transform.CompileStatic

import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.http.HttpServletRequest

import org.springframework.web.context.request.RequestAttributes

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * The request a task started during a web request sees.
 *
 * <p>Until the request completes, it is that request, so a task the response waits for, such as one started with
 * {@code WebPromises}, renders through it. The container recycles the request once it completes, including any
 * asynchronous processing, so from then on it is a {@link CompletedHttpServletRequest copy} of the request taken
 * as it completed.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequest implements InvocationHandler, AsyncListener {

    private static final String COMPLETION_CALLBACK = TaskRequest.name + '.COMPLETION.'
    private static final AtomicLong COMPLETION_CALLBACKS = new AtomicLong()

    private final HttpServletRequest request
    private volatile HttpServletRequest target

    private TaskRequest(HttpServletRequest request) {
        this.request = request
        this.target = request
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
        if (Proxy.isProxyClass(current.getClass()) && Proxy.getInvocationHandler(current) instanceof TaskRequest) {
            // A task started from a task sees what that task sees
            return current
        }
        TaskRequest taskRequest = new TaskRequest(current)
        if (webRequest.isActive()) {
            webRequest.registerDestructionCallback(COMPLETION_CALLBACK + COMPLETION_CALLBACKS.incrementAndGet(),
                    taskRequest::requestCompleted, RequestAttributes.SCOPE_REQUEST)
        }
        else {
            taskRequest.requestCompleted()
        }
        return (HttpServletRequest) Proxy.newProxyInstance(TaskRequest.classLoader, [HttpServletRequest] as Class[], taskRequest)
    }

    private void requestCompleted() {
        if (request.isAsyncStarted()) {
            try {
                // The response is still being produced, so a task it waits for may still be rendering it
                request.asyncContext.addListener(this)
                return
            }
            catch (IllegalStateException ignored) {
                // Too late to be told when the asynchronous processing completes, so the request is copied now
            }
        }
        copy()
    }

    private void copy() {
        try {
            target = CompletedHttpServletRequest.copyOf(request)
        }
        catch (IllegalStateException ignored) {
            // The container has already recycled the request, so there is nothing left to copy
        }
    }

    @Override
    void onComplete(AsyncEvent event) {
        copy()
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

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.name == 'equals' && method.parameterCount == 1) {
            return proxy.is(args[0])
        }
        if (method.name == 'hashCode' && method.parameterCount == 0) {
            return System.identityHashCode(proxy)
        }
        try {
            return method.invoke(target, args)
        }
        catch (InvocationTargetException e) {
            throw e.targetException
        }
    }
}

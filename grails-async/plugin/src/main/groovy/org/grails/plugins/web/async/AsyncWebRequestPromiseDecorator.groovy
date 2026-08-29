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

import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import jakarta.servlet.AsyncContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils

import grails.async.decorator.PromiseDecorator
import grails.async.web.AsyncGrailsWebRequest
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils

/**
 * A promise decorated lookup strategy that binds a WebRequest to the promise thread
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class AsyncWebRequestPromiseDecorator implements PromiseDecorator {

    GrailsWebRequest webRequest
    final AsyncGrailsWebRequest asyncRequest
    final AsyncContext asyncContext
    volatile boolean timeoutReached = false

    /**
     * Whether this decorator started the asynchronous cycle itself, as opposed to joining one
     * already in flight. The two are guarded differently when the task runs: see {@link #decorate}.
     */
    private final boolean startedCycle

    AsyncWebRequestPromiseDecorator(GrailsWebRequest webRequest) {
        this.webRequest = webRequest
        HttpServletRequest currentServletRequest = webRequest.currentRequest
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(currentServletRequest)
        AsyncGrailsWebRequest newWebRequest = AsyncGrailsWebRequest.lookup(currentServletRequest)
        boolean startedHere = false
        if (newWebRequest != null) {
            if (newWebRequest.isAsyncComplete() || newWebRequest.asyncContext == null) {
                throw new IllegalStateException('Cannot start a task once asynchronous request processing has completed')
            }
            asyncContext = newWebRequest.asyncContext
        }
        else if (asyncManager.isConcurrentHandlingStarted()) {
            throw new IllegalStateException('Cannot find the asynchronous request currently being processed')
        }
        else {
            newWebRequest = new AsyncGrailsWebRequest(currentServletRequest, webRequest.currentResponse, webRequest.servletContext, webRequest.applicationContext)
            asyncManager.setAsyncWebRequest(newWebRequest)
            newWebRequest.startAsync()
            asyncContext = newWebRequest.asyncContext
            asyncContext.setTimeout(-1)
            startedHere = true
        }
        newWebRequest.addTimeoutHandler({
            timeoutReached = true
        })
        asyncRequest = newWebRequest
        startedCycle = startedHere
    }

    @Override
    def <D> Closure<D> decorate(Closure<D> c) {
        return (Closure<D>) {  args ->
            if (timeoutReached) {
                throw new TimeoutException('Asynchronous request processing timeout reached')
            }
            // Checked before the AsyncContext is touched: a completed context throws from
            // getRequest(), which would replace the message below with the container's.
            if (asyncRequest.isAsyncComplete()) {
                throw new IllegalStateException('Asynchronous request already terminated. Likely timeout reached')
            }
            HttpServletRequest request = (HttpServletRequest) asyncContext.request
            // A cycle this decorator started must still be running, exactly as it was required to
            // be before a task could join a cycle in flight. A joined cycle is exempt: while the
            // container delivers its result, isAsyncStarted() is already false although the
            // request is still live, which is the window joining exists for.
            if (startedCycle && !request.isAsyncStarted()) {
                throw new IllegalStateException('Asynchronous request already terminated. Likely timeout reached')
            }
            WebUtils.storeGrailsWebRequest(new GrailsWebRequest(request, (HttpServletResponse) asyncContext.response, webRequest.attributes))
            try {
                return invokeClosure(c, args)
            }
            finally {
                RequestContextHolder.resetRequestAttributes()
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    def invokeClosure(Closure c, args) {
        if (args == null) {
            c.call(null)
        }
        else if (args && args.getClass().isArray()) {
            c.call(*args)
        }
        else if (args instanceof List) {
            c.call(*args)
        }
        else {
            c.call(args)
        }
    }
}

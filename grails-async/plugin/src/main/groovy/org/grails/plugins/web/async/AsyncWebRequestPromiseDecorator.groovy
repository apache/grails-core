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

import grails.async.web.AsyncGrailsWebRequest
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import grails.async.decorator.PromiseDecorator
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils

import jakarta.servlet.AsyncContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.TimeoutException

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

    AsyncWebRequestPromiseDecorator(GrailsWebRequest webRequest) {
        this.webRequest = webRequest
        HttpServletRequest currentServletRequest = webRequest.currentRequest
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(currentServletRequest)
        AsyncGrailsWebRequest newWebRequest
        if(asyncManager.isConcurrentHandlingStarted()) {
            newWebRequest = AsyncGrailsWebRequest.lookup(currentServletRequest)
            asyncContext = newWebRequest.asyncContext
            if( newWebRequest == null || newWebRequest.isAsyncComplete() ) {
                throw new IllegalStateException("Cannot start a task once asynchronous request processing has completed")
            }
        }
        else {
            newWebRequest = new AsyncGrailsWebRequest(currentServletRequest, webRequest.currentResponse, webRequest.servletContext,webRequest.applicationContext)
            asyncManager.setAsyncWebRequest(newWebRequest)
            newWebRequest.startAsync()
            asyncContext = newWebRequest.asyncContext
            asyncContext.setTimeout(-1)
        }
        newWebRequest.addTimeoutHandler({
            timeoutReached = true
        })
        asyncRequest = newWebRequest
    }

    @Override
    def <D> Closure<D> decorate(Closure<D> c) {
        return (Closure<D>) {  args ->
            if(timeoutReached) {
                throw new TimeoutException("Asynchronous request processing timeout reached")
            }
            HttpServletRequest request = (HttpServletRequest) asyncContext.request
            if(request.isAsyncStarted()) {

                WebUtils.storeGrailsWebRequest(new GrailsWebRequest(request, (HttpServletResponse)asyncContext.response, webRequest.attributes))
                try {
                    return invokeClosure(c, args)
                }
                finally {
                    RequestContextHolder.resetRequestAttributes()
                }
            }
            else {
                throw new IllegalStateException("Asynchronous request already terminated. Likely timeout reached")
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    def invokeClosure(Closure c, args) {
        if (args == null) {
            c.call(null)
        }
        else if(args && args.getClass().isArray()) {
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

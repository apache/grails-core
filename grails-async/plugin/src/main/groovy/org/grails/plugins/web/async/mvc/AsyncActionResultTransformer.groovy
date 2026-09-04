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
package org.grails.plugins.web.async.mvc

import groovy.transform.CompileStatic

import org.springframework.web.context.request.async.AsyncWebRequest
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.servlet.ModelAndView

import grails.async.Promise
import grails.async.PromiseList
import org.grails.web.servlet.mvc.ActionResultTransformer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Handles an Async response from a controller
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class AsyncActionResultTransformer implements ActionResultTransformer {

    Object transformActionResult(GrailsWebRequest webRequest, String viewName, Object actionResult) {

        if (actionResult instanceof Promise promise) {
            final request = webRequest.getCurrentRequest()
            WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)
            final response = webRequest.getResponse()

            if (!asyncManager.isConcurrentHandlingStarted()) {
                AsyncWebRequest asyncWebRequest = new StandardServletAsyncWebRequest(request, response)
                asyncManager.setAsyncWebRequest(asyncWebRequest)
            }
            DeferredResult<Object> deferredResult = new DeferredResult<Object>()
            asyncManager.startDeferredResultProcessing(deferredResult)
            request.setAttribute(GrailsApplicationAttributes.ASYNC_STARTED, true)

            promise.onComplete { Object value ->
                if (promise instanceof PromiseList) {
                    deferredResult.setResult(null)
                }
                else if (value instanceof Map model) {
                    deferredResult.setResult(new ModelAndView(viewName, model))
                }
                else {
                    deferredResult.setResult(request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW))
                }
            }
            promise.onError { Throwable failure ->
                deferredResult.setErrorResult(unwrap(failure))
            }
            return null
        }
        return actionResult
    }

    private static Throwable unwrap(Throwable failure) {
        return failure.cause ?: failure
    }
}

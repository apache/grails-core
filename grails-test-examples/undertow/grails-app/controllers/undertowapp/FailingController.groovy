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
package undertowapp

import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.InternalResourceView

import org.grails.web.mapping.ForwardUrlMappingInfo
import org.grails.web.mapping.UrlMappingUtils

class FailingController {

    def answeredByView() {
        throw new IllegalStateException('answered by the error view')
    }

    def answeredByController() {
        throw new UnsupportedOperationException('answered by the error controller')
    }

    def answeredByViewAsynchronously() {
        failAsynchronously(new IllegalStateException('answered by the error view'))
    }

    def answeredByControllerAsynchronously() {
        failAsynchronously(new UnsupportedOperationException('answered by the error controller'))
    }

    def answeredByViewAfterStartingAsync() {
        startAsyncThatTimesOut()
        throw new IllegalStateException('answered by the error view')
    }

    def answeredByControllerAfterStartingAsync() {
        startAsyncThatTimesOut()
        throw new UnsupportedOperationException('answered by the error controller')
    }

    def answeredByViewForAnError() {
        throw new AssertionError('answered by the error view')
    }

    def answeredByControllerForAnError() {
        throw new StackOverflowError('answered by the error controller')
    }

    def answeredByForwardingController() {
        throw new ArithmeticException('answered by an error controller that forwards')
    }

    def answeredByIncludingController() {
        throw new NoSuchElementException('answered by an error controller that includes an action')
    }

    def forwardedToView() {
        forward(action: 'answeredByView')
    }

    def forwardedToViewAsynchronously() {
        forwardAsynchronously('/failing/answeredByView')
    }

    def forwardedToControllerAsynchronously() {
        forwardAsynchronously('/failing/answeredByController')
    }

    def forwardedToController() {
        forward(action: 'answeredByController')
    }

    def failsAfterInclude() {
        g.include(action: 'included')
        throw new IllegalStateException('answered by the error view')
    }

    def failsAfterIncludeAnsweredByController() {
        g.include(action: 'included')
        throw new UnsupportedOperationException('answered by the error controller')
    }

    def includesAnswerOfView() {
        render "before ${g.include(action: 'answeredByView')} after"
    }

    def includesAnswerOfController() {
        render "before ${g.include(action: 'answeredByController')} after"
    }

    def includesThroughUrlMappingUtils() {
        response.writer.write('before ')
        UrlMappingUtils.includeRequestForUrlMappingInfo(request, response, new ForwardUrlMappingInfo(controller: 'failing', action: 'included', params: [marker: 'included']), [:], false)
        render " after, as ${controllerName}/${actionName} with ${params.marker}"
    }

    def included() {
        render 'included'
    }

    def errorHandler() {
        render status: 500, text: 'Answered by the error controller'
    }

    def forwardingErrorHandler() {
        forward(action: 'errorHandler')
    }

    def includingErrorHandler() {
        response.status = 500
        response.writer.write('Answered by the error controller, with ')
        UrlMappingUtils.includeRequestForUrlMappingInfo(request, response, new ForwardUrlMappingInfo(controller: 'failing', action: 'included'), [:], false)
        render ' inside'
    }

    private void failAsynchronously(Exception failure) {
        DeferredResult<Object> result = new DeferredResult<>()
        startAsync(result)
        result.errorResult = failure
    }

    /**
     * Completes asynchronous processing with a view that forwards, as a view Spring MVC resolves from a
     * {@code forward:} name does, so that the action the path maps to answers on the asynchronous dispatch.
     */
    private void forwardAsynchronously(String path) {
        DeferredResult<Object> result = new DeferredResult<>()
        startAsync(result)
        result.result = new ModelAndView(new InternalResourceView(path))
    }

    /**
     * Starts asynchronous processing that nothing completes. Unless the container ends it when the action fails, as
     * Tomcat does when the exception is left on the request, it ends when it times out.
     */
    private void startAsyncThatTimesOut() {
        startAsync(new DeferredResult<Object>(500L))
    }

    private void startAsync(DeferredResult<Object> result) {
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)
        asyncManager.asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response)
        asyncManager.startDeferredResultProcessing(result)
    }
}

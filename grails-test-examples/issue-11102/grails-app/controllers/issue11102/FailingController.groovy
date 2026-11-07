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

package issue11102

import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils

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

    def answeredByControllerAfterStartingAsync() {
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)
        asyncManager.asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response)
        asyncManager.startDeferredResultProcessing(new DeferredResult<Object>())
        throw new UnsupportedOperationException('answered by the error controller')
    }

    def failsInInterceptor() {
        render 'never reached, because the interceptor fails'
    }

    def errorHandler() {
        render status: 500, text: 'Answered by the error controller'
    }

    private void failAsynchronously(Exception failure) {
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)
        asyncManager.asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response)
        DeferredResult<Object> result = new DeferredResult<>()
        asyncManager.startDeferredResultProcessing(result)
        result.errorResult = failure
    }
}

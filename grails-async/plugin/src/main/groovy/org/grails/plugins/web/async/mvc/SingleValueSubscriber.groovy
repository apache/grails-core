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

import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import jakarta.servlet.http.HttpServletRequest
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.servlet.ModelAndView

import org.grails.web.util.GrailsApplicationAttributes

/**
 * Resolves a publisher that emits at most one item, such as a Reactor {@code Mono}, to the action's
 * result, the way a returned {@code Promise} resolves: a {@code Map} becomes the model of the view,
 * anything else leaves the response to what the action rendered, and a failure fails the request.
 *
 * @since 8.1
 */
@CompileStatic
class SingleValueSubscriber implements Subscriber<Object> {

    private final HttpServletRequest request
    private final String viewName
    private final DeferredResult<Object> deferredResult
    private final AtomicBoolean done = new AtomicBoolean()
    private volatile Object value

    SingleValueSubscriber(HttpServletRequest request, String viewName, DeferredResult<Object> deferredResult) {
        this.request = request
        this.viewName = viewName
        this.deferredResult = deferredResult
    }

    @Override
    void onSubscribe(Subscription subscription) {
        subscription.request(1)
    }

    @Override
    void onNext(Object item) {
        value = item
    }

    @Override
    void onError(Throwable failure) {
        if (done.compareAndSet(false, true)) {
            deferredResult.setErrorResult(failure)
        }
    }

    @Override
    void onComplete() {
        if (!done.compareAndSet(false, true)) {
            return
        }
        Object resolved = value
        if (resolved instanceof Map) {
            deferredResult.setResult(new ModelAndView(viewName, (Map) resolved))
        }
        else {
            deferredResult.setResult(request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW))
        }
    }

}

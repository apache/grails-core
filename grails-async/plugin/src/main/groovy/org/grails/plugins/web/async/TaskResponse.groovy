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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import jakarta.servlet.ServletResponse
import jakarta.servlet.ServletResponseWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper

/**
 * The response a task started during a web request is given: a wrapper of the response of the request the task was
 * started during that calls it until the request completes, and then throws an {@link IllegalStateException} on
 * every call, as {@link TaskRequest} does for the request.
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskResponse extends HttpServletResponseWrapper {

    private final TaskGuard guard

    @PackageScope
    TaskResponse(HttpServletResponse live, TaskRequestLifecycle lifecycle) {
        this(TaskGuard.of(lifecycle, live, 'response'))
    }

    private TaskResponse(TaskGuard guard) {
        super(guard.proxy(HttpServletResponse))
        this.guard = guard
    }

    /**
     * @return the response of the request the task was started during while the request is live, or, once it has
     *         completed, an object that throws an {@link IllegalStateException} on every call
     */
    @Override
    ServletResponse getResponse() {
        Object live = guard?.live
        return live != null ? (ServletResponse) live : super.getResponse()
    }

    /**
     * Calls the given response from now on, as a container does when it wraps the response for an include and
     * unwraps it after.
     *
     * @param response the response
     * @throws IllegalStateException once the request has completed
     */
    @Override
    void setResponse(ServletResponse response) {
        if (guard == null) {
            super.setResponse(response)
            return
        }
        if (response == null) {
            throw new IllegalArgumentException('Response cannot be null')
        }
        guard.replace(response)
    }

    @Override
    boolean isWrapperFor(ServletResponse wrapped) {
        ServletResponse response = getResponse()
        return response.is(wrapped) ||
                (response instanceof ServletResponseWrapper && ((ServletResponseWrapper) response).isWrapperFor(wrapped))
    }

    @Override
    boolean isWrapperFor(Class<?> wrappedType) {
        if (!ServletResponse.isAssignableFrom(wrappedType)) {
            throw new IllegalArgumentException("Given class ${wrappedType.name} not a subinterface of ${ServletResponse.name}")
        }
        ServletResponse response = getResponse()
        return wrappedType.isInstance(response) ||
                (response instanceof ServletResponseWrapper && ((ServletResponseWrapper) response).isWrapperFor(wrappedType))
    }
}

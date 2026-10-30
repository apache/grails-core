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

import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletRequestWrapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

import org.grails.web.util.GrailsApplicationAttributes

/**
 * The request a task started during a web request is given: a wrapper of the request the task was started during
 * that calls it until the request completes, and then throws an {@link IllegalStateException} on every call.
 *
 * <p>While the request is live, {@link #getRequest()} is the request itself, so unwrapping the task's request, as
 * {@code org.springframework.web.util.WebUtils.getNativeRequest} does, finds what it wraps. Once the request has
 * completed, the wrapper no longer holds it, since the container recycles it for the requests that follow.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequest extends HttpServletRequestWrapper {

    private final TaskGuard guard

    @PackageScope
    final TaskRequestLifecycle lifecycle

    @PackageScope
    TaskRequest(HttpServletRequest live, TaskRequestLifecycle lifecycle) {
        this(TaskGuard.of(lifecycle, live, 'request'), lifecycle)
    }

    private TaskRequest(TaskGuard guard, TaskRequestLifecycle lifecycle) {
        super(guard.proxy(HttpServletRequest))
        this.guard = guard
        this.lifecycle = lifecycle
    }

    /**
     * @return the request the task was started during while it is live, or, once it has completed, an object
     *         that throws an {@link IllegalStateException} on every call
     */
    @Override
    ServletRequest getRequest() {
        Object live = guard?.live
        return live != null ? (ServletRequest) live : super.getRequest()
    }

    /**
     * Calls the given request from now on, as a container does when it wraps the request for a forward and
     * unwraps it after.
     *
     * @param request the request
     * @throws IllegalStateException once the request has completed
     */
    @Override
    void setRequest(ServletRequest request) {
        if (guard == null) {
            super.setRequest(request)
            return
        }
        if (request == null) {
            throw new IllegalArgumentException('Request cannot be null')
        }
        guard.replace(request)
    }

    /**
     * The request's attribute, except the web request the request is bound to, which belongs to the thread
     * processing the request: a task finds its own through {@code GrailsWebRequest.lookup()}.
     */
    @Override
    Object getAttribute(String name) {
        return name == GrailsApplicationAttributes.WEB_REQUEST ? null : super.getAttribute(name)
    }

    @Override
    Enumeration<String> getAttributeNames() {
        List<String> names = Collections.list(super.attributeNames)
        names.remove(GrailsApplicationAttributes.WEB_REQUEST)
        return Collections.enumeration(names)
    }

    /**
     * Sets the request's attribute, except the web request the request is bound to, which a task does not replace.
     */
    @Override
    void setAttribute(String name, Object value) {
        if (name != GrailsApplicationAttributes.WEB_REQUEST) {
            super.setAttribute(name, value)
        }
    }

    @Override
    void removeAttribute(String name) {
        if (name != GrailsApplicationAttributes.WEB_REQUEST) {
            super.removeAttribute(name)
        }
    }

    @Override
    boolean isWrapperFor(ServletRequest wrapped) {
        ServletRequest request = getRequest()
        return request.is(wrapped) || (request instanceof ServletRequestWrapper && ((ServletRequestWrapper) request).isWrapperFor(wrapped))
    }

    @Override
    boolean isWrapperFor(Class<?> wrappedType) {
        if (!ServletRequest.isAssignableFrom(wrappedType)) {
            throw new IllegalArgumentException("Given class ${wrappedType.name} not a subinterface of ${ServletRequest.name}")
        }
        ServletRequest request = getRequest()
        return wrappedType.isInstance(request) ||
                (request instanceof ServletRequestWrapper && ((ServletRequestWrapper) request).isWrapperFor(wrappedType))
    }
}

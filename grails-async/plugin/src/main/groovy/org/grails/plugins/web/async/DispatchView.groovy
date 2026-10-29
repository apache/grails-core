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

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletMapping
import jakarta.servlet.http.HttpServletRequest

/**
 * How a request was dispatched when a task was given it: its path, its dispatcher type and mapping, the forward
 * and include attributes and, for a forward or an include, its parameters.
 *
 * <p>A container can give a forward or an include the very request the dispatch was started with, a wrapper the
 * application added, with its own wrapper inserted underneath, and remove that wrapper as the dispatch returns,
 * after which the request shows the dispatch that started it again. The task keeps what its request showed when
 * the task started, while the request is live and in its copy.</p>
 */
@CompileStatic
@PackageScope
class DispatchView {

    // Answers a method the view does not answer, so the request answers it
    static final Object UNANSWERED = new Object()

    private static final List<String> DISPATCH_ATTRIBUTE_PREFIXES = List.of('jakarta.servlet.forward.',
            'jakarta.servlet.include.')

    final String requestURI
    final String requestURL
    final String contextPath
    final String servletPath
    final String pathInfo
    final String pathTranslated
    final String queryString
    final DispatcherType dispatcherType
    final HttpServletMapping httpServletMapping
    // The forward and include attributes the request showed, which a task can set and remove, as on the request
    final Map<String, Object> attributes = new ConcurrentHashMap<>()
    // Null unless the request was a forward or an include, whose parameters the container restores too
    final CompletedHttpServletRequest.CopiedParameters parameters

    DispatchView(HttpServletRequest request) {
        this.requestURI = request.requestURI
        this.requestURL = request.requestURL?.toString()
        this.contextPath = request.contextPath
        this.servletPath = request.servletPath
        this.pathInfo = request.pathInfo
        this.pathTranslated = request.pathTranslated
        this.queryString = request.queryString
        this.dispatcherType = request.dispatcherType
        this.httpServletMapping = CompletedHttpServletRequest.copyMapping(request.httpServletMapping)
        for (String name : Collections.list(request.attributeNames)) {
            Object value = dispatchAttribute(name) ? request.getAttribute(name) : null
            if (value != null) {
                attributes.put(name, value)
            }
        }
        this.parameters = dispatcherType == DispatcherType.FORWARD || dispatcherType == DispatcherType.INCLUDE ?
                CompletedHttpServletRequest.copyParameters(request) : null
    }

    /**
     * The view of a request as it is now, or {@code null} when the request can no longer tell, as when the
     * container has recycled it.
     *
     * @param request the request a task is given
     * @return the view
     */
    static DispatchView of(HttpServletRequest request) {
        try {
            return new DispatchView(request)
        }
        catch (RuntimeException ignored) {
            return null
        }
    }

    static boolean same(DispatchView view, DispatchView other) {
        return view == null || other == null ? view.is(other) : view.sameAs(other)
    }

    static boolean dispatchAttribute(String name) {
        for (String prefix : DISPATCH_ATTRIBUTE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true
            }
        }
        return false
    }

    /**
     * Whether the view is the one another task was given the same request with, so the tasks can share it.
     *
     * @param other the view of the other task
     * @return whether both saw the request dispatched the same way
     */
    boolean sameAs(DispatchView other) {
        return requestURI == other.requestURI && contextPath == other.contextPath &&
                servletPath == other.servletPath && pathInfo == other.pathInfo &&
                queryString == other.queryString && dispatcherType == other.dispatcherType &&
                attributes == other.attributes
    }

    /**
     * Answers a call to the live request with what the view holds, so a task keeps how its request was dispatched
     * after a forward or an include returns.
     *
     * @param method the method called
     * @param args the arguments of the call
     * @param live the live request, for what the view does not hold
     * @return the answer, or {@link #UNANSWERED} when the request answers the call
     */
    Object answer(Method method, Object[] args, HttpServletRequest live) {
        int count = method.parameterCount
        String name = method.name
        if (count == 0) {
            return answerWithoutArguments(name, live)
        }
        if (count == 1 && args[0] instanceof String) {
            return answerFor(name, (String) args[0])
        }
        if (count == 2 && name == 'setAttribute' && args[0] instanceof String && dispatchAttribute((String) args[0])) {
            if (args[1] == null) {
                attributes.remove(args[0])
            }
            else {
                attributes.put((String) args[0], args[1])
            }
            return null
        }
        return UNANSWERED
    }

    private Object answerWithoutArguments(String name, HttpServletRequest live) {
        switch (name) {
            case 'getRequestURI': return requestURI
            case 'getRequestURL': return requestURL == null ? null : new StringBuffer(requestURL)
            case 'getContextPath': return contextPath
            case 'getServletPath': return servletPath
            case 'getPathInfo': return pathInfo
            case 'getPathTranslated': return pathTranslated
            case 'getQueryString': return queryString
            case 'getDispatcherType': return dispatcherType
            case 'getHttpServletMapping': return httpServletMapping
            case 'getAttributeNames': return attributeNames(live)
            case 'getParameterNames': return parameters == null ? UNANSWERED : Collections.enumeration(readParameters().keySet())
            case 'getParameterMap': return parameters == null ? UNANSWERED : readParameters()
            default: return UNANSWERED
        }
    }

    private Object answerFor(String name, String argument) {
        switch (name) {
            case 'getAttribute': return dispatchAttribute(argument) ? attributes.get(argument) : UNANSWERED
            case 'removeAttribute':
                if (!dispatchAttribute(argument)) {
                    return UNANSWERED
                }
                attributes.remove(argument)
                return null
            case 'getParameter':
                if (parameters == null) {
                    return UNANSWERED
                }
                String[] values = readParameters().get(argument)
                return values ? values[0] : null
            case 'getParameterValues':
                if (parameters == null) {
                    return UNANSWERED
                }
                String[] values = readParameters().get(argument)
                return values == null ? null : Arrays.copyOf(values, values.length)
            default: return UNANSWERED
        }
    }

    private Enumeration<String> attributeNames(HttpServletRequest live) {
        Set<String> names = new LinkedHashSet<>()
        for (String name : Collections.list(live.attributeNames)) {
            if (!dispatchAttribute(name)) {
                names.add(name)
            }
        }
        names.addAll(attributes.keySet())
        return Collections.enumeration(names)
    }

    private Map<String, String[]> readParameters() {
        if (parameters.failure != null) {
            throw parameters.failure
        }
        return parameters.values
    }
}

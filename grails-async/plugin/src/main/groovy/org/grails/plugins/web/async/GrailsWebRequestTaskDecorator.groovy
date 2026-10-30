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

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.core.task.TaskDecorator
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Propagates the current Grails web request through a Spring-managed task executor.
 *
 * <p>The task is given a {@link TaskRequest} and a {@link TaskResponse}: wrappers of the request and response it was
 * started during, which call them while the request is live and throw an {@link IllegalStateException} once it has
 * completed, since the container recycles them then. From then on {@link GrailsWebRequest#lookup()} no longer finds
 * the task's web request, so code the task runs, such as data binding, behaves as it does outside a request. A task
 * that starts after the request has completed runs without a web request.</p>
 *
 * <p>The task's web request is bound to the task's thread only, not stored on the request, which the request thread
 * is still processing, so {@link GrailsWebRequest#lookup(jakarta.servlet.http.HttpServletRequest)} finds the request
 * thread's own web request there and the task's in the task.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsWebRequestTaskDecorator implements TaskDecorator {

    @Override
    Runnable decorate(Runnable task) {
        return decorate(task, GrailsWebRequest.lookup())
    }

    Runnable decorate(Runnable task, GrailsWebRequest captured) {
        if (captured == null) {
            return task
        }

        HttpServletRequest current = captured.currentRequest
        HttpServletResponse currentResponse = captured.currentResponse
        TaskRequestLifecycle lifecycle
        if (current instanceof TaskRequest) {
            // A task started from a task is given wrappers of its own of what that task's wrap, with the same life
            lifecycle = ((TaskRequest) current).lifecycle
            current = (HttpServletRequest) current.request
            if (currentResponse instanceof TaskResponse) {
                currentResponse = (HttpServletResponse) currentResponse.response
            }
        }
        else {
            try {
                lifecycle = TaskRequestLifecycle.of(captured)
            }
            catch (IllegalStateException ignored) {
                // The web request outlived its request, which the container has recycled, so the task runs without it
                return task
            }
        }
        // Each task has wrappers of its own, so a forward or include one task makes does not change another's
        TaskRequest request = new TaskRequest(current, lifecycle)
        HttpServletResponse response = currentResponse == null ? null : new TaskResponse(currentResponse, lifecycle)
        GrailsApplicationAttributes attributes = captured.attributes
        return () -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes()
            try {
                // Nothing is bound while the task's web request is created, so whichever thread runs the task, it
                // starts with an encoding state of its own, not the one of a web request bound to that thread
                RequestContextHolder.resetRequestAttributes()
                // Created while the request is live, so it does not complete meanwhile. It is bound to the task's
                // thread only: the request keeps its own web request, which the container still uses after the tasks
                // of an asynchronous request are detached, such as for an error page
                GrailsWebRequest taskRequest = request.lifecycle.whileLive {
                    new TaskWebRequest(request, response, attributes)
                }
                if (taskRequest != null) {
                    RequestContextHolder.setRequestAttributes(taskRequest)
                }
                task.run()
            }
            finally {
                if (previous == null) {
                    RequestContextHolder.resetRequestAttributes()
                }
                else {
                    RequestContextHolder.setRequestAttributes(previous)
                }
            }
        }
    }
}

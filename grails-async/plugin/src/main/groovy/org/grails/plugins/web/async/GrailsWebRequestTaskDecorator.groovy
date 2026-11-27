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

import org.springframework.core.task.TaskDecorator
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Propagates the current Grails web request through a Spring-managed task executor.
 *
 * <p>The task is bound to a web request of its own for the request it was started during. That web request is
 * bound to the task's thread only, not stored on the request, which the request thread is still processing, so
 * {@link GrailsWebRequest#lookup(jakarta.servlet.http.HttpServletRequest)} finds the request thread's own web
 * request there and the task's in the task.</p>
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

        return () -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes()
            GrailsWebRequest taskRequest = new GrailsWebRequest(
                    captured.currentRequest,
                    captured.currentResponse,
                    captured.attributes)
            // Bound to the task's thread only: the request is still shared with the request thread, which
            // would find the task's web request on it from then on, and a task that outlives the response,
            // such as a fire-and-forget task, would write it to a request the container may have recycled
            RequestContextHolder.setRequestAttributes(taskRequest)
            try {
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

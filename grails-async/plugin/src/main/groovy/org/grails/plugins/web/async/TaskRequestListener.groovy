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

import java.time.Duration

import groovy.transform.CompileStatic

import jakarta.servlet.ServletRequestEvent
import jakarta.servlet.ServletRequestListener

/**
 * Tells the tasks started during a request when the container is done with it, after any forward, include or
 * asynchronous processing within it and before it recycles the request, so from then on their requests and
 * responses throw an {@link IllegalStateException} instead of reaching a recycled request. The async plugin
 * registers it.
 *
 * <p>Completing the request waits for a call a task is making on it, for as long as {@link #getCallWait()} gives,
 * in case the call waits for a lock of the container that the thread completing the request holds. After that the
 * request completes without it, and the task's next call throws.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequestListener implements ServletRequestListener {

    private final Duration callWait

    /**
     * A listener that waits for a call a task is making for 60 seconds, as long as Tomcat lets a blocking read or
     * write wait by default.
     */
    TaskRequestListener() {
        this(TaskRequestLifecycle.DEFAULT_CALL_WAIT)
    }

    /**
     * @param callWait how long completing a request waits for a call a task is making on it
     */
    TaskRequestListener(Duration callWait) {
        if (callWait == null || callWait.negative) {
            throw new IllegalArgumentException("The wait for a task's call must not be negative, but was $callWait")
        }
        this.callWait = callWait
    }

    /**
     * @return how long completing a request waits for a call a task is making on it
     */
    Duration getCallWait() {
        return callWait
    }

    @Override
    void requestInitialized(ServletRequestEvent event) {
        TaskRequestLifecycle.initialized(event.servletRequest, callWait)
    }

    @Override
    void requestDestroyed(ServletRequestEvent event) {
        TaskRequestLifecycle.destroyed(event.servletRequest)
    }
}

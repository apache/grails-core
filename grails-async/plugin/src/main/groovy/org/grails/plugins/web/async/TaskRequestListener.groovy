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

import jakarta.servlet.ServletRequestEvent
import jakarta.servlet.ServletRequestListener

/**
 * Tells the tasks started during a request when the container is done with it, after any forward, include or
 * asynchronous processing within it and before it recycles the request, so from then on their requests and
 * responses throw an {@link IllegalStateException} instead of reaching a recycled request. The async plugin
 * registers it.
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequestListener implements ServletRequestListener {

    @Override
    void requestInitialized(ServletRequestEvent event) {
        TaskRequestLifecycle.initialized(event.servletRequest)
    }

    @Override
    void requestDestroyed(ServletRequestEvent event) {
        TaskRequestLifecycle.destroyed(event.servletRequest)
    }
}

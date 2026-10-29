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

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

/**
 * Tells when a request has been processed, so the tasks started during it switch to copies of the requests they
 * were given then, and not when a forward or an include within it ends.
 *
 * <p>A forward or an include binds a web request of its own and completes it as it returns, or completes the one it
 * reused, while the request that dispatched it is still being processed. Mapped to the dispatches the container
 * starts, the request, asynchronous and error dispatches, never to a forward or an include, this filter marks the
 * request while such a dispatch is processed and has the requests copied as it ends, or, while the request is
 * processed asynchronously, as that processing completes. The async plugin registers it as the innermost filter, so
 * the copies are taken inside the other filters, such as a security filter whose context a copy reads. A dispatch
 * within one the filter already marks passes through.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class TaskRequestFilter implements Filter {

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!TaskRequest.processing(request)) {
            chain.doFilter(request, response)
            return
        }
        try {
            chain.doFilter(request, response)
        }
        finally {
            TaskRequest.processed(request)
        }
    }
}

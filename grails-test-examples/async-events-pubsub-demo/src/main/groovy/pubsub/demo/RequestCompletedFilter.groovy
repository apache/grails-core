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
package pubsub.demo

import java.util.concurrent.CountDownLatch

import groovy.transform.CompileStatic

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

/**
 * The outermost filter, which tells when a request leaves the application, once every other filter has ended. It
 * wraps a request that asks for it, as an application filter does, so a forward is given that wrapper with the
 * container's own wrapper underneath.
 */
@CompileStatic
class RequestCompletedFilter implements Filter {

    static final String COMPLETED = RequestCompletedFilter.name + '.COMPLETED'
    static final String WRAPPED = 'X-Wrapped'

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        CountDownLatch completed = new CountDownLatch(1)
        request.setAttribute(COMPLETED, completed)
        HttpServletRequest http = (HttpServletRequest) request
        ServletRequest processed = http.getHeader(WRAPPED) ? new HttpServletRequestWrapper(http) : request
        try {
            chain.doFilter(processed, response)
        }
        finally {
            completed.countDown()
        }
    }
}

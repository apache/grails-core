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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import jakarta.servlet.http.HttpServletRequest

import grails.async.Promises
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils

/**
 * Starts tasks that read the request they were started during once it has left the application, and keeps what
 * they read.
 */
class TaskRequestService {

    final Map<String, Map<String, Object>> seen = new ConcurrentHashMap<>()

    void readOnceCompleted(CharSequence name) {
        HttpServletRequest live = GrailsWebRequest.lookup().currentRequest
        CountDownLatch completed = (CountDownLatch) live.getAttribute(RequestCompletedFilter.COMPLETED)
        Promises.task {
            completed.await(10, TimeUnit.SECONDS)
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen[name.toString()] = [late: request.getAttribute('late'), forward: WebUtils.isForward(request),
                                     forwardURI: WebUtils.getForwardURI(request), uri: request.requestURI]
        }
    }
}

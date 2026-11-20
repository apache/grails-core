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
import jakarta.servlet.http.HttpServletResponse

import grails.async.Promises
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Starts tasks that read the request they were started during, and keeps what they saw.
 */
class TaskRequestService {

    final Map<String, Map<String, Object>> seen = new ConcurrentHashMap<>()

    // A task that outlives the request: it takes the request while it is live, waits until the container is done
    // with it, then uses what it took and binds a domain class
    void readOnceDetached(String name) {
        CountDownLatch taken = new CountDownLatch(1)
        Promises.task {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup()
            HttpServletRequest request = webRequest.currentRequest
            HttpServletResponse response = webRequest.currentResponse
            Map<String, Object> result = [live: request.getAttribute('model')]
            taken.countDown()
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (GrailsWebRequest.lookup() != null && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            result.detached = GrailsWebRequest.lookup() == null
            result.request = failureOf { request.getAttribute('model') }
            result.response = failureOf { response.contentType }
            Book book = new Book(title: 'Grails')
            result.title = book.title
            result.bindingErrors = book.errors.errorCount
            seen[name] = result
        }.onError { Throwable failure ->
            seen[name] = [failure: failure.toString()]
            taken.countDown()
        }
        taken.await(10, TimeUnit.SECONDS)
    }

    // A task the request waits for: it reads the request while it is live, including what was set after it started
    void readWhileLive(String name) {
        HttpServletRequest live = GrailsWebRequest.lookup().currentRequest
        CountDownLatch set = new CountDownLatch(1)
        CountDownLatch read = new CountDownLatch(1)
        Promises.task {
            set.await(10, TimeUnit.SECONDS)
            HttpServletRequest request = GrailsWebRequest.lookup().currentRequest
            seen[name] = [model: request.getAttribute('model'), late: request.getAttribute('late'), uri: request.requestURI]
            read.countDown()
        }
        live.setAttribute('late', 'set after the task started')
        set.countDown()
        read.await(10, TimeUnit.SECONDS)
    }

    private static String failureOf(Closure<?> call) {
        try {
            call.call()
            return null
        }
        catch (IllegalStateException failure) {
            return failure.message
        }
    }
}

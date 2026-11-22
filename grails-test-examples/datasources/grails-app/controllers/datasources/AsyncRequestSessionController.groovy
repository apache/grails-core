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
package datasources

import static grails.async.web.WebPromises.task

import grails.async.web.AsyncController

/**
 * Actions that start asynchronous handling without returning a promise: one never completes it, so that the container
 * times it out, and one starts a task that completes it, without a dispatch.
 */
class AsyncRequestSessionController implements AsyncController {

    def timesOut() {
        RequestSessions.asyncDefaultSession = RequestSessions.defaultSession()
        RequestSessions.asyncSecondarySession = RequestSessions.secondary()
        startAsync().timeout = 3000
    }

    def completesFromTask() {
        RequestSessions.asyncDefaultSession = RequestSessions.defaultSession()
        RequestSessions.asyncSecondarySession = RequestSessions.secondary()
        task {
            sleep(500)
            render(text: [RequestSessions.asyncDefaultSession, RequestSessions.asyncSecondarySession]*.open.join(','))
            request.asyncContext.complete()
        }
        null
    }
}

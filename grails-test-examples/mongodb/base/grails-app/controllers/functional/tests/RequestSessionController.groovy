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
package functional.tests

import java.util.concurrent.TimeUnit

import grails.async.Promises

/**
 * Actions that show which session a request reads through: a page that includes another action, and an action
 * that returns a promise, so that the request goes asynchronous.
 */
class RequestSessionController {

    def page() {
        [include: params.boolean('include')]
    }

    def included() {
        render(text: "included:${RequestSessions.describe(RequestSessions.current())}")
    }

    def current() {
        render(text: RequestSessions.describe(RequestSessions.current()))
    }

    def promise() {
        RequestSessions.saveBeforeGoingAsynchronous()
        Promises.task {
            RequestSessions.release.await(30, TimeUnit.SECONDS)
            'done'
        }
    }
}

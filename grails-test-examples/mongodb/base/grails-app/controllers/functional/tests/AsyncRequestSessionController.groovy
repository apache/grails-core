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

import grails.async.web.AsyncController

/**
 * An action that starts asynchronous handling itself, with the Servlet API.
 */
class AsyncRequestSessionController implements AsyncController {

    def start() {
        RequestSessions.saveBeforeGoingAsynchronous()
        final context = startAsync()
        context.start {
            RequestSessions.release.await(30, TimeUnit.SECONDS)
            context.response.writer.write('done')
            context.complete()
        }
    }
}

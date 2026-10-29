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

import groovy.json.JsonOutput

/**
 * Forwards while tasks read the request, and goes on once the forward has returned.
 */
class TaskRequestController {

    TaskRequestService taskRequestService

    def forwarding() {
        taskRequestService.readOnceCompleted("${params.run}-request")
        forward(action: 'forwarded')
        request.setAttribute('late', 'set after the forward')
    }

    def forwarded() {
        taskRequestService.readOnceCompleted("${params.run}-forward")
        render 'forwarded'
    }

    def forwardingOnly() {
        forward(action: 'forwardedOnly')
        request.setAttribute('late', 'set after the forward')
    }

    def forwardedOnly() {
        taskRequestService.readOnceCompleted("${params.run}-forwardOnly")
        render 'forwarded'
    }

    def seen() {
        render(contentType: 'application/json', text: JsonOutput.toJson(taskRequestService.seen))
    }
}

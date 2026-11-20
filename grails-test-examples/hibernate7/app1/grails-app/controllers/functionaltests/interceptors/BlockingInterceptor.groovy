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

package functionaltests.interceptors

/**
 * Interceptor that can block requests.
 * Returns false from before() to prevent controller execution.
 */
class BlockingInterceptor {

    int order = 5  // Runs before other interceptors

    BlockingInterceptor() {
        match(controller: 'interceptorTest', action: 'blocked')
    }

    boolean before() {
        InterceptorTestController.executionOrder << "blocking:before"
        if (params.block == 'true') {
            render(text: '{"blocked":true,"message":"Request blocked by interceptor","reason":"' + (params.reason ?: 'No reason provided') + '"}', contentType: 'application/json')
            return false  // Block the request
        }
        true
    }

    boolean after() {
        InterceptorTestController.executionOrder << "blocking:after"
        true
    }
}

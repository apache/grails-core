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
 * Interceptor with conditional matching using closure.
 */
class ConditionalMatchInterceptor {

    ConditionalMatchInterceptor() {
        match(controller: 'interceptorTest', action: 'conditionalAction')
            .except(action: 'index')
    }

    boolean before() {
        // Only add to execution order if 'match' param is 'yes'
        if (params.match == 'yes') {
            InterceptorTestController.executionOrder << 'conditional:before:matched'
            request.setAttribute('conditionalMatched', true)
        } else {
            InterceptorTestController.executionOrder << 'conditional:before:notmatched'
            request.setAttribute('conditionalMatched', false)
        }
        true
    }

    boolean after() {
        if (params.match == 'yes') {
            InterceptorTestController.executionOrder << 'conditional:after:matched'
        } else {
            InterceptorTestController.executionOrder << 'conditional:after:notmatched'
        }
        true
    }
}

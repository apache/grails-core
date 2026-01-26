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
 * Second interceptor in the chain (order = 20).
 * Runs after FirstInterceptor but before ThirdInterceptor.
 */
class SecondInterceptor {

    int order = 20

    SecondInterceptor() {
        match(controller: 'interceptorTest', action: ~/(index|testOrder|dataAction)/)
    }

    boolean before() {
        InterceptorTestController.executionOrder << "second:before"
        request.setAttribute('secondInterceptorRan', true)
        true
    }

    boolean after() {
        InterceptorTestController.executionOrder << "second:after"
        true
    }

    void afterView() {
        InterceptorTestController.executionOrder << "second:afterView"
    }
}

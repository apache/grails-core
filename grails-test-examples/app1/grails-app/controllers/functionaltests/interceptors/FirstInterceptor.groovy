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
 * First interceptor in the chain (order = 10).
 * Tests basic before/after functionality.
 */
class FirstInterceptor {

    int order = 10

    FirstInterceptor() {
        match(controller: 'interceptorTest', action: ~/(index|testOrder|dataAction)/)
    }

    boolean before() {
        InterceptorTestController.executionOrder << "first:before"
        request.setAttribute('firstInterceptorRan', true)
        true
    }

    boolean after() {
        InterceptorTestController.executionOrder << "first:after"
        true
    }

    void afterView() {
        InterceptorTestController.executionOrder << "first:afterView"
    }
}

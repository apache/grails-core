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

import grails.converters.JSON

/**
 * Controller for testing advanced interceptor functionality.
 */
class InterceptorTestController {

    // This list tracks interceptor execution order
    static List<String> executionOrder = []

    /**
     * Simple action for testing interceptor before/after execution.
     */
    def index() {
        executionOrder << 'controller:index'
        render([
            action: 'index',
            executionOrder: new ArrayList<>(executionOrder)
        ] as JSON)
    }

    /**
     * Action for testing interceptor ordering.
     */
    def testOrder() {
        executionOrder << 'controller:testOrder'
        render([
            action: 'testOrder',
            executionOrder: new ArrayList<>(executionOrder)
        ] as JSON)
    }

    /**
     * Action that can be blocked by interceptor.
     */
    def blocked() {
        executionOrder << 'controller:blocked'
        render([
            action: 'blocked',
            message: 'This should not be seen if blocked'
        ] as JSON)
    }

    /**
     * Action that can throw exception.
     */
    def throwException() {
        if (params.throwIt == 'true') {
            throw new RuntimeException("Controller threw exception: ${params.message ?: 'test error'}")
        }
        render([
            action: 'throwException',
            didNotThrow: true
        ] as JSON)
    }

    /**
     * Action for testing model modification by interceptor.
     */
    def modifyModel() {
        executionOrder << 'controller:modifyModel'
        [
            originalValue: 'from controller',
            timestamp: System.currentTimeMillis()
        ]
    }

    /**
     * Action that returns data to be potentially modified by after interceptor.
     */
    def dataAction() {
        executionOrder << 'controller:dataAction'
        render([
            data: params.data ?: 'default',
            interceptorModified: false
        ] as JSON)
    }

    /**
     * Reset execution order (for test setup).
     */
    def resetOrder() {
        executionOrder.clear()
        render([
            reset: true,
            executionOrder: executionOrder
        ] as JSON)
    }

    /**
     * Get current execution order without modifying it.
     */
    def getOrder() {
        render([
            executionOrder: new ArrayList<>(executionOrder)
        ] as JSON)
    }

    /**
     * Action for testing request attribute passing.
     */
    def checkAttributes() {
        render([
            interceptorSet: request.getAttribute('interceptorData'),
            headerAdded: response.getHeader('X-Interceptor-Header'),
            fromBefore: request.getAttribute('beforeInterceptorRan')
        ] as JSON)
    }

    /**
     * Action for testing session manipulation by interceptor.
     */
    def checkSession() {
        render([
            sessionData: session.getAttribute('interceptorSessionData'),
            sessionId: session.id
        ] as JSON)
    }

    /**
     * Action for testing afterView interceptor.
     */
    def withView() {
        executionOrder << 'controller:withView'
        [message: 'Hello from controller']
    }

    /**
     * Action that takes time (for timing tests).
     */
    def slowAction() {
        def delay = params.int('delay') ?: 100
        Thread.sleep(delay)
        executionOrder << 'controller:slowAction'
        render([
            action: 'slowAction',
            delay: delay
        ] as JSON)
    }

    /**
     * Action for testing conditional interceptor matching.
     */
    def conditionalAction() {
        executionOrder << 'controller:conditionalAction'
        render([
            action: 'conditionalAction',
            param: params.match
        ] as JSON)
    }
}

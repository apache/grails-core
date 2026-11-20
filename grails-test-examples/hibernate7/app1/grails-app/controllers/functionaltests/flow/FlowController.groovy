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
package functionaltests.flow

import grails.converters.JSON

/**
 * Controller for testing flash scope, chain, and forward functionality.
 */
class FlowController {

    // ========== Flash Scope Tests ==========

    /**
     * Sets a flash message and redirects.
     */
    def setFlashAndRedirect() {
        flash.message = 'This is a flash message'
        flash.type = 'success'
        redirect(action: 'readFlash')
    }

    /**
     * Reads flash content and returns as JSON.
     */
    def readFlash() {
        render([
            message: flash.message,
            type: flash.type,
            flashKeys: flash.keySet().toList()
        ] as JSON)
    }

    /**
     * Sets flash and returns without redirect (for testing flash persistence).
     */
    def setFlashOnly() {
        flash.message = params.message ?: 'Default message'
        flash.count = 1
        render([
            flashSet: true,
            message: flash.message
        ] as JSON)
    }

    /**
     * Sets multiple flash values with different types.
     */
    def setMultipleFlashValues() {
        flash.stringValue = 'Hello'
        flash.intValue = 42
        flash.listValue = ['a', 'b', 'c']
        flash.mapValue = [key: 'value', nested: [x: 1]]
        redirect(action: 'readMultipleFlash')
    }

    def readMultipleFlash() {
        render([
            stringValue: flash.stringValue,
            intValue: flash.intValue,
            listValue: flash.listValue,
            mapValue: flash.mapValue
        ] as JSON)
    }

    /**
     * Tests flash.now for same-request flash.
     */
    def flashNow() {
        flash.now.immediate = 'This is immediate'
        flash.persisted = 'This persists'
        render([
            immediate: flash.immediate,
            persisted: flash.persisted
        ] as JSON)
    }

    // ========== Chain Tests ==========

    /**
     * First action in chain - adds to model and chains.
     */
    def chainFirst() {
        chain(action: 'chainSecond', model: [first: 'value1', step: 1])
    }

    /**
     * Second action in chain - adds to accumulated model.
     */
    def chainSecond() {
        def accumulated = chainModel ?: [:]
        accumulated.second = 'value2'
        accumulated.step = (accumulated.step ?: 0) + 1
        chain(action: 'chainThird', model: accumulated)
    }

    /**
     * Third/final action in chain - renders accumulated model.
     */
    def chainThird() {
        def accumulated = chainModel ?: [:]
        accumulated.third = 'value3'
        accumulated.step = (accumulated.step ?: 0) + 1
        render([
            chainModel: accumulated,
            first: accumulated.first,
            second: accumulated.second,
            third: accumulated.third,
            totalSteps: accumulated.step
        ] as JSON)
    }

    /**
     * Chain with params preservation.
     */
    def chainWithParams() {
        chain(action: 'receiveChainParams', model: [fromChain: true], params: [extraParam: 'extra'])
    }

    def receiveChainParams() {
        render([
            fromChain: chainModel?.fromChain,
            extraParam: params.extraParam,
            originalParams: params.subMap(['id', 'name', 'extraParam'])
        ] as JSON)
    }

    /**
     * Chain to different controller.
     */
    def chainToOtherController() {
        chain(controller: 'flowTarget', action: 'receiveChain', model: [source: 'flowController'])
    }

    // ========== Forward Tests ==========

    /**
     * Forward to another action (same request).
     */
    def forwardToAction() {
        request.setAttribute('forwardedFrom', 'forwardToAction')
        forward(action: 'forwardTarget')
    }

    def forwardTarget() {
        render([
            forwardedFrom: request.getAttribute('forwardedFrom'),
            action: 'forwardTarget',
            sameRequest: true
        ] as JSON)
    }

    /**
     * Forward with params.
     */
    def forwardWithParams() {
        forward(action: 'forwardParamsTarget', params: [forwarded: 'yes', value: '123'])
    }

    def forwardParamsTarget() {
        render([
            forwarded: params.forwarded,
            value: params.value,
            originalId: params.id
        ] as JSON)
    }

    /**
     * Forward to different controller.
     */
    def forwardToOtherController() {
        request.setAttribute('sourceController', 'flow')
        forward(controller: 'flowTarget', action: 'receiveForward')
    }

    // ========== Redirect Tests ==========

    /**
     * Redirect with fragment.
     */
    def redirectWithFragment() {
        redirect(action: 'fragmentTarget', fragment: 'section1')
    }

    def fragmentTarget() {
        render([
            action: 'fragmentTarget',
            requestURI: request.requestURI
        ] as JSON)
    }

    /**
     * Redirect with all params preserved.
     */
    def redirectWithAllParams() {
        redirect(action: 'paramsTarget', params: params)
    }

    def paramsTarget() {
        render([
            params: params.findAll { k, v -> !['controller', 'action'].contains(k) }
        ] as JSON)
    }

    /**
     * Redirect to URI.
     */
    def redirectToUri() {
        redirect(uri: '/flow/uriTarget?fromRedirect=true')
    }

    def uriTarget() {
        render([
            fromRedirect: params.fromRedirect,
            uri: request.requestURI
        ] as JSON)
    }

    /**
     * Permanent redirect (301).
     */
    def permanentRedirect() {
        redirect(action: 'redirectTarget', permanent: true)
    }

    def redirectTarget() {
        render([action: 'redirectTarget'] as JSON)
    }
}

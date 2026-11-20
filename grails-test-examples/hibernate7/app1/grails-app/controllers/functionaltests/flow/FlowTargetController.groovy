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
 * Target controller for testing chain and forward across controllers.
 */
class FlowTargetController {

    /**
     * Receives chain from FlowController.
     */
    def receiveChain() {
        render([
            controller: 'flowTarget',
            action: 'receiveChain',
            chainModel: chainModel,
            source: chainModel?.source
        ] as JSON)
    }

    /**
     * Receives forward from FlowController.
     */
    def receiveForward() {
        render([
            controller: 'flowTarget',
            action: 'receiveForward',
            sourceController: request.getAttribute('sourceController'),
            isForward: request.getAttribute('javax.servlet.forward.request_uri') != null || 
                       request.getAttribute('sourceController') != null
        ] as JSON)
    }
}

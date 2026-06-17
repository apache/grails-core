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

package functionaltests

import org.springframework.http.HttpStatus

class ForwardingController {

    def one() {
        forward action: 'next'
    }

    def two() {
        forward controller: 'forwarding', action: 'next'
    }

    def three() {
        forward controller: 'forwarding', action: 'next', params: [param1: 'test']
    }

    def next() {
        render "<html><body>Forward Destination. Params: ${params.param1 ?: ''}</body></html>"
    }

    def forwardToList() {
        forward action: 'list'
    }

    def forwardWithRender(String anArgument) {
        if (!anArgument) {
            forward action: 'renderedView'
            return
        }

        render text: 'did not forward', status: HttpStatus.OK
    }

    def renderedView() {
        render view: 'forwardedView'
    }

    def list() {
        [people: ['Jeff', 'Jake', 'Zack', 'Betsy']]
    }

    def putMessageInFlash() {
        flash.message = 'some message'
        forward action: 'displayFlash'
    }

    def displayFlash() {
    }
}

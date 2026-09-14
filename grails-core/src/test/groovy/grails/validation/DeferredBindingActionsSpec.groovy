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
package grails.validation

import spock.lang.Specification

class DeferredBindingActionsSpec extends Specification {

    void cleanup() {
        DeferredBindingActions.clear()
    }

    void 'runActions invokes every added action in order and then clears them'() {
        given:
        List<Integer> order = []
        DeferredBindingActions.addBindingAction({ -> order << 1 } as Runnable)
        DeferredBindingActions.addBindingAction({ -> order << 2 } as Runnable)

        when:
        DeferredBindingActions.runActions()

        then:
        order == [1, 2]

        when: 'running again with no actions added does nothing'
        DeferredBindingActions.runActions()

        then:
        order == [1, 2]
    }

    void 'an action that throws does not prevent subsequent actions from running'() {
        given:
        List<Integer> order = []
        DeferredBindingActions.addBindingAction({ -> throw new RuntimeException('boom') } as Runnable)
        DeferredBindingActions.addBindingAction({ -> order << 'ran' } as Runnable)

        when:
        DeferredBindingActions.runActions()

        then:
        noExceptionThrown()
        order == ['ran']
    }

    void 'clear discards pending actions without running them'() {
        given:
        List<Integer> order = []
        DeferredBindingActions.addBindingAction({ -> order << 1 } as Runnable)

        when:
        DeferredBindingActions.clear()
        DeferredBindingActions.runActions()

        then:
        order.isEmpty()
    }
}

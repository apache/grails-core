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
package org.grails.events

import grails.events.Event
import org.grails.events.bus.ExecutorEventBus
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils
import spock.lang.Specification

/**
 * Created by graemerocher on 28/03/2017.
 */
class TransactionAwareEventSpec extends Specification {

    void 'Test task executor event bus with transactional event'() {
        
        given: 'a task executor event bus'
            def eventBus = new ExecutorEventBus()
        
        and: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        when: 'an event is fired with an active transaction'
            TransactionSynchronizationManager.initSynchronization()
            eventBus.notify(Event.from('test', 'bar'), TransactionPhase.AFTER_COMMIT)

        then: 'the event was not triggered'
            !result

        when: 'the transaction is committed'
            TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations())

        then: 'the event was triggered'
            result == "foo bar"

        cleanup:
            TransactionSynchronizationManager.clearSynchronization()
    }
}

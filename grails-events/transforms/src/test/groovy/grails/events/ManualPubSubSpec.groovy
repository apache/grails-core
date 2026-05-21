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
package grails.events

import grails.events.bus.EventBusAware
import spock.lang.Specification

import jakarta.annotation.PostConstruct
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by graemerocher on 03/04/2017.
 */
class ManualPubSubSpec extends Specification {

    void 'Test pub/sub with default event bus'() {

        given: 'A publisher and subscriber'
            def sumService = new SumService()
            def totalService = new TotalService()
            def annotatedSubscriber = totalService as EventBusAware
            def publisher = sumService as EventBusAware

        and: 'we set the target event bus'
            annotatedSubscriber.setTargetEventBus(publisher.getEventBus())
            totalService.init()

        when: 'we invoke methods on the publisher'
            sumService.sum(1,2)
            sumService.sum(1,2)

        then: 'the subscriber should receive the events'
            totalService.total.intValue() == 6
    }
}

// tag::publisher[]
class SumService implements EventPublisher {

    int sum(int a, int b) {
        int result = a + b
        notify('sum', result)
        return result
    }
}
// end::publisher[]

// tag::subscriber[]
class TotalService implements EventBusAware {

    AtomicInteger total = new AtomicInteger(0)

    @PostConstruct
    void init() {
        eventBus.subscribe('sum') { int num ->
            total.addAndGet(num)
        }
    }
}
// end::subscriber[]

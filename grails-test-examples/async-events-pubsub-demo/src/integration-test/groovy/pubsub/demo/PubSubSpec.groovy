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

package pubsub.demo

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import jakarta.inject.Inject
import spock.lang.PendingFeatureIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Created by graemerocher on 03/04/2017.
 */
@Integration
class PubSubSpec extends Specification {

    @Inject SumService sumService
    @Inject TotalService totalService
    @Inject BookService bookService
    @Inject BookSubscriber bookSubscriber

    void 'Test event bus within Grails'() {

        when: 'we invoke methods on the publisher'
            sumService.sum(1, 2)
            sumService.sum(1, 2)

        then: 'the subscriber should receive the events'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert totalService.accumulatedTotal == 6
            }
    }

    @Rollback
    void 'Test event from data service with rollback'() {

        when: 'a transaction is rolled back'
            bookService.saveBook('The Stand')

        then: 'no event is fired'
            new PollingConditions(initialDelay: 0.5, timeout: 5, delay: 0.2).eventually {
                assert bookSubscriber.newBooks == []
                assert bookSubscriber.insertEvents.empty
            }
    }

    void 'Test event from data service'() {

        when: 'a transaction is committed'
            bookService.saveBook('The Stand')

        then: 'the event is fired and received'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert bookSubscriber.newBooks == ['The Stand']
                assert bookSubscriber.insertEvents.size() == 1
            }
    }

    @Rollback
    void 'Test modify property event listener'() {

        when: 'when an event listener modifies a property'
            bookService.saveBook('funny book')

        then: 'the property was modified'
            Book.findByTitle('Humor - funny book') != null

    }


    @Rollback
    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test synchronous event listener'() {

        when: 'when a event listener cancels an insert'
            bookService.saveBook('UK Politics')

        // due to  https://hibernate.atlassian.net/browse/HHH-11721
        // an exception must be thrown
        then: 'the insert was cancelled'
            def e = thrown IllegalArgumentException
            e.message == 'Books about politics not allowed'
    }
}
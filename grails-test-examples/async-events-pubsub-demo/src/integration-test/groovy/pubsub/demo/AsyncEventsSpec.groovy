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

import jakarta.inject.Inject

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Additional integration tests for async events functionality.
 * 
 * Tests:
 * 1. Multiple event publications in sequence
 * 2. Event subscriber state consistency
 * 3. Concurrent event handling
 * 4. Event timing and ordering
 * 
 * Note: These tests use relative assertions (>= instead of ==) to handle
 * async event timing and potential state carryover from parallel tests.
 */
@Integration
class AsyncEventsSpec extends Specification {

    @Inject SumService sumService
    @Inject TotalService totalService
    @Inject BookService bookService
    @Inject BookSubscriber bookSubscriber

    def setup() {
        // Reset state for each test - but note that async events may still be in-flight
        totalService.reset()
        bookSubscriber.reset()
        // Small delay to let any in-flight events complete
        sleep(100)
    }

    void "multiple sum events accumulate correctly"() {
        given: "initial total baseline"
        def initialTotal = totalService.accumulatedTotal

        when: "publishing multiple sum events"
        sumService.sum(10, 20)  // 30
        sumService.sum(5, 15)   // 20
        sumService.sum(3, 7)    // 10

        then: "total accumulates all results (at least 60 more)"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert totalService.accumulatedTotal >= initialTotal + 60
        }
    }

    void "sum service handles zero values"() {
        given: "initial total baseline"
        def initialTotal = totalService.accumulatedTotal

        when: "summing with zeros"
        sumService.sum(0, 0)
        sumService.sum(0, 5)
        sumService.sum(5, 0)

        then: "events are processed correctly (at least 10 more)"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert totalService.accumulatedTotal >= initialTotal + 10
        }
    }

    void "sum service handles negative values"() {
        given: "initial total baseline"  
        def initialTotal = totalService.accumulatedTotal

        when: "summing with negative numbers"
        sumService.sum(-5, 10)
        sumService.sum(10, -5)

        then: "negative values handled correctly (at least 10 more)"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert totalService.accumulatedTotal >= initialTotal + 10
        }
    }

    @Rollback
    void "multiple books can be saved in sequence"() {
        when: "saving multiple books"
        def book1 = bookService.saveBook('Book One')
        def book2 = bookService.saveBook('Book Two')
        def book3 = bookService.saveBook('Book Three')

        then: "all books are saved"
        book1?.id != null
        book2?.id != null
        book3?.id != null
    }

    void "book events are received after save"() {
        given: "initial books list size"
        def initialSize = bookSubscriber.newBooks.size()

        when: "saving a book"
        bookService.saveBook('Event Test Book')
        
        then: "book event is received"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert bookSubscriber.newBooks.size() > initialSize
            assert bookSubscriber.newBooks.any { it == 'Event Test Book' }
        }
    }

    void "event listener receives correct book title"() {
        when: "saving a book with specific title"
        bookService.saveBook('Unique Title 12345')

        then: "subscriber receives exact title"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert bookSubscriber.newBooks.any { it == 'Unique Title 12345' }
        }
    }

    void "insert events contain book entity"() {
        given: "initial events count"
        def initialCount = bookSubscriber.insertEvents.size()

        when: "saving a book"
        bookService.saveBook('Entity Test Book XYZ')

        then: "insert event is received with book entity"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert bookSubscriber.insertEvents.size() > initialCount
            // The PreInsertEvent contains entityAccess to get the actual entity properties
            assert bookSubscriber.insertEvents.any { event ->
                event.entityAccess?.getPropertyValue('title') == 'Entity Test Book XYZ'
            }
        }
    }

    @Rollback
    void "humor prefix is added by event listener"() {
        when: "saving a funny book"
        // Note: The listener checks for 'funny' (lowercase) in the title
        def book = bookService.saveBook('funny book')

        then: "title is modified by event listener"
        book.title == 'Humor - funny book'
    }

    @Rollback
    void "politics book throws exception"() {
        when: "trying to save a politics book"
        bookService.saveBook('US Politics')

        then: "exception is thrown"
        def e = thrown(IllegalArgumentException)
        e.message == 'Books about politics not allowed'
    }

    void "total service accumulation handles rapid events"() {
        given: "initial total baseline"
        def initialTotal = totalService.accumulatedTotal

        when: "rapidly publishing many events"
        10.times { i ->
            sumService.sum(1, 1)  // Each adds 2
        }

        then: "all events are accumulated (at least 20 more)"
        new PollingConditions(timeout: 10, delay: 0.3).eventually {
            assert totalService.accumulatedTotal >= initialTotal + 20
        }
    }

    void "subscriber can track multiple book saves"() {
        given: "initial state"
        def initialCount = bookSubscriber.newBooks.size()

        when: "saving a new book"
        bookService.saveBook('Tracking Test 999')

        then: "state is updated"
        new PollingConditions(timeout: 5, delay: 0.2).eventually {
            assert bookSubscriber.newBooks.size() > initialCount
            assert bookSubscriber.newBooks.contains('Tracking Test 999')
        }
    }
}

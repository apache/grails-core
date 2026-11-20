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

package functionaltests.springevents

import grails.converters.JSON
import org.springframework.beans.factory.annotation.Autowired

/**
 * Controller for testing Spring event publishing.
 */
class SpringEventController {

    static responseFormats = ['json']

    @Autowired
    EventPublisherService eventPublisherService
    
    @Autowired
    EventListenerService eventListenerService

    /**
     * Publishes a custom event and returns event info.
     */
    def publishCustom() {
        String message = params.message ?: 'Default message'
        eventPublisherService.publishCustomEvent(message, [controller: 'springEvent'])
        
        render([
            published: true,
            message: message,
            eventCount: eventListenerService.eventCount
        ] as JSON)
    }

    /**
     * Publishes a user action event.
     * Note: Use 'userAction' param instead of 'action' to avoid conflict with Grails params.action
     */
    def publishUserAction() {
        String userId = params.userId ?: 'unknown'
        String userAction = params.userAction ?: 'unknown_action'
        
        eventPublisherService.publishUserAction(userId, userAction, [source: 'controller'])
        
        render([
            published: true,
            userId: userId,
            userAction: userAction
        ] as JSON)
    }

    /**
     * Publishes a priority event and returns ordered results.
     */
    def publishPriority() {
        String data = params.data ?: 'test'
        eventListenerService.clearEvents()
        eventPublisherService.publishPriorityEvent(1, data)
        
        render([
            published: true,
            orderedResults: eventListenerService.orderedResults
        ] as JSON)
    }

    /**
     * Publishes multiple events.
     */
    def publishMultiple() {
        int count = params.int('count', 5)
        eventListenerService.clearEvents()
        eventPublisherService.publishMultipleEvents(count)
        
        render([
            published: true,
            count: count,
            receivedCount: eventListenerService.eventCount
        ] as JSON)
    }

    /**
     * Publishes an event that should trigger conditional listener.
     */
    def publishConditional() {
        boolean important = params.boolean('important', false)
        String prefix = important ? 'IMPORTANT' : 'NORMAL'
        String message = "${prefix}: Test message"
        
        eventListenerService.clearEvents()
        eventPublisherService.publishCustomEvent(message, [conditional: true])
        
        render([
            published: true,
            message: message,
            events: eventListenerService.customEvents.collect { it.message },
            conditionalResults: eventListenerService.conditionalResults
        ] as JSON)
    }

    /**
     * Returns current event statistics.
     */
    def stats() {
        render([
            totalEvents: eventListenerService.eventCount,
            customEvents: eventListenerService.customEvents.size(),
            userActionEvents: eventListenerService.userActionEvents.size()
        ] as JSON)
    }

    /**
     * Clears all events.
     */
    def clearEvents() {
        eventListenerService.clearEvents()
        render([cleared: true] as JSON)
    }

    /**
     * Publishes event in transactional context.
     */
    def publishTransactional() {
        String message = params.message ?: 'transactional-test'
        eventPublisherService.publishEventTransactional(message)
        
        render([
            published: true,
            message: message
        ] as JSON)
    }
}

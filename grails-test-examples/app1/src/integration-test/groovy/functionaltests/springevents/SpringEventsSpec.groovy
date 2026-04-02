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

import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Tag

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for Spring application events in Grails.
 * 
 * Tests event publishing, listening, conditional handling,
 * ordered listeners, and event payloads.
 */
@Integration
@Narrative('''
Spring's ApplicationEvent mechanism allows decoupled communication between
components. Grails integrates with Spring events via @EventListener annotations
and ApplicationEventPublisher.
''')
@Tag('http-client')
class SpringEventsSpec extends Specification implements HttpClientSupport {

    @Autowired EventListenerService eventListenerService
    @Autowired EventPublisherService eventPublisherService

    def setup() {
        eventListenerService.clearEvents()
    }

    // ========== Basic Event Publishing ==========

    def "custom event is published and received"() {
        when: "publishing a custom event"
        eventPublisherService.publishCustomEvent('Test Message', [key: 'value'])

        then: "event is received by listener"
        eventListenerService.eventCount == 1
        eventListenerService.customEvents.size() == 1
        eventListenerService.customEvents[0].message == 'Test Message'
        eventListenerService.customEvents[0].payload.key == 'value'
    }

    def "event createdAt is set"() {
        when: "publishing an event"
        def before = new Date()
        eventPublisherService.publishCustomEvent('Timestamp Test')
        def after = new Date()

        then: "createdAt is within expected range"
        eventListenerService.customEvents[0].createdAt >= before
        eventListenerService.customEvents[0].createdAt <= after
    }

    def "user action event is published and received"() {
        when: "publishing a user action event"
        eventPublisherService.publishUserAction('user-123', 'LOGIN', [ip: '192.168.1.1'])

        then: "event is received with correct data"
        eventListenerService.userActionEvents.size() == 1
        eventListenerService.userActionEvents[0].userId == 'user-123'
        eventListenerService.userActionEvents[0].action == 'LOGIN'
        eventListenerService.userActionEvents[0].metadata.ip == '192.168.1.1'
    }

    // ========== Multiple Events ==========

    def "multiple events are received in order"() {
        when: "publishing multiple events"
        eventPublisherService.publishMultipleEvents(5)

        then: "all events are received"
        eventListenerService.eventCount == 5
        eventListenerService.customEvents.size() == 5
        
        and: "events have correct sequence"
        eventListenerService.customEvents[0].message == 'Event #1'
        eventListenerService.customEvents[4].message == 'Event #5'
    }

    def "events from different types are tracked separately"() {
        when: "publishing different event types"
        eventPublisherService.publishCustomEvent('Custom 1')
        eventPublisherService.publishUserAction('user1', 'ACTION1')
        eventPublisherService.publishCustomEvent('Custom 2')
        eventPublisherService.publishUserAction('user2', 'ACTION2')

        then: "events are tracked by type"
        eventListenerService.customEvents.size() == 2
        eventListenerService.userActionEvents.size() == 2
        eventListenerService.eventCount == 4
    }

    // ========== Ordered Event Listeners ==========

    def "event listeners are executed in order"() {
        when: "publishing a priority event"
        eventPublisherService.publishPriorityEvent(1, 'test')

        then: "listeners execute in @Order sequence"
        eventListenerService.orderedResults.size() == 3
        eventListenerService.orderedResults[0] == 'first-test'
        eventListenerService.orderedResults[1] == 'second-test'
        eventListenerService.orderedResults[2] == 'third-test'
    }

    // ========== Conditional Event Listeners ==========

    def "conditional listener handles matching events"() {
        when: "publishing event that matches condition"
        eventPublisherService.publishCustomEvent('IMPORTANT: Critical Alert', [:])

        then: "conditional handler is triggered"
        eventListenerService.conditionalResults.any { it.startsWith('CONDITIONAL:IMPORTANT') }
    }

    def "conditional listener ignores non-matching events"() {
        when: "publishing event that doesn't match condition"
        eventPublisherService.publishCustomEvent('NORMAL: Regular message', [:])

        then: "conditional handler is not triggered"
        !eventListenerService.conditionalResults.any { it.startsWith('CONDITIONAL:') }
    }

    // ========== Event Publishing via HTTP ==========

    def "event can be published via HTTP endpoint"() {
        when: "calling publish endpoint"
        def response = http('/springEvent/publishCustom?message=HTTP+Event')

        then: "event is published successfully"
        response.assertJsonContains(200, [
                published: true,
                message: 'HTTP Event'
        ])
    }

    def "user action event can be published via HTTP"() {
        when: "calling user action publish endpoint"
        def response = http('/springEvent/publishUserAction?userId=web-user&userAction=CLICK')

        then: "event is published"
        response.assertJson(200, [
                published: true,
                userId: 'web-user',
                userAction: 'CLICK'
        ])
    }

    def "priority event ordering works via HTTP"() {
        when: "calling priority publish endpoint"
        def response = http('/springEvent/publishPriority?data=http-test')

        then: "ordered results are returned"
        response.assertStatus(200)
        with(response.json()) {
            orderedResults.size() == 3
            orderedResults[0] == 'first-http-test'
        }
    }

    def "multiple events can be published via HTTP"() {
        when: "calling multiple publish endpoint"
        def response = http('/springEvent/publishMultiple?count=3')

        then: "all events are received"
        response.assertJsonContains(200, [
                count: 3,
                receivedCount: 3
        ])
    }

    def "conditional event works via HTTP for matching message"() {
        when: "publishing important message"
        def response = http('/springEvent/publishConditional?important=true')

        then: "conditional handler triggers"
        response.assertStatus(200)
        response.json().conditionalResults.any { it.startsWith('CONDITIONAL:') }
    }

    def "conditional event skipped via HTTP for non-matching message"() {
        when: "publishing normal message"
        def response = http('/springEvent/publishConditional?important=false')

        then: "conditional handler does not trigger"
        response.assertStatus(200)
        !response.json().conditionalResults.any { it.toString().startsWith('CONDITIONAL:') }
    }

    // ========== Event Stats ==========

    def "event stats are accessible via HTTP"() {
        given:
        eventPublisherService.publishCustomEvent('Stats Test 1')
        eventPublisherService.publishCustomEvent('Stats Test 2')
        eventPublisherService.publishUserAction('user', 'action')

        when: "getting stats"
        def response = http(
            '/springEvent/stats'
        )

        then: "stats are returned"
        response.assertJson(200, [
                totalEvents: 3,
                customEvents: 2,
                userActionEvents: 1
        ])
    }

    // ========== Clear Events ==========

    def "events can be cleared via HTTP"() {
        given:
        eventPublisherService.publishCustomEvent('To be cleared')

        when: "clearing events"
        def response = http(
            '/springEvent/clearEvents'
        )

        then: "events are cleared"
        response.assertStatus(200)
        eventListenerService.eventCount == 0
    }

    // ========== Transactional Event Publishing ==========

    def "event can be published in transactional context"() {
        when: "publishing transactional event"
        def response = http('/springEvent/publishTransactional?message=tx-test')

        then: "event is published"
        response.assertJsonContains(200, [
                published: true
        ])

        and: "event is received"
        eventListenerService.customEvents.any { it.message.contains('tx-test') }
    }

    // ========== Event Payload Tests ==========

    def "event payload can contain complex data"() {
        when: "publishing event with complex payload"
        def payload = [
            nested: [
                list: [1, 2, 3],
                map: [a: 'b', c: 'd']
            ],
            string: 'value',
            number: 42
        ]
        eventPublisherService.publishCustomEvent('Complex Payload', payload)

        then: "payload is preserved"
        def event = eventListenerService.customEvents[0]
        event.payload.nested.list == [1, 2, 3]
        event.payload.nested.map.a == 'b'
        event.payload.string == 'value'
        event.payload.number == 42
    }

    def "event payload handles null values"() {
        when: "publishing event with null in payload"
        eventPublisherService.publishCustomEvent('Null Payload', [key: null])

        then: "null is preserved"
        def event = eventListenerService.customEvents[0]
        event.payload.containsKey('key')
        event.payload.key == null
    }
}

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

package functional.tests

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import spock.lang.Tag

import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * A form bound to a domain class constrained with Bean Validation annotations only is validated against them when
 * the controller saves it, and their errors are resolved through the message source.
 */
@Integration(applicationClass = Application)
@Tag('http-client')
class TicketControllerSpec extends Specification implements HttpClientSupport {

    void 'a form violating the annotations is rejected with their errors and nothing is stored'() {
        when:
        def response = httpPostForm('/ticket/save', [title: '', contact: 'rejected-form', priority: '9'])

        then: 'the empty title is bound as null and reported by @NotBlank alone'
        with(response) {
            assertStatus(422)
            assertContains('title:NotBlank:Every ticket needs a title')
            assertContains('contact:Email:')
            assertContains('priority:Max:')
        }
        !response.body().contains('nullable')

        and:
        Ticket.withNewSession { Ticket.countByContact('rejected-form') } == 0
    }

    void 'a valid form is saved'() {
        when:
        def response = httpPostForm('/ticket/save', [title: 'Printer jam', contact: 'form@example.com', priority: '3', reference: 'OPS-007'])

        then:
        response.assertStatus(201)
        Long id = response.body().minus('id=') as Long
        Ticket.withNewSession { Ticket.get(id).with { [title, contact, priority, reference] } } == ['Printer jam', 'form@example.com', 3, 'OPS-007']

        cleanup:
        Ticket.withNewTransaction { Ticket.findByContact('form@example.com')?.delete(flush: true) }
    }
}

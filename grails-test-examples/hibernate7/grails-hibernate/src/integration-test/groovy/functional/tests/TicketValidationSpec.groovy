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

import org.springframework.context.MessageSource

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.validation.Constrained
import grails.validation.ValidationException
import spock.lang.Specification

/**
 * A domain class constrained with Bean Validation annotations only is validated against them on save and update,
 * cascades to its associations, describes them as constraints and resolves their errors through the message source.
 */
@Rollback
@Integration(applicationClass = Application)
class TicketValidationSpec extends Specification {

    MessageSource messageSource

    void 'save validates the annotations and stores nothing when they fail'() {
        given:
        def ticket = new Ticket(title: '', contact: 'not-an-email', priority: 9, reference: 'abc')

        expect:
        ticket.save(flush: true) == null
        ticket.errors.errorCount == 4
        ticket.errors.getFieldError('title').code == 'NotBlank'
        ticket.errors.getFieldError('contact').code == 'Email'
        ticket.errors.getFieldError('priority').code == 'Max'
        ticket.errors.getFieldError('reference').code == 'Pattern'
        Ticket.countByContact('not-an-email') == 0
    }

    void 'a null property an annotation requires is reported by the annotation alone'() {
        given:
        def ticket = new Ticket(title: 'Printer jam')

        expect:
        !ticket.validate()
        ticket.errors.errorCount == 2
        ticket.errors.getFieldError('contact').code == 'NotNull'
        ticket.errors.getFieldError('priority').code == 'NotNull'
    }

    void 'save with failOnError throws a ValidationException carrying the errors of the annotations'() {
        when:
        new Ticket(title: '', contact: 'ops@example.com', priority: 0).save(failOnError: true)

        then:
        ValidationException e = thrown()
        e.errors.errorCount == 2
        e.errors.getFieldError('title').code == 'NotBlank'
        e.errors.getFieldError('priority').code == 'Min'
    }

    void 'a valid ticket is saved with its notes'() {
        given:
        def ticket = new Ticket(title: 'Printer jam', contact: 'saved@example.com', priority: 2, reference: 'OPS-042')
        ticket.addToNotes(new TicketNote(body: 'Paper tray 2'))

        expect:
        ticket.save(flush: true) != null
        Ticket.countByContact('saved@example.com') == 1
        TicketNote.countByBody('Paper tray 2') == 1
    }

    void 'validation cascades to the notes of a ticket and checks their annotations'() {
        given:
        def ticket = new Ticket(title: 'Printer jam', contact: 'cascade@example.com', priority: 2)
        ticket.addToNotes(new TicketNote(body: ''))

        expect:
        ticket.save(flush: true) == null
        ticket.errors.errorCount == 1
        ticket.errors.getFieldError('notes[0].body').code == 'NotBlank'
        Ticket.countByContact('cascade@example.com') == 0
    }

    void 'an update violating the annotations is rejected and leaves the stored ticket unchanged'() {
        given: 'a stored ticket'
        Long id = Ticket.withNewTransaction {
            new Ticket(title: 'Printer jam', contact: 'update@example.com', priority: 2).save(flush: true, failOnError: true).id
        }

        when: 'another transaction gives it an invalid title and priority, and commits'
        Map<String, Object> result = Ticket.withNewTransaction {
            Ticket ticket = Ticket.get(id)
            ticket.title = ''
            ticket.priority = 0
            [saved: ticket.save(flush: true) != null,
             title: ticket.errors.getFieldError('title')?.code,
             priority: ticket.errors.getFieldError('priority')?.code]
        }

        then: 'the update is rejected by the annotations'
        result == [saved: false, title: 'NotBlank', priority: 'Min']

        and: 'nothing of it was written'
        Ticket.withNewSession { Ticket.get(id).with { [title, priority] } } == ['Printer jam', 2]

        cleanup:
        Ticket.withNewTransaction { Ticket.get(id)?.delete(flush: true) }
    }

    void 'the annotations are described as the constraints of the domain class'() {
        given:
        Map<String, Constrained> constraints = Ticket.constrainedProperties

        expect:
        !constraints.title.nullable
        !constraints.title.blank
        constraints.title.maxSize == 40
        !constraints.contact.nullable
        constraints.contact.email
        !constraints.priority.nullable
        constraints.priority.min == 1
        constraints.priority.max == 5
        constraints.reference.nullable
        constraints.reference.matches == '[A-Z]{3}-\\d{3}'
    }

    void 'the errors of the annotations are resolved through the message source'() {
        given:
        def ticket = new Ticket(title: '', contact: 'ops@example.com', priority: 2)

        expect:
        !ticket.validate()
        messageSource.getMessage(ticket.errors.getFieldError('title'), Locale.ENGLISH) == 'Every ticket needs a title'
    }
}

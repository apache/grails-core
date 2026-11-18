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
package org.grails.web.binding

import java.sql.Time
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

import spock.lang.Specification

import grails.artefact.Artefact
import grails.converters.JSON
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable

class DateTimeRoundTripBindingSpec extends Specification implements ControllerUnitTest<AppointmentController> {

    private TimeZone serverZone

    void setup() {
        serverZone = TimeZone.default
        // A server outside UTC, where a date read in the zone of the server is off by its offset.
        TimeZone.default = TimeZone.getTimeZone('America/Denver')
    }

    void cleanup() {
        TimeZone.default = serverZone
    }

    void 'binds the dates Grails renders in JSON back to what they were rendered from'() {
        given:
        Instant instant = Instant.parse('2025-10-07T21:14:31Z')
        request.method = 'POST'
        request.json = ([
                at    : Date.from(instant),
                offset: OffsetDateTime.parse('2025-10-07T23:14:31+02:00'),
                zoned : ZonedDateTime.parse('2025-10-07T23:14:31+02:00'),
                local : LocalDateTime.parse('2025-10-07T21:14:31.25'),
                time  : Time.valueOf('01:48:46')
        ] as JSON).toString()

        when:
        Appointment appointment = controller.save().appointment

        then:
        !appointment.hasErrors()
        appointment.at.toInstant() == instant
        appointment.offset.toInstant() == instant
        appointment.zoned.toInstant() == instant
        appointment.local == LocalDateTime.parse('2025-10-07T21:14:31.25')
        appointment.time == Time.valueOf('01:48:46')
    }

    void 'binds a date before 1582 that Grails renders in JSON back to the same date'() {
        given: 'a date the Julian calendar, which a Date is written in before 1582, names'
        Calendar julian = new GregorianCalendar(TimeZone.getTimeZone('UTC'))
        julian.clear()
        julian.set(year, Calendar.JANUARY, 1)
        request.method = 'POST'
        request.json = ([at: julian.time] as JSON).toString()

        when:
        Appointment appointment = controller.save().appointment

        then:
        appointment.at == julian.time

        where:
        year << [1500, 44]
    }

    void 'binds #sent as the instant it names, whatever the zone of the server'() {
        given:
        request.method = 'POST'
        request.json = """{"at": "${sent}", "offset": "${sent}"}""".toString()

        when:
        Appointment appointment = controller.save().appointment

        then:
        appointment.at.toInstant() == Instant.parse(named)
        appointment.offset.toInstant() == Instant.parse(named)

        where:
        sent                            | named
        '2024-05-01T10:00:00Z'          | '2024-05-01T10:00:00Z'
        '2024-05-01T10:00:00+02:00'     | '2024-05-01T08:00:00Z'
        '2024-05-01T10:00:00.5Z'        | '2024-05-01T10:00:00.500Z'
        '2024-05-01T10:00:00.000+02:00' | '2024-05-01T08:00:00Z'
    }

    void 'binds a date that no format reads all of with the first format that reads the start of it, as Grails 7 did'() {
        given: 'a date and time no format reads all of, where yyyy-MM-dd reads the date'
        request.method = 'POST'
        request.json = '{"at": "2024-05-01 10:00"}'

        when:
        Appointment appointment = controller.save().appointment

        then: 'it binds to the start of that day on the server'
        !appointment.errors.hasFieldErrors('at')
        appointment.at.toInstant() == Instant.parse('2024-05-01T06:00:00Z')
    }
}

class Appointment implements Validateable {
    Date at
    OffsetDateTime offset
    ZonedDateTime zoned
    LocalDateTime local
    Time time
}

@Artefact('Controller')
class AppointmentController {

    def save(Appointment appointment) {
        [appointment: appointment]
    }
}

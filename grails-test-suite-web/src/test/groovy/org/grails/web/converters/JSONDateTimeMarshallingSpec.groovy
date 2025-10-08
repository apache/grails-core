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
package org.grails.web.converters

import grails.converters.JSON
import grails.testing.web.GrailsWebUnitTest
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Tests for JSON marshalling of Date, Calendar, Instant, and LocalDateTime types.
 *
 * @since 7.0
 */
class JSONDateTimeMarshallingSpec extends Specification implements GrailsWebUnitTest {

    void "test Date, Calendar, and Instant render with Z suffix, LocalDateTime without"() {
        given: "All four date types representing the same point in time"
        def instant = Instant.parse("2025-10-07T21:14:31Z")
        def date = Date.from(instant)
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.setTime(date)
        def localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

        when: "All four are converted to JSON"
        def json = ([
            createdDate: date,
            createdCalendar: calendar,
            createdLocalDateTime: localDateTime,
            createdInstant: instant
        ] as JSON).toString()

        then: "Date, Calendar, and Instant render with Z suffix"
        json.contains('"createdDate":"2025-10-07T21:14:31Z"')
        json.contains('"createdCalendar":"2025-10-07T21:14:31Z"')
        json.contains('"createdInstant":"2025-10-07T21:14:31Z"')

        and: "LocalDateTime renders without timezone (ISO_LOCAL_DATE_TIME)"
        json.contains('"createdLocalDateTime":"2025-10-07T21:14:31"')
    }

    void "test Instant renders with ISO-8601 format instead of object structure"() {
        given: "An Instant value"
        def instant = Instant.parse("2025-10-07T21:14:31.123Z")

        when: "The Instant is converted to JSON"
        def json = ([timestamp: instant] as JSON).toString()

        then: "Instant renders as ISO-8601 string, not object properties"
        json == '{"timestamp":"2025-10-07T21:14:31.123Z"}'
        !json.contains('epochSecond')
        !json.contains('nano')
    }

    void "test LocalDateTime renders without timezone suffix"() {
        given: "A LocalDateTime value"
        def localDateTime = LocalDateTime.of(2025, 10, 7, 21, 14, 31, 456000000)

        when: "The LocalDateTime is converted to JSON"
        def json = ([dateTime: localDateTime] as JSON).toString()

        then: "LocalDateTime renders as ISO-8601 without Z suffix (local time, no timezone)"
        json == '{"dateTime":"2025-10-07T21:14:31.456"}'
        !json.contains('Z')
        !json.contains('year')
        !json.contains('month')
        !json.contains('dayOfMonth')
    }

    void "test Calendar renders with Z suffix"() {
        given: "A Calendar value"
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(2025, Calendar.OCTOBER, 7, 21, 14, 31)
        calendar.set(Calendar.MILLISECOND, 0)

        when: "The Calendar is converted to JSON"
        def json = ([timestamp: calendar] as JSON).toString()

        then: "Calendar renders as ISO-8601 with Z suffix, not as object properties"
        json == '{"timestamp":"2025-10-07T21:14:31Z"}'
        !json.contains('timeInMillis')
        !json.contains('firstDayOfWeek')
        !json.contains('lenient')
    }
}

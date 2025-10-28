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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.ZoneOffset

/**
 * Tests for JSON marshalling of Date, Calendar, Instant, LocalDate, LocalDateTime, OffsetDateTime, and ZonedDateTime types.
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

        then: "Date and Calendar render with Z suffix and milliseconds"
        json.contains('"createdDate":"2025-10-07T21:14:31.000Z"')
        json.contains('"createdCalendar":"2025-10-07T21:14:31.000Z"')

        and: "Instant renders with Z suffix"
        json.contains('"createdInstant":"2025-10-07T21:14:31Z"')

        and: "LocalDateTime renders without timezone (ISO_LOCAL_DATE_TIME)"
        json.contains('"createdLocalDateTime":"2025-10-07T21:14:31"')
    }

    void "test Instant renders with ISO-8601 format instead of object structure"() {
        given: "An Instant value with nanosecond precision"
        def instant = Instant.parse("2025-10-07T21:14:31.407254Z") // 407.254 milliseconds = 407254000 nanoseconds

        when: "The Instant is converted to JSON"
        def json = ([timestamp: instant] as JSON).toString()

        then: "Instant renders as ISO-8601 string with full precision, not object properties"
        json == '{"timestamp":"2025-10-07T21:14:31.407254Z"}'
        !json.contains('epochSecond')
        !json.contains('nano')
    }

    void "test LocalDateTime renders without timezone suffix"() {
        given: "A LocalDateTime value with nanosecond precision"
        def localDateTime = LocalDateTime.of(2025, 10, 7, 21, 14, 31, 407254000) // 407.254 milliseconds

        when: "The LocalDateTime is converted to JSON"
        def json = ([dateTime: localDateTime] as JSON).toString()

        then: "LocalDateTime renders as ISO-8601 with full precision, without Z suffix"
        json == '{"dateTime":"2025-10-07T21:14:31.407254"}'
        !json.contains('Z')
        !json.contains('year')
        !json.contains('month')
        !json.contains('dayOfMonth')
    }

    void "test Calendar renders with Z suffix and milliseconds"() {
        given: "A Calendar value"
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(2025, Calendar.OCTOBER, 7, 21, 14, 31)
        calendar.set(Calendar.MILLISECOND, 0)

        when: "The Calendar is converted to JSON"
        def json = ([timestamp: calendar] as JSON).toString()

        then: "Calendar renders as ISO-8601 with Z suffix and milliseconds, not as object properties"
        json == '{"timestamp":"2025-10-07T21:14:31.000Z"}'
        !json.contains('timeInMillis')
        !json.contains('firstDayOfWeek')
        !json.contains('lenient')
    }

    void "test OffsetDateTime renders with timezone offset"() {
        given: "An OffsetDateTime value with -07:00 offset"
        def offsetDateTime = OffsetDateTime.of(2025, 10, 8, 0, 48, 46, 407254000, ZoneOffset.ofHours(-7))

        when: "The OffsetDateTime is converted to JSON"
        def json = ([dateTime: offsetDateTime] as JSON).toString()

        then: "OffsetDateTime renders as ISO-8601 with offset"
        json == '{"dateTime":"2025-10-08T00:48:46.407254-07:00"}'
        !json.contains('offset')
        !json.contains('nano')
    }

    void "test ZonedDateTime renders with timezone offset (without zone ID)"() {
        given: "A ZonedDateTime value"
        def zonedDateTime = ZonedDateTime.of(2025, 10, 8, 0, 48, 46, 407254000, ZoneOffset.ofHours(-7))

        when: "The ZonedDateTime is converted to JSON"
        def json = ([dateTime: zonedDateTime] as JSON).toString()

        then: "ZonedDateTime renders as ISO-8601 with offset (no zone ID brackets)"
        json == '{"dateTime":"2025-10-08T00:48:46.407254-07:00"}'
        !json.contains('[')
        !json.contains('zone')
    }

    void "test LocalDate renders as date only (YYYY-MM-DD)"() {
        given: "A LocalDate value"
        def localDate = LocalDate.of(2025, 10, 8)

        when: "The LocalDate is converted to JSON"
        def json = ([date: localDate] as JSON).toString()

        then: "LocalDate renders as ISO-8601 date only (no time)"
        json == '{"date":"2025-10-08"}'
        !json.contains('T')
        !json.contains('year')
        !json.contains('month')
    }
}

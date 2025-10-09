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
package grails.plugin.json.view

import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.ZoneOffset

class DateTimeRenderingSpec extends Specification implements JsonViewTest {

    void "Test Date and Instant render with Z, LocalDateTime without"() {
        given: "A view that renders date/time types"
        String source = '''
import java.time.Instant
import java.time.LocalDateTime

model {
    Date createdDate
    LocalDateTime createdLocalDateTime
    Instant createdInstant
}

json {
    createdDate createdDate
    createdLocalDateTime createdLocalDateTime
    createdInstant createdInstant
}
'''

        and: "All three date types representing the same point in time"
        // Use a fixed instant: 2025-10-07T21:14:31Z
        def instant = Instant.parse("2025-10-07T21:14:31Z")
        def date = Date.from(instant)
        def localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

        when: "The view is rendered"
        def result = render(source, [
            createdDate: date,
            createdLocalDateTime: localDateTime,
            createdInstant: instant
        ])

        then: "Date and Instant render with Z suffix"
        result.json.createdDate == "2025-10-07T21:14:31Z"
        result.json.createdInstant == "2025-10-07T21:14:31Z"

        and: "LocalDateTime renders without timezone (local time)"
        result.json.createdLocalDateTime == "2025-10-07T21:14:31"
    }

    void "Test Instant renders with ISO-8601 format instead of epoch milliseconds"() {
        given: "A view that renders an Instant"
        String source = '''
import java.time.Instant

model {
    Instant timestamp
}

json {
    timestamp timestamp
}
'''

        and: "An Instant value with nanosecond precision"
        def instant = Instant.parse("2025-10-07T21:14:31.407254Z") // 407.254 milliseconds = 407254000 nanoseconds

        when: "The view is rendered"
        def result = render(source, [timestamp: instant])

        then: "Instant renders as ISO-8601 string with full precision, not epoch milliseconds"
        result.json.timestamp == "2025-10-07T21:14:31.407254Z"
        result.json.timestamp instanceof String
    }

    void "Test LocalDateTime renders without timezone suffix"() {
        given: "A view that renders a LocalDateTime"
        String source = '''
import java.time.LocalDateTime

model {
    LocalDateTime dateTime
}

json {
    dateTime dateTime
}
'''

        and: "A LocalDateTime value with nanosecond precision"
        def localDateTime = LocalDateTime.of(2025, 10, 7, 21, 14, 31, 407254000) // 407.254 milliseconds

        when: "The view is rendered"
        def result = render(source, [dateTime: localDateTime])

        then: "LocalDateTime renders as ISO-8601 with full precision, without timezone"
        result.json.dateTime == "2025-10-07T21:14:31.407254"
        result.json.dateTime instanceof String
    }

    void "Test OffsetDateTime renders with timezone offset"() {
        given: "A view that renders an OffsetDateTime"
        String source = '''
import java.time.OffsetDateTime

model {
    OffsetDateTime dateTime
}

json {
    dateTime dateTime
}
'''

        and: "An OffsetDateTime value with -07:00 offset"
        def offsetDateTime = OffsetDateTime.of(2025, 10, 8, 0, 48, 46, 407254000, ZoneOffset.ofHours(-7))

        when: "The view is rendered"
        def result = render(source, [dateTime: offsetDateTime])

        then: "OffsetDateTime renders with offset"
        result.json.dateTime == "2025-10-08T00:48:46.407254-07:00"
        result.json.dateTime instanceof String
    }

    void "Test ZonedDateTime renders with timezone offset (no zone ID)"() {
        given: "A view that renders a ZonedDateTime"
        String source = '''
import java.time.ZonedDateTime

model {
    ZonedDateTime dateTime
}

json {
    dateTime dateTime
}
'''

        and: "A ZonedDateTime value"
        def zonedDateTime = ZonedDateTime.of(2025, 10, 8, 0, 48, 46, 407254000, ZoneOffset.ofHours(-7))

        when: "The view is rendered"
        def result = render(source, [dateTime: zonedDateTime])

        then: "ZonedDateTime renders with offset (no zone ID brackets)"
        result.json.dateTime == "2025-10-08T00:48:46.407254-07:00"
        result.json.dateTime instanceof String
    }

    void "Test LocalDate renders as date only (YYYY-MM-DD)"() {
        given: "A view that renders a LocalDate"
        String source = '''
import java.time.LocalDate

model {
    LocalDate date
}

json {
    date date
}
'''

        and: "A LocalDate value"
        def localDate = LocalDate.of(2025, 10, 8)

        when: "The view is rendered"
        def result = render(source, [date: localDate])

        then: "LocalDate renders as date only (no time)"
        result.json.date == "2025-10-08"
        result.json.date instanceof String
    }
}

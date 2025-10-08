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
import java.time.LocalDateTime
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

        and: "An Instant value"
        def instant = Instant.parse("2025-10-07T21:14:31.123Z")

        when: "The view is rendered"
        def result = render(source, [timestamp: instant])

        then: "Instant renders as ISO-8601 string, not epoch milliseconds"
        result.json.timestamp == "2025-10-07T21:14:31.123Z"
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

        and: "A LocalDateTime value"
        def localDateTime = LocalDateTime.of(2025, 10, 7, 21, 14, 31, 456000000)

        when: "The view is rendered"
        def result = render(source, [dateTime: localDateTime])

        then: "LocalDateTime renders as ISO-8601 without timezone (local time)"
        result.json.dateTime == "2025-10-07T21:14:31.456"
        result.json.dateTime instanceof String
    }
}

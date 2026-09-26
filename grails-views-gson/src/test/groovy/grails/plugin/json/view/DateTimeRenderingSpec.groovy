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
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.sql.Time
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.ZoneOffset

class DateTimeRenderingSpec extends Specification implements JsonViewTest {

    @Shared
    JsonMapper jackson = JsonMapper.builder().build()

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

        then: "Date and Instant render with Z suffix and millisecond precision"
        result.json.createdDate == "2025-10-07T21:14:31.000Z"
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

    void "Test Date and Calendar render with millisecond precision"() {
        given: "A view that renders Date and Calendar"
        String source = '''
model {
    Date date
    Calendar calendar
}

json {
    date date
    calendar calendar
}
'''

        and: "Date and Calendar with millisecond precision"
        // Create a date with 407 milliseconds: 2025-10-08T07:48:46.407Z
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        calendar.set(2025, Calendar.OCTOBER, 8, 7, 48, 46)
        calendar.set(Calendar.MILLISECOND, 407)
        def date = calendar.getTime()

        when: "The view is rendered"
        def result = render(source, [date: date, calendar: calendar])

        then: "Date renders with millisecond precision"
        result.json.date == "2025-10-08T07:48:46.407Z"
        result.json.date instanceof String

        and: "Calendar renders with millisecond precision"
        result.json.calendar == "2025-10-08T07:48:46.407Z"
        result.json.calendar instanceof String
    }

    void "Test java.sql Date, Time and Timestamp render the same as Spring Boot"() {
        given: "A view that renders the java.sql date types"
        String source = '''
import java.sql.Time
import java.sql.Timestamp

model {
    java.sql.Date sqlDate
    Time time
    Timestamp timestamp
}

json {
    sqlDate sqlDate
    time time
    timestamp timestamp
}
'''

        when: "The view is rendered"
        def result = render(source, [
            sqlDate: new java.sql.Date(1759909726407L),
            time: Time.valueOf('01:48:46'),
            timestamp: Timestamp.from(Instant.parse('2025-10-08T07:48:46.407254Z'))
        ])

        then: "java.sql.Date and Timestamp render as UTC instants with millisecond precision"
        result.json.sqlDate == '2025-10-08T07:48:46.407Z'
        result.json.timestamp == '2025-10-08T07:48:46.407Z'

        and: "java.sql.Time renders as its wall-clock time (Time#toString)"
        result.json.time == '01:48:46'
    }

    void "a #description renders the same as Spring Boot's Jackson JsonMapper"() {
        given: "A view that renders any value"
        String source = '''
model {
    Object value
}

json {
    value value
}
'''

        when: "The view is rendered"
        def result = render(source, [value: value])

        then:
        result.jsonText == jackson.writeValueAsString([value: value])

        where:
        value << DateTimeValues.all()
        description = value instanceof Map ? "${value.keySet().first().class.simpleName} map key" : value.class.simpleName
    }

    void "Test Date, Calendar and ZonedDateTime map keys render like their values"() {
        given: "A view that renders a map keyed by dates"
        String source = '''
model {
    Map dates
}

json {
    dates dates
}
'''
        def instant = Instant.parse('2025-10-08T07:48:46.407254Z')
        def wholeSecond = Instant.parse('2026-09-25T03:00:00Z')

        when: "The view is rendered"
        def result = render(source, [dates: [
            (Date.from(instant)): 'date',
            (GregorianCalendar.from(wholeSecond.atZone(ZoneOffset.ofHours(9)))): 'calendar',
            (instant.atZone(ZoneOffset.ofHours(-3))): 'zonedDateTime',
            name: 'string'
        ]])

        then: "Date and Calendar keys use the date format and ZonedDateTime keys ISO_OFFSET_DATE_TIME, as Spring Boot does"
        result.jsonText == '{"dates":{' +
                '"2025-10-08T07:48:46.407Z":"date",' +
                '"2026-09-25T03:00:00.000Z":"calendar",' +
                '"2025-10-08T04:48:46.407254-03:00":"zonedDateTime",' +
                '"name":"string"}}'
    }

    void "Test a configured dateFormat applies to Date values and map keys"() {
        given: "A generator configured with a date format"
        def configuration = new JsonViewConfiguration(generator: new JsonViewGeneratorConfiguration(dateFormat: 'yyyy-MM-dd HH:mm', timeZone: 'UTC'))
        def generator = new JsonViewTemplateEngine(configuration, getClass().classLoader).generator
        def date = new Date(1759909726407L)

        expect: "Dates use the configured pattern and time zone instead of the Spring Boot format"
        generator.toJson([date: date, keyed: [(date): 'value']]) == '{"date":"2025-10-08 07:48","keyed":{"2025-10-08 07:48":"value"}}'
    }
}

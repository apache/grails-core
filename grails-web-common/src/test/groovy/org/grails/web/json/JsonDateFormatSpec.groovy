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
package org.grails.web.json

import java.sql.Time
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

import spock.lang.Specification

/**
 * {@link JsonDateFormat} formats dates the way Jackson's default {@code StdDateFormat} does, which is
 * how Spring Boot renders them.
 */
class JsonDateFormatSpec extends Specification {

    void "formats #description"() {
        expect:
        JsonDateFormat.format(date.time) == expected

        where:
        description                                | date                                               || expected
        'a UTC instant with millisecond precision' | new Date(1759909726407L)                           || '2025-10-08T07:48:46.407Z'
        'a zero millisecond fraction'              | new Date(1790305200000L)                           || '2026-09-25T03:00:00.000Z'
        'a date before the epoch'                  | new Date(-1L)                                      || '1969-12-31T23:59:59.999Z'
        'a Julian date before the 1582 cutover'    | Date.from(Instant.parse('0999-06-01T00:00:00Z'))   || '0999-05-27T00:00:00.000Z'
        'the first Gregorian day'                  | new Date(-12219292800000L)                         || '1582-10-15T00:00:00.000Z'
        'a year after 9999 with a plus sign'       | Date.from(Instant.parse('+12345-01-01T00:00:00Z')) || '+12345-01-01T00:00:00.000Z'
        '1 BC as ISO year +0000'                   | bcDate(1, Calendar.JANUARY, 1)                     || '+0000-01-01T00:00:00.000Z'
        '44 BC as ISO year -0043'                  | bcDate(44, Calendar.MARCH, 15)                     || '-0043-03-15T00:00:00.000Z'
    }

    void "formats in the #zone time zone with its offset, as Jackson does with a default time zone"() {
        expect:
        JsonDateFormat.format(1759909726407L, TimeZone.getTimeZone(zone)) == expected

        where:
        zone               || expected
        'UTC'              || '2025-10-08T07:48:46.407Z'
        'GMT'              || '2025-10-08T07:48:46.407Z'
        'America/New_York' || '2025-10-08T03:48:46.407-04:00'
        'Asia/Kolkata'     || '2025-10-08T13:18:46.407+05:30'
        'Pacific/Chatham'  || '2025-10-08T21:33:46.407+13:45'
    }

    void "formats a #key.class.simpleName map key as #expected"() {
        expect:
        JsonDateFormat.isDateKey(key)
        JsonDateFormat.formatKey(key) == expected

        where:
        key                                                                                      || expected
        new Date(1759909726407L)                                                                 || '2025-10-08T07:48:46.407Z'
        new java.sql.Date(1759909726407L)                                                        || '2025-10-08T07:48:46.407Z'
        new Time(1759909726407L)                                                                 || '2025-10-08T07:48:46.407Z'
        Timestamp.from(Instant.parse('2025-10-08T07:48:46.407254Z'))                             || '2025-10-08T07:48:46.407Z'
        GregorianCalendar.from(ZonedDateTime.parse('2025-10-08T16:48:46.407+09:00[Asia/Tokyo]')) || '2025-10-08T07:48:46.407Z'
        ZonedDateTime.parse('2026-09-25T00:00-03:00[America/Sao_Paulo]')                         || '2026-09-25T00:00:00-03:00'
    }

    void "formats any other map key with its toString()"() {
        expect:
        !JsonDateFormat.isDateKey(key)
        JsonDateFormat.formatKey(key) == key.toString()

        where:
        key << ['name', 42, Instant.parse('2025-10-08T07:48:46.407254Z'), LocalDate.of(2026, 9, 25)]
    }

    private static Date bcDate(int year, int month, int day) {
        new GregorianCalendar(TimeZone.getTimeZone('UTC')).tap {
            clear()
            set(Calendar.ERA, GregorianCalendar.BC)
            set(year, month, day)
        }.time
    }
}

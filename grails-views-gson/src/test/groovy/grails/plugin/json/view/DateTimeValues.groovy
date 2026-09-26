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

import java.sql.Time
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

import javax.xml.datatype.DatatypeFactory

/**
 * Date and time values, and maps keyed by them, whose JSON is compared with Spring Boot's Jackson rendering.
 */
class DateTimeValues {

    static List<Object> all() {
        Instant instant = Instant.parse('2025-10-08T07:48:46.407254Z')
        Instant wholeSecond = Instant.parse('2026-09-25T03:00:00Z')
        ZoneId saoPaulo = ZoneId.of('America/Sao_Paulo')
        DatatypeFactory xml = DatatypeFactory.newInstance()
        List<Object> values = [
                Date.from(instant),
                Date.from(wholeSecond),
                new Date(-1L),
                Date.from(Instant.parse('0999-06-01T00:00:00Z')),
                Date.from(Instant.parse('+12345-01-01T00:00:00Z')),
                bcDate(1, Calendar.JANUARY, 1),
                bcDate(44, Calendar.MARCH, 15),
                GregorianCalendar.from(instant.atZone(ZoneId.of('Asia/Tokyo'))),
                new java.sql.Date(instant.toEpochMilli()),
                java.sql.Date.valueOf('2026-09-25'),
                new Time(instant.toEpochMilli()),
                Time.valueOf('03:00:00'),
                Timestamp.from(instant),
                Timestamp.from(wholeSecond),
                xml.newXMLGregorianCalendar('2025-10-08T01:48:46.407-06:00'),
                xml.newXMLGregorianCalendar('2025-10-08'),
                instant,
                wholeSecond,
                Instant.EPOCH,
                Instant.parse('+12345-01-01T00:00:00Z'),
                LocalDate.of(2026, 9, 25),
                LocalDate.of(12345, 1, 1),
                LocalTime.of(1, 48, 46, 407254000),
                LocalTime.of(3, 0),
                LocalTime.of(3, 0, 0, 500_000_000),
                LocalDateTime.of(2025, 10, 8, 1, 48, 46, 407254000),
                LocalDateTime.of(2026, 9, 25, 3, 0),
                instant.atOffset(ZoneOffset.ofHours(-6)),
                wholeSecond.atOffset(ZoneOffset.UTC),
                OffsetTime.of(3, 0, 0, 0, ZoneOffset.ofHours(-3)),
                OffsetTime.of(3, 0, 0, 500_000_000, ZoneOffset.ofHoursMinutes(5, 30)),
                wholeSecond.atZone(saoPaulo),
                wholeSecond.atZone(ZoneId.of('UTC')),
                wholeSecond.atZone(ZoneOffset.UTC),
                Year.of(2026),
                Year.of(-44),
                YearMonth.of(2026, 9),
                YearMonth.of(-1, 12),
                MonthDay.of(2, 29),
                Month.JANUARY,
                Month.DECEMBER,
                DayOfWeek.FRIDAY,
                Duration.ofMinutes(90).plusMillis(250),
                Duration.ZERO,
                Duration.ofSeconds(-90),
                Period.of(1, 2, 3),
                Period.ZERO,
                saoPaulo,
                ZoneId.of('GMT+2'),
                ZoneOffset.ofHours(-3),
                ZoneOffset.UTC,
                TimeZone.getTimeZone('America/Sao_Paulo'),
                TimeZone.getTimeZone('GMT+02:00'),
                new SimpleTimeZone(3_600_000, 'Custom/Zone'),
                xml.newDuration('P1DT2H'),
        ]
        List<Object> keys = [
                Date.from(instant),
                GregorianCalendar.from(instant.atZone(ZoneId.of('Asia/Tokyo'))),
                new java.sql.Date(instant.toEpochMilli()),
                Time.valueOf('03:00:00'),
                Timestamp.from(instant),
                instant.atZone(saoPaulo),
                wholeSecond.atZone(ZoneId.of('UTC')),
                instant,
                LocalDate.of(2026, 9, 25),
                LocalDateTime.of(2026, 9, 25, 3, 0),
                LocalTime.of(3, 0),
                wholeSecond.atOffset(ZoneOffset.ofHours(-3)),
                Duration.ofMinutes(90),
                Year.of(2026),
                saoPaulo,
        ]
        values + keys.collect { [(it): 'value'] }
    }

    private static Date bcDate(int year, int month, int day) {
        new GregorianCalendar(TimeZone.getTimeZone('UTC')).tap {
            clear()
            set(Calendar.ERA, GregorianCalendar.BC)
            set(year, month, day)
        }.time
    }
}

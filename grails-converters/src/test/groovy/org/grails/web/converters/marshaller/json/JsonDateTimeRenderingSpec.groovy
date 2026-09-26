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
package org.grails.web.converters.marshaller.json

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
import java.time.format.DateTimeFormatter

import javax.xml.datatype.DatatypeFactory

import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.json.JSONWriter

/**
 * The default JSON converter configuration renders date and time values the same way as
 * Spring Boot's default Jackson {@code JsonMapper}.
 *
 * @see <a href="https://github.com/apache/grails-core/issues/16406">GitHub issue 16406</a>
 */
class JsonDateTimeRenderingSpec extends Specification {

    @Shared
    JsonMapper jackson = JsonMapper.builder().build()

    void setup() {
        new ConvertersConfigurationInitializer().initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void "a #value.class.simpleName renders as #expected"() {
        expect:
        new JSON([value: value]).toString() == "{\"value\":${expected}}"

        where:
        value                                                            || expected
        new Date(1790305200000L)                                         || '"2026-09-25T03:00:00.000Z"'
        new java.sql.Date(1759909726407L)                                || '"2025-10-08T07:48:46.407Z"'
        Time.valueOf('01:48:46')                                         || '"01:48:46"'
        Timestamp.from(Instant.parse('2025-10-08T07:48:46.407254Z'))     || '"2025-10-08T07:48:46.407Z"'
        calendar('2025-10-08T16:48:46.407+09:00[Asia/Tokyo]')            || '"2025-10-08T07:48:46.407Z"'
        xmlCalendar('2025-10-08T01:48:46.407-06:00')                     || '"2025-10-08T07:48:46.407Z"'
        Instant.parse('2025-10-08T07:48:46.407254Z')                     || '"2025-10-08T07:48:46.407254Z"'
        Instant.parse('2026-09-25T03:00:00Z')                            || '"2026-09-25T03:00:00Z"'
        LocalDate.of(2025, 10, 8)                                        || '"2025-10-08"'
        LocalTime.of(1, 48, 46, 407254000)                               || '"01:48:46.407254"'
        LocalTime.of(3, 0)                                               || '"03:00:00"'
        LocalDateTime.of(2025, 10, 8, 1, 48, 46, 407254000)              || '"2025-10-08T01:48:46.407254"'
        OffsetDateTime.parse('2025-10-08T01:48:46.407254-06:00')         || '"2025-10-08T01:48:46.407254-06:00"'
        OffsetTime.parse('03:00-03:00')                                  || '"03:00-03:00"'
        ZonedDateTime.parse('2026-09-25T00:00-03:00[America/Sao_Paulo]') || '"2026-09-25T00:00:00-03:00"'
        Year.of(2026)                                                    || '2026'
        YearMonth.of(2026, 9)                                            || '"2026-09"'
        MonthDay.of(9, 25)                                               || '"--09-25"'
        Month.SEPTEMBER                                                  || '9'
        DayOfWeek.FRIDAY                                                 || '"FRIDAY"'
        Duration.ofMinutes(90).plusMillis(250)                           || '"PT1H30M0.25S"'
        Period.of(1, 2, 3)                                               || '"P1Y2M3D"'
        ZoneId.of('America/Sao_Paulo')                                   || '"America/Sao_Paulo"'
        ZoneOffset.ofHours(-3)                                           || '"-03:00"'
        TimeZone.getTimeZone('America/Sao_Paulo')                        || '"America/Sao_Paulo"'
        DatatypeFactory.newInstance().newDuration('P1DT2H')              || '"P1DT2H"'
    }

    void "a #description renders the same as Spring Boot's Jackson JsonMapper"() {
        given:
        def map = [value: value]

        expect:
        new JSON(map).toString() == jackson.writeValueAsString(map)

        where:
        value << DateTimeValues.all()
        description = value instanceof Map ? "${value.keySet().first().class.simpleName} map key" : value.class.simpleName
    }

    void "a registered object marshaller overrides the rendering of a date type"() {
        given:
        JSON.registerObjectMarshaller(Month) { Month month ->
            month.name()
        }

        expect:
        new JSON([value: Month.SEPTEMBER]).toString() == '{"value":"SEPTEMBER"}'
    }

    void "with grails.converters.json.date set to javascript, Date values including java.sql.Time render as JavaScript dates"() {
        given: 'the default configuration from setup() replaced by a javascript one'
        ConvertersConfigurationHolder.clear()
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config.setAt('grails.converters.json.date', 'javascript')
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()

        expect: 'other date and time types render as they do by default'
        new JSON([date: new Date(0L), time: new Time(0L), month: Month.MAY, calendar: calendar('1970-01-01T00:00Z[UTC]')]).toString() ==
                '{"date":new Date(0),"time":new Date(0),"month":5,"calendar":"1970-01-01T00:00:00.000Z"}'
    }

    void "the JSON builder writes date map keys the same way as Spring Boot"() {
        given:
        def json = new JSON()
        def out = new StringWriter()
        json.writer = new JSONWriter(out)
        def date = new Date(1759909726407L)

        when:
        json.build {
            keyed([(date): 'date', name: 'string'])
        }

        then:
        out.toString() == '{"keyed":{"2025-10-08T07:48:46.407Z":"date","name":"string"}}'
    }

    void "a java.sql.Date renders with 'as JSON' instead of failing"() {
        given: 'the reproducer from issue 16406'
        def date = java.sql.Date.valueOf('2026-09-25')
        def midnightUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC)
                .format(LocalDate.of(2026, 9, 25).atStartOfDay(ZoneId.systemDefault()))

        expect:
        ([d: date] as JSON).toString() == "{\"d\":\"${midnightUtc}\"}"
    }

    private static Calendar calendar(String zonedDateTime) {
        GregorianCalendar.from(ZonedDateTime.parse(zonedDateTime))
    }

    private static Object xmlCalendar(String lexical) {
        DatatypeFactory.newInstance().newXMLGregorianCalendar(lexical)
    }
}

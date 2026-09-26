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
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.OffsetTime
import java.time.Period
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

import groovy.json.JsonSlurper

import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import grails.persistence.Entity
import grails.views.json.test.JsonViewUnitTest

/**
 * Date and time values rendered through {@code g.render} and a configured JSON generator are written the same way
 * as Spring Boot's Jackson {@code JsonMapper} writes them.
 */
class DateTimeHelperRenderingSpec extends Specification implements JsonViewUnitTest {

    @Shared
    JsonMapper jackson = JsonMapper.builder().build()

    @Shared
    Instant instant = Instant.parse('2025-10-08T07:48:46.407254Z')

    void "g.render of a domain instance writes a Month property as its number"() {
        given:
        mappingContext.addPersistentEntity(MonthEntity)

        when:
        def result = render('''
            model {
                Object object
            }
            json g.render(object)
        ''', [object: new MonthEntity(name: 'Fred', month: Month.SEPTEMBER)])

        then:
        result.json.month == 9
        result.json.name == 'Fred'
    }

    void "g.render of a POGO writes its date and time properties the same way as Spring Boot"() {
        given:
        def pogo = new DateTimeHolder(
                year: Year.of(2026), yearMonth: YearMonth.of(2026, 9), monthDay: MonthDay.of(9, 25), month: Month.SEPTEMBER,
                duration: Duration.ofMinutes(90), period: Period.of(1, 2, 3), zoneId: ZoneId.of('America/Sao_Paulo'),
                zoneOffset: ZoneOffset.ofHours(-3), timeZone: TimeZone.getTimeZone('America/Sao_Paulo'),
                xmlCalendar: DatatypeFactory.newInstance().newXMLGregorianCalendar('2025-10-08T01:48:46.407-06:00'),
                xmlDuration: DatatypeFactory.newInstance().newDuration('P1DT2H'), localTime: LocalTime.of(3, 0),
                offsetTime: OffsetTime.parse('03:00-03:00'), time: Time.valueOf('01:48:46'),
                dates: [Date.from(instant), Date.from(Instant.parse('2026-09-25T03:00:00Z'))])

        when:
        def result = render('''
            model {
                Object object
            }
            json g.render(object)
        ''', [object: pogo])

        then:
        parse(result.jsonText) == parse(jackson.writeValueAsString(pogo.properties.findAll { it.key != 'class' }))
    }

    void "g.render of a map writes date keys and values the same way as Spring Boot"() {
        given:
        def map = [
                (Date.from(instant)): 'date',
                (instant.atZone(ZoneOffset.ofHours(-3))): 'zonedDateTime',
                dates: [Date.from(instant)],
                year: Year.of(2026),
                name: 'string'
        ]

        when:
        def result = render('''
            model {
                Map map
            }
            json g.render(map)
        ''', [map: map])

        then:
        result.jsonText == jackson.writeValueAsString(map)
    }

    void "a configured timeZone writes Date and Calendar values and keys in that zone, as Spring Boot does"() {
        given:
        def zone = TimeZone.getTimeZone(zoneId)
        def generator = generator(new JsonViewGeneratorConfiguration(timeZone: zoneId))
        def values = [
                date: Date.from(instant),
                calendar: GregorianCalendar.from(instant.atZone(ZoneId.of('Asia/Tokyo'))),
                time: Time.valueOf('01:48:46'),
                keys: [(Date.from(instant)): 'date', (Time.valueOf('01:48:46')): 'time']
        ]

        expect:
        generator.toJson(values) == JsonMapper.builder().defaultTimeZone(zone).build().writeValueAsString(values)

        where:
        zoneId << ['GMT', 'America/New_York', 'Asia/Kolkata']
    }

    void "a configured dateFormat applies to Date values and keys but not to java.sql.Time values, as in Spring Boot"() {
        given:
        def generator = generator(new JsonViewGeneratorConfiguration(dateFormat: 'yyyy-MM-dd HH:mm', timeZone: 'UTC'))
        def time = Time.valueOf('01:48:46')
        def format = new SimpleDateFormat('yyyy-MM-dd HH:mm').tap { timeZone = TimeZone.getTimeZone('UTC') }

        expect:
        generator.toJson([date: Date.from(instant), time: time, keys: [(time): 'time']]) ==
                "{\"date\":\"2025-10-08 07:48\",\"time\":\"01:48:46\",\"keys\":{\"${format.format(time)}\":\"time\"}}"
    }

    void "date keys that format to the same text are all written, as Spring Boot writes them"() {
        given:
        def timestamp = Timestamp.from(instant)
        def map = [(Date.from(instant)): 'date', (timestamp): 'timestamp']

        expect:
        generator(new JsonViewGeneratorConfiguration()).toJson(map) == jackson.writeValueAsString(map)
    }

    private static Object parse(String json) {
        new JsonSlurper().parseText(json)
    }

    private static groovy.json.JsonGenerator generator(JsonViewGeneratorConfiguration generatorConfiguration) {
        new JsonViewTemplateEngine(new JsonViewConfiguration(generator: generatorConfiguration), DateTimeHelperRenderingSpec.classLoader).generator
    }
}

class DateTimeHolder {
    Year year
    YearMonth yearMonth
    MonthDay monthDay
    Month month
    Duration duration
    Period period
    ZoneId zoneId
    ZoneOffset zoneOffset
    TimeZone timeZone
    XMLGregorianCalendar xmlCalendar
    javax.xml.datatype.Duration xmlDuration
    LocalTime localTime
    OffsetTime offsetTime
    Time time
    List<Date> dates
}

@Entity
class MonthEntity {
    String name
    Month month
}

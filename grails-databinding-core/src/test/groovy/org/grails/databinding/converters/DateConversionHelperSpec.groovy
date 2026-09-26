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
package org.grails.databinding.converters

import spock.lang.Issue

import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant

import spock.lang.Specification

import static java.util.Calendar.*

class DateConversionHelperSpec extends Specification {

    void 'Test parsing dates'() {
        given:
        Calendar calendar = getInstance()
        DateConversionHelper helper = new DateConversionHelper(formatStrings: ['yyyy-MM-dd HH:mm:ss.S',"yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd HH:mm:ss.S z","yyyy-MM-dd'T'HH:mm:ss.SSSX"])

        when:
        Date date = helper.convert '2013-04-15 21:26:31.973'
        calendar.setTime(date)

        then:
        APRIL == calendar.get(MONTH)
        15 == calendar.get(DAY_OF_MONTH)
        2013 == calendar.get(YEAR)
        21 == calendar.get(HOUR_OF_DAY)
        26 == calendar.get(MINUTE)
        31 == calendar.get(SECOND)

        when: 'a time in UTC, read in UTC'
        date = helper.convert '2011-03-12T09:24:22Z'
        calendar = getInstance(TimeZone.getTimeZone("UTC"))
        calendar.setTime(date)

        then:
        MARCH == calendar.get(MONTH)
        12 == calendar.get(DAY_OF_MONTH)
        2011 == calendar.get(YEAR)
        9 == calendar.get(HOUR_OF_DAY)
        24 == calendar.get(MINUTE)
        22 == calendar.get(SECOND)

        when:
        date = helper.convert '2012-06-12T09:24:22.222Z'
        calendar = getInstance(TimeZone.getTimeZone("UTC"))
        calendar.setTime(date)

        then:
        JUNE == calendar.get(MONTH)
        12 == calendar.get(DAY_OF_MONTH)
        2012 == calendar.get(YEAR)
        9 == calendar.get(HOUR_OF_DAY)
        24 == calendar.get(MINUTE)
        22 == calendar.get(SECOND)
    }

    void 'Test custom formats'() {
        given:
        Calendar calendar = getInstance()
        DateConversionHelper helper = new DateConversionHelper()
        helper.formatStrings = ['MMddyyyy', "'Month: 'MM', Day: 'dd', Year: 'yyyy"]

        when:
        Date date = helper.convert '11151969'
        calendar.setTime(date)

        then:
        NOVEMBER == calendar.get(MONTH)
        15 == calendar.get(DAY_OF_MONTH)
        1969 == calendar.get(YEAR)
        0 == calendar.get(HOUR_OF_DAY)
        0 == calendar.get(MINUTE)
        0 == calendar.get(SECOND)

        when:
        date = helper.convert 'Month: 04, Day: 07, Year: 1984'
        calendar.setTime(date)

        then:
        APRIL == calendar.get(MONTH)
        7 == calendar.get(DAY_OF_MONTH)
        1984 == calendar.get(YEAR)
        0 == calendar.get(HOUR_OF_DAY)
        0 == calendar.get(MINUTE)
        0 == calendar.get(SECOND)
    }

    void 'Test invalid format String'() {
        given:
        def helper = new DateConversionHelper(formatStrings: ['yyyy-MM-dd HH:mm:ss.S'])

        when:
        helper.convert 'some bogus value'

        then:
        thrown ParseException
    }

    void 'Test formatting an empty String'() {
        given:
        def helper = new DateConversionHelper(formatStrings: ['yyyy-MM-dd HH:mm:ss.S'])

        when:
        def date = helper.convert ''

        then:
        date == null
    }

    void 'Test formatted an empty String'() {
        given:
        def helper = new FormattedDateValueConverter()

        when:
        def date = helper.convert '', "yyMMdd"

        then:
        date == null
    }

    void 'converts #value to the instant it names, whatever the zone of the server'() {
        given: 'a server outside UTC, and the formats Grails configures by default'
        TimeZone serverZone = TimeZone.default
        TimeZone.default = TimeZone.getTimeZone('America/Denver')
        DateConversionHelper helper = new DateConversionHelper(formatStrings: ['yyyy-MM-dd HH:mm:ss.S', "yyyy-MM-dd'T'HH:mm:ss'Z'",
                'yyyy-MM-dd HH:mm:ss.S z', "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ss"])

        expect:
        ((Date) helper.convert(value)).toInstant() == Instant.parse(named)

        cleanup:
        TimeZone.default = serverZone

        where:
        value                                       | named
        '2024-05-01T10:00:00Z'                      | '2024-05-01T10:00:00Z'
        '2024-05-01T10:00:00+02:00'                 | '2024-05-01T08:00:00Z'
        '2024-05-01T10:00:00.5Z'                    | '2024-05-01T10:00:00.500Z'
        '2024-05-01T10:00:00.000+02:00'             | '2024-05-01T08:00:00Z'
        '2024-05-01T10:00:00+02:00[Europe/Paris]'   | '2024-05-01T08:00:00Z'
    }

    void 'converts #value in the calendar a Date is written in, which is Julian before 1582'() {
        given:
        DateConversionHelper helper = new DateConversionHelper(formatStrings: [])
        SimpleDateFormat written = new SimpleDateFormat("G yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        written.timeZone = TimeZone.getTimeZone('UTC')

        expect:
        written.format((Date) helper.convert(value)) == readAs

        where:
        value                        | readAs
        '1500-01-01T00:00:00.000Z'   | 'AD 1500-01-01T00:00:00.000Z'
        '0044-03-15T12:00:00.000Z'   | 'AD 0044-03-15T12:00:00.000Z'
        '-0043-03-15T00:00:00.000Z'  | 'BC 0044-03-15T00:00:00.000Z'
        '1600-01-01T10:00:00+02:00'  | 'AD 1600-01-01T08:00:00.000Z'
    }

    void 'does not convert a value that a format reads only the start of'() {
        given:
        DateConversionHelper helper = new DateConversionHelper(formatStrings: ['yyyy-MM-dd'])

        when: 'the format would read the date and lose the time'
        helper.convert '2024-05-01 10:00'

        then:
        ParseException e = thrown()
        e.message == 'Unparseable date: "2024-05-01 10:00"'
    }

    @Issue("https://github.com/apache/grails-core/issues/10387")
    void 'Test lenient date'() {
        given:
        DateConversionHelper helper = new DateConversionHelper(formatStrings: ['yyyy-MM-dd'])

        when:
        helper.convert '2017-13-20'

        then:
        thrown ParseException
    }

}

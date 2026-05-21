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

import spock.lang.Specification

import java.text.SimpleDateFormat
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

        when:
        date = helper.convert '2011-03-12T09:24:22Z'
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

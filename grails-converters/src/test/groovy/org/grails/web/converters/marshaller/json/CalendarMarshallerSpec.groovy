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

import java.text.SimpleDateFormat

import grails.converters.JSON

import org.grails.web.json.JSONWriter

import spock.lang.Specification

class CalendarMarshallerSpec extends Specification {

    void "supports returns true for Calendar instances"() {
        given:
        def marshaller = new CalendarMarshaller()

        expect:
        marshaller.supports(Calendar.getInstance())
        marshaller.supports(new GregorianCalendar())
    }

    void "supports returns false for non-Calendar instances"() {
        given:
        def marshaller = new CalendarMarshaller()

        expect:
        !marshaller.supports(new Date())
        !marshaller.supports("not a calendar")
        !marshaller.supports(null)
    }

    void "default formatter produces ISO-8601 UTC format with Z suffix"() {
        given:
        def marshaller = new CalendarMarshaller()
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.setTimeInMillis(1718461845123L)

        when:
        def result = marshalToString(marshaller, calendar)

        then:
        result == '["2024-06-15T14:30:45.123Z"]'
    }

    void "default formatter converts non-UTC calendar to UTC"() {
        given:
        def marshaller = new CalendarMarshaller()
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        calendar.setTimeInMillis(1718461845123L)

        when:
        def result = marshalToString(marshaller, calendar)

        then: "output is always UTC regardless of calendar timezone"
        result == '["2024-06-15T14:30:45.123Z"]'
    }

    void "default formatter pads sub-100 milliseconds to three digits"() {
        given:
        def marshaller = new CalendarMarshaller()
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.setTimeInMillis(1704067200005L)

        when:
        def result = marshalToString(marshaller, calendar)

        then:
        result == '["2024-01-01T00:00:00.005Z"]'
    }

    void "legacy formatter is used when provided"() {
        given:
        def customFormat = new SimpleDateFormat("dd/MM/yyyy")
        customFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
        def marshaller = new CalendarMarshaller(customFormat)
        def calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.setTimeInMillis(1718461845123L)

        when:
        def result = marshalToString(marshaller, calendar)

        then:
        result == '["15/06/2024"]'
    }

    private static String marshalToString(CalendarMarshaller marshaller, Calendar calendar) {
        def json = new JSON()
        def stringWriter = new StringWriter()
        json.writer = new JSONWriter(stringWriter)
        json.writer.array()
        marshaller.marshalObject(calendar, json)
        json.writer.endArray()
        stringWriter.toString()
    }
}

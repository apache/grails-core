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
import java.text.SimpleDateFormat
import java.time.Instant

import spock.lang.Specification

import grails.converters.JSON
import org.grails.web.json.JSONWriter

class DateMarshallerSpec extends Specification {

    void "supports returns true for Date instances"() {
        given:
        def marshaller = new DateMarshaller()

        expect:
        marshaller.supports(new Date())
    }

    void "supports returns false for non-Date instances"() {
        given:
        def marshaller = new DateMarshaller()

        expect:
        !marshaller.supports('not a date')
        !marshaller.supports(42)
        !marshaller.supports(null)
    }

    void "default formatter produces ISO-8601 UTC format with Z suffix"() {
        given:
        def marshaller = new DateMarshaller()
        def date = new Date(1718461845123L)

        when:
        def result = marshalToString(marshaller, date)

        then:
        result == '["2024-06-15T14:30:45.123Z"]'
    }

    void "default formatter keeps a zero millisecond fraction, as Spring Boot does"() {
        given:
        def marshaller = new DateMarshaller()
        // 2024-01-01T00:00:00.000 UTC
        def date = new Date(1704067200000L)

        when:
        def result = marshalToString(marshaller, date)

        then: "the output is always yyyy-MM-ddTHH:mm:ss.SSSZ"
        result == '["2024-01-01T00:00:00.000Z"]'
    }

    void "default formatter pads sub-100 milliseconds to three digits"() {
        given:
        def marshaller = new DateMarshaller()
        // 5 milliseconds past epoch second
        def date = new Date(1704067200005L)

        when:
        def result = marshalToString(marshaller, date)

        then:
        result == '["2024-01-01T00:00:00.005Z"]'
    }

    void "default formatter renders a #type.simpleName from its epoch millis"() {
        given: "java.sql.Date and java.sql.Time throw UnsupportedOperationException from toInstant()"
        def marshaller = new DateMarshaller()

        when:
        def result = marshalToString(marshaller, date)

        then: "Timestamp nanos beyond the millisecond are dropped, as Spring Boot does"
        result == "[\"${expected}\"]"

        where:
        date                                                         || expected
        new java.sql.Date(1790305200000L)                            || '2026-09-25T03:00:00.000Z'
        new Time(1759909726407L)                                     || '2025-10-08T07:48:46.407Z'
        Timestamp.from(Instant.parse('2025-10-08T07:48:46.407254Z')) || '2025-10-08T07:48:46.407Z'
        Timestamp.from(Instant.parse('2026-09-25T03:00:00Z'))        || '2026-09-25T03:00:00.000Z'

        type = date.getClass()
    }

    void "legacy formatter is used when provided"() {
        given:
        def customFormat = new SimpleDateFormat('dd/MM/yyyy')
        customFormat.setTimeZone(TimeZone.getTimeZone('UTC'))
        def marshaller = new DateMarshaller(customFormat)
        def date = new Date(1718461845123L)

        when:
        def result = marshalToString(marshaller, date)

        then:
        result == '["15/06/2024"]'
    }

    private static String marshalToString(DateMarshaller marshaller, Date date) {
        def json = new JSON()
        def stringWriter = new StringWriter()
        json.writer = new JSONWriter(stringWriter)
        json.writer.array()
        marshaller.marshalObject(date, json)
        json.writer.endArray()
        stringWriter.toString()
    }
}

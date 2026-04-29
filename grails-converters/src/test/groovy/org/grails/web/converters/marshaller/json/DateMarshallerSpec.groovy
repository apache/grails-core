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
        !marshaller.supports("not a date")
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

    void "default formatter omits fractional seconds when millis are zero (ISO_INSTANT)"() {
        given:
        def marshaller = new DateMarshaller()
        // 2024-01-01T00:00:00.000 UTC
        def date = new Date(1704067200000L)

        when:
        def result = marshalToString(marshaller, date)

        then: "ISO_INSTANT drops the fraction entirely on whole-second instants"
        result == '["2024-01-01T00:00:00Z"]'
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

    void "legacy formatter is used when provided"() {
        given:
        def customFormat = new SimpleDateFormat("dd/MM/yyyy")
        customFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
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

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
package org.grails.web.converters.marshaller.xml

import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import grails.converters.XML

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

    void "default formatter produces date in system timezone with three-digit millis"() {
        given:
        def marshaller = new DateMarshaller()
        def date = new Date(1718461845123L)
        def xml = Mock(XML)

        // Compute expected output independently using the same pattern and system timezone
        def expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")
                .withZone(ZoneId.systemDefault())
                .format(date.toInstant())

        when:
        marshaller.marshalObject(date, xml)

        then:
        1 * xml.chars(expected)
    }

    void "default formatter pads milliseconds to three digits"() {
        given:
        def marshaller = new DateMarshaller()
        // 5 milliseconds past epoch second
        def date = new Date(1704067200005L)
        def xml = Mock(XML)

        when:
        marshaller.marshalObject(date, xml)

        then:
        1 * xml.chars({ String s -> s.contains('.005') })
    }

    void "default formatter output matches expected pattern"() {
        given:
        def marshaller = new DateMarshaller()
        def date = new Date(1718461845123L)
        def xml = Mock(XML)

        when:
        marshaller.marshalObject(date, xml)

        then:
        1 * xml.chars({ String s -> s ==~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} .+/ })
    }

    void "legacy formatter is used when provided"() {
        given:
        def customFormat = new SimpleDateFormat("dd/MM/yyyy")
        customFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
        def marshaller = new DateMarshaller(customFormat)
        def date = new Date(1718461845123L)
        def xml = Mock(XML)

        when:
        marshaller.marshalObject(date, xml)

        then:
        1 * xml.chars("15/06/2024")
    }
}

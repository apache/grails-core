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

import java.time.Year

import spock.lang.Specification

import grails.converters.JSON
import org.grails.web.json.JSONWriter

class SimpleTypeMarshallerSpec extends Specification {

    void "supports returns true only for instances of the given type, including subtypes"() {
        given:
        def marshaller = new SimpleTypeMarshaller<Number>(Number.class, { it })

        expect:
        with(marshaller) {
            supports(42)
            supports(42L)
            supports(3.14d)
            !supports('42')
            !supports(null)
        }
    }

    void "marshalObject writes a String value extractor result quoted"() {
        given:
        def marshaller = new SimpleTypeMarshaller<Year>(Year.class, { Year year -> year.toString() })

        when:
        def result = marshalToString(marshaller, Year.of(2026))

        then:
        result == '["2026"]'
    }

    void "marshalObject writes a Number value extractor result unquoted"() {
        given:
        def marshaller = new SimpleTypeMarshaller<Year>(Year.class, { Year year -> year.getValue() })

        when:
        def result = marshalToString(marshaller, Year.of(2026))

        then:
        result == '[2026]'
    }

    void "marshalObject passes the object to the extractor cast to the declared type"() {
        given:
        Class<?> extractorReceivedType = null
        def marshaller = new SimpleTypeMarshaller<Year>(Year.class, { Year year ->
            extractorReceivedType = year.class
            year.toString()
        })

        when:
        marshalToString(marshaller, Year.of(2026))

        then:
        extractorReceivedType == Year
    }

    private static String marshalToString(SimpleTypeMarshaller marshaller, Object value) {
        def json = new JSON()
        def stringWriter = new StringWriter()
        json.writer = new JSONWriter(stringWriter)
        json.writer.array()
        marshaller.marshalObject(value, json)
        json.writer.endArray()
        stringWriter.toString()
    }

}

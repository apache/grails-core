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

package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeCoercionSpec extends Specification {

    OffsetDateTimeCoercion coercion = new OffsetDateTimeCoercion(["yyyy-MM-dd'T'HH:mm:ssXXX"])

    void "test serialize with an OffsetDateTime instance"() {
        given:
        OffsetDateTime dateTime = OffsetDateTime.of(2020, 1, 15, 10, 15, 30, 0, ZoneOffset.ofHours(2))

        expect:
        coercion.serialize(dateTime).is(dateTime)
    }

    void "test serialize with a string matching a configured format"() {
        expect:
        coercion.serialize('2020-01-15T10:15:30+02:00') == OffsetDateTime.of(2020, 1, 15, 10, 15, 30, 0, ZoneOffset.ofHours(2))
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(42)

        then:
        thrown(CoercingSerializeException)
    }

    void "test serialize with a non matching string throws"() {
        when:
        coercion.serialize('not a date time')

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with an OffsetDateTime instance"() {
        given:
        OffsetDateTime dateTime = OffsetDateTime.of(2020, 1, 15, 10, 15, 30, 0, ZoneOffset.ofHours(2))

        expect:
        coercion.parseValue(dateTime).is(dateTime)
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(42)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with a StringValue matching a configured format"() {
        expect:
        coercion.parseLiteral(new StringValue('2020-01-15T10:15:30+02:00')) == OffsetDateTime.of(2020, 1, 15, 10, 15, 30, 0, ZoneOffset.ofHours(2))
    }

    void "test parseLiteral with a StringValue that matches no configured format returns null"() {
        expect:
        coercion.parseLiteral(new StringValue('not a date time')) == null
    }

    void "test parseLiteral with a non StringValue returns null"() {
        expect:
        coercion.parseLiteral(new IntValue(BigInteger.ONE)) == null
    }
}

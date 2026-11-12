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

import graphql.language.BooleanValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import java.time.Instant

class InstantCoercionSpec extends Specification {

    InstantCoercion coercion = new InstantCoercion()

    void "test serialize with an Instant instance"() {
        given:
        Instant instant = Instant.ofEpochSecond(1600000000)

        expect:
        coercion.serialize(instant).is(instant)
    }

    void "test serialize with an epoch seconds number"() {
        expect:
        coercion.serialize(1600000000L) == Instant.ofEpochSecond(1600000000)
    }

    void "test serialize with an ISO-8601 string"() {
        expect:
        coercion.serialize('2020-09-13T12:26:40Z') == Instant.parse('2020-09-13T12:26:40Z')
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(true)

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with an Instant instance"() {
        given:
        Instant instant = Instant.ofEpochSecond(1600000000)

        expect:
        coercion.parseValue(instant).is(instant)
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(true)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with an IntValue"() {
        expect:
        coercion.parseLiteral(new IntValue(BigInteger.valueOf(1600000000))) == Instant.ofEpochSecond(1600000000)
    }

    void "test parseLiteral with a StringValue"() {
        expect:
        coercion.parseLiteral(new StringValue('2020-09-13T12:26:40Z')) == Instant.parse('2020-09-13T12:26:40Z')
    }

    void "test parseLiteral with an unrecognized Value type returns null"() {
        expect:
        coercion.parseLiteral(new BooleanValue(true)) == null
    }

    void "test parseLiteral with a non Value type returns null"() {
        expect:
        coercion.parseLiteral('2020-09-13T12:26:40Z') == null
    }
}

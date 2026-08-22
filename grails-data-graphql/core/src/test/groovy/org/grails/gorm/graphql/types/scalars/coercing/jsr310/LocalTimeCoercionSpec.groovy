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

import java.time.LocalTime

class LocalTimeCoercionSpec extends Specification {

    LocalTimeCoercion coercion = new LocalTimeCoercion(['HH:mm:ss'])

    void "test serialize with a LocalTime instance"() {
        given:
        LocalTime time = LocalTime.of(10, 15, 30)

        expect:
        coercion.serialize(time).is(time)
    }

    void "test serialize with a string matching a configured format"() {
        expect:
        coercion.serialize('10:15:30') == LocalTime.of(10, 15, 30)
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(42)

        then:
        thrown(CoercingSerializeException)
    }

    void "test serialize with a non matching string throws"() {
        when:
        coercion.serialize('not a time')

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with a LocalTime instance"() {
        given:
        LocalTime time = LocalTime.of(10, 15, 30)

        expect:
        coercion.parseValue(time).is(time)
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(42)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with a StringValue matching a configured format"() {
        expect:
        coercion.parseLiteral(new StringValue('10:15:30')) == LocalTime.of(10, 15, 30)
    }

    void "test parseLiteral with a StringValue that matches no configured format returns null"() {
        expect:
        coercion.parseLiteral(new StringValue('not a time')) == null
    }

    void "test parseLiteral with a non StringValue returns null"() {
        expect:
        coercion.parseLiteral(new IntValue(BigInteger.ONE)) == null
    }
}

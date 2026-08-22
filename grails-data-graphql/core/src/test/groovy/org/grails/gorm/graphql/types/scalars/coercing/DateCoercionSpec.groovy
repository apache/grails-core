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

package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.ArrayValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import java.text.SimpleDateFormat

class DateCoercionSpec extends Specification {

    DateCoercion coercion = new DateCoercion(['yyyy-MM-dd'], false)

    Date parse(String pattern, String value) {
        new SimpleDateFormat(pattern).parse(value)
    }

    void "test serialize with a Date instance"() {
        given:
        Date date = new Date()

        expect:
        coercion.serialize(date).is(date)
    }

    void "test serialize with a string matching a configured format"() {
        expect:
        coercion.serialize('2020-01-15') == parse('yyyy-MM-dd', '2020-01-15')
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(42)

        then:
        thrown(CoercingSerializeException)
    }

    void "test serialize with a non matching string throws"() {
        when:
        coercion.serialize('not a date')

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with a Date instance"() {
        given:
        Date date = new Date()

        expect:
        coercion.parseValue(date).is(date)
    }

    void "test parseValue with a string matching a configured format"() {
        expect:
        coercion.parseValue('2020-01-15') == parse('yyyy-MM-dd', '2020-01-15')
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(42)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with an IntValue"() {
        given:
        long millis = 1600000000000

        expect:
        coercion.parseLiteral(new IntValue(BigInteger.valueOf(millis))) == new Date(millis)
    }

    void "test parseLiteral with a StringValue matching a configured format"() {
        expect:
        coercion.parseLiteral(new StringValue('2020-01-15')) == parse('yyyy-MM-dd', '2020-01-15')
    }

    void "test parseLiteral with a StringValue that matches no configured format returns null"() {
        expect:
        coercion.parseLiteral(new StringValue('not a date')) == null
    }

    void "test parseLiteral with an unsupported value type returns null"() {
        expect:
        coercion.parseLiteral(new ArrayValue([])) == null
    }

    void "test parseLiteral falls back to a later format when an earlier one fails to match"() {
        given:
        DateCoercion multiFormat = new DateCoercion(['yyyy/MM/dd', 'yyyy-MM-dd'], false)

        expect:
        multiFormat.parseLiteral(new StringValue('2020-01-15')) == parse('yyyy-MM-dd', '2020-01-15')
    }

    void "test parseLiteral with a StringValue returns null instead of throwing when no formats are configured"() {
        given:
        DateCoercion noFormats = new DateCoercion([], false)

        expect:
        noFormats.parseLiteral(new StringValue('2020-01-15')) == null
    }

    void "test serialize with a string and no configured formats throws a CoercingSerializeException instead of an NPE"() {
        given:
        DateCoercion noFormats = new DateCoercion([], false)

        when:
        noFormats.serialize('2020-01-15')

        then:
        thrown(CoercingSerializeException)
    }
}

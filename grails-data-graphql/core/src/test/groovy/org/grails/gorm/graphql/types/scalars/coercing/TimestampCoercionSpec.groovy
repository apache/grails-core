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

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.ArrayValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import java.sql.Timestamp

class TimestampCoercionSpec extends Specification {

    TimestampCoercion coercion = new TimestampCoercion()

    void "test serialize with a Timestamp instance"() {
        given:
        Timestamp timestamp = Timestamp.valueOf('2020-01-15 10:15:30')

        expect:
        coercion.serialize(timestamp, GraphQLContext.default, Locale.default).is(timestamp)
    }

    void "test serialize with a valid timestamp string"() {
        expect:
        coercion.serialize('2020-01-15 10:15:30', GraphQLContext.default, Locale.default) == Timestamp.valueOf('2020-01-15 10:15:30')
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(true, GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingSerializeException)
    }

    void "test serialize with an invalid timestamp string throws"() {
        when:
        coercion.serialize('not a timestamp', GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with a Timestamp instance"() {
        given:
        Timestamp timestamp = Timestamp.valueOf('2020-01-15 10:15:30')

        expect:
        coercion.parseValue(timestamp, GraphQLContext.default, Locale.default).is(timestamp)
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(true, GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with an IntValue"() {
        given:
        long millis = 1600000000000

        expect:
        coercion.parseLiteral(new IntValue(BigInteger.valueOf(millis)), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == new Timestamp(millis)
    }

    void "test parseLiteral with a valid StringValue"() {
        expect:
        coercion.parseLiteral(new StringValue('2020-01-15 10:15:30'), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == Timestamp.valueOf('2020-01-15 10:15:30')
    }

    void "test parseLiteral with an invalid StringValue returns null"() {
        expect:
        coercion.parseLiteral(new StringValue('not a timestamp'), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == null
    }

    void "test parseLiteral with an unsupported value type returns null"() {
        expect:
        coercion.parseLiteral(new ArrayValue([]), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == null
    }
}

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
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

class TimeZoneCoercionSpec extends Specification {

    TimeZoneCoercion coercion = new TimeZoneCoercion()

    void "test serialize with a TimeZone instance"() {
        given:
        TimeZone timeZone = TimeZone.getTimeZone('America/New_York')

        expect:
        coercion.serialize(timeZone, GraphQLContext.default, Locale.default).is(timeZone)
    }

    void "test serialize with a time zone id string"() {
        expect:
        coercion.serialize('America/New_York', GraphQLContext.default, Locale.default) == TimeZone.getTimeZone('America/New_York')
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(42, GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with a TimeZone instance"() {
        given:
        TimeZone timeZone = TimeZone.getTimeZone('Europe/Paris')

        expect:
        coercion.parseValue(timeZone, GraphQLContext.default, Locale.default).is(timeZone)
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(42, GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with a StringValue"() {
        expect:
        coercion.parseLiteral(new StringValue('Europe/Paris'), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == TimeZone.getTimeZone('Europe/Paris')
    }

    void "test parseLiteral with a non StringValue returns null"() {
        expect:
        coercion.parseLiteral(new IntValue(BigInteger.ONE), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == null
    }
}

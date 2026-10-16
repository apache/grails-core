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

class URICoercionSpec extends Specification {

    URICoercion coercion = new URICoercion()

    void "test serialize with a URI instance"() {
        given:
        URI uri = new URI('https://grails.apache.org')

        expect:
        coercion.serialize(uri, GraphQLContext.default, Locale.default).is(uri)
    }

    void "test serialize with a valid uri string"() {
        expect:
        coercion.serialize('https://grails.apache.org', GraphQLContext.default, Locale.default) == new URI('https://grails.apache.org')
    }

    void "test serialize with an unsupported type throws"() {
        when:
        coercion.serialize(42, GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingSerializeException)
    }

    void "test serialize with an invalid uri string throws"() {
        when:
        coercion.serialize(' ', GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingSerializeException)
    }

    void "test parseValue with a URI instance"() {
        given:
        URI uri = new URI('https://grails.apache.org')

        expect:
        coercion.parseValue(uri, GraphQLContext.default, Locale.default).is(uri)
    }

    void "test parseValue with an unsupported type throws"() {
        when:
        coercion.parseValue(42, GraphQLContext.default, Locale.default)

        then:
        thrown(CoercingParseValueException)
    }

    void "test parseLiteral with a valid StringValue"() {
        expect:
        coercion.parseLiteral(new StringValue('https://grails.apache.org'), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == new URI('https://grails.apache.org')
    }

    void "test parseLiteral with an invalid StringValue returns null"() {
        expect:
        coercion.parseLiteral(new StringValue(' '), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == null
    }

    void "test parseLiteral with a non StringValue returns null"() {
        expect:
        coercion.parseLiteral(new IntValue(BigInteger.ONE), CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == null
    }
}

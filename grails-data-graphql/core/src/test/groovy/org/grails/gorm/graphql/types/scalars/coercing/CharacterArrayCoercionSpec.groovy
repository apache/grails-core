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
import graphql.language.StringValue
import spock.lang.Specification

class CharacterArrayCoercionSpec extends Specification {

    void "test parseLiteral"() {
        given:
        def value = new ArrayValue([new StringValue('x'), new StringValue('y'), new StringValue('z'), new StringValue('a')])
        CharacterArrayCoercion coercion = new CharacterArrayCoercion()

        when:
        def result = coercion.parseLiteral(value)

        then:
        result == ['x', 'y', 'z', 'a'] as Character[]

        when: 'the values are too long for Character'
        result = coercion.parseLiteral(new ArrayValue([new StringValue('xy'), new StringValue('a')]))

        then:
        result == [null, 'a'] as Character[]
    }
}

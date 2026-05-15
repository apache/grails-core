/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.query

import spock.lang.Specification

class PropertyReferenceSpec extends Specification {

    def 'multiply returns a PropertyArithmetic with MULTIPLY operator'() {
        given:
        def ref = new PropertyReference('price')

        when:
        def result = ref.multiply(10)

        then:
        result instanceof PropertyArithmetic
        result.propertyName == 'price'
        result.operator == PropertyArithmetic.Operator.MULTIPLY
        result.operand == 10
    }

    def 'plus returns a PropertyArithmetic with ADD operator'() {
        given:
        def ref = new PropertyReference('salary')

        when:
        def result = ref.plus(500)

        then:
        result instanceof PropertyArithmetic
        result.propertyName == 'salary'
        result.operator == PropertyArithmetic.Operator.ADD
        result.operand == 500
    }

    def 'minus returns a PropertyArithmetic with SUBTRACT operator'() {
        given:
        def ref = new PropertyReference('balance')

        when:
        def result = ref.minus(100)

        then:
        result instanceof PropertyArithmetic
        result.propertyName == 'balance'
        result.operator == PropertyArithmetic.Operator.SUBTRACT
        result.operand == 100
    }

    def 'div returns a PropertyArithmetic with DIVIDE operator'() {
        given:
        def ref = new PropertyReference('total')

        when:
        def result = ref.div(3)

        then:
        result instanceof PropertyArithmetic
        result.propertyName == 'total'
        result.operator == PropertyArithmetic.Operator.DIVIDE
        result.operand == 3
    }

    def 'Groovy * operator delegates to multiply'() {
        given:
        def ref = new PropertyReference('price')

        when:
        def result = ref * 10

        then:
        result instanceof PropertyArithmetic
        result.operator == PropertyArithmetic.Operator.MULTIPLY
        result.operand == 10
    }
}

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
package org.grails.datastore.mapping.query.criteria

import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

class FunctionOperandSpec extends Specification {

    private static final conversion = new DefaultConversionService()

    @Unroll
    void "#number (#type) is #whole as a whole int"() {
        expect:
        FunctionCallingCriterion.isWholeInt(number) == whole
        FunctionCallingCriterion.isFractional(number) == !whole

        where:
        number                                | whole
        (byte) 3                              | true
        (short) -3                            | true
        Integer.MAX_VALUE                     | true
        Integer.MIN_VALUE                     | true
        (long) Integer.MAX_VALUE              | true
        (long) Integer.MIN_VALUE              | true
        Integer.MAX_VALUE + 1L                | false
        Integer.MIN_VALUE - 1L                | false
        BigInteger.valueOf(Integer.MAX_VALUE) | true
        BigInteger.valueOf(Integer.MIN_VALUE) | true
        BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)      | false
        BigInteger.valueOf(Integer.MIN_VALUE).subtract(BigInteger.ONE) | false
        BigInteger.TWO.pow(64)                | false
        BigInteger.TWO.pow(64).negate()       | false
        0G                                    | true
        0.0G                                  | true
        3.0G                                  | true
        3.5G                                  | false
        new BigDecimal('3.0000000000000000001') | false
        new BigDecimal('2147483647.0')        | true
        new BigDecimal('2147483648.0')        | false
        new BigDecimal('-2147483648.0')       | true
        new BigDecimal('-2147483649.0')       | false
        3.0d                                  | true
        3.5d                                  | false
        0.0d                                  | true
        -0.0d                                 | true
        1e-20d                                | false
        2147483647.0d                         | true
        2147483648.0d                         | false
        -2147483648.0d                        | true
        -2147483649.0d                        | false
        3.0f                                  | true
        3.5f                                  | false
        Double.NaN                            | false
        Double.POSITIVE_INFINITY              | false
        Double.NEGATIVE_INFINITY              | false
        Float.NaN                             | false

        type = number.getClass().simpleName
    }

    void "a value that is not a number is not fractional"() {
        expect:
        !FunctionCallingCriterion.isFractional('3.5')
        !FunctionCallingCriterion.isFractional(null)
        !FunctionCallingCriterion.isFractional(new Date())
    }

    @Unroll
    void "#function takes #value as #expected (#expected.class.simpleName) with a conversion service"() {
        when:
        def operand = FunctionCallingCriterion.convertOperand(conversion, function, value)

        then:
        operand == expected
        operand.getClass() == expected.getClass()

        where:
        function | value                   | expected
        'length' | 3                       | 3
        'length' | '3'                     | 3
        'length' | 3L                      | 3
        'length' | 3.0G                    | 3
        'length' | 3.0d                    | 3
        'length' | "${3}"                  | 3
        'length' | 3.5d                    | 3.5d
        'length' | 3.5G                    | 3.5G
        'length' | 5000000000L             | 5000000000L
        'year'   | '2011'                  | 2011
        'month'  | 12L                     | 12
        'upper'  | 'A'                     | 'A'
        'upper'  | "A${'B'}"               | 'AB'
        'upper'  | 3.5d                    | '3.5'
        'lower'  | 7                       | '7'
        'trim'   | 3L                      | '3'
    }

    void "no operand is a null operand, whatever the function and the service"() {
        expect:
        FunctionCallingCriterion.convertOperand(conversion, 'length', null) == null
        FunctionCallingCriterion.convertOperand(null, 'upper', null) == null
    }

    @Unroll
    void "without a conversion service #function leaves #value as #expected"() {
        when:
        def operand = FunctionCallingCriterion.convertOperand(null, function, value)

        then:
        operand == expected
        operand.getClass() == expected.getClass()

        where:
        function | value      | expected
        'length' | 3          | 3
        'length' | '3'        | '3'
        'length' | "${3}"     | '3'
        'length' | 3.5d       | 3.5d
        'upper'  | 'A'        | 'A'
        'upper'  | "A${'B'}"  | 'AB'
        'upper'  | 3.5d       | '3.5'
        'lower'  | 7          | '7'
    }
}

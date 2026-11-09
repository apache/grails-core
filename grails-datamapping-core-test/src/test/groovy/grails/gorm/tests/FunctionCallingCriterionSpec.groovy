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
package grails.gorm.tests

import spock.lang.Specification

import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion

class FunctionCallingCriterionSpec extends Specification {

    void "#value is a whole number an int holds exactly: #expected"() {
        expect:
        FunctionCallingCriterion.isWholeInt(value as Number) == expected
        FunctionCallingCriterion.isFractional(value) == !expected

        where:
        value                                  || expected
        0                                      || true
        3                                      || true
        (short) 3                              || true
        (byte) 3                               || true
        -3L                                    || true
        3.0                                    || true
        3.0d                                   || true
        3.0f                                   || true
        new BigDecimal('3.000')                || true
        new BigDecimal('0.0')                  || true
        new BigInteger('3')                    || true
        Integer.MAX_VALUE                      || true
        Integer.MIN_VALUE                      || true
        3.5                                    || false
        3.5d                                   || false
        3.5f                                   || false
        -3.5                                   || false
        3.0000000000000000001                  || false
        new BigDecimal('3.0000000000000000001') || false
        3.0000000000000001d                    || true
        Math.nextUp(3.0d)                      || false
        ((long) Integer.MAX_VALUE) + 1         || false
        ((long) Integer.MIN_VALUE) - 1         || false
        new BigInteger('3000000000')           || false
        new BigDecimal('3e10')                 || false
        Double.NaN                             || false
        Double.POSITIVE_INFINITY               || false
    }

    void "an operand that is not a number is not fractional"() {
        expect:
        !FunctionCallingCriterion.isFractional(null)
        !FunctionCallingCriterion.isFractional('3.5')
    }

    void "only the functions the where DSL accepts are supported"() {
        expect:
        FunctionCallingCriterion.SUPPORTED_FUNCTIONS.containsAll(['lower', 'upper', 'trim', 'length', 'year', 'month', 'day', 'hour', 'minute', 'second'])

        when:
        FunctionCallingCriterion.requireSupported('lower) or (1=1')

        then:
        thrown(org.springframework.dao.InvalidDataAccessResourceUsageException)
    }
}

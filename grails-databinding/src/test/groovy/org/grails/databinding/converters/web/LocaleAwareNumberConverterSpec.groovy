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
package org.grails.databinding.converters.web

import spock.lang.Specification
import spock.lang.Unroll

class LocaleAwareNumberConverterSpec extends Specification {

    private Locale defaultLocale

    void setup() {
        defaultLocale = Locale.default
    }

    void cleanup() {
        Locale.default = defaultLocale
    }

    @Unroll
    void 'converts #targetType.simpleName value with ASCII minus for #locale'() {
        given:
        Locale.default = locale
        converter.targetType = targetType

        expect:
        converter.convert(value) == expectedValue

        where:
        locale                 | converter                                  | targetType | value     | expectedValue
        new Locale('nb', 'NO') | new LocaleAwareNumberConverter()           | Long       | '-1'      | -1L
        new Locale('nb', 'NO') | new LocaleAwareBigDecimalConverter()       | BigDecimal | '-1,25'   | new BigDecimal('-1.25')
        new Locale('nb', 'NO') | new LocaleAwareBigDecimalConverter()       | BigInteger | '-1'      | new BigInteger('-1')
        Locale.US              | new LocaleAwareNumberConverter()           | Long       | '-1'      | -1L
        Locale.US              | new LocaleAwareBigDecimalConverter()       | BigDecimal | '-1.25'   | new BigDecimal('-1.25')
        Locale.US              | new LocaleAwareBigDecimalConverter()       | BigInteger | '-1'      | new BigInteger('-1')
    }
}

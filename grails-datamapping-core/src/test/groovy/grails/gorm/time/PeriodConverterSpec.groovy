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
package grails.gorm.time

import java.lang.reflect.Method
import java.time.Period

import groovy.transform.Generated

import spock.lang.Shared
import spock.lang.Specification

class PeriodConverterSpec extends Specification implements PeriodConverter {

    @Shared
    Period period

    void setupSpec() {
        period = Period.of(1941, 1, 5)
    }

    void 'test convert to long'() {
        expect:
        convert(period) == 'P1941Y1M5D'
    }

    void 'test convert from long'() {
        expect:
        convert('P1941Y1M5D') == period
    }

    void 'test that all PeriodConverter trait methods are marked as Generated'() {

        expect: 'all PeriodConverter methods are marked as Generated on implementation class'
        PeriodConverter.methods.each { Method traitMethod ->
            assert PeriodConverterSpec.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

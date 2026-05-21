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
package grails.gorm.time

import java.lang.reflect.Method
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import groovy.transform.Generated

import spock.lang.Shared
import spock.lang.Specification

class LocalDateTimeConverterSpec extends Specification implements LocalDateTimeConverter {

    @Shared
    LocalDateTime localDateTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone('America/Los_Angeles')
        localDateTime = LocalDateTime.of(
                LocalDate.of(1941, 1, 5),
                LocalTime.of(6,5,4,3)
        )
    }

    void 'test convert to long'() {
        expect:
        convert(localDateTime) == -914781296000L
    }

    void 'test convert from long'() {
        expect:
        convert(-914781296000L) == localDateTime.withNano(0)
    }

    void 'test that all LocalDateTimeConverter/TemporalConverter trait methods are marked as Generated'() {

        expect: 'all LocalDateTimeConverter methods are marked as Generated on implementation class'
        LocalDateTimeConverter.methods.each { Method traitMethod ->
            assert LocalDateTimeConverterSpec.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }

        and: 'all TemporalConverter methods are marked as Generated on implementation class'
        TemporalConverter.methods.each { Method traitMethod ->
            assert LocalDateTimeConverterSpec.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

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
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

import groovy.transform.Generated

import spock.lang.Shared
import spock.lang.Specification

class OffsetTimeConverterSpec extends Specification implements OffsetTimeConverter {

    @Shared
    OffsetTime offsetTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone('America/Los_Angeles')
        offsetTime = OffsetTime.of(
                LocalTime.of(6,5,4,3),
                ZoneOffset.ofHours(-6)
        )
    }

    void 'test convert to long'() {
        expect:
        convert(offsetTime) == 43504000000003L
    }

    void 'test convert from long'() {
        given:
        def converted = convert(43504000000003L)

        expect:
        converted == offsetTime.withOffsetSameInstant(ZoneOffset.ofHours(-7)) ||
                converted == offsetTime.withOffsetSameInstant(ZoneOffset.ofHours(-8))
    }

    void 'test that all OffsetTimeConverter/TemporalConverter trait methods are marked as Generated'() {

        expect: 'all OffsetTimeConverter methods are marked as Generated on implementation class'
        OffsetTimeConverter.methods.each { Method traitMethod ->
            assert OffsetTimeConverterSpec.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }

        and: 'all TemporalConverter methods are marked as Generated on implementation class'
        TemporalConverter.methods.each { Method traitMethod ->
            assert OffsetTimeConverterSpec.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

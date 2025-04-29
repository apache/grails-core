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

import spock.lang.Shared
import spock.lang.Specification

import java.time.*

class OffsetDateTimeConverterSpec extends Specification implements OffsetDateTimeConverter {

    @Shared
    OffsetDateTime offsetDateTime

    void setupSpec() {
        TimeZone.default = TimeZone.getTimeZone("America/Los_Angeles")
        LocalTime localTime = LocalTime.of(6,5,4,3)
        LocalDate localDate = LocalDate.of(1941, 1, 5)
        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime)
        offsetDateTime = OffsetDateTime.of(localDateTime, ZoneOffset.ofHours(-6))
    }

    void "test convert to long"() {
        expect:
        convert(offsetDateTime) == -914759696000L
    }

    void "test convert from long"() {
        given:
        OffsetDateTime converted = convert(-914759696000L)

        expect:
        converted == offsetDateTime.withNano(0).withOffsetSameInstant(ZoneOffset.ofHours(-7)) ||
                converted == offsetDateTime.withNano(0).withOffsetSameInstant(ZoneOffset.ofHours(-8))
    }

}
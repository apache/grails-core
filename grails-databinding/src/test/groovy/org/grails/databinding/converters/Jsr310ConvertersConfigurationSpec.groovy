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
package org.grails.databinding.converters

import org.grails.plugins.databinding.AbstractDataBindingGrailsPlugin
import spock.lang.Shared
import spock.lang.Specification

import java.time.*

class Jsr310ConvertersConfigurationSpec extends Specification {

    @Shared
    Jsr310ConvertersConfiguration config = new Jsr310ConvertersConfiguration(formatStrings: AbstractDataBindingGrailsPlugin.DEFAULT_DATE_FORMATS)

    @Shared
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    void setupSpec() {
        calendar.set(1941, 0, 5, 8, 0, 0)
    }

    void "localDateTimeConverter"() {
        given:
        def converter = config.localDateTimeConverter()

        expect:
        converter.targetType == LocalDateTime
        converter.convert("1941-01-05T08:00:00", AbstractDataBindingGrailsPlugin.DEFAULT_JSR310_LOCAL_DATE_TIME_FORMAT) instanceof LocalDateTime
    }

    void "localDateTimeValueConverter"() {
        def converter = config.localDateTimeValueConverter()

        expect:
        converter.targetType == LocalDateTime
        converter.canConvert('')
        converter.convert("1941-01-05T08:00:00") instanceof LocalDateTime
    }

    void "localDateTimeStructuredBindingEditor"() {
        given:
        def converter = config.localDateTimeStructuredBindingEditor()
        LocalDateTime date = converter.getDate(calendar)

        expect:
        converter.targetType == LocalDateTime
        date.year == 1941
        date.month == Month.JANUARY
        date.dayOfMonth == 5
        date.hour == 8
        date.minute == 0
        date.second == 0
    }

    void "localDateConverter"() {
        given:
        def converter = config.localDateConverter()

        expect:
        converter.targetType == LocalDate
        converter.convert("1941-01-05", AbstractDataBindingGrailsPlugin.DEFAULT_JSR310_LOCAL_DATE_FORMAT) instanceof LocalDate
    }

    void "localDateValueConverter"() {
        def converter = config.localDateValueConverter()

        expect:
        converter.targetType == LocalDate
        converter.canConvert('')
        converter.convert("1941-01-05") instanceof LocalDate
    }

    void "localDateStructuredBindingEditor"() {
        given:
        def converter = config.localDateStructuredBindingEditor()
        LocalDate date = converter.getDate(calendar)

        expect:
        converter.targetType == LocalDate
        date.year == 1941
        date.month == Month.JANUARY
        date.dayOfMonth == 5
    }

    void "localTimeConverter"() {
        given:
        def converter = config.localTimeConverter()

        expect:
        converter.targetType == LocalTime
        converter.convert("08:00:00", AbstractDataBindingGrailsPlugin.DEFAULT_JSR310_LOCAL_TIME_FORMAT) instanceof LocalTime
    }

    void "localTimeValueConverter"() {
        def converter = config.localTimeValueConverter()

        expect:
        converter.targetType == LocalTime
        converter.canConvert('')
        converter.convert("08:00:00") instanceof LocalTime
    }

    void "localTimeStructuredBindingEditor"() {
        given:
        def converter = config.localTimeStructuredBindingEditor()
        LocalTime date = converter.getDate(calendar)

        expect:
        converter.targetType == LocalTime
        date.hour == 8
        date.minute == 0
        date.second == 0
    }

    void "offsetTimeConverter"() {
        given:
        def converter = config.offsetTimeConverter()

        expect:
        converter.targetType == OffsetTime
        converter.convert("08:00:00+0000", AbstractDataBindingGrailsPlugin.DEFAULT_JSR310_OFFSET_TIME_FORMAT) instanceof OffsetTime
    }

    void "offsetTimeValueConverter"() {
        def converter = config.offsetTimeValueConverter()

        expect:
        converter.targetType == OffsetTime
        converter.canConvert('')
        converter.convert("08:00:00+0000") instanceof OffsetTime
    }

    void "offsetTimeStructuredBindingEditor"() {
        given:
        def converter = config.offsetTimeStructuredBindingEditor()
        OffsetTime date = converter.getDate(calendar)

        expect:
        converter.targetType == OffsetTime
        date.hour == 8
        date.minute == 0
        date.second == 0
    }

    void "offsetDateTimeConverter"() {
        given:
        def converter = config.offsetDateTimeConverter()

        expect:
        converter.targetType == OffsetDateTime
        converter.convert("1941-01-05T08:00:00+0000", AbstractDataBindingGrailsPlugin.DEFAULT_JSR310_OFFSET_ZONED_DATE_TIME_FORMAT) instanceof OffsetDateTime
    }

    void "offsetDateTimeValueConverter"() {
        def converter = config.offsetDateTimeValueConverter()

        expect:
        converter.targetType == OffsetDateTime
        converter.canConvert('')
        converter.convert("1941-01-05T08:00:00+0000") instanceof OffsetDateTime
    }

    void "offsetDateTimeStructuredBindingEditor"() {
        given:
        def converter = config.offsetDateTimeStructuredBindingEditor()
        OffsetDateTime date = converter.getDate(calendar)

        expect:
        converter.targetType == OffsetDateTime
        date.year == 1941
        date.month == Month.JANUARY
        date.dayOfMonth == 5
        date.hour == 8
        date.minute == 0
        date.second == 0
    }

    void "zonedDateTimeConverter"() {
        given:
        def converter = config.zonedDateTimeConverter()

        expect:
        converter.targetType == ZonedDateTime
        converter.convert("1941-01-05T08:00:00+0000", AbstractDataBindingGrailsPlugin.DEFAULT_JSR310_OFFSET_ZONED_DATE_TIME_FORMAT) instanceof ZonedDateTime
    }

    void "zonedDateTimeValueConverter"() {
        def converter = config.zonedDateTimeValueConverter()

        expect:
        converter.targetType == ZonedDateTime
        converter.canConvert('')
        converter.convert("1941-01-05T08:00:00+0000") instanceof ZonedDateTime
    }

    void "zonedDateTimeStructuredBindingEditor"() {
        given:
        def converter = config.zonedDateTimeStructuredBindingEditor()
        ZonedDateTime date = converter.getDate(calendar)

        expect:
        converter.targetType == ZonedDateTime
        date.year == 1941
        date.month == Month.JANUARY
        date.dayOfMonth == 5
        date.hour == 8
        date.minute == 0
        date.second == 0
    }

    void "periodValueConverter"() {
        def converter = config.periodValueConverter()

        expect:
        converter.targetType == Period
        converter.canConvert('')
        converter.convert("P2D") instanceof Period
    }

    void "instantStringValueConverter"() {
        given:
        def converter = config.instantStringValueConverter()

        expect:
        converter.targetType == Instant
        converter.canConvert("2021-05-11T22:55:41.017Z")
        converter.canConvert(new StringBuilder("2021-05-11T22:55:41.017Z"))
        !converter.canConvert(3)
        converter.convert("2021-05-11T22:55:41.017Z") instanceof Instant
        converter.convert(new StringBuilder("2021-05-11T22:55:41.017Z")) instanceof Instant
    }

    void "instantValueConverter"() {
        def converter = config.instantValueConverter()

        expect:
        converter.targetType == Instant
        converter.canConvert(2)
        !converter.canConvert("23")
        converter.convert(1) instanceof Instant
    }
}

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
package org.grails.datastore.bson.codecs

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.Decimal128
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Covers the simple {@link org.bson.codecs.Codec} implementations in this package: the
 * temporal codecs are thin adapters over already exhaustively-tested {@code *BsonConverter}
 * traits (see the specs alongside those traits in {@code codecs/temporal}), so what these
 * tests verify for them is the adapter's own behaviour - that decode/encode delegate to the
 * converter and that getEncoderClass reports the right type. BigDecimalCodec and
 * BigIntegerCodec have no converter to delegate to, so their conversion logic is tested here
 * directly.
 */
class SimpleCodecsSpec extends Specification {

    @Unroll
    void "#codec.class.simpleName decodes by delegating a single read to the reader"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            _(*_) >> rawValue
        }

        when:
        def result = codec.decode(reader, DecoderContext.builder().build())

        then:
        result != null
        result.class == encoderClass

        where:
        codec                 | encoderClass   | rawValue
        new InstantCodec()    | Instant        | 100L
        new LocalDateCodec()  | LocalDate      | -914803200000L
        new LocalDateTimeCodec() | LocalDateTime | -914781296000L
        new LocalTimeCodec()  | LocalTime      | 21904000000003L
        new OffsetDateTimeCodec() | OffsetDateTime | -914759696000L
        new OffsetTimeCodec() | OffsetTime     | 43504000000003L
        new PeriodCodec()     | Period         | 'P1941Y1M5D'
        new ZonedDateTimeCodec() | ZonedDateTime | -914759696000L
    }

    void "InstantCodec encodes by writing epoch millis"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new InstantCodec().encode(writer, Instant.ofEpochMilli(100L), EncoderContext.builder().build())

        then:
        1 * writer.writeDateTime(100L)
    }

    void "InstantCodec reports Instant as its encoder class"() {
        expect:
        new InstantCodec().encoderClass == Instant
    }

    void "LocalDateCodec encodes by writing a date-time value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new LocalDateCodec().encode(writer, LocalDate.of(1941, 1, 5), EncoderContext.builder().build())

        then:
        1 * writer.writeDateTime(_ as Long)
    }

    void "LocalDateCodec reports LocalDate as its encoder class"() {
        expect:
        new LocalDateCodec().encoderClass == LocalDate
    }

    void "LocalDateTimeCodec encodes by writing a date-time value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new LocalDateTimeCodec().encode(writer, LocalDateTime.of(1941, 1, 5, 10, 0), EncoderContext.builder().build())

        then:
        1 * writer.writeDateTime(_ as Long)
    }

    void "LocalDateTimeCodec reports LocalDateTime as its encoder class"() {
        expect:
        new LocalDateTimeCodec().encoderClass == LocalDateTime
    }

    void "LocalTimeCodec encodes by writing a nanosecond-of-day value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new LocalTimeCodec().encode(writer, LocalTime.of(6, 5), EncoderContext.builder().build())

        then:
        1 * writer.writeInt64(_ as Long)
    }

    void "LocalTimeCodec reports LocalTime as its encoder class"() {
        expect:
        new LocalTimeCodec().encoderClass == LocalTime
    }

    void "OffsetDateTimeCodec encodes by writing a date-time value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new OffsetDateTimeCodec().encode(writer, OffsetDateTime.of(1941, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC), EncoderContext.builder().build())

        then:
        1 * writer.writeDateTime(_ as Long)
    }

    void "OffsetDateTimeCodec reports OffsetDateTime as its encoder class"() {
        expect:
        new OffsetDateTimeCodec().encoderClass == OffsetDateTime
    }

    void "OffsetTimeCodec encodes by writing a nanosecond-of-day value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new OffsetTimeCodec().encode(writer, OffsetTime.of(12, 5, 4, 3, ZoneOffset.UTC), EncoderContext.builder().build())

        then:
        1 * writer.writeInt64(_ as Long)
    }

    void "OffsetTimeCodec reports OffsetTime as its encoder class"() {
        expect:
        new OffsetTimeCodec().encoderClass == OffsetTime
    }

    void "PeriodCodec encodes by writing its ISO-8601 string form"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Period period = Period.of(1941, 1, 5)

        when:
        new PeriodCodec().encode(writer, period, EncoderContext.builder().build())

        then:
        1 * writer.writeString(period.toString())
    }

    void "PeriodCodec reports Period as its encoder class"() {
        expect:
        new PeriodCodec().encoderClass == Period
    }

    void "ZonedDateTimeCodec encodes by writing a date-time value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new ZonedDateTimeCodec().encode(writer, ZonedDateTime.of(1941, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC), EncoderContext.builder().build())

        then:
        1 * writer.writeDateTime(_ as Long)
    }

    void "ZonedDateTimeCodec reports ZonedDateTime as its encoder class"() {
        expect:
        new ZonedDateTimeCodec().encoderClass == ZonedDateTime
    }

    void "BigDecimalCodec decodes a Decimal128 to its BigDecimal value"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            readDecimal128() >> new Decimal128(new BigDecimal('42.50'))
        }

        expect:
        new BigDecimalCodec().decode(reader, DecoderContext.builder().build()) == new BigDecimal('42.50')
    }

    void "BigDecimalCodec encodes by writing a Decimal128 of the value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new BigDecimalCodec().encode(writer, new BigDecimal('42.50'), EncoderContext.builder().build())

        then:
        1 * writer.writeDecimal128(new Decimal128(new BigDecimal('42.50')))
    }

    void "BigDecimalCodec reports BigDecimal as its encoder class"() {
        expect:
        new BigDecimalCodec().encoderClass == BigDecimal
    }

    void "BigIntegerCodec decodes a Decimal128 to its BigInteger value"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            readDecimal128() >> new Decimal128(new BigDecimal('42'))
        }

        expect:
        new BigIntegerCodec().decode(reader, DecoderContext.builder().build()) == 42G
    }

    void "BigIntegerCodec encodes by writing a Decimal128 of the value"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new BigIntegerCodec().encode(writer, 42G, EncoderContext.builder().build())

        then:
        1 * writer.writeDecimal128(new Decimal128(new BigDecimal(42G)))
    }

    void "BigIntegerCodec reports BigInteger as its encoder class"() {
        expect:
        new BigIntegerCodec().encoderClass == BigInteger
    }
}

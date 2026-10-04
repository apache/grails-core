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
package org.grails.datastore.bson.codecs.encoders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime

import org.bson.BsonWriter
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.model.PersistentProperty

/**
 * Each of these encoders is a thin {@code SimpleEncoder.TypeEncoder} adapter over an already
 * exhaustively-tested {@code *BsonConverter} trait (see the specs alongside those traits in
 * {@code codecs/temporal}), so what these tests verify is the adapter's own behaviour: that the
 * value is cast and handed straight to the converter's write method, which reaches the writer
 * through the method appropriate to that type.
 */
class TemporalTypeEncodersSpec extends Specification {

    PersistentProperty property = Stub(PersistentProperty)

    @Unroll
    void "#encoder.class.simpleName writes the value via #writerMethod"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        encoder.encode(writer, property, value)

        then:
        1 * writer."$writerMethod"(_)

        where:
        encoder                     | value                                                              | writerMethod
        new InstantEncoder()        | Instant.ofEpochMilli(100L)                                        | 'writeDateTime'
        new LocalDateEncoder()      | LocalDate.of(1941, 1, 5)                                          | 'writeDateTime'
        new LocalDateTimeEncoder()  | LocalDateTime.of(1941, 1, 5, 10, 0)                               | 'writeDateTime'
        new LocalTimeEncoder()      | LocalTime.of(6, 5)                                                | 'writeInt64'
        new OffsetDateTimeEncoder() | OffsetDateTime.of(1941, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC)        | 'writeDateTime'
        new OffsetTimeEncoder()     | OffsetTime.of(12, 5, 4, 3, ZoneOffset.UTC)                        | 'writeInt64'
        new PeriodEncoder()         | Period.of(1941, 1, 5)                                             | 'writeString'
        new ZonedDateTimeEncoder()  | ZonedDateTime.of(1941, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC)         | 'writeDateTime'
    }
}

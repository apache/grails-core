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
package org.grails.datastore.bson.codecs.decoders

import org.bson.BsonReader
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentProperty

/**
 * Each of these decoders is a thin {@code SimpleDecoder.TypeDecoder} adapter over an already
 * exhaustively-tested {@code *BsonConverter} trait (see the specs alongside those traits in
 * {@code codecs/temporal}), so what these tests verify is the adapter's own behaviour: that the
 * converted value is set on the entity, without conversion, under the property's name.
 */
class TemporalTypeDecodersSpec extends Specification {

    @Unroll
    void "#decoder.class.simpleName sets the converted value on the entity without conversion"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            _(*_) >> rawValue
        }
        PersistentProperty property = Stub(PersistentProperty) {
            getName() >> 'someProperty'
        }
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', { it != null })

        where:
        decoder                     | rawValue
        new InstantDecoder()        | 100L
        new LocalDateDecoder()      | -914803200000L
        new LocalDateTimeDecoder()  | -914781296000L
        new LocalTimeDecoder()      | 21904000000003L
        new OffsetDateTimeDecoder() | -914759696000L
        new OffsetTimeDecoder()     | 43504000000003L
        new PeriodDecoder()         | 'P1941Y1M5D'
        new ZonedDateTimeDecoder()  | -914759696000L
    }
}

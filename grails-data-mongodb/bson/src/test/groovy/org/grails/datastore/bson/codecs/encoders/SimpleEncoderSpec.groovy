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

import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.Binary
import org.bson.types.ObjectId
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Simple

class SimpleEncoderSpec extends Specification {

    SimpleEncoder encoder = new SimpleEncoder()
    EncoderContext encoderContext = EncoderContext.builder().build()
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    EntityAccess entityAccess = Mock(EntityAccess)

    private Simple propertyFor(Class type) {
        Stub(Simple) {
            getType() >> type
            getName() >> 'someProperty'
        }
    }

    void "writes the property name before the value, using the property's name as the target key"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        encoder.encode(writer, propertyFor(String), 'value', entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
    }

    @Unroll
    void "a #type.simpleName value is written via #writerMethod"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        encoder.encode(writer, propertyFor(type), value, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer."$writerMethod"(expected)

        where:
        type       | value                | writerMethod      | expected
        String     | 'hello'              | 'writeString'     | 'hello'
        Integer    | 42                   | 'writeInt32'      | 42
        Short      | (short) 4            | 'writeInt32'      | 4
        Byte       | (byte) 4             | 'writeInt32'      | 4
        Double     | 3.14d                | 'writeDouble'     | 3.14d
        Long       | 42L                  | 'writeInt64'      | 42L
        Boolean    | true                 | 'writeBoolean'    | true
        TimeZone   | TimeZone.getTimeZone('UTC') | 'writeString' | 'UTC'
    }

    void "a Date value is written as its epoch millis"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Date date = new Date(12345L)

        when:
        encoder.encode(writer, propertyFor(Date), date, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeDateTime(12345L)
    }

    void "a Calendar value is written as its epoch millis"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Calendar calendar = Calendar.getInstance()
        calendar.timeInMillis = 12345L

        when:
        encoder.encode(writer, propertyFor(Calendar), calendar, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeDateTime(12345L)
    }

    void "an ObjectId value is written directly as an ObjectId"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        ObjectId id = new ObjectId()

        when:
        encoder.encode(writer, propertyFor(ObjectId), id, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeObjectId(id)
    }

    void "a Binary value is written as binary data from its underlying bytes"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        byte[] bytes = [1, 2, 3] as byte[]
        Binary binary = new Binary(bytes)

        when:
        encoder.encode(writer, propertyFor(Binary), binary, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeBinaryData({ it.data == bytes })
    }

    void "a byte array value is written as binary data directly, without iterating the array"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        byte[] bytes = [1, 2, 3] as byte[]

        when:
        encoder.encode(writer, propertyFor(bytes.class), bytes, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeBinaryData({ it.data == bytes })
        0 * writer.writeStartArray()
    }

    void "a value of an unregistered type falls back to the default string encoder"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        encoder.encode(writer, propertyFor(URI), URI.create('https://example.org'), entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeString('https://example.org')
    }

    void "an array type with no dedicated array encoder writes each element via its component type's encoder"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Simple property = Stub(Simple) {
            getType() >> String[].class
            getName() >> 'someProperty'
        }

        when:
        encoder.encode(writer, property, ['a', 'b'], entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeStartArray()
        1 * writer.writeString('a')
        1 * writer.writeString('b')
        1 * writer.writeEndArray()
    }

    void "enableBigDecimalEncoding registers Decimal128 encoders for BigDecimal and BigInteger"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        SimpleEncoder.enableBigDecimalEncoding()
        encoder.encode(writer, propertyFor(BigDecimal), new BigDecimal('42.50'), entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeDecimal128({ it.bigDecimalValue() == new BigDecimal('42.50') })

        when:
        encoder.encode(writer, propertyFor(BigInteger), 42G, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeDecimal128({ it.bigDecimalValue() == new BigDecimal('42') })
    }
}

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
import org.bson.BsonType
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import spock.lang.Specification

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Simple

class SimpleDecoderSpec extends Specification {

    SimpleDecoder decoder = new SimpleDecoder()
    CodecRegistry codecRegistry = Mock(CodecRegistry)
    DecoderContext decoderContext = DecoderContext.builder().build()

    private Simple propertyOfType(Class type) {
        Stub(Simple) {
            getType() >> type
            getName() >> 'someProperty'
        }
    }

    void "decodes a String when the wire type matches, converting the value"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readString() >> 'a value'
        }
        Simple property = propertyOfType(String)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setProperty('someProperty', 'a value')
    }

    void "decodes an int without conversion when the wire type matches"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.INT32
            readInt32() >> 42
        }
        Simple property = propertyOfType(int)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', 42)
    }

    void "falls back to the wire type's default decoder when the wire type does not match the declared type"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.DOUBLE
            readDouble() >> 4.2d
        }
        Simple property = propertyOfType(int)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', 4.2d)
        0 * entityAccess.setProperty(_, _)
    }

    void "decodes a BigDecimal from a Decimal128 without conversion"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.DECIMAL128
            readDecimal128() >> new org.bson.types.Decimal128(new BigDecimal('42.50'))
        }
        Simple property = propertyOfType(BigDecimal)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', new BigDecimal('42.50'))
    }

    void "decodes an ObjectId without conversion"() {
        given:
        ObjectId id = new ObjectId()
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.OBJECT_ID
            readObjectId() >> id
        }
        Simple property = propertyOfType(ObjectId)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', id)
    }

    void "an array type with a registered element decoder decodes directly via that decoder"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            readBinaryData() >> new org.bson.BsonBinary('some bytes'.bytes)
        }
        Simple property = propertyOfType(([] as byte[]).class)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', 'some bytes'.bytes)
        0 * codecRegistry.get(_)
    }

    void "an array type with no registered element decoder decodes as a list via the codec registry"() {
        given:
        BsonReader reader = Stub(BsonReader)
        Codec<List> listCodec = Mock(Codec)
        Simple property = propertyOfType(([] as String[]).class)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * codecRegistry.get(List) >> listCodec
        1 * listCodec.decode(reader, decoderContext) >> ['a', 'b']
        1 * entityAccess.setProperty('someProperty', ['a', 'b'])
    }
}

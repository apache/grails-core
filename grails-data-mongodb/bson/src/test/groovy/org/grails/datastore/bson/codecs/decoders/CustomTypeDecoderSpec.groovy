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
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Specification

import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.types.Custom

class CustomTypeDecoderSpec extends Specification {

    CustomTypeDecoder decoder = new CustomTypeDecoder()
    DecoderContext decoderContext = DecoderContext.builder().build()

    private Custom propertyNamed(String name) {
        Stub(Custom) {
            getName() >> name
        }
    }

    void "when the marshaller wraps a Codec, decodes through that codec directly and sets the value without conversion"() {
        given:
        Codec innerCodec = Mock(Codec)
        CodecCustomTypeMarshaller marshaller = Stub(CodecCustomTypeMarshaller) {
            getCodec() >> innerCodec
        }
        BsonReader reader = Stub(BsonReader)
        CodecRegistry codecRegistry = Stub(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        property.customTypeMarshaller >> marshaller
        1 * innerCodec.decode(reader, decoderContext) >> 'decoded value'
        1 * entityAccess.setPropertyNoConversion('someProperty', 'decoded value')
    }

    void "when the codec-wrapping marshaller decodes to null, nothing is set on the entity"() {
        given:
        Codec innerCodec = Mock(Codec)
        CodecCustomTypeMarshaller marshaller = Stub(CodecCustomTypeMarshaller) {
            getCodec() >> innerCodec
        }
        BsonReader reader = Stub(BsonReader)
        CodecRegistry codecRegistry = Stub(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        property.customTypeMarshaller >> marshaller
        1 * innerCodec.decode(reader, decoderContext) >> null
        0 * entityAccess.setPropertyNoConversion(_, _)
        0 * entityAccess.setProperty(_, _)
    }

    void "for a plain marshaller, decodes the raw value via the registry's codec for the wire type, then asks the marshaller to read it"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readString() >> 'raw value'
        }
        CodecRegistry codecRegistry = Stub(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        property.customTypeMarshaller >> marshaller
        1 * marshaller.read(property, { Document d -> d.values().first() == 'raw value' }) >> 'marshalled value'
        1 * entityAccess.setProperty('someProperty', 'marshalled value')
    }

    void "for a plain marshaller that reads back null, nothing is set on the entity"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readString() >> 'raw value'
        }
        CodecRegistry codecRegistry = Stub(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        property.customTypeMarshaller >> marshaller
        1 * marshaller.read(property, _ as Document) >> null
        0 * entityAccess.setProperty(_, _)
    }

    void "for a plain marshaller and a wire type with no registered codec, the value is skipped and nothing is set"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        BsonReader reader = Mock(BsonReader) {
            getCurrentBsonType() >> BsonType.JAVASCRIPT
        }
        CodecRegistry codecRegistry = Stub(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        property.customTypeMarshaller >> marshaller
        1 * reader.skipValue()
        0 * marshaller.read(_, _)
        0 * entityAccess.setProperty(_, _)
        0 * entityAccess.setPropertyNoConversion(_, _)
    }
}

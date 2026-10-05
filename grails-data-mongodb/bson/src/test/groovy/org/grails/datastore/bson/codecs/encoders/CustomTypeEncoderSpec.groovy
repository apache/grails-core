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
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Specification

import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.types.Custom

class CustomTypeEncoderSpec extends Specification {

    CustomTypeEncoder encoder = new CustomTypeEncoder()
    EncoderContext encoderContext = EncoderContext.builder().build()
    EntityAccess entityAccess = Mock(EntityAccess)

    private Custom propertyNamed(String name) {
        Stub(Custom) {
            getName() >> name
        }
    }

    void "when the marshaller wraps a Codec, encodes through that codec directly"() {
        given:
        Codec innerCodec = Mock(Codec)
        CodecCustomTypeMarshaller marshaller = Stub(CodecCustomTypeMarshaller) {
            getCodec() >> innerCodec
        }
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Stub(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        property.customTypeMarshaller >> marshaller

        when:
        encoder.encode(writer, property, 'value', entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
        1 * innerCodec.encode(writer, 'value', encoderContext)
    }

    void "for a plain marshaller, writes the property into a document then encodes the converted value via the registry's codec"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        BsonWriter writer = Mock(BsonWriter)
        Codec convertedCodec = Mock(Codec)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        property.customTypeMarshaller >> marshaller

        when:
        encoder.encode(writer, property, 'value', entityAccess, encoderContext, codecRegistry)

        then:
        1 * marshaller.write(property, 'value', { Document d -> true }) >> { Custom p, Object v, Document d -> d.put('someProperty', 'converted') }
        1 * codecRegistry.get(String) >> convertedCodec
        1 * writer.writeName('someProperty')
        1 * convertedCodec.encode(writer, 'converted', encoderContext)
    }

    void "for a plain marshaller that writes nothing into the document, no codec lookup or write happens"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        property.customTypeMarshaller >> marshaller

        when:
        encoder.encode(writer, property, 'value', entityAccess, encoderContext, codecRegistry)

        then:
        1 * marshaller.write(property, 'value', _ as Document)
        0 * codecRegistry.get(_)
        0 * writer.writeName(_)
    }

    void "for a plain marshaller whose converted value has no registered codec, nothing is written"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        Custom property = propertyNamed('someProperty')
        property.customTypeMarshaller >> marshaller

        when:
        encoder.encode(writer, property, 'value', entityAccess, encoderContext, codecRegistry)

        then:
        1 * marshaller.write(property, 'value', _ as Document) >> { Custom p, Object v, Document d -> d.put('someProperty', 'converted') }
        1 * codecRegistry.get(String) >> null
        0 * writer.writeName(_)
    }
}

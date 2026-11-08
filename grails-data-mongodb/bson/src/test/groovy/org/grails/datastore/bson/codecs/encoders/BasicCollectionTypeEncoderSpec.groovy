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
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Specification

import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Basic

class BasicCollectionTypeEncoderSpec extends Specification {

    BasicCollectionTypeEncoder encoder = new BasicCollectionTypeEncoder()
    EncoderContext encoderContext = EncoderContext.builder().build()

    private Basic propertyFor(Class collectionType) {
        Stub(Basic) {
            getType() >> collectionType
            getName() >> 'someProperty'
            getCustomTypeMarshaller() >> null
        }
    }

    private EntityAccess entityAccessFor(Object entity) {
        Mock(EntityAccess) {
            getEntity() >> entity
        }
    }

    void "when the property has a custom type marshaller, encoding is delegated entirely to it"() {
        given:
        Codec innerCodec = Mock(Codec)
        CodecCustomTypeMarshaller marshaller = Stub(CodecCustomTypeMarshaller) {
            getCodec() >> innerCodec
        }
        Basic property = Stub(Basic) {
            getType() >> List
            getName() >> 'someProperty'
            getCustomTypeMarshaller() >> marshaller
        }
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = entityAccessFor(new Object())

        when:
        encoder.encode(writer, property, ['a'], entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
        1 * innerCodec.encode(writer, ['a'], encoderContext)
        0 * codecRegistry.get(_)
    }

    void "a List-typed property is encoded via the registry's codec for its declared type, unconverted"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(List)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = entityAccessFor(new Object())
        List value = ['a', 'b']

        when:
        encoder.encode(writer, property, value, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
        1 * codecRegistry.get(List) >> listCodec
        1 * listCodec.encode(writer, value, encoderContext)
    }

    void "a Set-typed property is encoded via the registry's codec for List, with the value converted to a List first"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(Set)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = entityAccessFor(new Object())

        when:
        encoder.encode(writer, property, ['a', 'b'] as Set, entityAccess, encoderContext, codecRegistry)

        then:
        0 * codecRegistry.get(Set)
        1 * codecRegistry.get(List) >> listCodec
        1 * listCodec.encode(writer, { it instanceof List && it.containsAll(['a', 'b']) }, encoderContext)
    }

    void "a Map-typed property is encoded via the registry's codec for its declared type"() {
        given:
        Codec<Map> mapCodec = Mock(Codec)
        Basic property = propertyFor(Map)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = entityAccessFor(new Object())
        Map value = [k: 'v']

        when:
        encoder.encode(writer, property, value, entityAccess, encoderContext, codecRegistry)

        then:
        1 * codecRegistry.get(Map) >> mapCodec
        1 * mapCodec.encode(writer, value, encoderContext)
    }

    void "when the owning entity is dirty-checkable and the value is a Collection, the property is rewrapped for dirty checking"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(List)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry) {
            get(List) >> listCodec
        }
        DirtyCheckable dirtyCheckableEntity = Mock(DirtyCheckable)
        EntityAccess entityAccess = entityAccessFor(dirtyCheckableEntity)

        when:
        encoder.encode(writer, property, ['a'], entityAccess, encoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', { it instanceof Collection && it.contains('a') })
    }

    void "when the owning entity is dirty-checkable and the value is a Map, the property is rewrapped for dirty checking"() {
        given:
        Codec<Map> mapCodec = Mock(Codec)
        Basic property = propertyFor(Map)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry) {
            get(Map) >> mapCodec
        }
        DirtyCheckable dirtyCheckableEntity = Mock(DirtyCheckable)
        EntityAccess entityAccess = entityAccessFor(dirtyCheckableEntity)

        when:
        encoder.encode(writer, property, [k: 'v'], entityAccess, encoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('someProperty', { it instanceof Map && it.k == 'v' })
    }

    void "when the owning entity is not dirty-checkable, the property is not rewrapped"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(List)
        BsonWriter writer = Mock(BsonWriter)
        CodecRegistry codecRegistry = Mock(CodecRegistry) {
            get(List) >> listCodec
        }
        EntityAccess entityAccess = entityAccessFor(new Object())

        when:
        encoder.encode(writer, property, ['a'], entityAccess, encoderContext, codecRegistry)

        then:
        0 * entityAccess.setPropertyNoConversion(_, _)
    }
}

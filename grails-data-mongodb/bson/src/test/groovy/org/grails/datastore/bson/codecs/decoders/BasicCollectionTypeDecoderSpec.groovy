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
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.springframework.core.convert.ConversionService
import spock.lang.Specification

import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Basic

class BasicCollectionTypeDecoderSpec extends Specification {

    BasicCollectionTypeDecoder decoder = new BasicCollectionTypeDecoder()
    BsonReader reader = Stub(BsonReader)
    DecoderContext decoderContext = DecoderContext.builder().build()
    ConversionService conversionService = Mock(ConversionService) {
        convert(_, _) >> { Object value, Class target -> value }
    }

    private EntityAccess entityAccessFor(Object entity) {
        PersistentEntity persistentEntity = Stub(PersistentEntity) {
            getMappingContext() >> Stub(MappingContext) {
                getConversionService() >> conversionService
            }
        }
        Mock(EntityAccess) {
            getEntity() >> entity
            getPersistentEntity() >> persistentEntity
        }
    }

    private Basic propertyFor(Class collectionType, Class componentType) {
        Stub(Basic) {
            getType() >> collectionType
            getComponentType() >> componentType
            getName() >> 'someProperty'
            getCustomTypeMarshaller() >> null
        }
    }

    void "when the property has a custom type marshaller, decoding is delegated entirely to it"() {
        given:
        CustomTypeMarshaller marshaller = Mock(CustomTypeMarshaller)
        Basic property = Stub(Basic) {
            getType() >> List
            getComponentType() >> String
            getName() >> 'someProperty'
            getCustomTypeMarshaller() >> marshaller
        }
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        0 * codecRegistry.get(_)
        0 * entityAccess.setProperty(_, _)
    }

    void "a List-typed property is decoded via the registry's codec for its declared type and each element is converted"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(List, Integer)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = entityAccessFor(new Object())

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * codecRegistry.get(List) >> listCodec
        1 * listCodec.decode(reader, decoderContext) >> ['1', '2']
        1 * conversionService.convert('1', Integer) >> 1
        1 * conversionService.convert('2', Integer) >> 2
        1 * entityAccess.setProperty('someProperty', [1, 2])
    }

    void "a Set-typed property is decoded via the registry's codec for List, not Set"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(Set, String)
        CodecRegistry codecRegistry = Mock(CodecRegistry)
        EntityAccess entityAccess = entityAccessFor(new Object())

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * codecRegistry.get(List) >> listCodec
        0 * codecRegistry.get(Set)
        1 * listCodec.decode(reader, decoderContext) >> ['a']
        1 * entityAccess.setProperty('someProperty', ['a'])
    }

    void "when the owning entity is dirty-checkable, the converted collection is wrapped for dirty checking"() {
        given:
        Codec<List> listCodec = Mock(Codec)
        Basic property = propertyFor(List, String)
        CodecRegistry codecRegistry = Mock(CodecRegistry) {
            get(List) >> listCodec
        }
        DirtyCheckable dirtyCheckableEntity = Mock(DirtyCheckable)
        EntityAccess entityAccess = entityAccessFor(dirtyCheckableEntity)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * listCodec.decode(reader, decoderContext) >> ['a']
        1 * entityAccess.setProperty('someProperty', { it instanceof Collection && it.contains('a') })
    }

    void "a Map-typed property is decoded via the registry's codec and each value is converted"() {
        given:
        Codec<Map> mapCodec = Mock(Codec)
        Basic property = propertyFor(Map, Integer)
        CodecRegistry codecRegistry = Mock(CodecRegistry) {
            get(Map) >> mapCodec
        }
        EntityAccess entityAccess = entityAccessFor(new Object())

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * mapCodec.decode(reader, decoderContext) >> [key: '1']
        1 * conversionService.convert('1', Integer) >> 1
        1 * entityAccess.setProperty('someProperty', [key: 1])
    }
}

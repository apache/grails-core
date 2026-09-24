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
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Specification

import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.reflect.EntityReflector

class EmbeddedCollectionDecoderSpec extends Specification {

    DecoderContext decoderContext = DecoderContext.builder().build()
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    DirtyCheckable owningEntity = Mock(DirtyCheckable)
    EntityReflector reflector = Mock(EntityReflector)
    Association inverseSide = Stub(Association) {
        getName() >> 'owner'
    }

    private static EmbeddedCollectionDecoder decoderReturning(BsonPersistentEntityCodec codec) {
        new EmbeddedCollectionDecoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry registry, PersistentEntity entity) {
                codec
            }
        }
    }

    private EmbeddedCollection propertyFor(Class type, boolean bidirectional) {
        PersistentEntity associatedEntity = Stub(PersistentEntity) {
            getReflector() >> reflector
        }
        Stub(EmbeddedCollection) {
            getType() >> type
            getAssociatedEntity() >> associatedEntity
            isBidirectional() >> bidirectional
            getInverseSide() >> inverseSide
            getName() >> 'someProperty'
        }
    }

    void "a List-typed property reads a bson array, decoding each element via the association codec"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonReader reader = Mock(BsonReader) {
            readBsonType() >>> [BsonType.DOCUMENT, BsonType.DOCUMENT, BsonType.END_OF_DOCUMENT]
        }
        EmbeddedCollection property = propertyFor(List, false)
        EntityAccess entityAccess = Mock(EntityAccess) {
            getEntity() >> owningEntity
        }

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * reader.readStartArray()
        1 * reader.readEndArray()
        2 * associationCodec.decode(reader, decoderContext) >>> ['first', 'second']
        0 * reflector.setProperty(*_)
        1 * entityAccess.setPropertyNoConversion('someProperty', { it instanceof List && it == ['first', 'second'] })
    }

    void "when bidirectional, each decoded element in the list has the inverse side set to the owning entity"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonReader reader = Mock(BsonReader) {
            readBsonType() >>> [BsonType.DOCUMENT, BsonType.END_OF_DOCUMENT]
        }
        EmbeddedCollection property = propertyFor(List, true)
        EntityAccess entityAccess = Mock(EntityAccess) {
            getEntity() >> owningEntity
        }

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * associationCodec.decode(reader, decoderContext) >> 'first'
        1 * reflector.setProperty('first', 'owner', owningEntity)
        1 * entityAccess.setPropertyNoConversion('someProperty', ['first'])
    }

    void "a Map-typed property reads a bson document, decoding each value via the association codec keyed by field name"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonReader reader = Mock(BsonReader) {
            readBsonType() >>> [BsonType.DOCUMENT, BsonType.END_OF_DOCUMENT]
            readName() >> 'k1'
        }
        EmbeddedCollection property = propertyFor(Map, false)
        EntityAccess entityAccess = Mock(EntityAccess) {
            getEntity() >> owningEntity
        }

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * reader.readStartDocument()
        1 * reader.readEndDocument()
        1 * associationCodec.decode(reader, decoderContext) >> 'value1'
        1 * entityAccess.setPropertyNoConversion('someProperty', { it instanceof Map && it.k1 == 'value1' })
    }

    void "a property that is neither a Collection nor a Map skips the value"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonReader reader = Mock(BsonReader)
        EmbeddedCollection property = propertyFor(String, false)
        EntityAccess entityAccess = Mock(EntityAccess) {
            getEntity() >> owningEntity
        }

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * reader.skipValue()
        0 * associationCodec.decode(_, _)
        0 * entityAccess.setPropertyNoConversion(_, _)
    }
}

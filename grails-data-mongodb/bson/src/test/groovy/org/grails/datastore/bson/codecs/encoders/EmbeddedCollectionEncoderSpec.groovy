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
import spock.lang.Specification

import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.EntityReflector

class EmbeddedCollectionEncoderSpec extends Specification {

    EncoderContext encoderContext = EncoderContext.builder().build()
    Object owningEntity = new Object()
    EntityReflector reflector = Mock(EntityReflector)
    PersistentEntity associatedEntity = Stub(PersistentEntity) {
        getJavaClass() >> String
    }
    MappingContext mappingContext = Mock(MappingContext) {
        getEntityReflector(associatedEntity) >> reflector
    }
    PersistentEntity owningPersistentEntity = Stub(PersistentEntity) {
        getMappingContext() >> mappingContext
    }
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    EntityAccess parentAccess = Mock(EntityAccess) {
        getPersistentEntity() >> owningPersistentEntity
        getEntity() >> owningEntity
    }

    private static EmbeddedCollectionEncoder encoderReturning(BsonPersistentEntityCodec codec) {
        new EmbeddedCollectionEncoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry registry, PersistentEntity entity) {
                codec
            }
        }
    }

    private EmbeddedCollection propertyFor(boolean bidirectional, Association inverseSide = null) {
        Stub(EmbeddedCollection) {
            getName() >> 'someProperty'
            getAssociatedEntity() >> associatedEntity
            isBidirectional() >> bidirectional
            getInverseSide() >> inverseSide
        }
    }

    void "a List value writes a bson array, encoding each element via the associated entity codec"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        EmbeddedCollection property = propertyFor(false)

        when:
        encoderReturning(associationCodec).encode(writer, property, ['first', 'second'], parentAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
        1 * writer.writeStartArray()
        1 * reflector.getIdentifier('first') >> 'id1'
        1 * associationCodec.encode(writer, 'first', encoderContext, true)
        1 * reflector.getIdentifier('second') >> null
        1 * associationCodec.encode(writer, 'second', encoderContext, false)
        1 * writer.writeEndArray()
    }

    void "null elements in the collection are skipped"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        EmbeddedCollection property = propertyFor(false)

        when:
        encoderReturning(associationCodec).encode(writer, property, [null, 'second'], parentAccess, encoderContext, codecRegistry)

        then:
        1 * reflector.getIdentifier('second') >> 'id1'
        1 * associationCodec.encode(writer, 'second', encoderContext, true)
        0 * associationCodec.encode(writer, null, _, _)
    }

    void "when bidirectional with a to-one inverse, each element has the inverse property set to the owning entity before encoding"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        ToOne inverseSide = Stub(ToOne) {
            getName() >> 'owner'
        }
        EmbeddedCollection property = propertyFor(true, inverseSide)

        when:
        encoderReturning(associationCodec).encode(writer, property, ['first'], parentAccess, encoderContext, codecRegistry)

        then:
        1 * reflector.getIdentifier('first') >> 'id1'
        1 * reflector.setProperty('first', 'owner', owningEntity)
        1 * associationCodec.encode(writer, 'first', encoderContext, true)
    }

    void "when bidirectional with a to-many inverse, the inverse property is not set"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        Association inverseSide = Stub(Association) {
            getName() >> 'owner'
        }
        EmbeddedCollection property = propertyFor(true, inverseSide)

        when:
        encoderReturning(associationCodec).encode(writer, property, ['first'], parentAccess, encoderContext, codecRegistry)

        then:
        1 * reflector.getIdentifier('first') >> 'id1'
        0 * reflector.setProperty(*_)
        1 * associationCodec.encode(writer, 'first', encoderContext, true)
    }

    void "an element whose runtime type differs from the associated entity is encoded via the mapping context's codec for that subclass"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonPersistentEntityCodec subclassCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        PersistentEntity childEntity = Stub(PersistentEntity)
        EntityReflector childReflector = Mock(EntityReflector) {
            getIdentifier(42) >> 'id1'
        }
        mappingContext.getPersistentEntity(Integer.name) >> childEntity
        mappingContext.getEntityReflector(childEntity) >> childReflector
        CodecRegistry registry = Stub(CodecRegistry) {
            get(Integer) >> subclassCodec
        }
        EmbeddedCollection property = propertyFor(false)

        when:
        encoderReturning(associationCodec).encode(writer, property, [42], parentAccess, encoderContext, registry)

        then:
        1 * subclassCodec.encode(writer, 42, encoderContext, true)
        0 * associationCodec.encode(*_)
    }

    void "an element whose runtime type is unknown to the mapping context is skipped"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        mappingContext.getPersistentEntity(Integer.name) >> null
        EmbeddedCollection property = propertyFor(false)

        when:
        encoderReturning(associationCodec).encode(writer, property, [42], parentAccess, encoderContext, codecRegistry)

        then:
        0 * associationCodec.encode(*_)
    }

    void "a Map value writes a bson document keyed by entry name, encoding each entry value via the associated entity codec"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        EmbeddedCollection property = propertyFor(false)

        when:
        encoderReturning(associationCodec).encode(writer, property, [k1: 'v1'], parentAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeStartDocument()
        1 * writer.writeName('k1')
        1 * reflector.getIdentifier('v1') >> 'id1'
        1 * associationCodec.encode(writer, 'v1', encoderContext, true)
        1 * writer.writeEndDocument()
    }

    void "a value that is neither a Collection nor a Map writes only the property name"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        EmbeddedCollection property = propertyFor(false)

        when:
        encoderReturning(associationCodec).encode(writer, property, 'not-a-collection', parentAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
        0 * writer.writeStartArray()
        0 * writer.writeStartDocument()
        0 * associationCodec.encode(*_)
    }
}

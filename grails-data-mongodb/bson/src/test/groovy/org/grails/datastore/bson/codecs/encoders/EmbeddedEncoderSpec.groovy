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
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.reflect.EntityReflector

class EmbeddedEncoderSpec extends Specification {

    EncoderContext encoderContext = EncoderContext.builder().build()
    CodecRegistry codecRegistry = Stub(CodecRegistry)

    private static EmbeddedEncoder encoderReturning(BsonPersistentEntityCodec codec) {
        new EmbeddedEncoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry registry, PersistentEntity entity) {
                codec
            }
        }
    }

    void "a null value is skipped entirely, without writing anything"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        Embedded property = Stub(Embedded)
        EntityAccess parentAccess = Mock(EntityAccess)

        when:
        encoderReturning(associationCodec).encode(writer, property, null, parentAccess, encoderContext, codecRegistry)

        then:
        0 * writer.writeName(_)
        0 * associationCodec.encode(*_)
    }

    void "encodes the association via the embedded entity codec, passing whether it has an identifier"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        Object value = new Object()
        EntityReflector reflector = Mock(EntityReflector) {
            getIdentifier(value) >> 'some-id'
        }
        PersistentEntity resolvedEntity = Stub(PersistentEntity)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(value.getClass().name) >> resolvedEntity
            getEntityReflector(resolvedEntity) >> reflector
        }
        PersistentEntity owningEntity = Stub(PersistentEntity) {
            getMappingContext() >> mappingContext
        }
        Embedded property = Stub(Embedded) {
            getName() >> 'someProperty'
        }
        EntityAccess parentAccess = Mock(EntityAccess) {
            getPersistentEntity() >> owningEntity
        }

        when:
        encoderReturning(associationCodec).encode(writer, property, value, parentAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('someProperty')
        1 * associationCodec.encode(writer, value, encoderContext, true)
    }

    void "passes hasIdentifier as false when the reflector resolves no identifier"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        Object value = new Object()
        EntityReflector reflector = Mock(EntityReflector) {
            getIdentifier(value) >> null
        }
        PersistentEntity resolvedEntity = Stub(PersistentEntity)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(value.getClass().name) >> resolvedEntity
            getEntityReflector(resolvedEntity) >> reflector
        }
        PersistentEntity owningEntity = Stub(PersistentEntity) {
            getMappingContext() >> mappingContext
        }
        Embedded property = Stub(Embedded) {
            getName() >> 'someProperty'
        }
        EntityAccess parentAccess = Mock(EntityAccess) {
            getPersistentEntity() >> owningEntity
        }

        when:
        encoderReturning(associationCodec).encode(writer, property, value, parentAccess, encoderContext, codecRegistry)

        then:
        1 * associationCodec.encode(writer, value, encoderContext, false)
    }

    void "falls back to the property's declared associated entity when the mapping context has none for the value's runtime type"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        BsonWriter writer = Mock(BsonWriter)
        Object value = new Object()
        EntityReflector reflector = Mock(EntityReflector) {
            getIdentifier(value) >> 'some-id'
        }
        PersistentEntity declaredEntity = Stub(PersistentEntity)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(value.getClass().name) >> null
            getEntityReflector(declaredEntity) >> reflector
        }
        PersistentEntity owningEntity = Stub(PersistentEntity) {
            getMappingContext() >> mappingContext
        }
        Embedded property = Stub(Embedded) {
            getName() >> 'someProperty'
            getAssociatedEntity() >> declaredEntity
        }
        EntityAccess parentAccess = Mock(EntityAccess) {
            getPersistentEntity() >> owningEntity
        }

        when:
        encoderReturning(associationCodec).encode(writer, property, value, parentAccess, encoderContext, codecRegistry)

        then:
        1 * associationCodec.encode(writer, value, encoderContext, true)
    }
}

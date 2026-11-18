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
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Specification

import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.reflect.EntityReflector

class EmbeddedDecoderSpec extends Specification {

    BsonReader reader = Stub(BsonReader)
    DecoderContext decoderContext = DecoderContext.builder().build()
    CodecRegistry codecRegistry = Stub(CodecRegistry)

    private static EmbeddedDecoder decoderReturning(BsonPersistentEntityCodec codec) {
        new EmbeddedDecoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry registry, PersistentEntity entity) {
                codec
            }
        }
    }

    void "decodes the association via the embedded entity codec and sets it without conversion"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        Object decoded = new Object()
        PersistentEntity associatedEntity = Stub(PersistentEntity)
        Embedded property = Stub(Embedded) {
            getAssociatedEntity() >> associatedEntity
            isBidirectional() >> false
            getName() >> 'someProperty'
        }
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * associationCodec.decode(reader, decoderContext) >> decoded
        1 * entityAccess.setPropertyNoConversion('someProperty', decoded)
    }

    void "when the decoded association is dirty-checkable, change tracking is enabled on it"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        DirtyCheckable decoded = Mock(DirtyCheckable)
        PersistentEntity associatedEntity = Stub(PersistentEntity)
        Embedded property = Stub(Embedded) {
            getAssociatedEntity() >> associatedEntity
            isBidirectional() >> false
            getName() >> 'someProperty'
        }
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * associationCodec.decode(reader, decoderContext) >> decoded
        1 * decoded.trackChanges()
        1 * entityAccess.setPropertyNoConversion('someProperty', decoded)
    }

    void "when bidirectional, the inverse side is set on the decoded association to point back at the owning entity"() {
        given:
        BsonPersistentEntityCodec associationCodec = Mock(BsonPersistentEntityCodec)
        Object decoded = new Object()
        Object owningEntity = new Object()
        EntityReflector reflector = Mock(EntityReflector)
        Association inverseSide = Stub(Association) {
            getName() >> 'owner'
        }
        PersistentEntity associatedEntity = Stub(PersistentEntity) {
            getReflector() >> reflector
        }
        Embedded property = Stub(Embedded) {
            getAssociatedEntity() >> associatedEntity
            isBidirectional() >> true
            getInverseSide() >> inverseSide
            getName() >> 'someProperty'
        }
        EntityAccess entityAccess = Mock(EntityAccess) {
            getEntity() >> owningEntity
        }

        when:
        decoderReturning(associationCodec).decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * associationCodec.decode(reader, decoderContext) >> decoded
        1 * reflector.setProperty(decoded, 'owner', owningEntity)
        1 * entityAccess.setPropertyNoConversion('someProperty', decoded)
    }
}

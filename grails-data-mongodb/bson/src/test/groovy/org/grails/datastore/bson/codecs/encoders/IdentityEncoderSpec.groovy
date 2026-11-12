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
import org.bson.types.ObjectId
import spock.lang.Specification

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Identity

class IdentityEncoderSpec extends Specification {

    IdentityEncoder encoder = new IdentityEncoder()
    EncoderContext encoderContext = EncoderContext.builder().build()
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    EntityAccess entityAccess = Mock(EntityAccess)

    private Identity propertyWith(String[] identifierName, Class<?> storedAs) {
        IdentityMapping identityMapping = Stub(IdentityMapping) {
            getIdentifierName() >> identifierName
            getStoredAs() >> storedAs
        }
        ClassMapping classMapping = Stub(ClassMapping) {
            getIdentifier() >> identityMapping
        }
        PersistentEntity owner = Stub(PersistentEntity) {
            getMapping() >> classMapping
        }
        Stub(Identity) {
            getOwner() >> owner
        }
    }

    void "writes the default 'id' name when no identifierName is mapped"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, null)

        when:
        encoder.encode(writer, property, new ObjectId(), entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('id')
    }

    void "writes a custom identifierName when one is mapped"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(['customId'] as String[], null)

        when:
        encoder.encode(writer, property, new ObjectId(), entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('customId')
    }

    void "without a storedAs mapping, an ObjectId value is written as an ObjectId"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, null)
        ObjectId id = new ObjectId()

        when:
        encoder.encode(writer, property, id, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeObjectId(id)
    }

    void "without a storedAs mapping, a Number value is written as int64"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, null)

        when:
        encoder.encode(writer, property, 42L, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeInt64(42L)
    }

    void "without a storedAs mapping, any other value is written as a string"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, null)

        when:
        encoder.encode(writer, property, 'natural-key', entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeString('natural-key')
    }

    void "storedAs ObjectId converts a valid 24-char hex id string to an ObjectId"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, ObjectId)
        String hex = new ObjectId().toHexString()

        when:
        encoder.encode(writer, property, hex, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeObjectId(new ObjectId(hex))
        0 * writer.writeString(_)
    }

    void "storedAs ObjectId falls back to writing a string when the id is not a valid hex ObjectId"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, ObjectId)

        when:
        encoder.encode(writer, property, 'not-a-valid-object-id', entityAccess, encoderContext, codecRegistry)

        then:
        0 * writer.writeObjectId(_)
        1 * writer.writeString('not-a-valid-object-id')
    }

    void "storedAs ObjectId leaves an already-ObjectId value untouched by the storedAs branch"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, ObjectId)
        ObjectId id = new ObjectId()

        when:
        encoder.encode(writer, property, id, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeObjectId(id)
    }

    void "storedAs String converts a non-String id to its string form"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, String)
        ObjectId id = new ObjectId()

        when:
        encoder.encode(writer, property, id, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeString(id.toString())
        0 * writer.writeObjectId(_)
    }

    void "storedAs String leaves an already-String id to fall through to the default string branch"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, String)

        when:
        encoder.encode(writer, property, 'already-a-string', entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeString('already-a-string')
    }

    void "a null id with no storedAs mapping is written as the string 'null'"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = propertyWith(null, null)

        when:
        encoder.encode(writer, property, null, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeString('null')
    }

    void "when the owner's mapping cannot be resolved, resolving the identifier name throws rather than falling back"() {
        // getIdentifierName() navigates property.owner.mapping.identifier without the null-safety that
        // resolveStoredAs() applies to the same chain, so a missing ClassMapping surfaces as an NPE here
        // instead of falling back like the storedAs lookup does. Flagged as a production inconsistency,
        // not fixed here since this is a test-only stage.
        given:
        BsonWriter writer = Mock(BsonWriter)
        Identity property = Stub(Identity) {
            getOwner() >> Stub(PersistentEntity) {
                getMapping() >> null
            }
        }
        ObjectId id = new ObjectId()

        when:
        encoder.encode(writer, property, id, entityAccess, encoderContext, codecRegistry)

        then:
        thrown(NullPointerException)
    }
}

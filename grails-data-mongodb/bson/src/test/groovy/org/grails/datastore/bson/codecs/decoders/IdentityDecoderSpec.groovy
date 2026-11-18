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
import org.bson.types.ObjectId
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Identity

class IdentityDecoderSpec extends Specification {

    IdentityDecoder decoder = new IdentityDecoder()
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    DecoderContext decoderContext = DecoderContext.builder().build()

    private Identity propertyOfType(Class type) {
        PersistentEntity owner = Stub(PersistentEntity) {
            getName() >> 'SomeEntity'
        }
        Stub(Identity) {
            getType() >> type
            getOwner() >> owner
        }
    }

    @Unroll
    void "decodes a #type.simpleName identity without conversion when the wire type matches"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> bsonType
            _(*_) >> rawValue
        }
        Identity property = propertyOfType(type)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setIdentifierNoConversion(rawValue)

        where:
        type     | bsonType             | rawValue
        ObjectId | BsonType.OBJECT_ID   | new ObjectId()
        Long     | BsonType.INT64       | 42L
        Integer  | BsonType.INT32       | 42
        String   | BsonType.STRING      | 'abc123'
    }

    void "falls back to the wire type's default decoder, converting, when the wire type does not match the property's declared type"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readString() >> '42'
        }
        Identity property = propertyOfType(Long)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setIdentifier('42')
        0 * entityAccess.setIdentifierNoConversion(_)
    }

    void "throws IllegalStateException when the property's declared identity type is not supported"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
        }
        Identity property = propertyOfType(Double)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        thrown(IllegalStateException)
    }

    void "throws DataIntegrityViolationException when the wire type has no default decoder either"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.DOUBLE
        }
        Identity property = propertyOfType(Long)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        thrown(DataIntegrityViolationException)
    }
}

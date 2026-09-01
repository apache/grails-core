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

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.TenantId

class TenantIdDecoderSpec extends Specification {

    TenantIdDecoder decoder = new TenantIdDecoder()
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    DecoderContext decoderContext = DecoderContext.builder().build()

    private TenantId propertyOfType(Class type) {
        Stub(TenantId) {
            getType() >> type
            getName() >> 'tenantId'
        }
    }

    void "decodes a String tenant id when the wire type matches the declared type"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readString() >> 'tenant-1'
        }
        TenantId property = propertyOfType(String)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setProperty('tenantId', 'tenant-1')
    }

    void "decodes without conversion when the declared type has its own registered decoder and the wire type matches"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.INT32
            readInt32() >> 7
        }
        TenantId property = propertyOfType(int)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setPropertyNoConversion('tenantId', 7)
    }

    void "falls back to the wire type's default decoder, converting, when the wire type does not match"() {
        given:
        BsonReader reader = Stub(BsonReader) {
            getCurrentBsonType() >> BsonType.INT32
            readInt32() >> 1
        }
        TenantId property = propertyOfType(String)
        EntityAccess entityAccess = Mock(EntityAccess)

        when:
        decoder.decode(reader, property, entityAccess, decoderContext, codecRegistry)

        then:
        1 * entityAccess.setProperty('tenantId', 1)
        0 * entityAccess.setPropertyNoConversion(_, _)
    }
}

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

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.TenantId

class TenantIdEncoderSpec extends Specification {

    TenantIdEncoder encoder = new TenantIdEncoder()
    EncoderContext encoderContext = EncoderContext.builder().build()
    CodecRegistry codecRegistry = Stub(CodecRegistry)
    EntityAccess entityAccess = Mock(EntityAccess)

    private TenantId propertyFor(Class type) {
        Stub(TenantId) {
            getType() >> type
            getName() >> 'tenantId'
        }
    }

    void "writes the property name before delegating to the simple type encoder for a String tenant id"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        encoder.encode(writer, propertyFor(String), 'tenant-1', entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeName('tenantId')
        1 * writer.writeString('tenant-1')
    }

    void "delegates to the simple type encoder registered for the property's declared type"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        encoder.encode(writer, propertyFor(Long), 42L, entityAccess, encoderContext, codecRegistry)

        then:
        1 * writer.writeInt64(42L)
    }
}

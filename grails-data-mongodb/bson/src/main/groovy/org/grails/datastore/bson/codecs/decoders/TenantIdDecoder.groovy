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

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.TenantId

/**
 * Decodes the tenant id
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantIdDecoder implements PropertyDecoder<TenantId> {
    @Override
    void decode(BsonReader reader, TenantId property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        BsonType bsonType = reader.currentBsonType
        def decoder = SimpleDecoder.SIMPLE_TYPE_DECODERS.get(property.type)
        if(bsonType != decoder.bsonType()) {
            SimpleDecoder.DEFAULT_DECODERS.get(bsonType).decode(reader, property, entityAccess)
        }
        else {
            decoder.decode reader, property, entityAccess
        }
    }
}

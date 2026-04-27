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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Identity

/**
 * A {@PropertyEncoder} capable of encoding the {@link org.grails.datastore.mapping.model.types.Identity}
 */
@CompileStatic
class IdentityEncoder implements PropertyEncoder<Identity> {

    private static final Logger log = LoggerFactory.getLogger(IdentityEncoder)

    // One warn per (owning entity class) so a misconfigured 'storedAs: ObjectId' on
    // natural-key data is debuggable in the field without flooding logs at write rate.
    private static final Set<String> warnedNonHexEntities = ConcurrentHashMap.newKeySet()

    @Override
    void encode(BsonWriter writer, Identity property, Object id, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        writer.writeName(getIdentifierName(property))

        Class<?> storedAs = resolveStoredAs(property)
        if (storedAs != null && id != null) {
            if (ObjectId.isAssignableFrom(storedAs) && !(id instanceof ObjectId)) {
                String hex = id.toString()
                // Guard against natural-key strings accidentally paired with storedAs: ObjectId.
                // new ObjectId(<non-hex>) throws IllegalArgumentException, which would surface
                // deep inside the BSON write pipeline. Fall through to writeString for consistency
                // with the converter-based paths (MongoCodecSession, MongoCodecEntityPersister).
                if (ObjectId.isValid(hex)) {
                    writer.writeObjectId(new ObjectId(hex))
                    return
                }
                warnNonHexFallback(property, hex)
            }
            if (String.isAssignableFrom(storedAs) && !(id instanceof String)) {
                writer.writeString(id.toString())
                return
            }
        }

        if (id instanceof ObjectId) {
            writer.writeObjectId(id)
        } else if (id instanceof Number) {
            writer.writeInt64(((Number) id).toLong())
        } else {
            writer.writeString(id.toString())
        }

    }

    private static Class<?> resolveStoredAs(Identity property) {
        try {
            return property?.owner?.mapping?.identifier?.storedAs
        } catch (Exception ignored) {
            return null
        }
    }

    private static void warnNonHexFallback(Identity property, String hex) {
        if (!log.warnEnabled) return
        String entityName = property?.owner?.javaClass?.name ?: 'unknown'
        if (warnedNonHexEntities.add(entityName)) {
            log.warn(
                "Identity for {} is mapped storedAs: ObjectId but value '{}' is not a valid 24-char hex ObjectId; persisting as BSON String. Use storedAs: String for natural-key domains. (warned once per entity class)",
                entityName, hex)
        }
    }

    protected String getIdentifierName(Identity property) {
        String[] identifierName = property.getOwner().mapping.identifier?.identifierName
        if (identifierName != null) {
            return identifierName[0]
        }
        else {
            return GormProperties.IDENTITY
        }
    }
}

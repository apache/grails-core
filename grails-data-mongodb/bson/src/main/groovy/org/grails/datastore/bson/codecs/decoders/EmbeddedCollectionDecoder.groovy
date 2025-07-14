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
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingMap
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * A {@PropertyDecoder} capable of decoding {@EmbeddedCollection} collection types
 */
@CompileStatic
class EmbeddedCollectionDecoder implements PropertyDecoder<EmbeddedCollection> {

    @Override
    void decode(BsonReader reader, EmbeddedCollection property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        def associatedEntity = property.associatedEntity
        BsonPersistentEntityCodec associationCodec = createEmbeddedEntityCodec(codecRegistry, associatedEntity)
        final boolean isBidirectional = property.isBidirectional()
        Association inverseSide = property.getInverseSide()
        EntityReflector associationReflector = property.getAssociatedEntity().getReflector()

        def owningEntity = entityAccess.entity
        if (Collection.isAssignableFrom(property.type)) {
            reader.readStartArray()
            def bsonType = reader.readBsonType()
            def collection = MappingUtils.createConcreteCollection(property.type)
            while (bsonType != BsonType.END_OF_DOCUMENT) {
                def decoded = associationCodec.decode(reader, decoderContext)
                if (isBidirectional) {
                    associationReflector.setProperty(
                            decoded,
                            inverseSide.name,
                            owningEntity
                    )
                }
                collection << decoded
                bsonType = reader.readBsonType()
            }
            reader.readEndArray()
            entityAccess.setPropertyNoConversion(
                    property.name,
                    DirtyCheckingSupport.wrap(collection, (DirtyCheckable) owningEntity, property.name)
            )
        } else if (Map.isAssignableFrom(property.type)) {
            reader.readStartDocument()
            def bsonType = reader.readBsonType()
            def map = [:]
            while (bsonType != BsonType.END_OF_DOCUMENT) {
                def key = reader.readName()
                def decoded = associationCodec.decode(reader, decoderContext)
                if (isBidirectional) {
                    associationReflector.setProperty(
                            decoded,
                            inverseSide.name,
                            owningEntity
                    )
                }
                map[key] = decoded
                bsonType = reader.readBsonType()
            }
            reader.readEndDocument()
            entityAccess.setPropertyNoConversion(
                    property.name,
                    new DirtyCheckingMap(map, (DirtyCheckable) owningEntity, property.name)
            )
        } else {
            reader.skipValue()
        }
    }

    protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
        new BsonPersistentEntityCodec(codecRegistry, associatedEntity)
    }
}
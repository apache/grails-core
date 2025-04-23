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

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingMap
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.types.Basic

/**
 * A {@PropertyEncoder} capable of encoding {@Basic}  collection types
 */
@CompileStatic
class BasicCollectionTypeEncoder implements PropertyEncoder<Basic> {

    @Override
    void encode(BsonWriter writer, Basic property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        def marshaller = property.customTypeMarshaller
        if(marshaller) {
            CustomTypeEncoder.encode(codecRegistry, encoderContext, writer, property, marshaller, value)
        }
        else {
            writer.writeName( MappingUtils.getTargetKey(property) )

            def collectionType = property.type
            Codec<Object> codec

            final boolean isSet = Set.isAssignableFrom(collectionType)

            if(isSet) {
                codec = (Codec<Object>)codecRegistry.get(List)
            }
            else {
                codec = (Codec<Object>)codecRegistry.get(collectionType)
            }
            codec.encode(writer,  isSet ? value as List : value, encoderContext)
            def parent = parentAccess.entity
            if(parent instanceof DirtyCheckable) {
                if(value instanceof Collection) {
                    def propertyName = property.name
                    parentAccess.setPropertyNoConversion(
                            propertyName,
                            DirtyCheckingSupport.wrap(value, parent, propertyName)
                    )
                }
                else if(value instanceof Map &&  !(value instanceof Bson)) {
                    def propertyName = property.name
                    parentAccess.setPropertyNoConversion(
                            propertyName,
                            new DirtyCheckingMap(value, parent, propertyName)
                    )
                }
            }
        }
    }
}

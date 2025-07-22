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

import groovy.transform.PackageScope
import org.bson.BsonReader
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller
import org.grails.datastore.bson.codecs.CodecExtensions
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Custom


/**
 * A {@PropertyDecoder} capable of decoding {@Custom} types
 */
class CustomTypeDecoder implements PropertyDecoder<Custom> {

    @Override
    void decode(BsonReader reader, Custom property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        CustomTypeMarshaller marshaller = property.customTypeMarshaller

        decode(codecRegistry, reader, decoderContext, marshaller, property, entityAccess)
    }


    protected static void decode(CodecRegistry codecRegistry, BsonReader reader, DecoderContext decoderContext, CustomTypeMarshaller marshaller, PersistentProperty property, EntityAccess entityAccess) {
        def bsonType = reader.currentBsonType

        if(marshaller instanceof CodecCustomTypeMarshaller) {
            Codec codec = marshaller.codec
            def value = codec.decode(reader, decoderContext)
            if (value != null) {
                entityAccess.setPropertyNoConversion(property.name, value)
            }
        }
        else {

            def codec = CodecExtensions.getCodecForBsonType(bsonType, codecRegistry)
            if(codec != null) {
                def decoded = codec.decode(reader, decoderContext)
                def value = marshaller.read(property, new Document(
                        MappingUtils.getTargetKey(property),
                        decoded
                ))
                if (value != null) {
                    entityAccess.setProperty(property.name, value)
                }
            }
            else {
                reader.skipValue()
            }
        }
    }
}

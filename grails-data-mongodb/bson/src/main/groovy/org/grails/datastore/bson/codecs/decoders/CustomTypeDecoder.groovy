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
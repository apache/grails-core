package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Custom

/**
 * A {@PropertyEncoder} capable of encoding {@Custom} types
 */
@CompileStatic
class CustomTypeEncoder implements PropertyEncoder<Custom> {

    @Override
    void encode(BsonWriter writer, Custom property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        def marshaller = property.customTypeMarshaller
        encode(codecRegistry, encoderContext, writer, property, marshaller, value)

    }

    protected static void encode(CodecRegistry codecRegistry, EncoderContext encoderContext, BsonWriter writer, PersistentProperty property, CustomTypeMarshaller marshaller, value) {
        String targetName = MappingUtils.getTargetKey(property)
        if(marshaller instanceof CodecCustomTypeMarshaller) {
            writer.writeName(targetName)
            Codec codec = marshaller.codec
            codec.encode(writer,value, encoderContext)
        }
        else {
            def document = new Document()
            marshaller.write(property, value, document)

            Object converted = document.get(targetName)
            if(converted != null) {
                Codec codec = (Codec) codecRegistry.get(converted.getClass())
                if (codec) {
                    writer.writeName(targetName)
                    codec.encode(writer, converted, encoderContext)
                }
            }
        }
    }
}
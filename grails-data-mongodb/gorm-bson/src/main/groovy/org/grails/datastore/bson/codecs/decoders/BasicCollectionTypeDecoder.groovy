package org.grails.datastore.bson.codecs.decoders

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingMap
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.types.Basic

/**
 * A {@PropertyDecoder} capable of decoding {@Basic} collection types
 */
@CompileStatic
class BasicCollectionTypeDecoder implements PropertyDecoder<Basic> {

    @CompileDynamic
    @Override
    void decode(BsonReader reader, Basic property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        CustomTypeMarshaller marshaller = property.customTypeMarshaller

        if(marshaller) {
            CustomTypeDecoder.decode(codecRegistry, reader, decoderContext, marshaller, property, entityAccess)
        }
        else {
            def conversionService = entityAccess.persistentEntity.mappingContext.conversionService
            def componentType = property.componentType
            def collectionType = property.type
            Codec codec

            if(Set.isAssignableFrom(collectionType)) {
                codec = codecRegistry.get(List)
            }
            else {
                codec = codecRegistry.get(collectionType)
            }
            def value = codec.decode(reader, decoderContext)
            def entity = entityAccess.entity
            if(value instanceof Collection) {
                def converted = value.collect() { conversionService.convert(it, componentType) }


                if(entity instanceof DirtyCheckable) {
                    converted = DirtyCheckingSupport.wrap(converted, (DirtyCheckable) entity, property.name)
                }
                entityAccess.setProperty( property.name, converted )
            }
            else if(value instanceof Map) {
                def converted = value.collectEntries() { Map.Entry entry ->
                    def v = entry.value
                    entry.value = conversionService.convert(v, componentType)
                    return entry
                }
                if(entity instanceof DirtyCheckable) {
                    converted = new DirtyCheckingMap(converted, (DirtyCheckable) entity, property.name)
                }
                entityAccess.setProperty( property.name, converted)
            }
        }
    }
}
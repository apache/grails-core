package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Embedded

/**
 * A {@PropertyEncoder} capable of encoding {@Embedded} association types
 */
@CompileStatic
class EmbeddedEncoder implements PropertyEncoder<Embedded> {

    @Override
    void encode(BsonWriter writer, Embedded property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        if(value != null) {

            def mappingContext = parentAccess.persistentEntity.mappingContext
            PersistentEntity associatedEntity = mappingContext.getPersistentEntity(value.getClass().name)
            if(associatedEntity == null) {
                associatedEntity = property.associatedEntity
            }

            writer.writeName MappingUtils.getTargetKey(property)

            def reflector = mappingContext.getEntityReflector(associatedEntity)
            BsonPersistentEntityCodec codec = createEmbeddedEntityCodec(codecRegistry, associatedEntity)


            def identifier = reflector.getIdentifier(value)

            def hasIdentifier = identifier != null
            codec.encode(writer, value, encoderContext, hasIdentifier)
        }
    }

    protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
        new BsonPersistentEntityCodec(codecRegistry, associatedEntity)
    }
}
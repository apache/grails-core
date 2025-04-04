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
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToOne


/**
 * A {@PropertyEncoder} capable of encoding {@EmbeddedCollection} collection types
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class EmbeddedCollectionEncoder implements PropertyEncoder<EmbeddedCollection> {

    @Override
    void encode(BsonWriter writer, EmbeddedCollection property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {

        writer.writeName MappingUtils.getTargetKey(property)

        def associatedEntity = property.associatedEntity
        BsonPersistentEntityCodec associatedCodec = createEmbeddedEntityCodec(codecRegistry, associatedEntity)
        def isBidirectional = property.isBidirectional()
        Association inverseSide = isBidirectional ? property.inverseSide : null
        String inverseProperty = isBidirectional ? inverseSide.name : null
        def isToOne = inverseSide instanceof ToOne
        def mappingContext = parentAccess.persistentEntity.mappingContext

        if(Collection.isInstance(value)) {
            writer.writeStartArray()

            for(v in value) {
                if(v != null) {
                    BsonPersistentEntityCodec codec = associatedCodec
                    PersistentEntity entity = associatedEntity

                    def cls = v.getClass()
                    if(cls != associatedEntity.javaClass) {
                        // try subclass

                        def childEntity = mappingContext.getPersistentEntity(cls.name)
                        if(childEntity != null) {
                            entity = childEntity
                            codec = (BsonPersistentEntityCodec)codecRegistry.get(cls)
                        }
                        else {
                            continue
                        }
                    }

                    def ea = mappingContext.getEntityReflector(entity)
                    def id = ea.getIdentifier(v)
                    if(isBidirectional) {
                        if(isToOne) {
                            ea.setProperty(v, inverseProperty, parentAccess.entity)
                        }
                    }

                    codec.encode(writer, v, encoderContext, id != null)
                }
            }
            writer.writeEndArray()
        }
        else if(Map.isInstance(value)) {
            writer.writeStartDocument()

            for(e in value) {
                Map.Entry<String, Object> entry = (Map.Entry<String, Object>)e

                writer.writeName entry.key


                def v = entry.value
                def ea = mappingContext.getEntityReflector(associatedEntity)
                def id = ea.getIdentifier(v)

                associatedCodec.encode(writer, v, encoderContext, id != null)

            }
            writer.writeEndDocument()
        }


    }

    protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
        new BsonPersistentEntityCodec(codecRegistry, associatedEntity)
    }
}
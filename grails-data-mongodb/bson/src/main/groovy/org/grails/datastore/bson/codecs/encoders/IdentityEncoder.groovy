package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Identity

/**
 * A {@PropertyEncoder} capable of encoding the {@link org.grails.datastore.mapping.model.types.Identity}
 */
@CompileStatic
class IdentityEncoder implements PropertyEncoder<Identity> {

    @Override
    void encode(BsonWriter writer, Identity property, Object id, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        writer.writeName(getIdentifierName(property))

        if (id instanceof ObjectId) {
            writer.writeObjectId(id)
        } else if (id instanceof Number) {
            writer.writeInt64(((Number) id).toLong())
        } else {
            writer.writeString(id.toString())
        }

    }

    protected String getIdentifierName(Identity property) {
        String[] identifierName = property.getOwner().mapping.identifier?.identifierName
        if(identifierName != null) {
            return identifierName[0]
        }
        else {
            return GormProperties.IDENTITY
        }
    }
}

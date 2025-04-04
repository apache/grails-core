package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Identity
import org.grails.datastore.mapping.model.types.TenantId

/**
 * Encodes the TenantId
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantIdEncoder implements PropertyEncoder<TenantId> {

    @Override
    void encode(BsonWriter writer, TenantId property, Object id, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        writer.writeName( MappingUtils.getTargetKey(property) )
        SimpleEncoder.SIMPLE_TYPE_ENCODERS.get(property.type).encode(writer, property, id)
    }

}


package org.grails.datastore.bson.codecs.decoders

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.TenantId

/**
 * Decodes the tenant id
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantIdDecoder implements PropertyDecoder<TenantId> {
    @Override
    void decode(BsonReader reader, TenantId property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        BsonType bsonType = reader.currentBsonType
        def decoder = SimpleDecoder.SIMPLE_TYPE_DECODERS.get(property.type)
        if(bsonType != decoder.bsonType()) {
            SimpleDecoder.DEFAULT_DECODERS.get(bsonType).decode(reader, property, entityAccess)
        }
        else {
            decoder.decode reader, property, entityAccess
        }
    }
}

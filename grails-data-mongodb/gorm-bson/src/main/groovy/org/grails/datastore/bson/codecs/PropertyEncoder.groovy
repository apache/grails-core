package org.grails.datastore.bson.codecs

import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentProperty

/**
 * An interface for encoding PersistentProperty instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface PropertyEncoder<T extends PersistentProperty> {

    /**
     * Encodes a property to the given writer
     *
     * @param writer The {@link BsonWriter}
     * @param property The property
     * @param value The value
     * @param parentAccess Access to the parent entity
     * @param encoderContext The encoder context
     * @param codecRegistry The {@link CodecRegistry}
     */
    void encode(BsonWriter writer, T property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry)
}

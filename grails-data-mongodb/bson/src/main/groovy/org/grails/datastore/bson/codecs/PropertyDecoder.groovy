package org.grails.datastore.bson.codecs

import org.bson.BsonReader
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentProperty

/**
 * An interface for encoding PersistentProperty instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface PropertyDecoder<T extends PersistentProperty> {

    /**
     * Decodes a persistent property using the given reader
     *
     * @param reader The {@link BsonReader}
     * @param property The property
     * @param entityAccess Access to the entity
     * @param decoderContext The decoder context
     * @param codecRegistry The code registry
     */
    void decode(BsonReader reader, T property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry)
}

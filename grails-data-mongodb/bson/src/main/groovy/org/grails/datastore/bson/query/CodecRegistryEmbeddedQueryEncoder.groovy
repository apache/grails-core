package org.grails.datastore.bson.query

import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Embedded

/**
 * Default embedded encoder that uses the codec registry
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class CodecRegistryEmbeddedQueryEncoder implements EmbeddedQueryEncoder {

    final CodecRegistry codecRegistry

    CodecRegistryEmbeddedQueryEncoder(CodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry
    }

    @Override
    Object encode(Embedded embedded, Object instance) {
        PersistentEntity associatedEntity = embedded.associatedEntity
        Codec codec = codecRegistry.get(associatedEntity.javaClass)
        if(codec == null) {
            codec = new BsonPersistentEntityCodec(codecRegistry, associatedEntity)
        }
        final BsonDocument doc = new BsonDocument();
        codec.encode(new BsonDocumentWriter(doc), instance, BsonQuery.ENCODER_CONTEXT);
        return doc;
    }
}

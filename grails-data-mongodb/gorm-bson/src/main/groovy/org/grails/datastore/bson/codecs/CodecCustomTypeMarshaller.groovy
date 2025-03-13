package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonWriter
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query

/**
 * Custom type handler for types that have codecs
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class CodecCustomTypeMarshaller implements CustomTypeMarshaller<Document, Document, Document> {
    final Codec codec
    final MappingContext mappingContext

    CodecCustomTypeMarshaller(Codec codec, MappingContext mappingContext) {
        this.codec = codec
        this.mappingContext = mappingContext
    }

    @Override
    boolean supports(MappingContext context) {
        return context == mappingContext
    }

    @Override
    boolean supports(Datastore datastore) {
        return false
    }

    @Override
    Class getTargetType() {
        return codec.encoderClass
    }

    @Override
    Object write(PersistentProperty property, Document value, Document nativeTarget) {
        throw new UnsupportedOperationException("Use the codec directly");
    }

    @Override
    Document query(PersistentProperty property, Query.PropertyCriterion criterion, Document nativeQuery) {
        throw new UnsupportedOperationException("Use the codec directly");
    }

    @Override
    Document read(PersistentProperty property, Document source) {
        throw new UnsupportedOperationException("Use the codec directly");
    }

}

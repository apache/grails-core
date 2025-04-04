package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.grails.datastore.bson.codecs.temporal.OffsetDateTimeBsonConverter

import java.time.OffsetDateTime

/**
 * A class to translate a {@link OffsetDateTime} in MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
class OffsetDateTimeCodec implements Codec<OffsetDateTime>, OffsetDateTimeBsonConverter {

    @Override
    OffsetDateTime decode(BsonReader reader, DecoderContext decoderContext) {
        read(reader)
    }

    @Override
    void encode(BsonWriter writer, OffsetDateTime value, EncoderContext encoderContext) {
        write(writer, value)
    }

    @Override
    Class<OffsetDateTime> getEncoderClass() { OffsetDateTime }
}

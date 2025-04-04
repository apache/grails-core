package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.grails.datastore.bson.codecs.temporal.LocalDateTimeBsonConverter

import java.time.LocalDateTime

/**
 * A class to translate a {@link LocalDateTime} in MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateTimeCodec implements Codec<LocalDateTime>, LocalDateTimeBsonConverter {

    @Override
    LocalDateTime decode(BsonReader reader, DecoderContext decoderContext) {
        return read(reader)
    }

    @Override
    void encode(BsonWriter writer, LocalDateTime value, EncoderContext encoderContext) {
        write(writer, value)
    }

    @Override
    Class<LocalDateTime> getEncoderClass() { LocalDateTime }
}

package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.grails.datastore.bson.codecs.temporal.ZonedDateTimeBsonConverter

import java.time.ZonedDateTime

/**
 * A class to translate a {@link ZonedDateTime} in MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
class ZonedDateTimeCodec implements Codec<ZonedDateTime>, ZonedDateTimeBsonConverter {

    @Override
    ZonedDateTime decode(BsonReader reader, DecoderContext decoderContext) {
        read(reader)
    }

    @Override
    void encode(BsonWriter writer, ZonedDateTime value, EncoderContext encoderContext) {
        write(writer, value)
    }

    @Override
    Class<ZonedDateTime> getEncoderClass() { ZonedDateTime }
}
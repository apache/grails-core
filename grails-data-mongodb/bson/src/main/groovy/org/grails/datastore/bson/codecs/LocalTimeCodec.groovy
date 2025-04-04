package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.grails.datastore.bson.codecs.temporal.LocalTimeBsonConverter

import java.time.LocalTime

/**
 * A class to translate a {@link LocalTime} in MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalTimeCodec implements Codec<LocalTime>, LocalTimeBsonConverter {

    @Override
    LocalTime decode(BsonReader reader, DecoderContext decoderContext) {
        read(reader)
    }

    @Override
    void encode(BsonWriter writer, LocalTime value, EncoderContext encoderContext) {
        write(writer, value)
    }

    @Override
    Class<LocalTime> getEncoderClass() { LocalTime }
}

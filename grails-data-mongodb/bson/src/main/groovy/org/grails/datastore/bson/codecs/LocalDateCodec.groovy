package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.grails.datastore.bson.codecs.temporal.LocalDateBsonConverter

import java.time.LocalDate

/**
 * A class to translate a {@link LocalDate} in MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateCodec implements Codec<LocalDate>, LocalDateBsonConverter {

    @Override
    LocalDate decode(BsonReader reader, DecoderContext decoderContext) {
        return read(reader)
    }

    @Override
    void encode(BsonWriter writer, LocalDate value, EncoderContext encoderContext) {
        write(writer, value)
    }

    @Override
    Class<LocalDate> getEncoderClass() { LocalDate }
}

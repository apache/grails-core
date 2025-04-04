package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.grails.datastore.bson.codecs.temporal.PeriodBsonConverter

import java.time.Period

/**
 * A class to translate a {@link java.time.Period} in MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
class PeriodCodec implements Codec<Period>, PeriodBsonConverter {

    @Override
    Period decode(BsonReader reader, DecoderContext decoderContext) {
        read(reader)
    }

    @Override
    void encode(BsonWriter writer, Period value, EncoderContext encoderContext) {
        write(writer, value)
    }

    @Override
    Class<Period> getEncoderClass() { Period }
}
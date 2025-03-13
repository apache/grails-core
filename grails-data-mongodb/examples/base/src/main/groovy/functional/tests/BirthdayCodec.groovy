package functional.tests

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

/**
 * Created by graemerocher on 17/10/16.
 */
class BirthdayCodec implements Codec<Birthday> {
    Birthday decode(BsonReader reader, DecoderContext decoderContext) {
        return new Birthday(new Date(reader.readDateTime()))
    }
    void encode(BsonWriter writer, Birthday value, EncoderContext encoderContext) {
        writer.writeDateTime(value.date.time)
    }
    Class<Birthday> getEncoderClass() { Birthday }
}
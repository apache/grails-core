package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.Decimal128

/**
 * A codec for BigDecimal
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class BigDecimalCodec implements Codec<BigDecimal> {
    @Override
    BigDecimal decode(BsonReader reader, DecoderContext decoderContext) {
        return reader.readDecimal128().bigDecimalValue()
    }

    @Override
    void encode(BsonWriter writer, BigDecimal value, EncoderContext encoderContext) {
        writer.writeDecimal128(new Decimal128(value))
    }

    @Override
    Class<BigDecimal> getEncoderClass() {
        return BigDecimal
    }
}

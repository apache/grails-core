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
class BigIntegerCodec implements Codec<BigInteger> {
    @Override
    BigInteger decode(BsonReader reader, DecoderContext decoderContext) {
        return reader.readDecimal128().bigDecimalValue().toBigInteger()
    }

    @Override
    void encode(BsonWriter writer, BigInteger value, EncoderContext encoderContext) {
        writer.writeDecimal128(new Decimal128(value.toBigDecimal()))
    }

    @Override
    Class<BigInteger> getEncoderClass() {
        return BigInteger
    }
}

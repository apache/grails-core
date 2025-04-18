package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.ArrayValue
import graphql.language.IntValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.lang.reflect.Array

/**
 * Coercion class for whole number arrays
 *
 * @param <T> The type of the property
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ByteArrayCoercion implements Coercing<Byte[], Byte[]> {

    private static final BigInteger BYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE)
    private static final BigInteger BYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE)

    protected Optional<Byte[]> convert(Object input) {
        if (input instanceof Byte[]) {
            Optional.of((Byte[]) input)
        }
        else if (input instanceof Collection) {
            Collection c = (Collection) input
            Byte[] converted = new Byte[c.size()]
            for (int i = 0; i < c.size(); i++) {
                converted[i] = (byte)c[i]
            }
            Optional.of(converted)
        }
        else if (input.class.array) {
            Byte[] bytes = new Byte[Array.getLength(input)]
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte)Array.get(input, i)
            }
            Optional.of(bytes)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Byte[] serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a Byte[]")
        }
    }

    @Override
    Byte[] parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a Byte[]")
        }
    }

    private Byte parse(Value input) {
        if (!(input instanceof IntValue)) {
            return null
        }
        BigInteger value = ((IntValue) input).value
        if (value < BYTE_MIN || value > BYTE_MAX) {
            null
        } else {
            value.byteValue()
        }
    }

    @Override
    Byte[] parseLiteral(Object input) {
        if (input instanceof ArrayValue) {
            List<Byte> returnList = []
            List<Value> values = ((ArrayValue) input).values
            for (Value value: values) {
                Byte parsedValue = parse(value)
                returnList.add(parsedValue)
            }
            (Byte[])returnList.toArray()
        }
        else {
            null
        }
    }
}

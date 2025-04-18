package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.sql.Timestamp

/**
 * Default {@link Timestamp} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class TimestampCoercion implements Coercing<Timestamp, Timestamp> {

    protected Optional<Timestamp> convert(Object input) {
        if (input instanceof Timestamp) {
            Optional.of((Timestamp) input)
        }
        else if (input instanceof String) {
            parseTimestamp((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Timestamp serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.sql.Timestamp")
        }
    }

    @Override
    Timestamp parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.sql.Timestamp")
        }
    }

    @Override
    Timestamp parseLiteral(Object input) {
        if (input instanceof IntValue) {
            new Timestamp(((IntValue) input).value.longValue())
        }
        else if (input instanceof StringValue) {
            parseTimestamp(((StringValue) input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<Timestamp> parseTimestamp(String input) {
        try {
            Optional.of(Timestamp.valueOf(input))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}

package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.sql.Time

/**
 * Default {@link java.sql.Time} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class TimeCoercion implements Coercing<Time, Time> {

    protected Optional<Time> convert(Object input) {
        if (input instanceof Time) {
            Optional.of((Time) input)
        }
        else if (input instanceof String) {
            parseTime((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Time serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.sql.Time")
        }
    }

    @Override
    Time parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.sql.Time")
        }
    }

    @Override
    Time parseLiteral(Object input) {
        if (input instanceof IntValue) {
            new Time(((IntValue) input).value.longValue())
        }
        else if (input instanceof StringValue) {
            parseTime(((StringValue) input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<Time> parseTime(String input) {
        try {
            Optional.of(Time.valueOf(input))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}

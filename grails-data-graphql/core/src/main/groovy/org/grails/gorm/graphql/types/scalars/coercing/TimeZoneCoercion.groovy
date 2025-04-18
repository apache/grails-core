package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

/**
 * Default {@link TimeZone} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class TimeZoneCoercion implements Coercing<TimeZone, TimeZone> {

    protected Optional<TimeZone> convert(Object input) {
        if (input instanceof TimeZone) {
            Optional.of((TimeZone) input)
        }
        else if (input instanceof String) {
            parseTimeZone((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    TimeZone serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.util.TimeZone")
        }
    }

    @Override
    TimeZone parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.util.TimeZone")
        }
    }

    @Override
    TimeZone parseLiteral(Object input) {
        if (input instanceof StringValue) {
            parseTimeZone(((StringValue)input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<TimeZone> parseTimeZone(String input) {
        Optional.of(TimeZone.getTimeZone(input))
    }
}

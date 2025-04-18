package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.sql.Date

/**
 * Default {@link java.sql.Date} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class SqlDateCoercion implements Coercing<Date, Date> {

    protected Optional<Date> convert(Object input) {
        if (input instanceof Date) {
            Optional.of((Date) input)
        }
        else if (input instanceof String)  {
            parseDate((String) input)
        }
        else if (input instanceof Long) {
            Optional.of(new Date((Long) input))
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Date serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.sql.Date")
        }
    }

    @Override
    Date parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.sql.Date")
        }
    }

    @Override
    Date parseLiteral(Object input) {
        if (input instanceof IntValue) {
            new Date(((IntValue) input).value.longValue())
        }
        else if (input instanceof StringValue) {
            parseDate(((StringValue) input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<Date> parseDate(String value) {
        try {
            Optional.of(Date.valueOf(value))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}

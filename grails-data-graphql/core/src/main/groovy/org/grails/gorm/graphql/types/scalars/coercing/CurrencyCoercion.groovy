package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

/**
 * Default {@link Currency} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class CurrencyCoercion implements Coercing<Currency, Currency> {

    protected Optional<Currency> convert(Object input) {
        if (input instanceof Currency) {
            Optional.of((Currency) input)
        }
        else if (input instanceof String) {
            Optional.of(Currency.getInstance((String) input))
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Currency serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a Currency")
        }
    }

    @Override
    Currency parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a Currency")
        }
    }

    @Override
    Currency parseLiteral(Object input) {
        if (input instanceof StringValue) {
            Currency.getInstance(((StringValue)input).value)
        }
        else {
            null
        }
    }
}

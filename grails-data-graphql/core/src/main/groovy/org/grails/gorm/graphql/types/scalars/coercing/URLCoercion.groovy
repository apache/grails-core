package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

/**
 * Default {@link URL} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class URLCoercion implements Coercing<URL, URL> {

    protected Optional<URL> convert(Object input) {
        if (input instanceof URL) {
            Optional.of((URL) input)
        }
        else if (input instanceof String) {
            parseURL((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    URL serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.net.URL")
        }
    }

    @Override
    URL parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.net.URL")
        }
    }

    @Override
    URL parseLiteral(Object input) {
        if (input instanceof StringValue) {
            parseURL(((StringValue)input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<URL> parseURL(String value) {
        try {
            Optional.of(new URL(value))
        } catch (Exception e) {
            Optional.empty()
        }
    }
     
}

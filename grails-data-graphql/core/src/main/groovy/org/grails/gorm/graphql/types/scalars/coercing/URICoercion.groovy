package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

/**
 * Default {@link URI} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class URICoercion implements Coercing<URI, URI> {

    protected Optional<URI> convert(Object input) {
        if (input instanceof URI) {
            Optional.of((URI) input)
        }
        else if (input instanceof String) {
            parseURI((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    URI serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.net.URI")
        }
    }

    @Override
    URI parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.net.URI")
        }
    }

    @Override
    URI parseLiteral(Object input) {
        if (input instanceof StringValue) {
            parseURI(((StringValue)input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<URI> parseURI(String value) {
        try {
            Optional.of(new URI(value))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}

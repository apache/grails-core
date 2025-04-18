package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

/**
 * Default {@link UUID} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class UUIDCoercion implements Coercing<UUID, UUID> {

    protected Optional<UUID> convert(Object input) {
        if (input instanceof UUID) {
            Optional.of((UUID) input)
        }
        else if (input instanceof String) {
            parseUUID((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    UUID serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.util.UUID")
        }
    }

    @Override
    UUID parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.util.UUID")
        }
    }

    @Override
    UUID parseLiteral(Object input) {
        if (input instanceof StringValue) {
            parseUUID(((StringValue)input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<UUID> parseUUID(String value) {
        try {
            Optional.of(UUID.fromString(value))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}

package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.ArrayValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic
import java.lang.reflect.Array

/**
 * Conversion class for string arrays
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class CharacterArrayCoercion implements Coercing<Character[], Character[]> {

    protected Optional<Character[]> convert(Object input) {
        if (input instanceof Character[]) {
            Optional.of((Character[]) input)
        }
        else if (input instanceof Collection) {
            Collection c = (Collection) input
            Character[] converted = new Character[c.size()]
            for (int i = 0; i < c.size(); i++) {
                converted[i] = new Character((char)c[i])
            }
            Optional.of(converted)
        }
        else if (input.class.array) {
            Character[] chars = new Character[Array.getLength(input)]
            for (int i = 0; i < chars.length; i++) {
                chars[i] = new Character((char)Array.get(input, i))
            }
            Optional.of(chars)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Character[] serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a Character[]")
        }
    }

    @Override
    Character[] parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a Character[]")
        }
    }

    private Character convertValue(Value input) {
        if (!(input instanceof StringValue)) {
            return null
        }
        String value = ((StringValue) input).value
        if (value.length() != 1) {
            return null
        }
        new Character(value.charAt(0))
    }

    @Override
    Character[] parseLiteral(Object input) {
        if (input instanceof ArrayValue) {
            List<Value> values = ((ArrayValue) input).values
            Character[] returnArray = new Character[values.size()]
            for (int i = 0; i < values.size(); i++) {
                Character convertedValue = convertValue(values[i])
                returnArray[i] = convertedValue
            }
            returnArray
        }
        else {
            null
        }
    }
}
